//! The forum of a public server. Guests read it; writing needs an account,
//! and categories an admin. A private server answers `forum_off`.
//!
//! An account posts at most once in 15 seconds and 100 times a day
//! (`RateLimited`); admins aren't held to either.

use super::Api;
use crate::error::Result;
use crate::model::{
    CategoryUpdate, ForumCategory, ForumPost, ForumThread, MAX_FORUM_LIMIT, NewCategory, NewThread,
    ThreadPage, ThreadUpdate, ThreadView,
};

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// The categories, in order.
    pub async fn forum(&self) -> Result<Vec<ForumCategory>> {
        let categories = self.client.forum().await?;
        Ok(categories.into_iter().map(Into::into).collect())
    }

    /// A page of threads, the latest activity first: one category's, pinned
    /// first, or with `None` every category's. A non-empty `query` keeps the
    /// threads whose title or a post has all its words. `limit` is clamped
    /// to 1 to 100.
    pub async fn forum_threads(
        &self,
        category: Option<i64>,
        query: String,
        page: u32,
        limit: u32,
    ) -> Result<ThreadPage> {
        let limit = limit.clamp(1, MAX_FORUM_LIMIT);
        let threads = self
            .client
            .forum_threads(category, &query, page.max(1), limit)
            .await?;
        Ok(ThreadPage::from_core(threads, &self.client))
    }

    /// A thread with a page of its posts, oldest first. `limit` is clamped
    /// to 1 to 100.
    pub async fn forum_thread(&self, id: i64, page: u32, limit: u32) -> Result<ThreadView> {
        let limit = limit.clamp(1, MAX_FORUM_LIMIT);
        let view = self.client.forum_thread(id, page.max(1), limit).await?;
        Ok(ThreadView::from_core(view, &self.client))
    }

    /// A thread with its first post. An unknown category is `bad_request`.
    pub async fn create_forum_thread(&self, thread: NewThread) -> Result<ForumThread> {
        let thread = self.client.create_forum_thread(&thread.into()).await?;
        Ok(ForumThread::from_core(thread, &self.client))
    }

    /// See `ThreadUpdate` for who may change what; others get `forbidden`.
    pub async fn update_forum_thread(&self, id: i64, update: ThreadUpdate) -> Result<ForumThread> {
        let thread = self.client.update_forum_thread(id, &update.into()).await?;
        Ok(ForumThread::from_core(thread, &self.client))
    }

    /// Admins: the thread with all its posts.
    pub async fn delete_forum_thread(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_forum_thread(id).await?)
    }

    /// A reply. In a locked thread it is `locked`, except for admins.
    pub async fn reply_in_forum(&self, thread_id: i64, body: String) -> Result<ForumPost> {
        let post = self.client.reply_in_forum(thread_id, &body).await?;
        Ok(ForumPost::from_core(post, &self.client))
    }

    /// Its author only. A deleted post is `deleted`; one in a locked thread
    /// `locked`.
    pub async fn edit_forum_post(&self, id: i64, body: String) -> Result<ForumPost> {
        let post = self.client.edit_forum_post(id, &body).await?;
        Ok(ForumPost::from_core(post, &self.client))
    }

    /// Its author or an admin. It keeps its place in the thread, empty.
    pub async fn delete_forum_post(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_forum_post(id).await?)
    }

    /// Admins. A name in use is `name_taken`; past 100 categories
    /// `too_many_categories`.
    pub async fn create_forum_category(&self, category: NewCategory) -> Result<ForumCategory> {
        Ok(self
            .client
            .create_forum_category(&category.into())
            .await?
            .into())
    }

    /// Admins.
    pub async fn update_forum_category(
        &self,
        id: i64,
        update: CategoryUpdate,
    ) -> Result<ForumCategory> {
        Ok(self
            .client
            .update_forum_category(id, &update.into())
            .await?
            .into())
    }

    /// Admins. One with threads is `category_not_empty`.
    pub async fn delete_forum_category(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_forum_category(id).await?)
    }
}
