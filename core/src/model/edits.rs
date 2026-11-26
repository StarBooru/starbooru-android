//! Changing posts: one at a time, many at once, and the recycle bin.

use super::Rating;

/// The search that lists the recycle bin. It takes other terms and an
/// `order:` like any search, and the blacklist doesn't apply to it.
pub const TRASH_QUERY: &str = "status:deleted";

/// The search that lists the recycle bin: pass it to `Api::posts`.
#[uniffi::export]
pub fn trash_query() -> String {
    TRASH_QUERY.to_owned()
}

/// A change to one post. What is left `None` stays as it is.
#[derive(Debug, Clone, Default, PartialEq, Eq, uniffi::Record)]
pub struct PostUpdate {
    /// The whole new list, which replaces the old one.
    #[uniffi(default)]
    pub tags: Option<Vec<String>>,
    #[uniffi(default)]
    pub rating: Option<Rating>,
    /// `""` clears it.
    #[uniffi(default)]
    pub source: Option<String>,
}

impl From<PostUpdate> for starbooru_core::PostUpdate {
    fn from(u: PostUpdate) -> Self {
        Self {
            tags: u.tags,
            rating: u.rating.map(Into::into),
            source: u.source,
        }
    }
}

/// One change to many posts, up to 500. `remove_tags` come off first,
/// then `add_tags` go on. `delete` moves them to the recycle bin and can't
/// be combined with the rest.
#[derive(Debug, Clone, Default, PartialEq, Eq, uniffi::Record)]
pub struct BulkEdit {
    pub ids: Vec<i64>,
    #[uniffi(default)]
    pub add_tags: Vec<String>,
    #[uniffi(default)]
    pub remove_tags: Vec<String>,
    #[uniffi(default)]
    pub rating: Option<Rating>,
    #[uniffi(default)]
    pub delete: bool,
}

impl From<BulkEdit> for starbooru_core::BulkEdit {
    fn from(e: BulkEdit) -> Self {
        Self {
            ids: e.ids,
            add_tags: e.add_tags,
            remove_tags: e.remove_tags,
            rating: e.rating.map(Into::into),
            delete: e.delete,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct BulkResult {
    pub updated: u64,
    /// Posts left alone: missing, in the recycle bin, or not yours to delete.
    pub skipped: u64,
}

impl From<starbooru_core::BulkResult> for BulkResult {
    fn from(r: starbooru_core::BulkResult) -> Self {
        Self {
            updated: r.updated,
            skipped: r.skipped,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct PurgeResult {
    /// Posts deleted for good.
    pub purged: u64,
}

impl From<starbooru_core::PurgeResult> for PurgeResult {
    fn from(r: starbooru_core::PurgeResult) -> Self {
        Self { purged: r.purged }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_bin_is_a_search_the_server_knows() {
        let q = starbooru_core::parse_query(&trash_query()).unwrap();
        assert!(q.deleted);
    }

    #[test]
    fn an_update_sends_only_what_changes() {
        let update = PostUpdate {
            rating: Some(Rating::Questionable),
            ..PostUpdate::default()
        };
        let json = serde_json::to_value(starbooru_core::PostUpdate::from(update)).unwrap();
        assert_eq!(json, serde_json::json!({"rating": "questionable"}));
        let cleared = PostUpdate {
            tags: Some(Vec::new()),
            source: Some(String::new()),
            ..PostUpdate::default()
        };
        let json = serde_json::to_value(starbooru_core::PostUpdate::from(cleared)).unwrap();
        assert_eq!(json, serde_json::json!({"tags": [], "source": ""}));
    }

    #[test]
    fn a_bulk_edit_maps_its_rating_and_leaves_out_the_rest() {
        let edit = BulkEdit {
            ids: vec![1, 2],
            add_tags: vec!["sky".into()],
            rating: Some(Rating::Safe),
            ..BulkEdit::default()
        };
        let json = serde_json::to_value(starbooru_core::BulkEdit::from(edit)).unwrap();
        assert_eq!(
            json,
            serde_json::json!({"ids": [1, 2], "add_tags": ["sky"], "rating": "safe"})
        );
        let delete = BulkEdit {
            ids: vec![3],
            delete: true,
            ..BulkEdit::default()
        };
        let json = serde_json::to_value(starbooru_core::BulkEdit::from(delete)).unwrap();
        assert_eq!(json, serde_json::json!({"ids": [3], "delete": true}));
    }
}
