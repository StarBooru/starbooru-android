//! What the browsing screens show beyond a search: Explore, the home page,
//! saved searches and the account's usage.

use starbooru_client::Client;

use super::{Post, Tag, TagCorrection, millis};

fn posts(list: Vec<starbooru_core::Post>, client: &Client) -> Vec<Post> {
    list.into_iter()
        .map(|p| Post::from_core(p, client))
        .collect()
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SavedSearch {
    pub id: i64,
    pub name: String,
    pub query: String,
    pub created_at_ms: i64,
}

impl From<starbooru_core::SavedSearch> for SavedSearch {
    fn from(s: starbooru_core::SavedSearch) -> Self {
        Self {
            id: s.id,
            name: s.name,
            query: s.query,
            created_at_ms: millis(s.created_at),
        }
    }
}

/// A tag much used on the posts of Explore's window.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TagTrend {
    pub tag: Tag,
    /// Posts added in the window that carry it.
    pub recent: i64,
}

impl From<starbooru_core::TagTrend> for TagTrend {
    fn from(t: starbooru_core::TagTrend) -> Self {
        Self {
            tag: t.tag.into(),
            recent: t.recent,
        }
    }
}

/// A character of the collection.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Person {
    /// The character's tag; searching it finds their posts.
    pub name: String,
    /// Their live posts; in `popular_characters`, the voted ones.
    pub posts: i64,
    pub franchise: Option<String>,
    /// Absolute and signed: a square crop of their face.
    pub face_url: Option<String>,
    /// Absolute and signed: a thumbnail of one of their posts, for when
    /// there is no face.
    pub thumb_url: Option<String>,
    /// Only in `popular_characters`: the net upvotes of their posts.
    pub votes: Option<i64>,
}

impl Person {
    pub(crate) fn from_core(p: starbooru_core::Person, client: &Client) -> Self {
        Self {
            name: p.name,
            posts: p.posts,
            franchise: p.franchise,
            face_url: p.face_url.map(|u| client.absolute(&u)),
            thumb_url: p.thumb_url.map(|u| client.absolute(&u)),
            votes: p.votes,
        }
    }
}

fn people(list: Vec<starbooru_core::Person>, client: &Client) -> Vec<Person> {
    list.into_iter()
        .map(|p| Person::from_core(p, client))
        .collect()
}

/// What Explore shows. On a private server `trending`, `tags` and
/// `popular_characters` are always empty, and the screen leaves them out.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Explore {
    pub days: u32,
    /// Most net upvotes cast in the window.
    pub trending: Vec<Post>,
    /// Most used on the posts added in the window.
    pub tags: Vec<TagTrend>,
    /// Added on today's date (UTC) in earlier years, newest first.
    pub on_this_day: Vec<Post>,
    pub random: Vec<Post>,
    /// The characters on the most posts.
    pub people: Vec<Person>,
    /// The characters on the best-liked posts of the window.
    pub popular_characters: Vec<Person>,
    /// Unknown tags of the search that were corrected.
    pub corrected: Vec<TagCorrection>,
}

impl Explore {
    pub(crate) fn from_core(e: starbooru_core::Explore, client: &Client) -> Self {
        Self {
            days: e.days,
            trending: posts(e.trending, client),
            tags: e.tags.into_iter().map(Into::into).collect(),
            on_this_day: posts(e.on_this_day, client),
            random: posts(e.random, client),
            people: people(e.people, client),
            popular_characters: people(e.popular_characters, client),
            corrected: e.corrected.into_iter().map(Into::into).collect(),
        }
    }
}

/// The home page: a title over a moving background of posts.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Home {
    pub enabled: bool,
    /// The admin's, or the server's name.
    pub title: String,
    pub subtitle: Option<String>,
    /// Empty while the page is off, and before signing in to a private
    /// server.
    pub posts: Vec<HomePost>,
}

