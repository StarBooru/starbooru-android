//! Share links: chosen posts anyone with the link sees, media only, without
//! signing in. They are for private servers: a public one shares a post by
//! its own address, and `create_share` there is `share_by_address`.

use starbooru_client::Client;

use super::{MediaKind, millis};

/// The longest a link can last; it may also last until removed.
#[uniffi::export]
pub fn max_share_days() -> u32 {
    starbooru_core::share::MAX_SHARE_DAYS
}

/// Whether chats and sites show a link with its first picture. The server
/// shows none while its own switch is off (`ServerInfo::link_previews`).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum LinkPreview {
    /// As the account's `SharingSettings::previews` says.
    Account,
    Shown,
    Hidden,
}

impl LinkPreview {
    fn from_core(preview: Option<bool>) -> Self {
        match preview {
            None => LinkPreview::Account,
            Some(true) => LinkPreview::Shown,
            Some(false) => LinkPreview::Hidden,
        }
    }

    fn to_core(self) -> Option<bool> {
        match self {
            LinkPreview::Account => None,
            LinkPreview::Shown => Some(true),
            LinkPreview::Hidden => Some(false),
        }
    }
}

/// One of the account's links.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Share {
    pub id: i64,
    pub token: String,
    /// The page to give out: `<server>/s/<token>`.
    pub url: String,
    pub title: Option<String>,
    /// Posts it was made with; some may have gone to the bin since.
    pub posts: u32,
    /// Absolute and signed: the first post still shown.
    pub cover_url: Option<String>,
    pub created_at_ms: i64,
    /// `None`: until removed. An expired link stays listed until removed.
    pub expires_at_ms: Option<i64>,
    /// Times the link was opened.
    pub views: i64,
    pub last_viewed_at_ms: Option<i64>,
    /// The link's own choice.
    pub preview: LinkPreview,
    /// Whether it shows a preview now, after the server's and the account's
    /// settings.
    pub preview_on: bool,
}

impl Share {
    pub(crate) fn from_core(s: starbooru_core::Share, client: &Client) -> Self {
        Self {
            url: client.absolute(&starbooru_core::share::share_path(&s.token)),
            id: s.id,
            token: s.token,
            title: s.title,
            posts: s.posts,
            cover_url: s.cover_url.map(|u| client.absolute(&u)),
            created_at_ms: millis(s.created_at),
            expires_at_ms: s.expires_at.map(millis),
            views: s.views,
            last_viewed_at_ms: s.last_viewed_at.map(millis),
            preview: LinkPreview::from_core(s.preview),
            preview_on: s.preview_on,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewShare {
    /// 1 to 500, in the order shown; repeats are dropped.
    pub post_ids: Vec<i64>,
    /// At most 100 characters; blank is none.
    #[uniffi(default)]
    pub title: Option<String>,
    /// 1 to `max_share_days()`; `None` keeps it until removed.
    #[uniffi(default)]
    pub days: Option<u32>,
    /// `None` is `Account`.
    #[uniffi(default)]
    pub preview: Option<LinkPreview>,
}

impl From<NewShare> for starbooru_core::NewShare {
    fn from(s: NewShare) -> Self {
        Self {
            post_ids: s.post_ids,
            title: s.title,
            days: s.days,
            preview: s.preview.and_then(LinkPreview::to_core),
        }
    }
}

/// A change to a link. `None` leaves it as it is.
#[derive(Debug, Clone, Default, PartialEq, Eq, uniffi::Record)]
pub struct ShareUpdate {
    #[uniffi(default)]
    pub preview: Option<LinkPreview>,
}

impl From<ShareUpdate> for starbooru_core::ShareUpdate {
    fn from(u: ShareUpdate) -> Self {
        Self {
            preview: u.preview.map(LinkPreview::to_core),
        }
    }
}

/// The account's defaults for its links.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct SharingSettings {
    /// New links, and those that leave it to the account, show a preview.
    pub previews: bool,
}

impl From<starbooru_core::SharingSettings> for SharingSettings {
    fn from(s: starbooru_core::SharingSettings) -> Self {
        Self {
            previews: s.previews,
        }
    }
}

impl From<SharingSettings> for starbooru_core::SharingSettings {
    fn from(s: SharingSettings) -> Self {
        Self {
            previews: s.previews,
        }
    }
}

/// A post as a link shows it: the media, nothing about it.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SharedPost {
    pub kind: MediaKind,
    pub mime: String,
    pub width: Option<i32>,
    pub height: Option<i32>,
    pub duration_ms: Option<i64>,
    /// Absolute and signed, like a post's.
    pub file_url: String,
    pub thumb_url: Option<String>,
    pub sample_url: Option<String>,
}

impl SharedPost {
    fn from_core(p: starbooru_core::SharedPost, client: &Client) -> Self {
        Self {
            kind: p.kind.into(),
            mime: p.mime,
            width: p.width,
            height: p.height,
            duration_ms: p.duration_ms,
            file_url: client.absolute(&p.file_url),
            thumb_url: p.thumb_url.map(|u| client.absolute(&u)),
            sample_url: p.sample_url.map(|u| client.absolute(&u)),
        }
    }
}

/// What a link shows.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SharedView {
    pub title: Option<String>,
    /// The server's name.
    pub server: String,
    pub expires_at_ms: Option<i64>,
    /// In the link's order, without those binned since.
    pub posts: Vec<SharedPost>,
}

