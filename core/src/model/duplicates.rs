//! Posts that look alike: groups of them, and what to do with one.

use starbooru_client::Client;

use super::Post;

/// How many bits (of 64) two fingerprints may differ by when nothing else
/// is chosen.
#[uniffi::export]
pub fn default_duplicate_distance() -> u32 {
    starbooru_core::DEFAULT_DUPLICATE_DISTANCE
}

/// The loosest a search for duplicates can be.
#[uniffi::export]
pub fn max_duplicate_distance() -> u32 {
    starbooru_core::MAX_DUPLICATE_DISTANCE
}

/// The most groups one call lists.
pub const MAX_DUPLICATE_GROUPS: u32 = 100;

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Duplicates {
    pub distance: u32,
    /// Every group found; only the first `limit` are listed.
    pub total: u64,
    /// Live posts that can't be compared yet.
    pub unhashed: i64,
    /// The closest first.
    pub groups: Vec<DuplicateGroup>,
}

impl Duplicates {
    pub(crate) fn from_core(d: starbooru_core::Duplicates, client: &Client) -> Self {
        Self {
            distance: d.distance,
            total: d.total,
            unhashed: d.unhashed,
            groups: d
                .groups
                .into_iter()
                .map(|g| DuplicateGroup {
                    distance: g.distance,
                    size: g.size,
                    posts: g
                        .posts
                        .into_iter()
                        .map(|p| Post::from_core(p, client))
                        .collect(),
                })
                .collect(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DuplicateGroup {
    /// The widest difference, in bits, between two linked posts.
    pub distance: u32,
    /// Posts in the group, which may be more than are listed.
    pub size: u32,
    /// Up to 50, the best first: most pixels, then the largest file.
    pub posts: Vec<Post>,
}

/// Keep one post of a group and move the others to the recycle bin.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ResolveDuplicates {
    pub keep: i64,
    /// 1 to 50 posts, without `keep`.
    pub remove: Vec<i64>,
    /// The kept post also gets their tags, and their source if it has none.
    #[uniffi(default)]
    pub merge_tags: bool,
}

impl From<ResolveDuplicates> for starbooru_core::ResolveDuplicates {
    fn from(r: ResolveDuplicates) -> Self {
        Self {
            keep: r.keep,
            remove: r.remove,
            merge_tags: r.merge_tags,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn groups_get_absolute_addresses() {
        let client = Client::new("https://booru.example").unwrap();
        let found: starbooru_core::Duplicates = serde_json::from_value(serde_json::json!({
            "distance": 6, "total": 1, "unhashed": 2,
            "groups": [{"distance": 1, "size": 2, "posts": [{
                "id": 7, "kind": "image", "mime": "image/png", "file_size": 10, "width": 8,
                "height": 8, "sha256": "ab", "rating": "safe", "score": 0, "my_vote": 0,
                "source": null, "tags": [], "created_at": "2026-11-24T00:00:00Z",
                "deleted": false, "file_url": "/media/original/ab.png?sig=1", "thumb_url": null
            }]}]
        }))
        .unwrap();
        let found = Duplicates::from_core(found, &client);
        assert_eq!((found.total, found.unhashed), (1, 2));
        assert_eq!(found.groups[0].size, 2);
        assert_eq!(
            found.groups[0].posts[0].file_url,
            "https://booru.example/media/original/ab.png?sig=1"
        );
        assert_eq!(default_duplicate_distance(), 6);
        assert_eq!(max_duplicate_distance(), 10);
    }

    #[test]
    fn a_resolution_sends_all_three() {
        let resolve = ResolveDuplicates {
            keep: 1,
            remove: vec![2, 3],
            merge_tags: true,
        };
        let json = serde_json::to_value(starbooru_core::ResolveDuplicates::from(resolve)).unwrap();
        assert_eq!(
            json,
            serde_json::json!({"keep": 1, "remove": [2, 3], "merge_tags": true})
        );
    }
}
