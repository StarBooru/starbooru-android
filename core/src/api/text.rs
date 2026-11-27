//! The text in a post's picture: reading it again, and translating it.
//! Any account. `to` is a BCP 47 code such as `en` or `pt-BR`, usually the
//! app's language; a bad one is `bad_request`.

use super::Api;
use crate::error::Result;
use crate::model::{PanelTranslation, Post};

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// Reads the picture's text again (OCR) and answers the post with its
    /// `ocr_text`. It takes some seconds; OCR off is `bad_request`.
    pub async fn rescan_ocr(&self, id: i64) -> Result<Post> {
        let post = self.client.rescan_ocr(id).await?;
        Ok(Post::from_core(post, &self.client))
    }

    /// The post's `ocr_text` in language `to`. Nothing is kept. Translation
    /// off, or a post without text, is `bad_request`; a provider that fails
    /// `fetch_failed`.
    pub async fn translate_text(&self, id: i64, to: String) -> Result<String> {
        Ok(self.client.translate_text(id, &to).await?.text)
    }

    /// The translations kept to lay over the picture, newest first.
    pub async fn panel_translations(&self, id: i64) -> Result<Vec<PanelTranslation>> {
        let kept = self.client.panel_translations(id).await?;
        Ok(kept.into_iter().map(Into::into).collect())
    }

    /// Finds each piece of text in the picture, where it is and its
    /// translation into `to`, and keeps it in place of an older one in that
    /// language. Translation off, a video, a GIF or a format it can't read
    /// is `bad_request`; a model that fails `fetch_failed`.
    pub async fn translate_panels(&self, id: i64, to: String) -> Result<PanelTranslation> {
        Ok(self.client.translate_panels(id, &to).await?.into())
    }
}