impl Home {
    pub(crate) fn from_core(h: starbooru_core::Home, client: &Client) -> Self {
        Self {
            enabled: h.enabled,
            title: h.title,
            subtitle: h.subtitle,
            posts: h
                .posts
                .into_iter()
                .map(|p| HomePost::from_core(p, client))
                .collect(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct HomePost {
    pub id: i64,
    /// Absolute and signed, 512 px at most.
    pub thumb_url: String,
    /// Absolute and signed: a bigger copy, for big still images.
    pub sample_url: Option<String>,
    pub width: u32,
    pub height: u32,
    /// The main colours as `#rrggbb`, most common first.
    pub palette: Vec<String>,
}

impl HomePost {
    pub(crate) fn from_core(p: starbooru_core::HomePost, client: &Client) -> Self {
        Self {
            id: p.id,
            thumb_url: client.absolute(&p.thumb_url),
            sample_url: p.sample_url.map(|u| client.absolute(&u)),
            width: p.width,
            height: p.height,
            palette: p.palette,
        }
    }
}

/// What the account has uploaded, the recycle bin included, against its
/// storage limit.
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct Usage {
    pub posts: u64,
    pub bytes: u64,
    /// `None` for no limit.
    pub quota_bytes: Option<u64>,
    /// How much of the limit is used, 0 to 1; `None` for no limit.
    pub used_fraction: Option<f64>,
}

impl From<starbooru_core::Usage> for Usage {
    fn from(u: starbooru_core::Usage) -> Self {
        Self {
            used_fraction: u.fraction(),
            posts: u.posts,
            bytes: u.bytes,
            quota_bytes: u.quota_bytes,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn client() -> Client {
        Client::new("https://booru.example/sub").unwrap()
    }

    fn post_json(id: i64) -> serde_json::Value {
        serde_json::json!({
            "id": id, "kind": "image", "mime": "image/png", "file_size": 10,
            "width": 8, "height": 8, "sha256": "ab", "rating": "safe", "score": 0,
            "my_vote": 0, "source": null, "tags": [], "created_at": "2026-11-24T00:00:00Z",
            "deleted": false, "file_url": "/media/original/ab.png?sig=1",
            "thumb_url": "/media/thumb/ab.webp?sig=2"
        })
    }

    #[test]
    fn explore_gets_absolute_addresses_in_every_section() {
        let explore: starbooru_core::Explore = serde_json::from_value(serde_json::json!({
            "days": 7,
            "trending": [post_json(1)],
            "tags": [{"tag": {"id": 3, "name": "sky", "category": "general", "post_count": 4},
                      "recent": 2}],
            "on_this_day": [post_json(2)],
            "random": [post_json(3)],
            "people": [{"name": "saber", "posts": 31, "franchise": "fate_(series)",
                        "face_url": "/media/face/9f.jpg?sig=3", "thumb_url": null}],
            "popular_characters": [{"name": "rin", "posts": 2, "franchise": null,
                                    "face_url": null, "thumb_url": "/media/thumb/c4.jpg?sig=4",
                                    "votes": 5}],
            "corrected": [{"from": "skyy", "to": "sky"}]
        }))
        .unwrap();
        let explore = Explore::from_core(explore, &client());
        assert_eq!(
            explore.trending[0].file_url,
            "https://booru.example/sub/media/original/ab.png?sig=1"
        );
        assert_eq!(explore.on_this_day[0].id, 2);
        assert_eq!(explore.random[0].id, 3);
        assert_eq!(explore.tags[0].tag.name, "sky");
        assert_eq!(explore.tags[0].recent, 2);
        assert_eq!(
            explore.people[0].face_url.as_deref(),
            Some("https://booru.example/sub/media/face/9f.jpg?sig=3")
        );
        assert_eq!(explore.people[0].votes, None);
        let rin = &explore.popular_characters[0];
        assert_eq!(
            rin.thumb_url.as_deref(),
            Some("https://booru.example/sub/media/thumb/c4.jpg?sig=4")
        );
        assert_eq!(rin.votes, Some(5));
        assert_eq!(explore.corrected[0].to, "sky");
    }

    #[test]
    fn a_server_from_before_people_has_none() {
        let explore: starbooru_core::Explore = serde_json::from_value(serde_json::json!({
            "days": 1, "trending": [], "tags": [], "on_this_day": [], "random": []
        }))
        .unwrap();
        let explore = Explore::from_core(explore, &client());
        assert!(explore.people.is_empty() && explore.popular_characters.is_empty());
    }

    #[test]
    fn the_home_page_gets_absolute_addresses() {
        let home: starbooru_core::Home = serde_json::from_value(serde_json::json!({
            "enabled": true, "title": "StarBooru", "subtitle": null,
            "posts": [{"id": 3, "thumb_url": "/media/thumb/c8.jpg?sig=1",
                       "sample_url": "/media/sample/c8.jpg?sig=2", "width": 1920, "height": 1080}]
        }))
        .unwrap();
        let home = Home::from_core(home, &client());
        let post = &home.posts[0];
        assert_eq!(
            post.thumb_url,
            "https://booru.example/sub/media/thumb/c8.jpg?sig=1"
        );
        assert_eq!(
            post.sample_url.as_deref(),
            Some("https://booru.example/sub/media/sample/c8.jpg?sig=2")
        );
        assert!(post.palette.is_empty());
    }

    #[test]
    fn a_saved_search_keeps_its_time_in_milliseconds() {
        let saved: starbooru_core::SavedSearch = serde_json::from_value(serde_json::json!({
            "id": 4, "name": "Skies", "query": "sky order:score",
            "created_at": "2026-11-24T00:00:01.5Z"
        }))
        .unwrap();
        assert_eq!(SavedSearch::from(saved).created_at_ms, 1_795_478_401_500);
    }

    #[test]
    fn usage_says_how_full_the_limit_is() {
        let usage = Usage::from(starbooru_core::Usage {
            posts: 3,
            bytes: 250,
            quota_bytes: Some(1000),
        });
        assert_eq!(usage.used_fraction, Some(0.25));
        let unlimited = Usage::from(starbooru_core::Usage {
            posts: 3,
            bytes: 250,
            quota_bytes: None,
        });
        assert_eq!(unlimited.used_fraction, None);
    }
}
