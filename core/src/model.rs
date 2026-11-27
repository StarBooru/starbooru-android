//! The records Kotlin sees, shaped for the screens. Each comes from a type in
//! `starbooru-core`; media paths become absolute addresses and times become
//! milliseconds since the epoch.

use chrono::{DateTime, Utc};
use starbooru_client::Client;

mod account;
mod admin;
mod ai;
mod ai_admin;
mod boorus;
mod browse;
mod duplicates;
mod editor;
mod edits;
mod figures;
mod forum;
mod imports;
mod library;
mod people;
mod profile;
mod sharing;
mod tags;
mod text;
mod transfer;
mod updates;

pub use account::{
    ApiKey, CreatedApiKey, NewApiKey, ProviderIdentity, SessionInfo, SignInMethods,
    max_api_key_days,
};
pub use admin::{
    AdminLimits, AdminUser, Backup, BackupSchedule, Backups, DayCount, HomeSettings, ModePreview,
    ModeSwitched, QuotaChange, ScorePreview, ServerChange, ServerSettings, ServerStats, TagRemoval,
    TagRemovalPage, UserUpdate, admin_limits,
};
pub use ai::{AiStatus, TagSuggestion, TagSuggestions};
pub use ai_admin::{
    AiAdminStatus, AiInstallProgress, AiMode, AiModelOption, AiPreset, AiPresetOption,
    AiRuntimeStatus, AiTool, AiToolSettings, AiToolState, AiToolStatus, AiToolUpdate, Gpu, GpuKind,
    Hardware, ai_tool_update_of,
};
pub use boorus::{
    BooruAccount, BooruApi, BooruFields, BooruSite, NewBooruAccount, NewRemoteSearch, RemotePage,
    RemotePost, RemoteSearch, RemoteSite, RemoteTag, RemoteTagGroups, SavedRemoteSearch,
    booru_api_name, booru_fields, booru_sites,
};
pub use browse::{Explore, Home, HomePost, Person, SavedSearch, TagTrend, Usage};
pub use duplicates::{
    DuplicateGroup, Duplicates, MAX_DUPLICATE_GROUPS, ResolveDuplicates,
    default_duplicate_distance, max_duplicate_distance,
};
pub use editor::{
    Crop, EditKind, EditProblem, MediaEdit, Trim, edit_kind, edit_problem, max_straighten,
    min_trim_ms, straighten_scale, turn_edit,
};
pub use edits::{BulkEdit, BulkResult, PostUpdate, PurgeResult, TRASH_QUERY, trash_query};
pub(crate) use figures::figure_list;
pub use figures::{
    FaceBox, Figure, FigureKind, FigureSlot, FoundPeople, SlotTags, face_box_fits, face_scale,
};
pub use forum::{
    CategoryUpdate, ForumCategory, ForumPost, ForumThread, MAX_FORUM_LIMIT, NewCategory, NewThread,
    PostMention, ThreadPage, ThreadUpdate, ThreadView, post_mentions,
};
pub use imports::{ImportItem, ImportJob, ImportProgress, JobState};
pub use library::{
    LibraryCheck, LibraryFailure, LibraryImport, LibraryPhase, LibrarySite, LibrarySource,
    LibrarySourceForm, LibraryState, library_source_form, max_active_library_imports,
};
pub use people::{
    Franchise, FranchisePage, MAX_FRANCHISE_LIMIT, MAX_PEOPLE_LIMIT, PeopleOrder, PeoplePage,
    PeopleQuery,
};
pub use profile::{AvatarCrop, PostTagVote, Profile, ProfileUpdate, UserCard};
pub use sharing::{
    LinkPreview, NewShare, Share, ShareLink, ShareUpdate, SharedPost, SharedView, SharingSettings,
    max_share_days, share_link,
};
pub use tags::{MAX_TAG_LIMIT, Tag, TagDetail, TagOrder, TagPage, TagUpdate};
pub use text::{PanelTranslation, TextBlock};
pub use transfer::{DuplicatePost, duplicate_of};
pub use updates::{
    Release, UpdateBlock, UpdateKind, UpdateMethod, UpdateRun, UpdateSettings, UpdateState,
    UpdateStatus,
};

