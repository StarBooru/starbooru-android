//! People: the page of everyone, the people in a post, and the AI that
//! finds them or suggests tags.

use super::Api;
use crate::error::Result;
use crate::model::{
    AiStatus, Figure, FoundPeople, FranchisePage, MAX_FRANCHISE_LIMIT, PeoplePage, PeopleQuery,
    Post, TagSuggestions,
};

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// One page of the collection's characters.
    pub async fn people(&self, query: PeopleQuery) -> Result<PeoplePage> {
        let page = self.client.people(&query.into()).await?;
        Ok(PeoplePage::from_core(page, &self.client))
    }

    /// One page of the franchises with people, the most people first, whose
    /// name holds `q`. `limit` is clamped to 1 to 200.
    pub async fn franchises(&self, q: String, page: u32, limit: u32) -> Result<FranchisePage> {
        let limit = limit.clamp(1, MAX_FRANCHISE_LIMIT);
        Ok(self.client.franchises(&q, page.max(1), limit).await?.into())
    }

    /// Replaces the people in a post; its tags follow. Too many people or
    /// tags, a bad tag or a face box outside the picture is `bad_request`;
    /// a post in the bin is `deleted`. The crops of new faces come a moment
    /// later, in `post(id)`.
    pub async fn set_figures(&self, id: i64, figures: Vec<Figure>) -> Result<Post> {
        let figures = figures.into_iter().map(Into::into).collect();
        let post = self.client.set_figures(id, figures).await?;
        Ok(Post::from_core(post, &self.client))
    }

    /// Who the AI finds in a post's picture, ready for `set_figures`.
    /// Nothing is saved. With the tool off (`AiStatus::people`) it is
    /// `bad_request`; while its models load `ai_unavailable`, while it is
    /// busy `ai_busy`; a provider that fails `fetch_failed`.
    pub async fn find_people(&self, id: i64) -> Result<FoundPeople> {
        let found = self.client.find_people(id).await?;
        Ok(FoundPeople::from_core(found, &self.client))
    }

    /// Which AI tools the server offers.
    pub async fn ai_status(&self) -> Result<AiStatus> {
        Ok(self.client.ai_status().await?.into())
    }

    /// Tags, a rating and people an AI model suggests for a post. Nothing
    /// changes. With tagging off it is `bad_request`.
    pub async fn suggest_tags(&self, id: i64) -> Result<TagSuggestions> {
        let found = self.client.suggest_tags(id).await?;
        Ok(TagSuggestions::from_core(found, &self.client))
    }
}
