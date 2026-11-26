//! The forum of a public server: categories set by admins, threads in them,
//! posts in threads. A private server answers every call with `forum_off`.

use starbooru_client::Client;

use super::{UserCard, millis};
use crate::search::utf16_index;

fn author(card: Option<starbooru_core::UserCard>, client: &Client) -> Option<UserCard> {
    card.map(|c| UserCard::from_core(c, client))
}

/// The most threads or posts one page holds.
pub const MAX_FORUM_LIMIT: u32 = starbooru_core::forum::MAX_FORUM_LIMIT;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ForumCategory {
    pub id: i64,
    pub name: String,
    pub description: String,
    /// Listed by this, lowest first, then by name.
    pub position: i32,
    pub threads: u64,
    /// The newest post in any of its threads.
    pub last_activity_at_ms: Option<i64>,
}

impl From<starbooru_core::ForumCategory> for ForumCategory {
    fn from(c: starbooru_core::ForumCategory) -> Self {
        Self {
            id: c.id,
            name: c.name,
            description: c.description,
            position: c.position,
            threads: c.threads,
            last_activity_at_ms: c.last_activity_at.map(millis),
        }
    }
}

/// A new category, for admins.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewCategory {
    /// One line, 1 to 64 characters, unique in any case (`name_taken`).
    pub name: String,
    /// Up to 500 characters.
    #[uniffi(default = "")]
    pub description: String,
    /// `None` puts it last.
    #[uniffi(default)]
    pub position: Option<i32>,
}

impl From<NewCategory> for starbooru_core::NewCategory {
    fn from(c: NewCategory) -> Self {
        Self {
            name: c.name,
            description: c.description,
            position: c.position,
        }
    }
}

/// A change to a category, for admins. `None` leaves a field as it is.
#[derive(Debug, Clone, Default, PartialEq, Eq, uniffi::Record)]
pub struct CategoryUpdate {
    #[uniffi(default)]
    pub name: Option<String>,
    #[uniffi(default)]
    pub description: Option<String>,
    #[uniffi(default)]
    pub position: Option<i32>,
}

impl From<CategoryUpdate> for starbooru_core::CategoryUpdate {
    fn from(u: CategoryUpdate) -> Self {
        Self {
            name: u.name,
            description: u.description,
            position: u.position,
        }
    }
}

/// A thread, as lists show it.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ForumThread {
    pub id: i64,
    pub category_id: i64,
    pub title: String,
    /// `None` once the account is deleted.
    pub author: Option<UserCard>,
    pub created_at_ms: i64,
    /// When its newest post was written.
    pub last_activity_at_ms: i64,
    /// Its posts, the first and the deleted ones included.
    pub posts: u64,
    /// Listed first in its category.
    pub pinned: bool,
    /// Only admins may post in it.
    pub locked: bool,
}

impl ForumThread {
    pub(crate) fn from_core(t: starbooru_core::ForumThread, client: &Client) -> Self {
        Self {
            id: t.id,
            category_id: t.category_id,
            title: t.title,
            author: author(t.author, client),
            created_at_ms: millis(t.created_at),
            last_activity_at_ms: millis(t.last_activity_at),
            posts: t.posts,
            pinned: t.pinned,
            locked: t.locked,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ThreadPage {
    pub threads: Vec<ForumThread>,
    pub page: u32,
    pub has_more: bool,
}

impl ThreadPage {
    pub(crate) fn from_core(p: starbooru_core::ThreadPage, client: &Client) -> Self {
        Self {
            threads: p
                .threads
                .into_iter()
                .map(|t| ForumThread::from_core(t, client))
                .collect(),
            page: p.page,
            has_more: p.has_more,
        }
    }
}

/// One post in a thread.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ForumPost {
    pub id: i64,
    pub thread_id: i64,
    /// `None` once deleted, or once the account is.
    pub author: Option<UserCard>,
    /// Plain text with Markdown as typed; empty once deleted. See
    /// `post_mentions` for the posts it names.
    pub body: String,
    pub created_at_ms: i64,
    /// The last time its author changed it.
    pub edited_at_ms: Option<i64>,
    /// It keeps its place, empty, so the thread still reads in order.
    pub deleted: bool,
}

impl ForumPost {
    pub(crate) fn from_core(p: starbooru_core::ForumPost, client: &Client) -> Self {
        Self {
            id: p.id,
            thread_id: p.thread_id,
            author: author(p.author, client),
            body: p.body,
            created_at_ms: millis(p.created_at),
            edited_at_ms: p.edited_at.map(millis),
            deleted: p.deleted,
        }
    }
}

/// A thread and a page of its posts, oldest first.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ThreadView {
    pub thread: ForumThread,
    pub posts: Vec<ForumPost>,
    pub page: u32,
    pub has_more: bool,
}

impl ThreadView {
    pub(crate) fn from_core(v: starbooru_core::ThreadView, client: &Client) -> Self {
        Self {
            thread: ForumThread::from_core(v.thread, client),
            posts: v
                .posts
                .into_iter()
                .map(|p| ForumPost::from_core(p, client))
                .collect(),
            page: v.page,
            has_more: v.has_more,
        }
    }
}

/// A thread with its first post.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewThread {
    pub category_id: i64,
    /// One line, 1 to 120 characters.
    pub title: String,
    /// 1 to 10,000 characters.
    pub body: String,
}