pub(crate) fn millis(t: DateTime<Utc>) -> i64 {
    t.timestamp_millis()
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum Role {
    Admin,
    User,
}

impl From<starbooru_core::Role> for Role {
    fn from(r: starbooru_core::Role) -> Self {
        match r {
            starbooru_core::Role::Admin => Role::Admin,
            starbooru_core::Role::User => Role::User,
        }
    }
}

impl From<Role> for starbooru_core::Role {
    fn from(r: Role) -> Self {
        match r {
            Role::Admin => starbooru_core::Role::Admin,
            Role::User => starbooru_core::Role::User,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum Rating {
    Safe,
    Sensitive,
    Questionable,
    Explicit,
}

impl From<starbooru_core::Rating> for Rating {
    fn from(r: starbooru_core::Rating) -> Self {
        match r {
            starbooru_core::Rating::Safe => Rating::Safe,
            starbooru_core::Rating::Sensitive => Rating::Sensitive,
            starbooru_core::Rating::Questionable => Rating::Questionable,
            starbooru_core::Rating::Explicit => Rating::Explicit,
        }
    }
}

impl From<Rating> for starbooru_core::Rating {
    fn from(r: Rating) -> Self {
        match r {
            Rating::Safe => starbooru_core::Rating::Safe,
            Rating::Sensitive => starbooru_core::Rating::Sensitive,
            Rating::Questionable => starbooru_core::Rating::Questionable,
            Rating::Explicit => starbooru_core::Rating::Explicit,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum MediaKind {
    Image,
    Animation,
    Video,
}

impl From<starbooru_core::MediaKind> for MediaKind {
    fn from(k: starbooru_core::MediaKind) -> Self {
        match k {
            starbooru_core::MediaKind::Image => MediaKind::Image,
            starbooru_core::MediaKind::Animation => MediaKind::Animation,
            starbooru_core::MediaKind::Video => MediaKind::Video,
        }
    }
}

impl From<MediaKind> for starbooru_core::MediaKind {
    fn from(k: MediaKind) -> Self {
        match k {
            MediaKind::Image => starbooru_core::MediaKind::Image,
            MediaKind::Animation => starbooru_core::MediaKind::Animation,
            MediaKind::Video => starbooru_core::MediaKind::Video,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum TagCategory {
    General,
    Artist,
    Character,
    Copyright,
    Meta,
}

impl From<starbooru_core::TagCategory> for TagCategory {
    fn from(c: starbooru_core::TagCategory) -> Self {
        match c {
            starbooru_core::TagCategory::General => TagCategory::General,
            starbooru_core::TagCategory::Artist => TagCategory::Artist,
            starbooru_core::TagCategory::Character => TagCategory::Character,
            starbooru_core::TagCategory::Copyright => TagCategory::Copyright,
            starbooru_core::TagCategory::Meta => TagCategory::Meta,
        }
    }
}

impl From<TagCategory> for starbooru_core::TagCategory {
    fn from(c: TagCategory) -> Self {
        match c {
            TagCategory::General => starbooru_core::TagCategory::General,
            TagCategory::Artist => starbooru_core::TagCategory::Artist,
            TagCategory::Character => starbooru_core::TagCategory::Character,
            TagCategory::Copyright => starbooru_core::TagCategory::Copyright,
            TagCategory::Meta => starbooru_core::TagCategory::Meta,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum Mode {
    Private,
    Public,
}

impl From<starbooru_core::Mode> for Mode {
    fn from(m: starbooru_core::Mode) -> Self {
        match m {
            starbooru_core::Mode::Private => Mode::Private,
            starbooru_core::Mode::Public => Mode::Public,
        }
    }
}

impl From<Mode> for starbooru_core::Mode {
    fn from(m: Mode) -> Self {
        match m {
            Mode::Private => starbooru_core::Mode::Private,
            Mode::Public => starbooru_core::Mode::Public,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ScoreKind {
    Likes,
    Stars,
}

impl From<starbooru_core::ScoreKind> for ScoreKind {
    fn from(k: starbooru_core::ScoreKind) -> Self {
        match k {
            starbooru_core::ScoreKind::Likes => ScoreKind::Likes,
            starbooru_core::ScoreKind::Stars => ScoreKind::Stars,
        }
    }
}

impl From<ScoreKind> for starbooru_core::ScoreKind {
    fn from(k: ScoreKind) -> Self {
        match k {
            ScoreKind::Likes => starbooru_core::ScoreKind::Likes,
            ScoreKind::Stars => starbooru_core::ScoreKind::Stars,
        }
    }
}

/// What a server is, from `GET /info`.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ServerInfo {
    pub name: String,
    pub version: String,
    /// False until the first account exists; that account becomes admin.
    pub has_users: bool,
    pub registration_open: bool,
    pub max_upload_bytes: u64,
    pub url_import: bool,
    /// The identity provider's name, when sign-in through one is set up.
    pub oidc: Option<String>,
    pub video_editing: bool,
    pub mode: Mode,
    /// Likes on a public server, whatever is stored, as the server counts.
    pub score_kind: ScoreKind,
    pub guests: bool,
    pub home: bool,
    pub link_previews: bool,
}

impl From<starbooru_core::ServerInfo> for ServerInfo {
    fn from(i: starbooru_core::ServerInfo) -> Self {
        Self {
            score_kind: i.score_kind.in_mode(i.mode).into(),
            name: i.name,
            version: i.version,
            has_users: i.has_users,
            registration_open: i.registration_open,
            max_upload_bytes: i.max_upload_bytes,
            url_import: i.url_import,
            oidc: i.oidc,
            video_editing: i.video_editing,
            mode: i.mode.into(),
            guests: i.guests,
            home: i.home,
            link_previews: i.link_previews,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct User {
    pub id: i64,
    pub username: String,
    pub role: Role,
    pub created_at_ms: i64,
}

impl From<starbooru_core::User> for User {
    fn from(u: starbooru_core::User) -> Self {
        Self {
            id: u.id,
            username: u.username,
            role: u.role.into(),
            created_at_ms: millis(u.created_at),
        }
    }
}

/// A new session: the token to keep and who it belongs to.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SignedIn {
    pub token: String,
    pub user: User,
}

impl From<starbooru_core::AuthResponse> for SignedIn {
    fn from(a: starbooru_core::AuthResponse) -> Self {
        Self {
            token: a.token,
            user: a.user.into(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct PostTag {
    pub name: String,
    pub category: TagCategory,
    /// The top of the tag's parent chain, or `None` for a tag on its own.
    pub section: Option<String>,
}

impl From<starbooru_core::PostTag> for PostTag {
    fn from(t: starbooru_core::PostTag) -> Self {
        Self {
            name: t.name,
            category: t.category.into(),
            section: t.section,
        }
    }
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Post {
    pub id: i64,
    pub kind: MediaKind,
    pub mime: String,
    pub file_size: i64,
    pub width: Option<i32>,
    pub height: Option<i32>,
    pub duration_ms: Option<i64>,
    /// Lowercase hex SHA-256 of the original, the key of the media cache.
    pub sha256: String,
    pub rating: Rating,
    pub score: i32,
    pub my_vote: i16,
    pub stars: Option<f64>,
    pub votes: u32,
    pub my_stars: u8,
    pub source: Option<String>,
    pub tags: Vec<String>,
    pub created_at_ms: i64,
    pub file_date_ms: Option<i64>,
    /// The main colours as `#rrggbb`, most common first.
    pub palette: Vec<String>,
    pub deleted: bool,
    /// Absolute, signed addresses that need no token.
    pub file_url: String,
    pub thumb_url: Option<String>,
    pub sample_url: Option<String>,
    /// Only in a single post.
    pub ocr_text: Option<String>,
    /// Only in a single post.
    pub tag_details: Vec<PostTag>,
    /// The entries of the reader's blacklist the post matches. Only in a
    /// single post.
    pub blacklisted: Vec<String>,
    /// The people in the picture. Only in a single post and the answers
    /// that change one.
    #[uniffi(default)]
    pub figures: Vec<Figure>,
    /// Who uploaded it, on a public server.
    #[uniffi(default)]
    pub uploader: Option<UserCard>,
    /// On a public server, the votes on its tags, by name, for the tags that
    /// have any; another tag's score is 0 with no vote of the reader's.
    #[uniffi(default)]
    pub tag_votes: Vec<PostTagVote>,
    /// On a public server, the tags voted off it, which edits leave off.
    #[uniffi(default)]
    pub voted_off: Vec<String>,
}

impl Post {
    pub(crate) fn from_core(p: starbooru_core::Post, client: &Client) -> Self {
        Self {
            id: p.id,
            kind: p.kind.into(),
            mime: p.mime,
            file_size: p.file_size,
            width: p.width,
            height: p.height,
            duration_ms: p.duration_ms,
            sha256: p.sha256,
            rating: p.rating.into(),
            score: p.score,
            my_vote: p.my_vote,
            stars: p.stars,
            votes: p.votes,
            my_stars: p.my_stars,
            source: p.source,
            tags: p.tags,
            created_at_ms: millis(p.created_at),
            file_date_ms: p.file_date.map(millis),
            palette: p.palette,
            deleted: p.deleted,
            file_url: client.absolute(&p.file_url),
            thumb_url: p.thumb_url.map(|u| client.absolute(&u)),
            sample_url: p.sample_url.map(|u| client.absolute(&u)),
            ocr_text: p.ocr_text,
            tag_details: p.tag_details.into_iter().map(Into::into).collect(),
            blacklisted: p.blacklisted,
            figures: figure_list(p.figures, client),
            uploader: p.uploader.map(|u| UserCard::from_core(u, client)),
            tag_votes: p
                .tag_votes
                .into_iter()
                .map(|(tag, vote)| PostTagVote {
                    tag,
                    score: vote.score,
                    mine: vote.mine,
                })
                .collect(),
            voted_off: p.voted_off,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TagCorrection {
    pub from: String,
    pub to: String,
}

impl From<starbooru_core::TagCorrection> for TagCorrection {
    fn from(c: starbooru_core::TagCorrection) -> Self {
        Self {
            from: c.from,
            to: c.to,
        }
    }
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PostPage {
    pub posts: Vec<Post>,
    pub page: u32,
    pub has_more: bool,
    /// Unknown tags the search corrected.
    pub corrected: Vec<TagCorrection>,
}

impl PostPage {
    pub(crate) fn from_core(p: starbooru_core::PostPage, client: &Client) -> Self {
        Self {
            posts: p
                .posts
                .into_iter()
                .map(|post| Post::from_core(post, client))
                .collect(),
            page: p.page,
            has_more: p.has_more,
            corrected: p.corrected.into_iter().map(Into::into).collect(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct Neighbors {
    pub prev: Option<i64>,
    pub next: Option<i64>,
}

impl From<starbooru_core::Neighbors> for Neighbors {
    fn from(n: starbooru_core::Neighbors) -> Self {
        Self {
            prev: n.prev,
            next: n.next,
        }
    }
}

/// Where a provider sign-in starts: the page to open in a browser tab.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ProviderStart {
    pub url: String,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn post_json() -> serde_json::Value {
        serde_json::json!({
            "id": 7, "kind": "video", "mime": "video/mp4", "file_size": 10,
            "width": 1920, "height": 1080, "duration_ms": 5000, "sha256": "ab",
            "rating": "general", "score": 1, "my_vote": 0, "source": null,
            "tags": ["sky"], "created_at": "2026-11-24T00:00:00Z", "file_date": null,
            "palette": ["#112233"],
            "deleted": false, "file_url": "/media/original/ab.mp4?sig=1",
            "thumb_url": "/media/thumb/ab.webp?sig=2", "sample_url": null
        })
    }

    #[test]
    fn a_post_gets_absolute_addresses() {
        let client = Client::new("https://booru.example/sub").unwrap();
        let core: starbooru_core::Post = serde_json::from_value(post_json()).unwrap();
        let post = Post::from_core(core, &client);
        assert_eq!(
            post.file_url,
            "https://booru.example/sub/media/original/ab.mp4?sig=1"
        );
        assert_eq!(
            post.thumb_url.as_deref(),
            Some("https://booru.example/sub/media/thumb/ab.webp?sig=2")
        );
        assert_eq!(post.kind, MediaKind::Video);
        assert_eq!(post.rating, Rating::Safe);
        assert_eq!(post.created_at_ms, 1_795_478_400_000);
    }

    #[test]
    fn a_post_on_a_public_server_brings_its_people_uploader_and_tag_votes() {
        let client = Client::new("https://booru.example").unwrap();
        let mut json = post_json();
        json["figures"] = serde_json::json!([{"kind": "girl", "franchise": null,
            "face": {"x": 1, "y": 2, "w": 3, "h": 4}, "face_url": "/media/face/ab.jpg?sig=3"}]);
        json["uploader"] = serde_json::json!({"name": "ann", "display_name": "Ann",
            "avatar_url": "/media/avatar/9f.jpg?sig=4", "avatar_small_url": null});
        json["tag_votes"] = serde_json::json!({"sky": {"score": 2, "mine": 1},
            "cat": {"score": -1, "mine": 0}});
        json["voted_off"] = serde_json::json!(["dog"]);
        let core: starbooru_core::Post = serde_json::from_value(json).unwrap();
        let post = Post::from_core(core, &client);
        assert_eq!(
            post.figures[0].face_url.as_deref(),
            Some("https://booru.example/media/face/ab.jpg?sig=3")
        );
        assert_eq!(
            post.uploader.and_then(|u| u.avatar_url).as_deref(),
            Some("https://booru.example/media/avatar/9f.jpg?sig=4")
        );
        // By name, as the server keeps them.
        assert_eq!(
            post.tag_votes,
            [
                PostTagVote {
                    tag: "cat".into(),
                    score: -1,
                    mine: 0
                },
                PostTagVote {
                    tag: "sky".into(),
                    score: 2,
                    mine: 1
                },
            ]
        );
        assert_eq!(post.voted_off, ["dog"]);
        // A private server's post has none of them.
        let core: starbooru_core::Post = serde_json::from_value(post_json()).unwrap();
        let post = Post::from_core(core, &client);
        assert!(post.figures.is_empty() && post.tag_votes.is_empty() && post.voted_off.is_empty());
        assert_eq!(post.uploader, None);
    }

    #[test]
    fn media_kinds_map_both_ways() {
        for kind in [MediaKind::Image, MediaKind::Animation, MediaKind::Video] {
            assert_eq!(MediaKind::from(starbooru_core::MediaKind::from(kind)), kind);
        }
    }

    #[test]
    fn a_public_server_counts_likes() {
        let info: starbooru_core::ServerInfo = serde_json::from_value(serde_json::json!({
            "name": "S", "version": "0.7.1", "has_users": true, "registration_open": false,
            "max_upload_bytes": 1, "mode": "public", "score_kind": "stars"
        }))
        .unwrap();
        let info = ServerInfo::from(info);
        assert_eq!(info.mode, Mode::Public);
        assert_eq!(info.score_kind, ScoreKind::Likes);
    }
}
