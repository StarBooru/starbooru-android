//! Changing posts: tags, rating and source, votes, the editor, and the
//! recycle bin. The bin is listed with `posts(trash_query(), ..)`.

use super::Api;
use crate::error::Result;
use crate::model::{BulkEdit, BulkResult, MediaEdit, Post, PostUpdate, PurgeResult};

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    pub async fn update_post(&self, id: i64, update: PostUpdate) -> Result<Post> {
        let post = self.client.update_post(id, &update.into()).await?;
        Ok(Post::from_core(post, &self.client))
    }

    /// One change to many posts, in one go.
    pub async fn bulk_edit(&self, edit: BulkEdit) -> Result<BulkResult> {
        Ok(self.client.bulk_edit(&edit.into()).await?.into())
    }

    /// Moves the post to the recycle bin (its uploader or an admin).
    pub async fn delete_post(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_post(id).await?)
    }

    /// Brings a post back from the recycle bin.
    pub async fn restore_post(&self, id: i64) -> Result<Post> {
        let post = self.client.restore_post(id).await?;
        Ok(Post::from_core(post, &self.client))
    }

    /// Deletes a post in the recycle bin for good, with its files. A live
    /// post is `not_deleted`.
    pub async fn purge_post(&self, id: i64) -> Result<()> {
        Ok(self.client.purge_post(id).await?)
    }

    /// Admins: deletes everything in the recycle bin for good.
    pub async fn empty_trash(&self) -> Result<PurgeResult> {
        Ok(self.client.empty_trash().await?.into())
    }

    /// A like (1), a dislike (-1) or no vote (0). On a server that counts
    /// stars, a like gives 5 and a dislike 1.
    pub async fn vote(&self, id: i64, value: i16) -> Result<Post> {
        let post = self.client.vote(id, value).await?;
        Ok(Post::from_core(post, &self.client))
    }

    /// 1 to 5 stars, or 0 for none, where the server counts stars.
    pub async fn vote_stars(&self, id: i64, stars: u8) -> Result<Post> {
        let post = self.client.vote_stars(id, stars).await?;
        Ok(Post::from_core(post, &self.client))
    }

    /// On a public server, a vote on one of a post's tags: 1 it belongs, -1
    /// it doesn't, 0 takes the vote back. Enough votes against take the tag
    /// off (`Post::voted_off`). A tag not on the post is `bad_request`; a
    /// private server answers `tag_votes_off`.
    pub async fn vote_tag(&self, post_id: i64, tag: String, value: i16) -> Result<Post> {
        let post = self.client.vote_tag(post_id, &tag, value).await?;
        Ok(Post::from_core(post, &self.client))
    }

    /// Saves an edited copy of a post's picture or video as a new post,
    /// with the original's tags, rating, source and people, and answers it.
    /// The server does the work, so this can take a while for a video. See
    /// `edit_problem` for what it refuses (`bad_request`); a result over the
    /// upload limit is 413, one that exists already `duplicate`, and
    /// `bin_original` without the right to delete is `forbidden`.
    pub async fn edit_post(&self, id: i64, edit: MediaEdit) -> Result<Post> {
        let post = self.client.edit_post(id, &edit.into()).await?;
        Ok(Post::from_core(post, &self.client))
    }
}
