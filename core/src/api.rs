//! `Api`: one server, signed in or as a guest. Kotlin makes a new one when
//! the server or the session changes; each async method is a `suspend`
//! function there. The methods are split by screen: tags, editing posts,
//! browsing, files in and out, the account's security, sharing, people,
//! duplicates, profiles, the forum, the text in pictures, other boorus and
//! background imports, library imports and the administration.

use std::sync::Arc;

use starbooru_client::Client;
use starbooru_core::Credentials;

use crate::error::Result;
use crate::model::{Neighbors, Post, PostPage, ServerInfo, SignedIn, Usage, User};
use crate::oidc::ProviderSignIn;

mod account;
mod admin;
mod boorus;
mod browse;
mod duplicates;
mod edits;
mod forum;
mod library;
#[cfg(test)]
mod live;
#[cfg(test)]
mod live_admin;
#[cfg(test)]
mod live_screens;
mod people;
mod profile;
mod sharing;
mod tags;
mod text;
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

    /// A guest of a public server with a list of their own, kept on the
    /// device (see `guest_blacklist`). It goes with every read of posts:
    /// searches, a post, its neighbours and related posts, Explore, people,
    /// franchises and the home page. `None` leaves the server's default; an
    /// empty list hides nothing. At most `max_guest_blacklist_entries()`, or
    /// those reads are `bad_request`.
    #[uniffi::constructor]
    pub fn guest(server: String, exclude: Option<Vec<String>>) -> Result<Arc<Self>> {
        let client = Client::new(&server)?.with_guest_blacklist(exclude.as_deref());
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
        let own = Api::guest("https://booru.example/".into(), Some(vec!["cat".into()])).unwrap();
        assert_eq!(own.server(), "https://booru.example");
        assert!(!own.signed_in());
        assert!(matches!(
            Api::guest("ftp://x".into(), None),
            Err(CoreError::InvalidAddress { .. })
        ));
    }

    /// Answers one request with an empty page of posts, and hands back the
    /// request line.
    async fn one_page() -> (String, tokio::task::JoinHandle<String>) {
        use tokio::io::{AsyncReadExt, AsyncWriteExt};
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let base = format!("http://{}", listener.local_addr().unwrap());
        let asked = tokio::spawn(async move {
            let (mut sock, _) = listener.accept().await.unwrap();
            let mut buf = vec![0u8; 8192];
            let n = sock.read(&mut buf).await.unwrap();
            let body = br#"{"posts":[],"page":1,"limit":10,"has_more":false}"#;
            let head = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                body.len()
            );
            sock.write_all(head.as_bytes()).await.unwrap();
            sock.write_all(body).await.unwrap();
            let request = String::from_utf8_lossy(&buf[..n]).into_owned();
            request.lines().next().unwrap_or_default().to_owned()
        });
        (base, asked)
    }

    #[tokio::test]
    async fn a_guests_own_list_goes_with_a_search() {
        let (base, asked) = one_page().await;
        let entries = vec!["cat".to_owned(), "rating:explicit".to_owned()];
        let api = Api::guest(base, Some(entries)).unwrap();
        api.posts(String::new(), None, 1, 10).await.unwrap();
        let line = asked.await.unwrap();
        assert!(line.contains("exclude=cat+rating%3Aexplicit"), "{line}");

        // An empty list goes too: it hides nothing.
        let (base, asked) = one_page().await;
        let api = Api::guest(base, Some(Vec::new())).unwrap();
        api.posts(String::new(), None, 1, 10).await.unwrap();
        let line = asked.await.unwrap();
        assert!(
            line.contains("exclude=&") || line.ends_with("exclude= HTTP/1.1"),
            "{line}"
        );

        // Without one, the server's default.
        let (base, asked) = one_page().await;
        let api = Api::guest(base, None).unwrap();
        api.posts(String::new(), None, 1, 10).await.unwrap();
        let line = asked.await.unwrap();
        assert!(!line.contains("exclude"), "{line}");
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
