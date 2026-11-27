//! Administration. Every call here is for admins; others get `forbidden`.
//! Some need a session rather than an API key (`forbidden` with a key):
//! the backups, switching the mode, and undoing a tag the votes took off.
//!
//! Long jobs report through a status read, asked again until done:
//! `backups()` says `running` while a backup is made, `update_status()`
//! has the update's `run` (`UpdateRun::running`), and `admin_ai()` has
//! each model's download `progress`, with `busy` while anything moves.

use super::Api;
use crate::error::Result;
use crate::model::{
    AdminUser, AiAdminStatus, AiTool, AiToolUpdate, Backup, BackupSchedule, Backups, Mode,
    ModePreview, ModeSwitched, Post, ScoreKind, ScorePreview, ServerSettings, ServerStats,
    TagRemovalPage, UpdateSettings, UpdateStatus, UserUpdate,
};

/// The longest page of `tag_removals`.
const MAX_REMOVALS_LIMIT: u32 = starbooru_core::MAX_PAGE_LIMIT;

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// Posts, accounts, storage and the last 30 days of uploads.
    pub async fn admin_stats(&self) -> Result<ServerStats> {
        Ok(self.client.admin_stats().await?.into())
    }

    /// Every account, oldest first.
    pub async fn admin_users(&self) -> Result<Vec<AdminUser>> {
        let users = self.client.admin_users().await?;
        Ok(users
            .into_iter()
            .map(|u| AdminUser::from_core(u, &self.client))
            .collect())
    }

    /// Changes an account's role, state, storage limit or profile. The
    /// admin's own role, state or limit is `bad_request`; an unknown id
    /// `not_found`.
    pub async fn update_user(&self, id: i64, update: UserUpdate) -> Result<AdminUser> {
        let user = self.client.update_user(id, &update.into()).await?;
        Ok(AdminUser::from_core(user, &self.client))
    }

    /// Gives an account a new password (8 characters or more) and signs it
    /// out everywhere; its API keys keep working. The admin's own account is
    /// `bad_request`: use `change_password`.
    pub async fn reset_user_password(&self, id: i64, new_password: String) -> Result<()> {
        Ok(self.client.reset_user_password(id, &new_password).await?)
    }

    /// Removes an account with its sessions, keys, votes, saved searches
    /// and picture. Its posts and forum posts stay, without an author. The
    /// admin's own account is `bad_request`.
    pub async fn delete_user(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_user(id).await?)
    }

    /// The settings in force.
    pub async fn admin_settings(&self) -> Result<ServerSettings> {
        Ok(self.client.admin_settings().await?.into())
    }

    /// Saves the settings; they apply at once. Another `mode` than the one
    /// in force is `use_mode_switch`; stars on a public server
    /// `public_counts_likes`; a bad name, number, default blacklist or home
    /// page `bad_request`. A new score kind converts the votes, and the
    /// answer's `changes` say how.
    pub async fn set_admin_settings(&self, settings: ServerSettings) -> Result<ServerSettings> {
        Ok(self
            .client
            .set_admin_settings(&settings.into())
            .await?
            .into())
    }

    /// What switching to `to` would delete and convert. Nothing changes.
    pub async fn mode_preview(&self, to: Mode) -> Result<ModePreview> {
        Ok(self.client.mode_preview(to.into()).await?.into())
    }

    /// Switches between private and public, after a backup unless `backup`
    /// is false, and answers once it's done. The same mode is `same_mode`;
    /// a backup asked for without `pg_dump` `backup_unavailable`, one already
    /// being made `backup_running`, a failed one `backup_failed` (nothing
    /// changed), another switch under way `mode_switch_running`.
    pub async fn switch_mode(&self, to: Mode, backup: bool) -> Result<ModeSwitched> {
        let switch = starbooru_core::ModeSwitch {
            to: to.into(),
            backup,
        };
        Ok(self.client.switch_mode(&switch).await?.into())
    }

    /// What saving the settings with score kind `to` would convert and
    /// delete. Stars on a public server is `public_counts_likes`.
    pub async fn score_preview(&self, to: ScoreKind) -> Result<ScorePreview> {
        Ok(self.client.score_preview(to.into()).await?.into())
    }

    /// The database backups, newest first, with whether one is being made.
    pub async fn backups(&self) -> Result<Backups> {
        let backups = self.client.backups().await?;
        Ok(Backups::from_core(backups, &self.client))
    }

    /// Makes a backup and answers once it's written, which can take a while
    /// on a big collection; `backups().running` says it's under way
    /// meanwhile. Without `pg_dump` it is `forbidden`; with one being made
    /// already `backup_running`.
    pub async fn create_backup(&self) -> Result<Backup> {
        let backup = self.client.create_backup().await?;
        Ok(Backup::from_core(backup, &self.client))
    }

    /// An unknown name is `not_found`.
    pub async fn delete_backup(&self, name: String) -> Result<()> {
        Ok(self.client.delete_backup(&name).await?)
    }

    /// Automatic backups beyond the new `keep` go at once.
    pub async fn set_backup_schedule(&self, schedule: BackupSchedule) -> Result<BackupSchedule> {
        Ok(self
            .client
            .set_backup_schedule(&schedule.into())
            .await?
            .into())
    }

    /// The version running, the newest release, how the server updates and
    /// the update under way or the last one's result. Ask again every second
    /// or two while `run.running`; while the server restarts it is
    /// `Unreachable` for a moment.
    pub async fn update_status(&self) -> Result<UpdateStatus> {
        Ok(self.client.update_status().await?.into())
    }

    /// Asks for the newest release now. Once a minute: sooner is
    /// `RateLimited`; with the checks off `forbidden`. A failed check isn't
    /// an error: `check_error` says what went wrong.
    pub async fn check_for_updates(&self) -> Result<UpdateStatus> {
        Ok(self.client.check_for_updates().await?.into())
    }

    /// `auto_hour` past 23 is `bad_request`.
    pub async fn set_update_settings(&self, settings: UpdateSettings) -> Result<UpdateStatus> {
        Ok(self
            .client
            .set_update_settings(&settings.into())
            .await?
            .into())
    }

    /// Starts installing the newest release, after a backup unless `backup`
    /// is false, and answers at once; follow it with `update_status`.
    /// Nothing newer is `up_to_date`; a server that can't update itself
    /// `cannot_update`; a backup without `pg_dump` `backup_unavailable`; one
    /// under way `update_running`.
    pub async fn install_update(&self, backup: bool) -> Result<UpdateStatus> {
        let install = starbooru_core::UpdateInstall { backup };
        Ok(self.client.install_update(&install).await?.into())
    }

    /// Every AI tool, its models and the machine.
    pub async fn admin_ai(&self) -> Result<AiAdminStatus> {
        Ok(self.client.admin_ai().await?.into())
    }

    /// Tags new uploads, and translates new images' text into
    /// `auto_translate` (a language code, or empty for off; with comics off
    /// it is `bad_request`).
    pub async fn set_ai_general(
        &self,
        autotag: bool,
        auto_translate: String,
    ) -> Result<AiAdminStatus> {
        let update = starbooru_core::AiGeneralUpdate {
            autotag,
            auto_translate,
        };
        Ok(self.client.set_ai_general(&update).await?.into())
    }

    /// How one tool runs; the models it needs start downloading. A bad
    /// value or another tool's model is `bad_request`.
    pub async fn set_ai_tool(&self, tool: AiTool, update: AiToolUpdate) -> Result<AiAdminStatus> {
        Ok(self
            .client
            .set_ai_tool(tool.into(), &update.into())
            .await?
            .into())
    }

    /// A short chat with the tool's saved API, and how many milliseconds it
    /// took. Outside API mode it is `bad_request`; an API that errors
    /// `fetch_failed`.
    pub async fn test_ai_tool(&self, tool: AiTool) -> Result<u32> {
        Ok(self.client.test_ai_tool(tool.into()).await?.ms)
    }

    /// Queues a model's download; its `progress` shows in `admin_ai`.
    pub async fn install_ai_model(&self, id: String) -> Result<AiAdminStatus> {
        Ok(self.client.install_ai_model(&id).await?.into())
    }

    /// A model a tool uses is `bad_request`.
    pub async fn uninstall_ai_model(&self, id: String) -> Result<AiAdminStatus> {
        Ok(self.client.uninstall_ai_model(&id).await?.into())
    }

    /// One page, from 1, of the tags votes took off posts, newest first.
    /// `limit` is clamped to 1 to 200.
    pub async fn tag_removals(&self, page: u32, limit: u32) -> Result<TagRemovalPage> {
        let limit = limit.clamp(1, MAX_REMOVALS_LIMIT);
        let page = self.client.tag_removals(page.max(1), limit).await?;
        Ok(TagRemovalPage::from_core(page, &self.client))
    }

    /// Puts the tag back on its post, its votes cleared, and answers the
    /// post.
    pub async fn undo_tag_removal(&self, id: i64) -> Result<Post> {
        let post = self.client.undo_tag_removal(id).await?;
        Ok(Post::from_core(post, &self.client))
    }
}
