//! `Api`: one server, signed in or as a guest. Kotlin makes a new one when
//! the server or the session changes; each async method is a `suspend`
//! function there. The methods are split by screen: tags, editing posts,
//! browsing, files in and out, the account's security, sharing, people,
//! duplicates, profiles and the forum.

use std::sync::Arc;

use starbooru_client::Client;
use starbooru_core::Credentials;

use crate::error::Result;
use crate::model::{Neighbors, Post, PostPage, ServerInfo, SignedIn, Usage, User};
use crate::oidc::ProviderSignIn;

mod account;
mod browse;
mod duplicates;
mod edits;
mod forum;
#[cfg(test)]
mod live;
#[cfg(test)]
mod live_screens;
mod people;
mod profile;
mod sharing;
mod tags;
mod transfer;

pub use transfer::UploadProgress;

#[derive(uniffi::Object)]
pub struct Api {
    client: Client,
}

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// `server` is the instance root, as `normalize_server` keeps it.
    /// Without a token, requests go out as a guest.
    #[uniffi::constructor]
    pub fn new(server: String, token: Option<String>) -> Result<Arc<Self>> {
        let client = Client::new(&server)?.with_token(token);
        Ok(Arc::new(Self { client }))
    }

    pub fn server(&self) -> String {
        self.client.base_url().to_owned()
    }

    pub fn signed_in(&self) -> bool {
        self.client.token().is_some()
    }

    pub async fn info(&self) -> Result<ServerInfo> {
        Ok(self.client.info().await?.into())
    }

    pub async fn sign_in(&self, username: String, password: String) -> Result<SignedIn> {
        let creds = Credentials { username, password };
        Ok(self.client.login(&creds).await?.into())
    }

    /// Makes an account; the first on a server becomes its administrator.
    pub async fn register(&self, username: String, password: String) -> Result<SignedIn> {
        let creds = Credentials { username, password };
        Ok(self.client.register(&creds).await?.into())
    }

    /// Ends this session on the server.
    pub async fn sign_out(&self) -> Result<()> {
        Ok(self.client.logout().await?)
    }

    /// Starts a sign-in through the identity provider.
    pub async fn begin_provider_sign_in(&self) -> Result<Arc<ProviderSignIn>> {
        ProviderSignIn::begin(self.client.clone()).await
    }

    pub async fn me(&self) -> Result<User> {
        Ok(self.client.me().await?.into())
    }

    /// What the account has uploaded, against its storage limit.
    pub async fn usage(&self) -> Result<Usage> {
        Ok(self.client.usage().await?.into())
    }

    /// One page of a search's results. `seed` keeps `order:random` the same
    /// from page to page.
    pub async fn posts(
        &self,
        query: String,
        seed: Option<i64>,
        page: u32,
        limit: u32,
    ) -> Result<PostPage> {
        let page = self.client.search_posts(&query, seed, page, limit).await?;
        Ok(PostPage::from_core(page, &self.client))
    }

    pub async fn post(&self, id: i64) -> Result<Post> {
        let post = self.client.post(id).await?;
        Ok(Post::from_core(post, &self.client))
    }

    /// The posts before and after `id` in a search's results.
    pub async fn neighbors(&self, id: i64, query: String, seed: Option<i64>) -> Result<Neighbors> {
        Ok(self.client.neighbors(id, &query, seed).await?.into())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::error::CoreError;

    #[test]
    fn a_bad_address_is_refused() {
        assert!(matches!(
            Api::new("ftp://x".into(), None),
            Err(CoreError::InvalidAddress { .. })
        ));
    }

    #[test]
    fn keeps_the_server_and_the_session() {
        let api = Api::new("https://booru.example/".into(), Some("t".into())).unwrap();
        assert_eq!(api.server(), "https://booru.example");
        assert!(api.signed_in());
        let guest = Api::new("https://booru.example".into(), None).unwrap();
        assert!(!guest.signed_in());
    }

    #[tokio::test]
    async fn a_server_that_isnt_there_is_unreachable() {
        let api = Api::new("http://127.0.0.1:9".into(), None).unwrap();
        assert!(matches!(
            api.info().await,
            Err(CoreError::Unreachable { .. })
        ));
    }
}