impl SharedView {
    pub(crate) fn from_core(v: starbooru_core::SharedView, client: &Client) -> Self {
        Self {
            title: v.title,
            server: v.server,
            expires_at_ms: v.expires_at.map(millis),
            posts: v
                .posts
                .into_iter()
                .map(|p| SharedPost::from_core(p, client))
                .collect(),
        }
    }
}

/// Where a share link points: the server to open with `Api::new(server,
/// None)` and the token for `shared`.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ShareLink {
    pub server: String,
    pub token: String,
}

/// The server and token of a share link's page (`<server>/s/<token>`), or
/// `None` for any other address.
#[uniffi::export]
pub fn share_link(url: &str) -> Option<ShareLink> {
    let mut url = url::Url::parse(url.trim()).ok()?;
    if !matches!(url.scheme(), "http" | "https")
        || !url.username().is_empty()
        || url.password().is_some()
    {
        return None;
    }
    let path = url.path().trim_end_matches('/').to_owned();
    let (root, token) = path.rsplit_once("/s/")?;
    if !starbooru_core::share::is_share_token(token) {
        return None;
    }
    let token = token.to_owned();
    url.set_query(None);
    url.set_fragment(None);
    url.set_path(root);
    Some(ShareLink {
        server: url.as_str().trim_end_matches('/').to_owned(),
        token,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    const TOKEN: &str = "0123456789abcdef0123456789abcdef";

    fn client() -> Client {
        Client::new("https://booru.example/sub").unwrap()
    }

    #[test]
    fn a_share_gets_its_page_and_cover_as_absolute_addresses() {
        let share: starbooru_core::Share = serde_json::from_value(serde_json::json!({
            "id": 1, "token": TOKEN, "title": "Trip", "posts": 3,
            "cover_url": "/media/thumb/ab.webp?sig=1", "created_at": "2026-11-24T00:00:00Z",
            "expires_at": "2026-11-25T00:00:00Z", "views": 4, "last_viewed_at": null,
            "preview": false, "preview_on": false
        }))
        .unwrap();
        let share = Share::from_core(share, &client());
        assert_eq!(share.url, format!("https://booru.example/sub/s/{TOKEN}"));
        assert_eq!(
            share.cover_url.as_deref(),
            Some("https://booru.example/sub/media/thumb/ab.webp?sig=1")
        );
        assert_eq!(share.expires_at_ms, Some(1_795_564_800_000));
        assert_eq!(share.last_viewed_at_ms, None);
        assert_eq!(share.preview, LinkPreview::Hidden);
    }

    #[test]
    fn previews_map_both_ways() {
        for preview in [None, Some(true), Some(false)] {
            assert_eq!(LinkPreview::from_core(preview).to_core(), preview);
        }
        let follow = ShareUpdate {
            preview: Some(LinkPreview::Account),
        };
        let json = serde_json::to_string(&starbooru_core::ShareUpdate::from(follow)).unwrap();
        assert_eq!(json, r#"{"preview":null}"#);
        let json =
            serde_json::to_string(&starbooru_core::ShareUpdate::from(ShareUpdate::default()))
                .unwrap();
        assert_eq!(json, "{}");
    }

    #[test]
    fn a_new_share_leaves_the_preview_to_the_account_unless_it_says() {
        let share = NewShare {
            post_ids: vec![3, 1],
            title: Some("Trip".into()),
            days: Some(7),
            preview: None,
        };
        let json = serde_json::to_value(starbooru_core::NewShare::from(share.clone())).unwrap();
        assert_eq!(
            json,
            serde_json::json!({"post_ids": [3, 1], "title": "Trip", "days": 7})
        );
        let account = NewShare {
            preview: Some(LinkPreview::Account),
            ..share.clone()
        };
        assert_eq!(starbooru_core::NewShare::from(account).preview, None);
        let shown = NewShare {
            preview: Some(LinkPreview::Shown),
            days: None,
            ..share
        };
        let json = serde_json::to_value(starbooru_core::NewShare::from(shown)).unwrap();
        assert_eq!(json["preview"], true);
        assert_eq!(json["days"], serde_json::Value::Null);
        assert_eq!(max_share_days(), 365);
    }

    #[test]
    fn a_shared_view_gets_absolute_addresses() {
        let view: starbooru_core::SharedView = serde_json::from_value(serde_json::json!({
            "title": null, "server": "StarBooru", "expires_at": null,
            "posts": [{"kind": "video", "mime": "video/mp4", "width": 640, "height": 360,
                       "duration_ms": 5000, "file_url": "/media/original/ab.mp4?sig=1",
                       "thumb_url": "/media/thumb/ab.webp?sig=2"}]
        }))
        .unwrap();
        let view = SharedView::from_core(view, &client());
        let post = &view.posts[0];
        assert_eq!(post.kind, MediaKind::Video);
        assert_eq!(
            post.file_url,
            "https://booru.example/sub/media/original/ab.mp4?sig=1"
        );
        assert_eq!(
            post.thumb_url.as_deref(),
            Some("https://booru.example/sub/media/thumb/ab.webp?sig=2")
        );
        assert_eq!(post.sample_url, None);
        assert_eq!(view.expires_at_ms, None);
    }

    #[test]
    fn reads_the_server_and_token_of_a_link() {
        let link = |server: &str| ShareLink {
            server: server.into(),
            token: TOKEN.into(),
        };
        assert_eq!(
            share_link(&format!("https://booru.example/s/{TOKEN}")),
            Some(link("https://booru.example"))
        );
        assert_eq!(
            share_link(&format!(" http://192.168.1.2:8080/sub/s/{TOKEN}/?x=1#top ")),
            Some(link("http://192.168.1.2:8080/sub"))
        );
        assert_eq!(
            share_link(&format!("https://booru.example/sub/s/{TOKEN}")).map(|l| l.server),
            Some(client().base_url().to_owned())
        );
        for other in [
            format!("https://booru.example/post/{TOKEN}"),
            format!("https://booru.example/s/{}", TOKEN.to_uppercase()),
            "https://booru.example/s/0123".to_owned(),
            format!("ftp://booru.example/s/{TOKEN}"),
            format!("https://ann:pw@booru.example/s/{TOKEN}"),
            "not an address".to_owned(),
        ] {
            assert_eq!(share_link(&other), None, "{other}");
        }
    }
}
