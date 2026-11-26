//! Posts that look alike.

use super::Api;
use crate::error::Result;
use crate::model::{
    Duplicates, MAX_DUPLICATE_GROUPS, Post, ResolveDuplicates, max_duplicate_distance,
};

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// Up to `limit` groups (1 to 100) of posts whose fingerprints differ
    /// by at most `distance` bits (0 to `max_duplicate_distance()`), the
    /// closest first.
    pub async fn duplicates(&self, distance: u32, limit: u32) -> Result<Duplicates> {
        let distance = distance.min(max_duplicate_distance());
        let limit = limit.clamp(1, MAX_DUPLICATE_GROUPS);
        let found = self.client.duplicates(distance, limit).await?;
        Ok(Duplicates::from_core(found, &self.client))
    }

    /// Keeps one post and moves the others to the recycle bin, and answers
    /// the kept one. A post missing or binned already is `not_found`; one
    /// the account may not delete is `forbidden`.
    pub async fn resolve_duplicates(&self, resolve: ResolveDuplicates) -> Result<Post> {
        let kept = self.client.resolve_duplicates(&resolve.into()).await?;
        Ok(Post::from_core(kept, &self.client))
    }

    /// Marks 2 to 50 posts as not duplicates of each other, for good.
    pub async fn dismiss_duplicates(&self, ids: Vec<i64>) -> Result<()> {
        Ok(self.client.dismiss_duplicates(ids).await?)
    }
}
