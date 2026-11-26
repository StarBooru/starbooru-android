//! Sign-in through the server's identity provider, and linking the
//! provider's account to the one signed in.
//!
//! The provider's page opens in a browser tab. The server sends the browser
//! back to `return_to`, a loopback address this module listens on, with the
//! code in the query; the answer to that request sends the browser on to the
//! app's own `starbooru://signed-in`, which brings the app back to the front.

use std::sync::Arc;
use std::time::Duration;

use starbooru_client::Client;
use starbooru_core::OidcFinish;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;
use tokio::sync::Mutex;

use crate::error::{CoreError, Result};
use crate::model::{ProviderIdentity, SignedIn};

/// The address the browser is sent back to once the app has the code.
pub const BACK_TO_APP: &str = "starbooru://signed-in";
/// The server keeps a sign-in open this long.
const WAIT: Duration = Duration::from_secs(10 * 60);
/// More than a request line and its headers is never needed.
const MAX_REQUEST: usize = 16 * 1024;

/// What came back on the loopback address.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Callback {
    Code(String),
    /// `denied` (the user said no) or `failed`.
    Error(String),
}

/// Reads the callback from the start of an HTTP request. Anything else, such
/// as the browser asking for a favicon, is `None`.
pub fn parse_callback(request: &str) -> Option<Callback> {
    let line = request.lines().next()?;
    let mut parts = line.split_whitespace();
    if parts.next()? != "GET" {
        return None;
    }
    let target = parts.next()?;
    let url = url::Url::parse(&format!("http://127.0.0.1{target}")).ok()?;
    let mut error = None;
    for (key, value) in url.query_pairs() {
        match key.as_ref() {
            "oidc_code" if !value.is_empty() => return Some(Callback::Code(value.into_owned())),
            "oidc_error" => error = Some(value.into_owned()),
            _ => {}
        }
    }
    error.map(Callback::Error)
}

fn answer() -> String {
    let body = "<!doctype html><meta charset=utf-8><meta name=viewport content=\"width=device-width\">\
        <title>StarBooru</title><p style=\"font:16px sans-serif;text-align:center;margin-top:30vh\">\
        <a href=\"starbooru://signed-in\">StarBooru</a></p>";
    format!(
        "HTTP/1.1 303 See Other\r\nLocation: {BACK_TO_APP}\r\nContent-Type: text/html; charset=utf-8\r\n\
         Content-Length: {}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n{body}",
        body.len()
    )
}

async fn wait_for_code(listener: &TcpListener) -> Result<String> {
    loop {
        let (mut stream, _) = listener.accept().await.map_err(|e| CoreError::Io {
            detail: e.to_string(),
        })?;
        let mut buf = vec![0u8; MAX_REQUEST];
        let mut read = 0;
        while read < buf.len() {
            let Some(rest) = buf.get_mut(read..) else {
                break;
            };
            match stream.read(rest).await {
                Ok(0) | Err(_) => break,
                Ok(n) => read += n,
            }
            let seen = buf.get(..read).unwrap_or_default();
            if seen.windows(4).any(|w| w == b"\r\n\r\n") {
                break;
            }
        }
        let request = String::from_utf8_lossy(buf.get(..read).unwrap_or_default()).into_owned();
        match parse_callback(&request) {
            Some(callback) => {
                // The browser only needs to be sent on; a failure here
                // doesn't change the sign-in.
                let _ = stream.write_all(answer().as_bytes()).await;
                let _ = stream.shutdown().await;
                return match callback {
                    Callback::Code(code) => Ok(code),
                    Callback::Error(reason) => Err(CoreError::SignInCancelled { reason }),
                };
            }
            None => {
                let _ = stream
                    .write_all(
                        b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                    )
                    .await;
            }
        }
    }
}

/// The loopback address the browser comes back to, open until the code
/// arrives or the user goes back.
struct Loopback {
    listener: Mutex<Option<TcpListener>>,
    return_to: String,
}

impl Loopback {
    async fn open() -> Result<Self> {
        let io = |e: std::io::Error| CoreError::Io {
            detail: e.to_string(),
        };
        let listener = TcpListener::bind("127.0.0.1:0").await.map_err(io)?;
        let port = listener.local_addr().map_err(io)?.port();
        Ok(Self {
            listener: Mutex::new(Some(listener)),
            return_to: format!("http://127.0.0.1:{port}/oidc"),
        })
    }

    /// Waits for the code, once.
    async fn code(&self) -> Result<String> {
        let Some(listener) = self.listener.lock().await.take() else {
            return Err(CoreError::SignInCancelled {
                reason: "finished".to_owned(),
            });
        };
        tokio::time::timeout(WAIT, wait_for_code(&listener))
            .await
            .map_err(|_| CoreError::SignInCancelled {
                reason: "timeout".to_owned(),
            })?
    }

    async fn close(&self) {
        self.listener.lock().await.take();
    }
}

/// A sign-in under way, from `Api::begin_provider_sign_in`.
#[derive(uniffi::Object)]
pub struct ProviderSignIn {
    client: Client,
    url: String,
    handle: String,
    loopback: Loopback,
}

impl ProviderSignIn {
    pub(crate) async fn begin(client: Client) -> Result<Arc<Self>> {
        let loopback = Loopback::open().await?;
        let start = client.oidc_begin(&loopback.return_to).await?;
        Ok(Arc::new(Self {
            client,
            url: start.url,
            handle: start.handle,
            loopback,
        }))
    }
}

#[uniffi::export(async_runtime = "tokio")]
impl ProviderSignIn {
    /// The provider's page, to open in a browser tab.
    pub fn url(&self) -> String {
        self.url.clone()
    }

