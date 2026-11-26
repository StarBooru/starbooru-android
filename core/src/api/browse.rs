//! Browsing beyond a search: related posts, Explore, the home page, saved
//! searches and the blacklist.

use super::Api;
use crate::error::Result;
use crate::model::{Explore, Home, Post, SavedSearch};

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// Up to `limit` posts like `id` (at most 48), most alike first.
    pub async fn related(&self, id: i64, limit: u32) -> Result<Vec<Post>> {
        let related = self.client.related(id, limit).await?;
        Ok(related
            .posts
            .into_iter()
            .map(|p| Post::from_core(p, &self.client))
            .collect())
    }

    /// Explore over the last `days` (1 to 365), narrowed by search `query`.
    pub async fn explore(&self, days: u32, query: String) -> Result<Explore> {
        let explore = self.client.explore(days, &query).await?;
        Ok(Explore::from_core(explore, &self.client))
    }

    /// The home page, when `ServerInfo::home` says it is on.
    pub async fn home(&self) -> Result<Home> {
        let home = self.client.home().await?;
        Ok(Home::from_core(home, &self.client))
    }

    /// The account's saved searches, by name.
    pub async fn saved_searches(&self) -> Result<Vec<SavedSearch>> {
        let saved = self.client.saved_searches().await?;
        Ok(saved.into_iter().map(Into::into).collect())
    }

    /// Keeps a search. A name the account has already is `name_taken`.
    pub async fn save_search(&self, name: String, query: String) -> Result<SavedSearch> {
        Ok(self.client.save_search(&name, &query).await?.into())
    }

    pub async fn delete_search(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_search(id).await?)
    }

    /// The tags and ratings (`rating:explicit`) whose posts the account
    /// doesn't see; for a guest, the server's default.
    pub async fn blacklist(&self) -> Result<Vec<String>> {
        Ok(self.client.blacklist().await?.entries)
    }

    /// Replaces the account's blacklist, and answers with it as the server
    /// keeps it: names cleaned, aliases followed.
    pub async fn set_blacklist(&self, entries: Vec<String>) -> Result<Vec<String>> {
        Ok(self.client.set_blacklist(&entries).await?.entries)
    }
}
