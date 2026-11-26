//! The account's security: the password, the signed-in devices, the
//! provider accounts it signs in with, and API keys.
//!
//! These need a session: with an API key as the token they answer
//! `forbidden`.

use std::sync::Arc;

use super::Api;
use crate::error::Result;
use crate::model::{ApiKey, CreatedApiKey, NewApiKey, SessionInfo, SignInMethods};
use crate::oidc::ProviderLink;

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// Changes the password and signs out every other session; this one
    /// stays. `current` is ignored while the account has no password (see
    /// `sign_in_methods`). A wrong `current` is `bad_request`; ten in a row
    /// are `RateLimited`. The new one needs 8 characters or more.
    pub async fn change_password(&self, current: String, new_password: String) -> Result<()> {
        Ok(self.client.change_password(&current, &new_password).await?)
    }

    /// The account's live sessions, this one first, marked `current`.
    pub async fn sessions(&self) -> Result<Vec<SessionInfo>> {
        let sessions = self.client.sessions().await?;
        Ok(sessions.into_iter().map(Into::into).collect())
    }

    /// Signs one session out; this one's own id signs this device out.
    pub async fn revoke_session(&self, id: i64) -> Result<()> {
        Ok(self.client.revoke_session(id).await?)
    }

    /// Signs out everywhere but here, and answers how many sessions ended.
    pub async fn revoke_other_sessions(&self) -> Result<u64> {
        Ok(self.client.revoke_other_sessions().await?.revoked)
    }

    /// Whether the account has a password, and the provider accounts linked
    /// to it.
    pub async fn sign_in_methods(&self) -> Result<SignInMethods> {
        Ok(self.client.sign_in_methods().await?.into())
    }

    /// Starts linking an account at the identity provider to this one, for
    /// signing in with it. A server without a provider (`ServerInfo::oidc`
    /// is `None`) answers `not_found`.
    pub async fn begin_provider_link(&self) -> Result<Arc<ProviderLink>> {
        ProviderLink::begin(self.client.clone()).await
    }

    /// Unlinks a provider account. The last way to sign in (no password,
    /// no other provider account) is `bad_request`.
    pub async fn unlink_identity(&self, id: i64) -> Result<()> {
        Ok(self.client.unlink_identity(id).await?)
    }

    /// The account's API keys, newest first.
    pub async fn api_keys(&self) -> Result<Vec<ApiKey>> {
        let keys = self.client.api_keys().await?;
        Ok(keys.into_iter().map(Into::into).collect())
    }

    /// Makes a key. The answer holds the only copy of its token. Past 50
    /// keys it is `bad_request`.
    pub async fn create_api_key(&self, key: NewApiKey) -> Result<CreatedApiKey> {
        Ok(self.client.create_api_key(&key.into()).await?.into())
    }

    /// The key stops working at once.
    pub async fn delete_api_key(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_api_key(id).await?)
    }
}
