//! Tags: the autocomplete, the Tags page, one tag, and changing them.
//! Aliases and implications are for admins; others get `forbidden`.

use super::Api;
use crate::error::Result;
use crate::model::{MAX_TAG_LIMIT, Tag, TagCorrection, TagDetail, TagOrder, TagPage, TagUpdate};

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// The autocomplete: tags whose name or alias starts with `prefix`,
    /// most used first. `limit` is 1 to 100.
    pub async fn tags(&self, prefix: String, limit: u32) -> Result<Vec<Tag>> {
        let tags = self.client.tags(&prefix, limit).await?;
        Ok(tags.into_iter().map(Into::into).collect())
    }

    /// One page of the Tags page, from 1. An empty `prefix` lists every tag
    /// in use. `limit` is clamped to 1 to 100.
    pub async fn tag_list(
        &self,
        prefix: String,
        order: TagOrder,
        page: u32,
        limit: u32,
    ) -> Result<TagPage> {
        let limit = limit.clamp(1, MAX_TAG_LIMIT);
        let tags = self
            .client
            .tag_list(&prefix, order.into(), page, limit)
            .await?;
        Ok(TagPage::new(tags, page, limit))
    }

    /// "Did you mean" for the unknown ones among `names`.
    pub async fn tag_corrections(&self, names: Vec<String>) -> Result<Vec<TagCorrection>> {
        let found = self.client.tag_corrections(&names.join(" ")).await?;
        Ok(found.into_iter().map(Into::into).collect())
    }

    /// A tag with its aliases, implications and place in the tree. An alias
    /// opens its tag.
    pub async fn tag(&self, name: String) -> Result<TagDetail> {
        Ok(self.client.tag(&name).await?.into())
    }

    /// Changes a tag's category or parent; a tag that doesn't exist is made.
    pub async fn update_tag(&self, name: String, update: TagUpdate) -> Result<Tag> {
        Ok(self.client.update_tag(&name, &update.into()).await?.into())
    }

    /// Makes `alias` another name for the tag. A tag already called that
    /// is merged in: its posts, aliases and implications move over.
    pub async fn add_alias(&self, name: String, alias: String) -> Result<TagDetail> {
        Ok(self.client.add_alias(&name, &alias).await?.into())
    }

    pub async fn remove_alias(&self, name: String, alias: String) -> Result<TagDetail> {
        Ok(self.client.remove_alias(&name, &alias).await?.into())
    }

    /// Every post tagged `name` gets `implies` too, the ones tagged now
    /// included.
    pub async fn add_implication(&self, name: String, implies: String) -> Result<TagDetail> {
        Ok(self.client.add_implication(&name, &implies).await?.into())
    }

    /// Posts keep the tags the implication already gave them.
    pub async fn remove_implication(&self, name: String, implied: String) -> Result<TagDetail> {
        Ok(self
            .client
            .remove_implication(&name, &implied)
            .await?
            .into())
    }
}
