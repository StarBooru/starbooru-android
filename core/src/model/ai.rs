//! Which AI tools the server offers, and the tags one suggests for a post.

use starbooru_client::Client;

use super::{Figure, Rating, TagCategory, figure_list};

/// Which AI buttons to show.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AiStatus {
    /// Any AI tool is on.
    pub enabled: bool,
    /// The tagging model, when tagging is on.
    pub model: Option<String>,
    /// Suggest tags.
    pub tagging: bool,
    /// Translate a post's text.
    pub translation: bool,
    /// Translate the text over a comic's picture.
    pub comics: bool,
    /// Find people.
    pub people: bool,
    /// New uploads are tagged by the AI unless the uploader says.
    pub autotag: bool,
}

impl From<starbooru_core::AiStatus> for AiStatus {
    fn from(s: starbooru_core::AiStatus) -> Self {
        Self {
            enabled: s.enabled,
            model: s.model,
            tagging: s.tagging,
            translation: s.translation,
            comics: s.comics,
            people: s.people,
            autotag: s.autotag,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TagSuggestion {
    pub name: String,
    /// The existing tag's, or the one the model gave.
    pub category: TagCategory,
    /// The tag, or an alias of it, is on the server already.
    pub exists: bool,
}

/// What the AI suggests for a post. Nothing on the post changes.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TagSuggestions {
    pub tags: Vec<TagSuggestion>,
    pub rating: Option<Rating>,
    /// The people it saw, names as the model gave them, without faces.
    pub figures: Vec<Figure>,
}

impl TagSuggestions {
    pub(crate) fn from_core(s: starbooru_core::TagSuggestions, client: &Client) -> Self {
        Self {
            tags: s
                .tags
                .into_iter()
                .map(|t| TagSuggestion {
                    name: t.name,
                    category: t.category.into(),
                    exists: t.exists,
                })
                .collect(),
            rating: s.rating.map(Into::into),
            figures: figure_list(s.figures, client),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn suggestions_keep_their_categories_and_people() {
        let client = Client::new("https://booru.example").unwrap();
        let found: starbooru_core::TagSuggestions = serde_json::from_value(serde_json::json!({
            "tags": [{"name": "saber", "category": "character", "exists": false}],
            "rating": "general",
            "figures": [{"kind": "girl", "tags": {"hair": ["blonde_hair"]}}]
        }))
        .unwrap();
        let found = TagSuggestions::from_core(found, &client);
        assert_eq!(found.tags[0].category, TagCategory::Character);
        assert_eq!(found.rating, Some(Rating::Safe));
        assert_eq!(found.figures[0].tags[0].tags, ["blonde_hair"]);
        let status: starbooru_core::AiStatus = serde_json::from_value(serde_json::json!({
            "enabled": true, "model": "wd", "tagging": true, "translation": false
        }))
        .unwrap();
        // A server from before people says nothing about them.
        assert!(!AiStatus::from(status).people);
    }
}
