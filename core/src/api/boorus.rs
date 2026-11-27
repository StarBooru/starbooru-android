//! Other boorus, searched through the server, and background imports. A
//! public server answers the boorus with `remote_off`; imports work in
//! either mode. Each needs an account.

use super::Api;
use crate::error::Result;
use crate::model::{
    BooruAccount, BooruApi, ImportItem, ImportJob, NewBooruAccount, NewRemoteSearch, RemotePage,
    RemotePost, RemoteSearch, RemoteTag, SavedRemoteSearch,
};

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// The accounts on other boorus kept on the server, without their keys.
    pub async fn booru_accounts(&self) -> Result<Vec<BooruAccount>> {
        let accounts = self.client.booru_accounts().await?;
        Ok(accounts.into_iter().map(Into::into).collect())
    }

    /// Keeps the account for its site, in place of the one kept before.
    /// Without both parts it is `bad_request`; past 50 accounts
    /// `too_many_accounts`.
    pub async fn save_booru_account(&self, account: NewBooruAccount) -> Result<BooruAccount> {
        Ok(self
            .client
            .save_booru_account(&account.into())
            .await?
            .into())
    }

    pub async fn delete_booru_account(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_booru_account(id).await?)
    }

    /// The searches on other sites the account kept, by name.
    pub async fn remote_searches(&self) -> Result<Vec<SavedRemoteSearch>> {
        let searches = self.client.remote_searches().await?;
        Ok(searches.into_iter().map(Into::into).collect())
    }

    /// Past 200 it is `too_many_searches`; no name or no site `bad_request`.
    pub async fn save_remote_search(&self, search: NewRemoteSearch) -> Result<SavedRemoteSearch> {
        Ok(self.client.save_remote_search(&search.into()).await?.into())
    }

    pub async fn delete_remote_search(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_remote_search(id).await?)
    }

    /// One page of a search on one site, fetched by the server; a search on
    /// several sites is one call each. A refusal is `bad_request` with the
    /// site's words; a busy site `RateLimited`; one that errors
    /// `fetch_failed`.
    pub async fn remote_search(&self, search: RemoteSearch) -> Result<RemotePage> {
        let page = self.client.remote_search(&search.into()).await?;
        Ok(RemotePage::from_core(page, &self.client))
    }

    /// One post in full from a link to it: a post's page, or a file whose
    /// address carries its MD5. A link to no post is `not_found`.
    pub async fn remote_post(&self, url: String) -> Result<RemotePost> {
        let post = self.client.remote_post(&url).await?;
        Ok(RemotePost::from_core(post, &self.client))
    }

    /// Up to 10 of a site's tags that start with `term`, most used first.
    /// Empty when it offers none or can't be reached.
    pub async fn remote_tags(
        &self,
        api: BooruApi,
        base: String,
        term: String,
    ) -> Result<Vec<RemoteTag>> {
        let query = starbooru_core::RemoteTagQuery {
            api: api.into(),
            base,
            term,
        };
        let tags = self.client.remote_tags(&query).await?;
        Ok(tags.into_iter().map(Into::into).collect())
    }

    /// Queues 1 to 500 addresses for the server to download by itself; the
    /// answer has them all `Queued`. One bad address, a bad tag or URL
    /// import off refuses the batch (`bad_request`); past 2000 waiting it is
    /// `too_many_imports`. Follow them with `imports` (see `imports_poll_every`).
    pub async fn queue_imports(&self, items: Vec<ImportItem>) -> Result<Vec<ImportJob>> {
        let items = items.into_iter().map(Into::into).collect();
        let jobs = self.client.queue_imports(items).await?;
        Ok(jobs.into_iter().map(Into::into).collect())
    }

    /// The account's imports still waiting or running, and those finished
    /// in the last day, newest first.
    pub async fn imports(&self) -> Result<Vec<ImportJob>> {
        let jobs = self.client.imports().await?;
        Ok(jobs.into_iter().map(Into::into).collect())
    }

    /// Cancels a waiting import or forgets a finished one. One downloading
    /// is `running`.
    pub async fn delete_import(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_import(id).await?)
    }

    /// Forgets the finished imports.
    pub async fn clear_imports(&self) -> Result<()> {
        Ok(self.client.clear_imports().await?)
    }
}
