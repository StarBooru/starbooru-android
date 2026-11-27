//! Administration: the server's numbers, its accounts, its settings, the
//! switch between private and public, backups and the tags votes took off.
//! Only admins get answers; others get `forbidden`.

use chrono::NaiveTime;
use starbooru_client::Client;

use super::{Mode, Role, ScoreKind, millis};

/// The limits of the administration's forms.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct AdminLimits {
    /// The server's name, in characters.
    pub max_name_chars: u32,
    /// Days the recycle bin keeps posts; 0 keeps them for good.
    pub max_trash_days: u32,
    /// Days between automatic backups; 0 turns them off.
    pub max_backup_days: u32,
    /// Automatic backups kept, from 1.
    pub max_backup_keep: u32,
    /// The score a tag comes off its post at, as minus this; 0 never.
    pub max_tag_vote_threshold: u32,
    /// The threshold a public server starts with.
    pub default_tag_vote_threshold: u32,
    /// Entries in the default blacklist.
    pub max_default_blacklist: u32,
    pub min_home_posts: u32,
    pub max_home_posts: u32,
    pub default_home_posts: u32,
    pub max_home_title_chars: u32,
    pub max_home_subtitle_chars: u32,
    pub max_home_query_chars: u32,
}

fn small(n: usize) -> u32 {
    u32::try_from(n).unwrap_or(u32::MAX)
}

#[uniffi::export]
pub fn admin_limits() -> AdminLimits {
    use starbooru_core::{admin, home, tag_vote};
    AdminLimits {
        max_name_chars: small(admin::MAX_INSTANCE_NAME_CHARS),
        max_trash_days: admin::MAX_TRASH_DAYS,
        max_backup_days: admin::MAX_BACKUP_DAYS,
        max_backup_keep: admin::MAX_BACKUP_KEEP,
        max_tag_vote_threshold: tag_vote::MAX_TAG_VOTE_THRESHOLD,
        default_tag_vote_threshold: tag_vote::DEFAULT_TAG_VOTE_THRESHOLD,
        max_default_blacklist: small(starbooru_core::blacklist::MAX_EXCLUDE),
        min_home_posts: home::MIN_HOME_POSTS,
        max_home_posts: home::MAX_HOME_POSTS,
        default_home_posts: home::DEFAULT_HOME_POSTS,
        max_home_title_chars: small(home::MAX_HOME_TITLE_CHARS),
        max_home_subtitle_chars: small(home::MAX_HOME_SUBTITLE_CHARS),
        max_home_query_chars: small(home::MAX_HOME_QUERY_CHARS),
    }
}

// ---------- the numbers ----------

/// Uploads on one day, in UTC.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct DayCount {
    /// `2026-11-24`.
    pub day: String,
    /// Its midnight, UTC.
    pub start_ms: i64,
    pub posts: u64,
}

/// The server at a glance.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ServerStats {
    pub version: String,
    pub started_at_ms: i64,
    pub users: u64,
    pub disabled_users: u64,
    /// Live posts, by kind, and all of them.
    pub images: u64,
    pub animations: u64,
    pub videos: u64,
    pub posts: u64,
    pub bin_posts: u64,
    pub tags: u64,
    /// Originals on disk, the bin's included; thumbnails aren't counted.
    pub media_bytes: u64,
    pub bin_bytes: u64,
    pub database_bytes: u64,
    /// The last 30 days, oldest first, every day there.
    pub uploads_by_day: Vec<DayCount>,
    pub max_upload_bytes: u64,
    /// ffmpeg is there: thumbnails of videos, AVIF and JPEG XL.
    pub video_thumbnails: bool,
    pub url_import: bool,
}

impl From<starbooru_core::ServerStats> for ServerStats {
    fn from(s: starbooru_core::ServerStats) -> Self {
        Self {
            posts: s.posts(),
            version: s.version,
            started_at_ms: millis(s.started_at),
            users: s.users,
            disabled_users: s.disabled_users,
            images: s.images,
            animations: s.animations,
            videos: s.videos,
            bin_posts: s.bin_posts,
            tags: s.tags,
            media_bytes: s.media_bytes,
            bin_bytes: s.bin_bytes,
            database_bytes: s.database_bytes,
            uploads_by_day: s
                .uploads_by_day
                .into_iter()
                .map(|d| DayCount {
                    day: d.day.format("%Y-%m-%d").to_string(),
                    start_ms: millis(d.day.and_time(NaiveTime::MIN).and_utc()),
                    posts: d.posts,
                })
                .collect(),
            max_upload_bytes: s.max_upload_bytes,
            video_thumbnails: s.video_thumbnails,
            url_import: s.url_import,
        }
    }
}

