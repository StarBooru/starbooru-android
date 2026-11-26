//! Profiles. Others see them on a public server only; a private one keeps
//! display names, which nobody else sees, and answers the rest with
//! `profiles_off`.

use std::path::PathBuf;

use super::Api;
use crate::error::{CoreError, Result};
use crate::model::{AvatarCrop, Profile, ProfileUpdate};

/// The largest picture `set_avatar` takes.
const MAX_AVATAR_BYTES: u64 = starbooru_core::profile::MAX_AVATAR_BYTES as u64;

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// The account's own profile, in either mode.
    pub async fn my_profile(&self) -> Result<Profile> {
        let profile = self.client.my_profile().await?;
        Ok(Profile::from_core(profile, &self.client))
    }

    /// Anyone's profile by username, in any case. Guests may ask too. An
    /// unknown name is `not_found`.
    pub async fn profile(&self, name: String) -> Result<Profile> {
        let profile = self.client.profile(&name).await?;
        Ok(Profile::from_core(profile, &self.client))
    }

    /// Too long, or a control character, is `bad_request`.
    pub async fn update_profile(&self, update: ProfileUpdate) -> Result<Profile> {
        let profile = self.client.update_profile(&update.into()).await?;
        Ok(Profile::from_core(profile, &self.client))
    }

    /// Makes the picture at `path` (JPEG, PNG, WebP or GIF, at most 5 MiB)
    /// the profile picture, cut to `crop` or to the largest square in the
    /// middle. A bigger file is `TooLarge` before anything is sent; a square
    /// outside the picture or one under 16 px is `bad_request`.
    pub async fn set_avatar(&self, path: String, crop: Option<AvatarCrop>) -> Result<Profile> {
        let path = PathBuf::from(path);
        if tokio::fs::metadata(&path).await?.len() > MAX_AVATAR_BYTES {
            return Err(CoreError::TooLarge {
                limit: MAX_AVATAR_BYTES,
            });
        }
        let bytes = tokio::fs::read(&path).await?;
        let profile = self.client.set_avatar(bytes, crop.map(Into::into)).await?;
        Ok(Profile::from_core(profile, &self.client))
    }

    /// In either mode.
    pub async fn remove_avatar(&self) -> Result<Profile> {
        let profile = self.client.remove_avatar().await?;
        Ok(Profile::from_core(profile, &self.client))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scratch(name: &str) -> PathBuf {
        std::env::temp_dir().join(format!(
            "starbooru-core-{}-{name}-{}",
            std::process::id(),
            chrono::Utc::now().timestamp_nanos_opt().unwrap_or_default()
        ))
    }

    #[tokio::test]
    async fn a_picture_too_big_is_refused_before_it_is_sent() {
        let path = scratch("avatar");
        let file = std::fs::File::create(&path).unwrap();
        file.set_len(MAX_AVATAR_BYTES + 1).unwrap();
        // Nothing listens there: sending would be `Unreachable`.
        let api = Api::new("http://127.0.0.1:9".into(), Some("t".into())).unwrap();
        let err = api
            .set_avatar(path.to_string_lossy().into_owned(), None)
            .await
            .unwrap_err();
        std::fs::remove_file(&path).unwrap();
        assert!(
            matches!(err, CoreError::TooLarge { limit } if limit == MAX_AVATAR_BYTES),
            "{err}"
        );
    }

    #[tokio::test]
    async fn a_missing_picture_is_an_io_error() {
        let api = Api::new("http://127.0.0.1:9".into(), Some("t".into())).unwrap();
        let err = api
            .set_avatar(scratch("missing").to_string_lossy().into_owned(), None)
            .await
            .unwrap_err();
        assert!(matches!(err, CoreError::Io { .. }), "{err}");
    }
}
