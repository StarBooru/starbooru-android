//! Other boorus: searching them through the server, the accounts and
//! searches kept there for them, and one of their posts. A public server
//! answers all of it with `remote_off`.

use starbooru_client::Client;

use super::{Rating, TagCategory, millis};

/// The booru APIs the server speaks.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum BooruApi {
    /// Danbooru 2.
    Danbooru,
    /// Gelbooru 0.2 and its forks, such as Safebooru.
    Gelbooru,
    /// yande.re, Konachan.
    Moebooru,
    /// e621 and e926.
    E621,
    /// Self-hosted Blombooru.
    Blombooru,
}

impl From<starbooru_core::BooruApi> for BooruApi {
    fn from(a: starbooru_core::BooruApi) -> Self {
        match a {
            starbooru_core::BooruApi::Danbooru => BooruApi::Danbooru,
            starbooru_core::BooruApi::Gelbooru => BooruApi::Gelbooru,
            starbooru_core::BooruApi::Moebooru => BooruApi::Moebooru,
            starbooru_core::BooruApi::E621 => BooruApi::E621,
            starbooru_core::BooruApi::Blombooru => BooruApi::Blombooru,
        }
    }
}

impl From<BooruApi> for starbooru_core::BooruApi {
    fn from(a: BooruApi) -> Self {
        match a {
            BooruApi::Danbooru => starbooru_core::BooruApi::Danbooru,
            BooruApi::Gelbooru => starbooru_core::BooruApi::Gelbooru,
            BooruApi::Moebooru => starbooru_core::BooruApi::Moebooru,
            BooruApi::E621 => starbooru_core::BooruApi::E621,
            BooruApi::Blombooru => starbooru_core::BooruApi::Blombooru,
        }
    }
}

/// A well-known site, offered as a preset.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct BooruSite {
    pub name: String,
    pub api: BooruApi,
    pub base: String,
}

/// The presets: any other site with one of the APIs works too.
#[uniffi::export]
pub fn booru_sites() -> Vec<BooruSite> {
    starbooru_core::SITES
        .iter()
        .map(|s| BooruSite {
            name: s.name.to_owned(),
            api: s.api.into(),
            base: s.base.to_owned(),
        })
        .collect()
}

/// An API's name, as people know it ("Danbooru", "e621").
#[uniffi::export]
pub fn booru_api_name(api: BooruApi) -> String {
    starbooru_core::BooruApi::from(api).name().to_owned()
}

/// What the site calls the two parts of an account: the login (`login`, or
/// Gelbooru's `user_id`) and the key (`api_key`, or Moebooru's
/// `password_hash`).
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct BooruFields {
    pub login: String,
    pub key: String,
}

#[uniffi::export]
pub fn booru_fields(api: BooruApi) -> BooruFields {
    let api = starbooru_core::BooruApi::from(api);
    BooruFields {
        login: api.login_field().to_owned(),
        key: api.key_field().to_owned(),
    }
}

/// An account on another booru kept on the server. Its key never comes
/// back.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct BooruAccount {
    pub id: i64,
    pub api: BooruApi,
    pub base: String,
    /// The login, or the user ID on Gelbooru.
    pub login: String,
    /// False once its key no longer opens (the server's secret changed):
    /// save it again.
    pub usable: bool,
    pub updated_at_ms: i64,
}

impl From<starbooru_core::BooruAccount> for BooruAccount {
    fn from(a: starbooru_core::BooruAccount) -> Self {
        Self {
            id: a.id,
            api: a.api.into(),
            base: a.base,
            login: a.login,
            usable: a.usable,
            updated_at_ms: millis(a.updated_at),
        }
    }
}

/// The account to keep for the site at `base`, in place of the one kept
/// for it before. Both parts are needed.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewBooruAccount {
    pub api: BooruApi,
    pub base: String,
    pub login: String,
    pub api_key: String,
}

impl From<NewBooruAccount> for starbooru_core::SaveBooruAccount {
    fn from(a: NewBooruAccount) -> Self {
        Self {
            api: a.api.into(),
            base: a.base,
            login: a.login,
            api_key: a.api_key,
        }
    }
}

/// A site to search: its API and its address.
#[derive(Debug, Clone, PartialEq, Eq, Hash, uniffi::Record)]
pub struct RemoteSite {
    pub api: BooruApi,
    pub base: String,
}

impl From<starbooru_core::RemoteSite> for RemoteSite {
    fn from(s: starbooru_core::RemoteSite) -> Self {
        Self {
            api: s.api.into(),
            base: s.base,
        }
    }
}