// ---------- accounts ----------

/// An account as admins see it, oldest first.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AdminUser {
    pub id: i64,
    pub username: String,
    pub role: Role,
    pub created_at_ms: i64,
    /// Can't sign in; its sessions and keys are refused.
    pub disabled: bool,
    /// Its uploads, the bin's included, and their originals' bytes.
    pub posts: u64,
    pub bytes: u64,
    /// The most it may upload; `None` is no limit.
    pub quota_bytes: Option<u64>,
    /// The last use of any of its sessions or keys.
    pub last_active_ms: Option<i64>,
    /// Its profile, for moderation.
    pub display_name: Option<String>,
    pub about: Option<String>,
    /// Absolute and signed: its 64 px picture.
    pub avatar_url: Option<String>,
}

impl AdminUser {
    pub(crate) fn from_core(u: starbooru_core::AdminUser, client: &Client) -> Self {
        Self {
            id: u.id,
            username: u.username,
            role: u.role.into(),
            created_at_ms: millis(u.created_at),
            disabled: u.disabled,
            posts: u.posts,
            bytes: u.bytes,
            quota_bytes: u.quota_bytes,
            last_active_ms: u.last_active.map(millis),
            display_name: u.display_name,
            about: u.about,
            avatar_url: u.avatar_url.map(|a| client.absolute(&a)),
        }
    }
}

/// A new storage limit.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum QuotaChange {
    Unlimited,
    Limit { bytes: u64 },
}

/// A change to an account. `None` and `false` leave a part as it is. An
/// admin may not change their own role, state or limit (`bad_request`),
/// so there is always one; clearing their own profile is fine.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, uniffi::Record)]
pub struct UserUpdate {
    #[uniffi(default)]
    pub role: Option<Role>,
    /// Disabling signs the account out everywhere.
    #[uniffi(default)]
    pub disabled: Option<bool>,
    #[uniffi(default)]
    pub quota: Option<QuotaChange>,
    /// Moderation: take off its picture, its text or its display name.
    #[uniffi(default)]
    pub clear_avatar: bool,
    #[uniffi(default)]
    pub clear_about: bool,
    #[uniffi(default)]
    pub clear_display_name: bool,
}

impl From<UserUpdate> for starbooru_core::UserUpdate {
    fn from(u: UserUpdate) -> Self {
        Self {
            role: u.role.map(Into::into),
            disabled: u.disabled,
            quota_bytes: u.quota.map(|q| match q {
                QuotaChange::Unlimited => None,
                QuotaChange::Limit { bytes } => Some(bytes),
            }),
            clear_avatar: u.clear_avatar,
            clear_about: u.clear_about,
            clear_display_name: u.clear_display_name,
        }
    }
}

// ---------- settings ----------

/// The home page as an admin sets it up.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct HomeSettings {
    pub enabled: bool,
    /// `None` or blank shows the server's name; one line.
    #[uniffi(default)]
    pub title: Option<String>,
    /// A line under the title; `None` for none.
    #[uniffi(default)]
    pub subtitle: Option<String>,
    /// The background's posts, in the search syntax; empty is the most
    /// popular. `rating:safe` is added unless it names a rating.
    #[uniffi(default)]
    pub query: String,
    /// How many, `min_home_posts` to `max_home_posts` of `admin_limits()`.
    #[uniffi(default = 36)]
    pub posts: u32,
}

impl From<starbooru_core::HomeSettings> for HomeSettings {
    fn from(h: starbooru_core::HomeSettings) -> Self {
        Self {
            enabled: h.enabled,
            title: h.title,
            subtitle: h.subtitle,
            query: h.query,
            posts: h.posts,
        }
    }
}

impl From<HomeSettings> for starbooru_core::HomeSettings {
    fn from(h: HomeSettings) -> Self {
        Self {
            enabled: h.enabled,
            title: h.title,
            subtitle: h.subtitle,
            query: h.query,
            posts: h.posts,
        }
    }
}

/// Something a switch of mode or of score kind deletes or converts.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ServerChange {
    /// One of `share_links`, `booru_accounts`, `remote_searches`,
    /// `star_votes_converted`, `star_votes_dropped`, `like_votes_converted`,
    /// `profile_pictures`, `profile_texts`, `forum_posts`, `forum_threads`,
    /// `tag_votes`, `tag_removals`; a newer server may add more.
    pub kind: String,
    pub count: u64,
    /// The same in an English sentence, for a kind the app doesn't know.
    pub text: String,
}

