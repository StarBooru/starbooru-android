//! The account's security: its sessions, how it signs in, and API keys.

use super::millis;

/// One signed-in device or app.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SessionInfo {
    pub id: i64,
    /// Like "Firefox on Linux" or "StarBooru app on Android"; empty for
    /// old sessions.
    pub client: String,
    pub created_at_ms: i64,
    /// Kept to within a few minutes.
    pub last_used_at_ms: i64,
    pub expires_at_ms: i64,
    /// The session of this `Api`: this device. It comes first.
    pub current: bool,
}

impl From<starbooru_core::SessionInfo> for SessionInfo {
    fn from(s: starbooru_core::SessionInfo) -> Self {
        Self {
            id: s.id,
            client: s.client,
            created_at_ms: millis(s.created_at),
            last_used_at_ms: millis(s.last_used_at),
            expires_at_ms: millis(s.expires_at),
            current: s.current,
        }
    }
}

/// An account at the identity provider, linked to this one.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ProviderIdentity {
    pub id: i64,
    /// Its username or email there, when the provider said.
    pub label: Option<String>,
    pub created_at_ms: i64,
    pub last_used_at_ms: Option<i64>,
}

impl From<starbooru_core::Identity> for ProviderIdentity {
    fn from(i: starbooru_core::Identity) -> Self {
        Self {
            id: i.id,
            label: i.label,
            created_at_ms: millis(i.created_at),
            last_used_at_ms: i.last_used_at.map(millis),
        }
    }
}

/// How the account signs in.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SignInMethods {
    /// It has a password. One made through the provider has none until it
    /// sets one, and then `change_password` ignores `current`.
    pub password: bool,
    pub identities: Vec<ProviderIdentity>,
}

impl From<starbooru_core::SignInMethods> for SignInMethods {
    fn from(m: starbooru_core::SignInMethods) -> Self {
        Self {
            password: m.password,
            identities: m.identities.into_iter().map(Into::into).collect(),
        }
    }
}

/// The most days an API key may last.
#[uniffi::export]
pub fn max_api_key_days() -> u32 {
    starbooru_core::keys::MAX_API_KEY_DAYS
}

/// A token for scripts and other tools. It acts as the account, but can't
/// manage sign-ins, the password or keys.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ApiKey {
    pub id: i64,
    pub name: String,
    /// The start of the token, like `sbk_1a2b3c4d`, to tell keys apart.
    pub prefix: String,
    /// Only reads.
    pub read_only: bool,
    pub created_at_ms: i64,
    /// `None` until first used; kept to within a few minutes.
    pub last_used_at_ms: Option<i64>,
    /// `None`: it never expires. Expired keys stay listed until deleted.
    pub expires_at_ms: Option<i64>,
}

impl From<starbooru_core::ApiKey> for ApiKey {
    fn from(k: starbooru_core::ApiKey) -> Self {
        Self {
            id: k.id,
            name: k.name,
            prefix: k.prefix,
            read_only: k.read_only,
            created_at_ms: millis(k.created_at),
            last_used_at_ms: k.last_used_at.map(millis),
            expires_at_ms: k.expires_at.map(millis),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct NewApiKey {
    /// 1 to 64 characters.
    pub name: String,
    #[uniffi(default)]
    pub read_only: bool,
    /// Days until it stops working, 1 to `max_api_key_days()`; `None` for
    /// never.
    #[uniffi(default)]
    pub expires_days: Option<u32>,
}

impl From<NewApiKey> for starbooru_core::NewApiKey {
    fn from(k: NewApiKey) -> Self {
        Self {
            name: k.name,
            read_only: k.read_only,
            expires_days: k.expires_days,
        }
    }
}

/// A new key and its token. The server keeps only a hash: this is the one
/// time the token can be shown or copied.
#[derive(Clone, PartialEq, Eq, uniffi::Record)]
pub struct CreatedApiKey {
    pub key: ApiKey,
    pub token: String,
}

impl std::fmt::Debug for CreatedApiKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CreatedApiKey")
            .field("key", &self.key)
            .field("token", &"…")
            .finish()
    }
}

impl From<starbooru_core::CreatedApiKey> for CreatedApiKey {
    fn from(c: starbooru_core::CreatedApiKey) -> Self {
        Self {
            key: c.key.into(),
            token: c.token,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_session_keeps_its_times_in_milliseconds() {
        let session: starbooru_core::SessionInfo = serde_json::from_value(serde_json::json!({
            "id": 2, "client": "curl", "created_at": "2026-11-24T00:00:00Z",
            "last_used_at": "2026-11-24T00:00:01.25Z", "expires_at": "2026-11-25T00:00:00Z",
            "current": true
        }))
        .unwrap();
        let session = SessionInfo::from(session);
        assert_eq!(session.created_at_ms, 1_795_478_400_000);
        assert_eq!(session.last_used_at_ms, 1_795_478_401_250);
        assert_eq!(session.expires_at_ms, 1_795_564_800_000);
        assert!(session.current);
    }

    #[test]
    fn a_key_that_never_expires_and_was_never_used_has_no_times() {
        let created: starbooru_core::CreatedApiKey = serde_json::from_value(serde_json::json!({
            "key": {"id": 3, "name": "backup", "prefix": "sbk_1a2b3c4d", "read_only": true,
                    "created_at": "2026-11-24T00:00:00Z", "last_used_at": null,
                    "expires_at": null},
            "token": "sbk_1a2b3c4d5e6f"
        }))
        .unwrap();
        let created = CreatedApiKey::from(created);
        assert_eq!(created.key.last_used_at_ms, None);
        assert_eq!(created.key.expires_at_ms, None);
        assert_eq!(created.token, "sbk_1a2b3c4d5e6f");
        assert!(!format!("{created:?}").contains("5e6f"));
    }

    #[test]
    fn a_new_key_sends_its_lifetime() {
        let key = NewApiKey {
            name: "script".into(),
            read_only: true,
            expires_days: Some(30),
        };
        let json = serde_json::to_value(starbooru_core::NewApiKey::from(key)).unwrap();
        assert_eq!(
            json,
            serde_json::json!({"name": "script", "read_only": true, "expires_days": 30})
        );
        assert_eq!(max_api_key_days(), 3650);
    }

    #[test]
    fn identities_keep_their_label() {
        let methods: starbooru_core::SignInMethods = serde_json::from_value(serde_json::json!({
            "password": false,
            "identities": [{"id": 1, "label": "ann@example.com",
                            "created_at": "2026-11-24T00:00:00Z", "last_used_at": null}]
        }))
        .unwrap();
        let methods = SignInMethods::from(methods);
        assert!(!methods.password);
        assert_eq!(
            methods.identities[0].label.as_deref(),
            Some("ann@example.com")
        );
        assert_eq!(methods.identities[0].last_used_at_ms, None);
    }
}