impl From<RemoteSite> for starbooru_core::RemoteSite {
    fn from(s: RemoteSite) -> Self {
        Self {
            api: s.api.into(),
            base: s.base,
        }
    }
}

/// A search on other sites the account kept.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SavedRemoteSearch {
    pub id: i64,
    pub name: String,
    pub query: String,
    pub sites: Vec<RemoteSite>,
    pub created_at_ms: i64,
}

impl From<starbooru_core::SavedRemoteSearch> for SavedRemoteSearch {
    fn from(s: starbooru_core::SavedRemoteSearch) -> Self {
        Self {
            id: s.id,
            name: s.name,
            query: s.query,
            sites: s.sites.into_iter().map(Into::into).collect(),
            created_at_ms: millis(s.created_at),
        }
    }
}

/// A search to keep: 1 to 8 sites; an empty query is their newest posts.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewRemoteSearch {
    pub name: String,
    #[uniffi(default)]
    pub query: String,
    pub sites: Vec<RemoteSite>,
}

impl From<NewRemoteSearch> for starbooru_core::SaveRemoteSearch {
    fn from(s: NewRemoteSearch) -> Self {
        Self {
            name: s.name,
            query: s.query,
            sites: s.sites.into_iter().map(Into::into).collect(),
        }
    }
}

/// One page of a search on one site, which the server fetches.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct RemoteSearch {
    pub api: BooruApi,
    pub base: String,
    /// In the site's own syntax; `rating:safe` and `order:score` are turned
    /// into it.
    #[uniffi(default)]
    pub tags: String,
    /// From 1.
    #[uniffi(default = 1)]
    pub page: u32,
    /// An account on that site, sent only to it and only with both parts.
    /// Without, the one kept for `base` is used, if any.
    #[uniffi(default)]
    pub login: Option<String>,
    #[uniffi(default)]
    pub api_key: Option<String>,
}

impl From<RemoteSearch> for starbooru_core::RemoteSearch {
    fn from(s: RemoteSearch) -> Self {
        Self {
            api: s.api.into(),
            base: s.base,
            tags: s.tags,
            page: s.page.max(1),
            login: s.login,
            api_key: s.api_key,
        }
    }
}

/// Which of a post's tags aren't general ones; each is in its `tags` too.
#[derive(Debug, Clone, Default, PartialEq, Eq, uniffi::Record)]
pub struct RemoteTagGroups {
    pub artist: Vec<String>,
    pub copyright: Vec<String>,
    pub character: Vec<String>,
    pub meta: Vec<String>,
}

/// A post on another booru.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct RemotePost {
    pub id: u64,
    /// The site it is on, as its address.
    pub site: String,
    /// The post's page there: the source to import it with, and what its
    /// import is found by (`imports_by_source`).
    pub page_url: String,
    /// A small picture. Sites that only show their files on their own pages
    /// come through this server, as an absolute address on it; others are
    /// the site's own, as it gave them (some are plain `http:`). Empty when
    /// the site gave none.
    pub preview_url: String,
    /// A copy sized for a screen, the same way.
    pub sample_url: Option<String>,
    /// The original on its site, for importing. Empty when the site showed
    /// none (Gelbooru without a key): import by `page_url` then, without a
    /// source, or ask `Api::remote_post`.
    pub file_url: String,
    /// Tags valid here; the site's others are left out.
    pub tags: Vec<String>,
    pub groups: RemoteTagGroups,
    /// `tags` as an import sends them, artists, copyrights and characters
    /// first with the prefix that sets their category.
    pub import_tags: Vec<String>,
    /// The file is a video, by its address.
    pub video: bool,
    pub rating: Rating,
    /// 0 when the site doesn't say.
    pub width: u32,
    pub height: u32,
    pub md5: Option<String>,
    /// Where the site says the picture comes from.
    pub source: Option<String>,
    pub score: Option<i64>,
    pub created_at_ms: Option<i64>,
    pub file_size: Option<u64>,
    /// The post here imported from it, if any.
    pub post_id: Option<i64>,
}

/// A picture address as the remote answer has it: the server's relayed
/// ones are paths on it.
fn picture(url: String, client: &Client) -> String {
    if url.starts_with('/') {
        client.absolute(&url)
    } else {
        url
    }
}

