//! The one error type that crosses the bridge. Kotlin gets it as a sealed
//! exception class and turns each kind into words from `strings.xml`.

use starbooru_client::ClientError;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    /// The address isn't one the app can use.
    #[error("invalid server address: {detail}")]
    InvalidAddress { detail: String },
    /// The server was never reached: no network, a wrong address, a server
    /// that's down, or a connection that died on the way.
    #[error("cannot reach the server: {detail}")]
    Unreachable { detail: String },
    /// The session or key is no longer valid.
    #[error("{reason}")]
    Unauthorized { reason: String },
    /// Still rate limited after the client's own retries. `retry_after_secs`
    /// says when to try again, when the server said.
    #[error("too many requests")]
    RateLimited { retry_after_secs: Option<u64> },
    /// The server answered with an error. `code` is its stable code, such as
    /// `not_found` or `duplicate`; `reason` its own words. (Kotlin's
    /// exceptions have a `message` of their own.)
    #[error("{reason}")]
    Api {
        status: u16,
        code: String,
        reason: String,
    },
    /// A file was bigger than allowed.
    #[error("the file is larger than {limit} bytes")]
    TooLarge { limit: u64 },
    /// Reading or writing a file on the device failed.
    #[error("{detail}")]
    Io { detail: String },
    /// Sign-in through the provider was turned down, failed or timed out.
    #[error("sign-in through the provider did not finish: {reason}")]
    SignInCancelled { reason: String },
}

impl From<ClientError> for CoreError {
    fn from(error: ClientError) -> Self {
        match error {
            ClientError::InvalidUrl(detail) => CoreError::InvalidAddress { detail },
            ClientError::Http(e) => {
                if e.is_decode() {
                    // The address answered, but not as a StarBooru server.
                    CoreError::Api {
                        status: e.status().map_or(0, |s| s.as_u16()),
                        code: "not_starbooru".to_owned(),
                        reason: e.to_string(),
                    }
                } else {
                    CoreError::Unreachable {
                        detail: e.to_string(),
                    }
                }
            }
            ClientError::Api { status: 401, error } => CoreError::Unauthorized {
                reason: error.message,
            },
            ClientError::Api { status, error } => CoreError::Api {
                status,
                code: error.code,
                reason: error.message,
            },
            ClientError::RateLimited { retry_after } => CoreError::RateLimited {
                retry_after_secs: retry_after,
            },
            ClientError::TooLarge { limit } => CoreError::TooLarge { limit },
            ClientError::Io(e) => CoreError::Io {
                detail: e.to_string(),
            },
            ClientError::Read(detail) => CoreError::Io { detail },
        }
    }
}

impl From<std::io::Error> for CoreError {
    fn from(error: std::io::Error) -> Self {
        CoreError::Io {
            detail: error.to_string(),
        }
    }
}

pub type Result<T> = std::result::Result<T, CoreError>;

#[cfg(test)]
mod tests {
    use super::*;
    use starbooru_core::ApiError;

    fn api(status: u16, code: &str) -> ClientError {
        ClientError::Api {
            status,
            error: ApiError {
                code: code.to_owned(),
                message: format!("{code} message"),
            },
        }
    }

    #[test]
    fn a_401_is_its_own_kind() {
        assert!(matches!(
            CoreError::from(api(401, "unauthorized")),
            CoreError::Unauthorized { .. }
        ));
    }

    #[test]
    fn other_api_errors_keep_their_code() {
        match CoreError::from(api(404, "not_found")) {
            CoreError::Api {
                status,
                code,
                reason,
            } => {
                assert_eq!(status, 404);
                assert_eq!(code, "not_found");
                assert_eq!(reason, "not_found message");
            }
            other => panic!("{other:?}"),
        }
    }

    #[test]
    fn rate_limits_keep_their_wait() {
        assert!(matches!(
            CoreError::from(ClientError::RateLimited {
                retry_after: Some(90)
            }),
            CoreError::RateLimited {
                retry_after_secs: Some(90)
            }
        ));
    }

    #[test]
    fn a_bad_address_says_so() {
        assert!(matches!(
            CoreError::from(ClientError::InvalidUrl("x".into())),
            CoreError::InvalidAddress { .. }
        ));
    }
}