impl From<starbooru_core::Change> for ServerChange {
    fn from(c: starbooru_core::Change) -> Self {
        Self {
            kind: c.kind,
            count: c.count,
            text: c.message,
        }
    }
}

fn changes(list: Vec<starbooru_core::Change>) -> Vec<ServerChange> {
    list.into_iter().map(Into::into).collect()
}

/// The server's settings. Read them with `admin_settings`, change what the
/// screen changes, and send them back: a part left `None` stays as it is.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ServerSettings {
    /// 1 to 64 characters, spaces collapsed.
    pub name: String,
    pub allow_registration: bool,
    /// Days posts stay in the recycle bin; 0 keeps them.
    pub trash_days: u32,
    /// New posts get tags such as `highres` and `video`.
    pub auto_meta_tags: bool,
    /// Changed only by `switch_mode`; send back the one read.
    pub mode: Mode,
    /// A new one converts the votes (see `score_preview`). Stars on a
    /// public server is `public_counts_likes`.
    pub score_kind: ScoreKind,
    /// On a public server: a tag voted down to minus this comes off its
    /// post; 0 never. `None` on a private one.
    #[uniffi(default)]
    pub tag_vote_threshold: Option<u32>,
    /// What new accounts start with and guests get.
    #[uniffi(default)]
    pub default_blacklist: Option<Vec<String>>,
    #[uniffi(default)]
    pub home: Option<HomeSettings>,
    /// Chats and sites show a shared address with its picture.
    #[uniffi(default)]
    pub link_previews: Option<bool>,
    /// Only in the answer of a save that changed the score kind: what it
    /// converted and dropped. Never sent.
    #[uniffi(default)]
    pub changes: Vec<ServerChange>,
}

impl From<starbooru_core::ServerSettings> for ServerSettings {
    fn from(s: starbooru_core::ServerSettings) -> Self {
        Self {
            name: s.name,
            allow_registration: s.allow_registration,
            trash_days: s.trash_days,
            auto_meta_tags: s.auto_meta_tags,
            mode: s.mode.into(),
            score_kind: s.score_kind.into(),
            tag_vote_threshold: s.tag_vote_threshold,
            default_blacklist: s.default_blacklist,
            home: s.home.map(Into::into),
            link_previews: s.link_previews,
            changes: changes(s.changes),
        }
    }
}

impl From<ServerSettings> for starbooru_core::ServerSettings {
    fn from(s: ServerSettings) -> Self {
        Self {
            name: s.name,
            allow_registration: s.allow_registration,
            trash_days: s.trash_days,
            auto_meta_tags: s.auto_meta_tags,
            mode: s.mode.into(),
            score_kind: s.score_kind.into(),
            tag_vote_threshold: s.tag_vote_threshold,
            default_blacklist: s.default_blacklist,
            home: s.home.map(Into::into),
            link_previews: s.link_previews,
            changes: Vec::new(),
        }
    }
}

/// What switching to `to` would delete and convert. Empty when nothing,
/// or when the server is in that mode already.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ModePreview {
    pub to: Mode,
    pub changes: Vec<ServerChange>,
}

impl From<starbooru_core::ModePreview> for ModePreview {
    fn from(p: starbooru_core::ModePreview) -> Self {
        Self {
            to: p.to.into(),
            changes: changes(p.changes),
        }
    }
}

/// A switch done.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ModeSwitched {
    /// The settings in force now.
    pub settings: ServerSettings,
    /// What was deleted and converted.
    pub changes: Vec<ServerChange>,
    /// The backup made first, by name.
    pub backup: Option<String>,
}

impl From<starbooru_core::ModeSwitched> for ModeSwitched {
    fn from(s: starbooru_core::ModeSwitched) -> Self {
        Self {
            settings: s.settings.into(),
            changes: changes(s.changes),
            backup: s.backup,
        }
    }
}

/// What saving the settings with score kind `to` would convert and delete.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ScorePreview {
    pub to: ScoreKind,
    pub changes: Vec<ServerChange>,
}

impl From<starbooru_core::ScorePreview> for ScorePreview {
    fn from(p: starbooru_core::ScorePreview) -> Self {
        Self {
            to: p.to.into(),
            changes: changes(p.changes),
        }
    }
}

// ---------- backups ----------

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct BackupSchedule {
    /// Days between automatic backups; 0 turns them off.
    pub every_days: u32,
    /// Automatic backups kept, 1 to 100; those made by hand stay.
    pub keep: u32,
}