impl RemotePost {
    pub(crate) fn from_core(p: starbooru_core::RemotePost, client: &Client) -> Self {
        let import_tags = p.import_tags();
        let video = p.is_video();
        Self {
            id: p.id,
            site: p.site,
            page_url: p.page_url,
            preview_url: picture(p.preview_url, client),
            sample_url: p.sample_url.map(|u| picture(u, client)),
            file_url: p.file_url,
            tags: p.tags,
            groups: RemoteTagGroups {
                artist: p.groups.artist,
                copyright: p.groups.copyright,
                character: p.groups.character,
                meta: p.groups.meta,
            },
            import_tags,
            video,
            rating: p.rating.into(),
            width: p.width,
            height: p.height,
            md5: p.md5,
            source: p.source,
            score: p.score,
            created_at_ms: p.created_at.map(millis),
            file_size: p.file_size,
            post_id: p.post_id,
        }
    }
}

/// One page of a search on another site.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct RemotePage {
    pub posts: Vec<RemotePost>,
    /// The site sent a full page and may have more.
    pub more: bool,
}

impl RemotePage {
    pub(crate) fn from_core(p: starbooru_core::RemotePage, client: &Client) -> Self {
        Self {
            posts: p
                .posts
                .into_iter()
                .map(|post| RemotePost::from_core(post, client))
                .collect(),
            more: p.more,
        }
    }
}

/// A tag on another site, for its search box.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct RemoteTag {
    /// As the site writes it, to put in a search there.
    pub name: String,
    pub category: TagCategory,
    pub count: Option<u64>,
}

impl From<starbooru_core::RemoteTag> for RemoteTag {
    fn from(t: starbooru_core::RemoteTag) -> Self {
        Self {
            name: t.name,
            category: t.category.into(),
            count: t.count,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn relayed_pictures_get_the_servers_address_and_the_sites_own_stay() {
        let client = Client::new("https://booru.example/sub").unwrap();
        let page: starbooru_core::RemotePage = serde_json::from_value(serde_json::json!({
            "posts": [{
                "id": 9, "site": "https://danbooru.donmai.us",
                "page_url": "https://danbooru.donmai.us/posts/9",
                "preview_url": "/remote/media?u=x&exp=1&sig=2",
                "sample_url": "/remote/media?u=y&exp=1&sig=3",
                "file_url": "https://cdn.donmai.us/original/ab.webm",
                "tags": ["saber", "sky", "highres"],
                "groups": {"character": ["saber"], "meta": ["highres"]},
                "rating": "general", "width": 640, "height": 360, "post_id": 4
            }, {
                "id": 10, "site": "https://yande.re", "page_url": "https://yande.re/post/show/10",
                "preview_url": "http://assets.yande.re/p/10.jpg", "file_url": "",
                "tags": [], "rating": "explicit", "width": 0, "height": 0
            }],
            "more": true
        }))
        .unwrap();
        let page = RemotePage::from_core(page, &client);
        assert!(page.more);
        let (first, second) = (&page.posts[0], &page.posts[1]);
        assert_eq!(
            first.preview_url,
            "https://booru.example/sub/remote/media?u=x&exp=1&sig=2"
        );
        assert_eq!(
            first.sample_url.as_deref(),
            Some("https://booru.example/sub/remote/media?u=y&exp=1&sig=3")
        );
        assert_eq!(first.file_url, "https://cdn.donmai.us/original/ab.webm");
        assert!(first.video);
        assert_eq!(
            first.import_tags,
            ["character:saber", "sky", "meta:highres"]
        );
        assert_eq!(first.groups.character, ["saber"]);
        assert_eq!(first.rating, Rating::Safe);
        assert_eq!(first.post_id, Some(4));
        assert_eq!(second.preview_url, "http://assets.yande.re/p/10.jpg");
        assert_eq!((second.sample_url.clone(), second.video), (None, false));
        assert_eq!(second.rating, Rating::Explicit);
    }

    #[test]
    fn a_search_starts_at_the_first_page() {
        let search = RemoteSearch {
            api: BooruApi::E621,
            base: "https://e926.net".into(),
            tags: "fox".into(),
            page: 0,
            login: None,
            api_key: None,
        };
        let json = serde_json::to_value(starbooru_core::RemoteSearch::from(search)).unwrap();
        assert_eq!(
            json,
            serde_json::json!({"api": "e621", "base": "https://e926.net", "tags": "fox", "page": 1})
        );
    }

    #[test]
    fn the_presets_and_the_fields_come_from_the_server_crate() {
        let sites = booru_sites();
        assert!(
            sites
                .iter()
                .any(|s| s.name == "Danbooru" && s.api == BooruApi::Danbooru)
        );
        assert_eq!(booru_api_name(BooruApi::E621), "e621");
        assert_eq!(booru_fields(BooruApi::Gelbooru).login, "user_id");
        assert_eq!(booru_fields(BooruApi::Moebooru).key, "password_hash");
    }
}
