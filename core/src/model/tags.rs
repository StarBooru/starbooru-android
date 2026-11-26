//! Tags: the autocomplete, the Tags page's list, one tag with what it is
//! linked to, and what an edit may change.

use super::TagCategory;

/// The most tags one request returns; the server clamps to it.
pub const MAX_TAG_LIMIT: u32 = 100;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Tag {
    pub id: i64,
    pub name: String,
    pub category: TagCategory,
    /// Live posts with the tag.
    pub post_count: i32,
}

impl From<starbooru_core::Tag> for Tag {
    fn from(t: starbooru_core::Tag) -> Self {
        Self {
            id: t.id,
            name: t.name,
            category: t.category.into(),
            post_count: t.post_count,
        }
    }
}

fn tags(list: Vec<starbooru_core::Tag>) -> Vec<Tag> {
    list.into_iter().map(Into::into).collect()
}

/// How the Tags page sorts.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum TagOrder {
    /// Most used first.
    Count,
    Name,
}

impl From<TagOrder> for starbooru_core::TagOrder {
    fn from(o: TagOrder) -> Self {
        match o {
            TagOrder::Count => starbooru_core::TagOrder::Count,
            TagOrder::Name => starbooru_core::TagOrder::Name,
        }
    }
}

/// One page of the Tags page's list.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TagPage {
    pub tags: Vec<Tag>,
    pub page: u32,
    /// The page is full, so another may follow. The server doesn't say, so
    /// after a full last page the next one comes back empty.
    pub has_more: bool,
}

impl TagPage {
    /// `limit` as it was sent, already clamped to the server's range.
    pub(crate) fn new(list: Vec<starbooru_core::Tag>, page: u32, limit: u32) -> Self {
        let has_more = usize::try_from(limit).is_ok_and(|limit| limit > 0 && list.len() >= limit);
        Self {
            tags: tags(list),
            page,
            has_more,
        }
    }
}

/// A tag with its aliases, implications and place in the tree.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TagDetail {
    pub tag: Tag,
    /// Other names that mean this tag.
    pub aliases: Vec<String>,
    /// Tags every post with this one also gets.
    pub implies: Vec<Tag>,
    /// Tags that bring this one.
    pub implied_by: Vec<Tag>,
    /// Its parents, the top of the tree first.
    pub ancestors: Vec<Tag>,
    /// The tags right under it; searching it finds their posts too.
    pub children: Vec<Tag>,
}

impl From<starbooru_core::TagDetail> for TagDetail {
    fn from(d: starbooru_core::TagDetail) -> Self {
        Self {
            tag: d.tag.into(),
            aliases: d.aliases,
            implies: tags(d.implies),
            implied_by: tags(d.implied_by),
            ancestors: tags(d.ancestors),
            children: tags(d.children),
        }
    }
}

/// A change to a tag. What is left `None` stays as it is. Tags have no
/// description and no rename. To rename `old`, an admin calls
/// `add_alias(new, old)`: `new` is made, takes `old`'s posts, aliases and
/// implications, and keeps `old` as an alias. Its category starts as
/// general, so set it again.
#[derive(Debug, Clone, Default, PartialEq, Eq, uniffi::Record)]
pub struct TagUpdate {
    #[uniffi(default)]
    pub category: Option<TagCategory>,
    /// The tag to put it under, made if it doesn't exist; `""` moves it to
    /// the top of the tree.
    #[uniffi(default)]
    pub parent: Option<String>,
}

impl From<TagUpdate> for starbooru_core::TagUpdate {
    fn from(u: TagUpdate) -> Self {
        Self {
            category: u.category.map(Into::into),
            parent: u.parent,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tag(name: &str) -> starbooru_core::Tag {
        starbooru_core::Tag {
            id: 1,
            name: name.into(),
            category: starbooru_core::TagCategory::Character,
            post_count: 3,
        }
    }

    #[test]
    fn a_full_page_may_have_more() {
        let page = TagPage::new(vec![tag("a"), tag("b")], 2, 2);
        assert!(page.has_more);
        assert_eq!(page.page, 2);
        assert_eq!(page.tags[0].category, TagCategory::Character);
        assert!(!TagPage::new(vec![tag("a")], 1, 2).has_more);
        assert!(!TagPage::new(Vec::new(), 1, 0).has_more);
    }

    #[test]
    fn an_update_sends_only_what_changes() {
        let update = TagUpdate {
            category: Some(TagCategory::Copyright),
            parent: None,
        };
        let json = serde_json::to_value(starbooru_core::TagUpdate::from(update)).unwrap();
        assert_eq!(json, serde_json::json!({"category": "copyright"}));
        let to_top = TagUpdate {
            parent: Some(String::new()),
            ..TagUpdate::default()
        };
        let json = serde_json::to_value(starbooru_core::TagUpdate::from(to_top)).unwrap();
        assert_eq!(json, serde_json::json!({"parent": ""}));
    }

    #[test]
    fn categories_and_orders_map_both_ways() {
        for c in [
            TagCategory::General,
            TagCategory::Artist,
            TagCategory::Character,
            TagCategory::Copyright,
            TagCategory::Meta,
        ] {
            assert_eq!(TagCategory::from(starbooru_core::TagCategory::from(c)), c);
        }
        assert_eq!(
            starbooru_core::TagOrder::from(TagOrder::Name),
            starbooru_core::TagOrder::Name
        );
        assert_eq!(
            starbooru_core::TagOrder::from(TagOrder::Count),
            starbooru_core::TagOrder::Count
        );
    }

    #[test]
    fn a_detail_keeps_its_relations() {
        let detail: starbooru_core::TagDetail = serde_json::from_value(serde_json::json!({
            "tag": {"id": 5, "name": "boots", "category": "general", "post_count": 9},
            "aliases": ["boot"],
            "implies": [{"id": 6, "name": "footwear", "category": "general", "post_count": 12}],
            "implied_by": []
        }))
        .unwrap();
        let detail = TagDetail::from(detail);
        assert_eq!(detail.tag.name, "boots");
        assert_eq!(detail.tag.post_count, 9);
        assert_eq!(detail.aliases, ["boot"]);
        assert_eq!(detail.implies[0].name, "footwear");
        // A server from before the tree leaves these out.
        assert!(detail.ancestors.is_empty() && detail.children.is_empty());
    }
}
