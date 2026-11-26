//! Share links, the page a link opens, and the account's defaults for them.

use starbooru_client::Client;

use super::Api;
use crate::error::Result;
use crate::model::{NewShare, Share, ShareUpdate, SharedView, SharingSettings};

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// The account's links, newest first. Empty on a public server.
    pub async fn shares(&self) -> Result<Vec<Share>> {
        let shares = self.client.shares().await?;
        Ok(shares
            .into_iter()
            .map(|s| Share::from_core(s, &self.client))
            .collect())
    }

    /// Makes a link. A post missing or in the bin, or past 500 links, is
    /// `bad_request`; a public server answers `share_by_address`.
    pub async fn create_share(&self, share: NewShare) -> Result<Share> {
        let share = self.client.create_share(&share.into()).await?;
        Ok(Share::from_core(share, &self.client))
    }

    pub async fn update_share(&self, id: i64, update: ShareUpdate) -> Result<Share> {
        let share = self.client.update_share(id, &update.into()).await?;
        Ok(Share::from_core(share, &self.client))
    }

    /// The link stops working at once.
    pub async fn delete_share(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_share(id).await?)
    }

    /// What the link with `token` shows (see `share_link`). It needs no
    /// account and goes out without this `Api`'s token. An unknown,
    /// expired or removed link is `not_found`.
    pub async fn shared(&self, token: String) -> Result<SharedView> {
        let guest = match self.client.token() {
            None => self.client.clone(),
            Some(_) => Client::new(self.client.base_url())?,
        };
        let view = guest.shared(&token).await?;
        Ok(SharedView::from_core(view, &self.client))
    }

    /// Whether the account's links show a preview when they leave it to
    /// the account.
    pub async fn sharing(&self) -> Result<SharingSettings> {
        Ok(self.client.sharing().await?.into())
    }

    pub async fn set_sharing(&self, settings: SharingSettings) -> Result<SharingSettings> {
        Ok(self.client.set_sharing(&settings.into()).await?.into())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::error::CoreError;

    #[tokio::test]
    async fn a_shared_page_from_a_server_that_isnt_there_is_unreachable() {
        let api = Api::new("http://127.0.0.1:9".into(), Some("t".into())).unwrap();
        let err = api.shared("0".repeat(32)).await.unwrap_err();
        assert!(matches!(err, CoreError::Unreachable { .. }), "{err}");
    }
}
