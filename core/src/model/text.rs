//! The text in a picture, translated and laid over it: comic and manga
//! panels.

use super::millis;

/// One piece of text in the picture, such as a speech balloon, with its
/// translation. The box is in thousandths of the picture's width (`x`,
/// `w`) and height (`y`, `h`), from its top-left corner.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TextBlock {
    pub x: u16,
    pub y: u16,
    pub w: u16,
    pub h: u16,
    /// As it reads in the picture; may be empty.
    pub text: String,
    pub translation: String,
}

/// The kept translation of a picture's text into one language.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct PanelTranslation {
    /// BCP 47, such as `en` or `pt-BR`.
    pub lang: String,
    /// In reading order.
    pub blocks: Vec<TextBlock>,
    /// The model that made it.
    pub model: String,
    pub created_at_ms: i64,
}

impl From<starbooru_core::PanelTranslation> for PanelTranslation {
    fn from(p: starbooru_core::PanelTranslation) -> Self {
        Self {
            lang: p.lang,
            blocks: p
                .blocks
                .into_iter()
                .map(|b| TextBlock {
                    x: b.x,
                    y: b.y,
                    w: b.w,
                    h: b.h,
                    text: b.text,
                    translation: b.translation,
                })
                .collect(),
            model: p.model,
            created_at_ms: millis(p.created_at),
        }
    }
}
