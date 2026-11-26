//! Sign-in through the server's identity provider.
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
use crate::model::SignedIn;

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

/// A sign-in under way, from `Api::begin_provider_sign_in`.
#[derive(uniffi::Object)]
pub struct ProviderSignIn {
    client: Client,
    url: String,
    handle: String,
    listener: Mutex<Option<TcpListener>>,
}

impl ProviderSignIn {
    pub(crate) async fn begin(client: Client) -> Result<Arc<Self>> {
        let listener = TcpListener::bind("127.0.0.1:0")
            .await
            .map_err(|e| CoreError::Io {
                detail: e.to_string(),
            })?;
        let port = listener
            .local_addr()
            .map_err(|e| CoreError::Io {
                detail: e.to_string(),
            })?
            .port();
        let start = client
            .oidc_begin(&format!("http://127.0.0.1:{port}/oidc"))
            .await?;
        Ok(Arc::new(Self {
            client,
            url: start.url,
            handle: start.handle,
            listener: Mutex::new(Some(listener)),
        }))
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
                        .write_all(b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                        .await;
                }
            }
        }
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
        let Some(listener) = self.listener.lock().await.take() else {
            return Err(CoreError::SignInCancelled {
                reason: "finished".to_owned(),
            });
        };
        let code = tokio::time::timeout(WAIT, Self::wait_for_code(&listener))
            .await
            .map_err(|_| CoreError::SignInCancelled {
                reason: "timeout".to_owned(),
            })??;
        drop(listener);
        let finish = OidcFinish {
            code,
            handle: self.handle.clone(),
        };
        Ok(self.client.oidc_finish(&finish).await?.into())
    }

    /// Stops listening, when the user goes back without signing in.
    pub async fn cancel(&self) {
        self.listener.lock().await.take();
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
        let code = ProviderSignIn::wait_for_code(&listener).await.unwrap();
        assert_eq!(code, "xyz");
        browser.await.unwrap();
    }
}