impl From<NewThread> for starbooru_core::NewThread {
    fn from(t: NewThread) -> Self {
        Self {
            category_id: t.category_id,
            title: t.title,
            body: t.body,
        }
    }
}

/// A change to a thread. `None` leaves a field as it is. The title is its
/// author's (not while locked) or an admin's to change; the rest only an
/// admin's.
#[derive(Debug, Clone, Default, PartialEq, Eq, uniffi::Record)]
pub struct ThreadUpdate {
    #[uniffi(default)]
    pub title: Option<String>,
    #[uniffi(default)]
    pub pinned: Option<bool>,
    #[uniffi(default)]
    pub locked: Option<bool>,
    /// Moves it to another category.
    #[uniffi(default)]
    pub category_id: Option<i64>,
}

impl From<ThreadUpdate> for starbooru_core::ThreadUpdate {
    fn from(u: ThreadUpdate) -> Self {
        Self {
            title: u.title,
            pinned: u.pinned,
            locked: u.locked,
            category_id: u.category_id,
        }
    }
}

/// A post of the booru named in a forum post (`>>123`, `post #123`), where
/// it is in UTF-16 units, as Kotlin counts a string.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct PostMention {
    pub start: u32,
    pub end: u32,
    pub id: i64,
}

/// The posts `text` names, for the screen to link.
#[uniffi::export]
pub fn post_mentions(text: &str) -> Vec<PostMention> {
    let units = |byte| u32::try_from(utf16_index(text, byte)).ok();
    starbooru_core::post_mentions(text)
        .into_iter()
        .filter_map(|m| {
            Some(PostMention {
                start: units(m.start)?,
                end: units(m.end)?,
                id: m.id,
            })
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_thread_gets_absolute_pictures_and_times_in_milliseconds() {
        let client = Client::new("https://booru.example").unwrap();
        let view: starbooru_core::ThreadView = serde_json::from_value(serde_json::json!({
            "thread": {"id": 4, "category_id": 1, "title": "Best sky tags",
                       "author": {"name": "ann", "display_name": "Ann",
                                  "avatar_url": "/media/avatar/9f.jpg?sig=1",
                                  "avatar_small_url": "/media/avatar64/9f.jpg?sig=2"},
                       "created_at": "2026-11-24T00:00:00Z",
                       "last_activity_at": "2026-11-24T00:00:02Z",
                       "posts": 2, "pinned": false, "locked": true},
            "posts": [{"id": 11, "thread_id": 4, "author": null, "body": "",
                       "created_at": "2026-11-24T00:00:01Z", "edited_at": null,
                       "deleted": true}],
            "page": 1, "limit": 50, "has_more": false
        }))
        .unwrap();
        let view = ThreadView::from_core(view, &client);
        let ann = view.thread.author.as_ref().unwrap();
        assert_eq!(
            ann.avatar_url.as_deref(),
            Some("https://booru.example/media/avatar/9f.jpg?sig=1")
        );
        assert_eq!(view.thread.last_activity_at_ms, 1_795_478_402_000);
        assert!(view.thread.locked);
        assert_eq!(view.posts[0].author, None);
        assert_eq!(view.posts[0].created_at_ms, 1_795_478_401_000);
    }

    #[test]
    fn changes_send_only_what_changes() {
        let update = ThreadUpdate {
            pinned: Some(true),
            ..ThreadUpdate::default()
        };
        let json = serde_json::to_value(starbooru_core::ThreadUpdate::from(update)).unwrap();
        assert_eq!(json, serde_json::json!({"pinned": true}));
        let category = NewCategory {
            name: "Art".into(),
            description: String::new(),
            position: None,
        };
        let json = serde_json::to_value(starbooru_core::NewCategory::from(category)).unwrap();
        assert_eq!(json, serde_json::json!({"name": "Art", "description": ""}));
        let rename = CategoryUpdate {
            name: Some("Drawings".into()),
            ..CategoryUpdate::default()
        };
        let json = serde_json::to_value(starbooru_core::CategoryUpdate::from(rename)).unwrap();
        assert_eq!(json, serde_json::json!({"name": "Drawings"}));
    }

    #[test]
    fn mentions_are_where_kotlin_counts_them() {
        // "è" is one unit, "🐱" two, though both are more than a byte.
        let text = "è 🐱 >>12 and post #7";
        let found = post_mentions(text);
        assert_eq!(
            found,
            [
                PostMention {
                    start: 5,
                    end: 9,
                    id: 12
                },
                PostMention {
                    start: 14,
                    end: 21,
                    id: 7
                },
            ]
        );
        let units: Vec<u16> = text.encode_utf16().collect();
        let first = String::from_utf16(&units[5..9]).unwrap();
        assert_eq!(first, ">>12");
        assert!(post_mentions("nothing here").is_empty());
    }
}