    /// Waits for the browser to come back, then finishes the sign-in.
    pub async fn finish(&self) -> Result<SignedIn> {
        let code = self.loopback.code().await?;
        let finish = OidcFinish {
            code,
            handle: self.handle.clone(),
        };
        Ok(self.client.oidc_finish(&finish).await?.into())
    }

    /// Stops listening, when the user goes back without signing in.
    pub async fn cancel(&self) {
        self.loopback.close().await;
    }
}

/// Linking the provider's account to the one signed in, from
/// `Api::begin_provider_link`. Once linked, it signs in to this account.
#[derive(uniffi::Object)]
pub struct ProviderLink {
    client: Client,
    url: String,
    handle: String,
    loopback: Loopback,
}

impl ProviderLink {
    pub(crate) async fn begin(client: Client) -> Result<Arc<Self>> {
        let loopback = Loopback::open().await?;
        let start = client.oidc_begin_link(&loopback.return_to).await?;
        Ok(Arc::new(Self {
            client,
            url: start.url,
            handle: start.handle,
            loopback,
        }))
    }
}

#[uniffi::export(async_runtime = "tokio")]
impl ProviderLink {
    /// The provider's page, to open in a browser tab.
    pub fn url(&self) -> String {
        self.url.clone()
    }

    /// Waits for the browser to come back, then links the account. One
    /// linked to another account already is `CoreError::Api` with code
    /// `identity_taken`.
    pub async fn finish(&self) -> Result<ProviderIdentity> {
        let code = self.loopback.code().await?;
        let finish = OidcFinish {
            code,
            handle: self.handle.clone(),
        };
        Ok(self.client.oidc_link(&finish).await?.into())
    }

    /// Stops listening, when the user goes back without linking.
    pub async fn cancel(&self) {
        self.loopback.close().await;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_the_code() {
        let req = "GET /oidc?oidc_code=abc%2B1 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n";
        assert_eq!(parse_callback(req), Some(Callback::Code("abc+1".into())));
    }

    #[test]
    fn reads_a_refusal() {
        let req = "GET /oidc?oidc_error=denied HTTP/1.1\r\n\r\n";
        assert_eq!(parse_callback(req), Some(Callback::Error("denied".into())));
    }

    #[test]
    fn ignores_everything_else() {
        assert_eq!(parse_callback("GET /favicon.ico HTTP/1.1\r\n\r\n"), None);
        assert_eq!(
            parse_callback("POST /oidc?oidc_code=a HTTP/1.1\r\n\r\n"),
            None
        );
        assert_eq!(parse_callback(""), None);
        assert_eq!(parse_callback("GET /oidc?oidc_code= HTTP/1.1"), None);
    }

    #[test]
    fn the_answer_sends_the_browser_back_to_the_app() {
        let a = answer();
        assert!(a.starts_with("HTTP/1.1 303"));
        assert!(a.contains("Location: starbooru://signed-in\r\n"));
    }

    #[tokio::test]
    async fn waits_past_other_requests_for_the_code() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let browser = tokio::spawn(async move {
            for path in ["/favicon.ico", "/oidc?oidc_code=xyz"] {
                let mut s = tokio::net::TcpStream::connect(("127.0.0.1", port))
                    .await
                    .unwrap();
                s.write_all(format!("GET {path} HTTP/1.1\r\nHost: x\r\n\r\n").as_bytes())
                    .await
                    .unwrap();
                let mut out = String::new();
                s.read_to_string(&mut out).await.unwrap();
            }
        });
        let code = wait_for_code(&listener).await.unwrap();
        assert_eq!(code, "xyz");
        browser.await.unwrap();
    }

    async fn come_back(return_to: &str, query: &str) -> String {
        let address = return_to
            .strip_prefix("http://")
            .and_then(|rest| rest.split_once('/'))
            .map(|(host, _)| host.to_owned())
            .unwrap();
        let mut s = tokio::net::TcpStream::connect(address).await.unwrap();
        s.write_all(format!("GET /oidc?{query} HTTP/1.1\r\nHost: x\r\n\r\n").as_bytes())
            .await
            .unwrap();
        let mut out = String::new();
        s.read_to_string(&mut out).await.unwrap();
        out
    }

    #[tokio::test]
    async fn a_loopback_takes_one_code_then_says_it_finished() {
        let loopback = Loopback::open().await.unwrap();
        assert!(loopback.return_to.starts_with("http://127.0.0.1:"));
        assert!(loopback.return_to.ends_with("/oidc"));
        let back = tokio::spawn({
            let return_to = loopback.return_to.clone();
            async move { come_back(&return_to, "oidc_code=c0de").await }
        });
        assert_eq!(loopback.code().await.unwrap(), "c0de");
        assert!(back.await.unwrap().starts_with("HTTP/1.1 303"));
        assert!(matches!(
            loopback.code().await,
            Err(CoreError::SignInCancelled { reason }) if reason == "finished"
        ));
    }

    #[tokio::test]
    async fn a_refusal_or_a_cancel_ends_the_wait() {
        let loopback = Loopback::open().await.unwrap();
        let back = tokio::spawn({
            let return_to = loopback.return_to.clone();
            async move { come_back(&return_to, "oidc_error=denied").await }
        });
        assert!(matches!(
            loopback.code().await,
            Err(CoreError::SignInCancelled { reason }) if reason == "denied"
        ));
        back.await.unwrap();
        let cancelled = Loopback::open().await.unwrap();
        cancelled.close().await;
        assert!(matches!(
            cancelled.code().await,
            Err(CoreError::SignInCancelled { .. })
        ));
    }
}