impl From<starbooru_core::BackupSchedule> for BackupSchedule {
    fn from(s: starbooru_core::BackupSchedule) -> Self {
        Self {
            every_days: s.every_days,
            keep: s.keep,
        }
    }
}

impl From<BackupSchedule> for starbooru_core::BackupSchedule {
    fn from(s: BackupSchedule) -> Self {
        Self {
            every_days: s.every_days,
            keep: s.keep,
        }
    }
}

/// One database backup.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Backup {
    pub name: String,
    pub bytes: u64,
    pub created_at_ms: i64,
    /// Made by the schedule, not by hand.
    pub automatic: bool,
    /// Absolute and signed for an hour; `Api::download_to` saves it.
    pub url: String,
}

impl Backup {
    pub(crate) fn from_core(b: starbooru_core::Backup, client: &Client) -> Self {
        Self {
            name: b.name,
            bytes: b.bytes,
            created_at_ms: millis(b.created_at),
            automatic: b.automatic,
            url: client.absolute(&b.url),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Backups {
    /// `pg_dump` is there and on, so backups can be made.
    pub available: bool,
    /// One is being made now; ask again until it isn't.
    pub running: bool,
    pub schedule: BackupSchedule,
    /// Newest first.
    pub items: Vec<Backup>,
}

impl Backups {
    pub(crate) fn from_core(b: starbooru_core::Backups, client: &Client) -> Self {
        Self {
            available: b.available,
            running: b.running,
            schedule: b.schedule.into(),
            items: b
                .items
                .into_iter()
                .map(|i| Backup::from_core(i, client))
                .collect(),
        }
    }
}

// ---------- tags votes took off ----------

/// A tag the votes took off a post.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TagRemoval {
    pub id: i64,
    pub post_id: i64,
    pub tag: String,
    /// Its score when it came off.
    pub score: i32,
    pub removed_at_ms: i64,
    /// Absolute and signed: the post's thumbnail.
    pub thumb_url: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TagRemovalPage {
    /// Newest first.
    pub removals: Vec<TagRemoval>,
    pub page: u32,
    pub limit: u32,
    pub has_more: bool,
}

impl TagRemovalPage {
    pub(crate) fn from_core(p: starbooru_core::TagRemovalPage, client: &Client) -> Self {
        Self {
            removals: p
                .removals
                .into_iter()
                .map(|r| TagRemoval {
                    id: r.id,
                    post_id: r.post_id,
                    tag: r.tag,
                    score: r.score,
                    removed_at_ms: millis(r.removed_at),
                    thumb_url: r.thumb_url.map(|u| client.absolute(&u)),
                })
                .collect(),
            page: p.page,
            limit: p.limit,
            has_more: p.has_more,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn client() -> Client {
        Client::new("https://booru.example/sub").unwrap()
    }

    #[test]
    fn stats_count_every_post_and_give_each_day_its_midnight() {
        let stats: starbooru_core::ServerStats = serde_json::from_value(serde_json::json!({
            "version": "0.7.1", "started_at": "2026-11-24T00:00:00Z", "users": 2,
            "disabled_users": 0, "images": 40, "animations": 3, "videos": 8, "bin_posts": 1,
            "tags": 90, "media_bytes": 10, "bin_bytes": 1, "database_bytes": 5,
            "uploads_by_day": [{"day": "2026-11-25", "posts": 4}],
            "max_upload_bytes": 100, "video_thumbnails": true, "url_import": false
        }))
        .unwrap();
        let stats = ServerStats::from(stats);
        assert_eq!(stats.posts, 51);
        assert_eq!(stats.started_at_ms, 1_795_478_400_000);
        let day = &stats.uploads_by_day[0];
        assert_eq!(day.day, "2026-11-25");
        assert_eq!(day.start_ms, 1_795_478_400_000 + 86_400_000);
        assert_eq!(day.posts, 4);
    }

    #[test]
    fn an_accounts_picture_gets_an_absolute_address() {
        let user: starbooru_core::AdminUser = serde_json::from_value(serde_json::json!({
            "id": 2, "username": "ann", "role": "user", "created_at": "2026-11-24T00:00:00Z",
            "disabled": false, "posts": 3, "bytes": 30, "quota_bytes": null,
            "last_active": null, "avatar_url": "/media/avatar/ab.jpg?sig=1"
        }))
        .unwrap();
        let user = AdminUser::from_core(user, &client());
        assert_eq!(
            user.avatar_url.as_deref(),
            Some("https://booru.example/sub/media/avatar/ab.jpg?sig=1")
        );
        assert_eq!((user.role, user.last_active_ms), (Role::User, None));
    }

    #[test]
    fn a_quota_is_set_lifted_or_left_alone() {
        let json = |u: UserUpdate| serde_json::to_value(starbooru_core::UserUpdate::from(u));
        assert_eq!(json(UserUpdate::default()).unwrap(), serde_json::json!({}));
        let lifted = UserUpdate {
            quota: Some(QuotaChange::Unlimited),
            ..UserUpdate::default()
        };
        assert_eq!(
            json(lifted).unwrap(),
            serde_json::json!({"quota_bytes": null})
        );
        let set = UserUpdate {
            quota: Some(QuotaChange::Limit { bytes: 5 }),
            disabled: Some(true),
            role: Some(Role::Admin),
            clear_about: true,
            ..UserUpdate::default()
        };
        assert_eq!(
            json(set).unwrap(),
            serde_json::json!({"quota_bytes": 5, "disabled": true, "role": "admin",
                "clear_about": true})
        );
    }

    #[test]
    fn settings_go_back_as_they_came_without_their_changes() {
        let read = serde_json::json!({
            "name": "Home", "allow_registration": true, "trash_days": 30,
            "auto_meta_tags": true, "mode": "public", "score_kind": "likes",
            "tag_vote_threshold": 3, "default_blacklist": ["cat"],
            "home": {"enabled": true, "title": null, "subtitle": "Skies", "query": "", "posts": 36},
            "link_previews": false,
            "changes": [{"kind": "star_votes_converted", "count": 2, "message": "2 votes"}]
        });
        let settings: starbooru_core::ServerSettings =
            serde_json::from_value(read.clone()).unwrap();
        let settings = ServerSettings::from(settings);
        assert_eq!(settings.mode, Mode::Public);
        assert_eq!(settings.changes[0].text, "2 votes");
        assert_eq!(settings.home.as_ref().map(|h| h.posts), Some(36));
        let sent = serde_json::to_value(starbooru_core::ServerSettings::from(settings)).unwrap();
        let mut expected = read;
        if let Some(o) = expected.as_object_mut() {
            o.remove("changes");
        }
        assert_eq!(sent, expected);
        // A private server's: no threshold, and the rest left alone.
        let private = ServerSettings {
            name: "x".into(),
            allow_registration: false,
            trash_days: 0,
            auto_meta_tags: true,
            mode: Mode::Private,
            score_kind: ScoreKind::Stars,
            tag_vote_threshold: None,
            default_blacklist: None,
            home: None,
            link_previews: None,
            changes: Vec::new(),
        };
        let sent = serde_json::to_value(starbooru_core::ServerSettings::from(private)).unwrap();
        assert_eq!(
            sent,
            serde_json::json!({"name": "x", "allow_registration": false, "trash_days": 0,
                "auto_meta_tags": true, "mode": "private", "score_kind": "stars"})
        );
    }

    #[test]
    fn backups_and_removed_tags_get_absolute_addresses() {
        let backups: starbooru_core::Backups = serde_json::from_value(serde_json::json!({
            "available": true, "running": false, "schedule": {"every_days": 7, "keep": 4},
            "items": [{"name": "starbooru-1.dump", "bytes": 80, "created_at": "2026-11-24T00:00:00Z",
                       "automatic": true, "url": "/backups/starbooru-1.dump?exp=1&sig=2"}]
        }))
        .unwrap();
        let backups = Backups::from_core(backups, &client());
        assert_eq!(
            backups.items[0].url,
            "https://booru.example/sub/backups/starbooru-1.dump?exp=1&sig=2"
        );
        assert_eq!(backups.schedule.keep, 4);
        let page: starbooru_core::TagRemovalPage = serde_json::from_value(serde_json::json!({
            "removals": [{"id": 1, "post_id": 7, "tag": "dog", "score": -3,
                          "removed_at": "2026-11-24T00:00:00Z", "thumb_url": "/media/thumb/a.webp"}],
            "page": 1, "limit": 20, "has_more": false
        }))
        .unwrap();
        let page = TagRemovalPage::from_core(page, &client());
        assert_eq!(
            page.removals[0].thumb_url.as_deref(),
            Some("https://booru.example/sub/media/thumb/a.webp")
        );
    }

    #[test]
    fn the_forms_limits_come_from_the_server_crate() {
        let limits = admin_limits();
        assert_eq!(limits.max_name_chars, 64);
        assert_eq!(limits.max_trash_days, 3650);
        assert_eq!(limits.max_default_blacklist, 100);
        assert_eq!(
            (
                limits.min_home_posts,
                limits.default_home_posts,
                limits.max_home_posts
            ),
            (12, 36, 60)
        );
    }
}
