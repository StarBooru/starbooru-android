//! Profiles and the votes on a post's tags. Others see a profile, a post's
//! uploader and the votes on a public server only.

use starbooru_client::Client;

use super::{Role, millis};

/// Who wrote or uploaded something, as others see it.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct UserCard {
    /// The username, the handle.
    pub name: String,
    pub display_name: Option<String>,
    /// Absolute and signed, 256 px; `None` without a picture, when the
    /// screens draw an initial.
    pub avatar_url: Option<String>,
    /// The same at 64 px.
    pub avatar_small_url: Option<String>,
}

impl UserCard {
    pub(crate) fn from_core(c: starbooru_core::UserCard, client: &Client) -> Self {
        Self {
            name: c.name,
            display_name: c.display_name,
            avatar_url: c.avatar_url.map(|u| client.absolute(&u)),
            avatar_small_url: c.avatar_small_url.map(|u| client.absolute(&u)),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Profile {
    pub name: String,
    pub display_name: Option<String>,
    pub about: Option<String>,
    /// Absolute and signed, 256 px.
    pub avatar_url: Option<String>,
    /// Absolute and signed, 64 px.
    pub avatar_small_url: Option<String>,
    pub role: Role,
    pub joined_at_ms: i64,
    /// Posts uploaded, outside the recycle bin.
    pub posts: u64,
    /// Forum posts written, deleted ones left out.
    pub forum_posts: u64,
}

impl Profile {
    pub(crate) fn from_core(p: starbooru_core::Profile, client: &Client) -> Self {
        Self {
            name: p.name,
            display_name: p.display_name,
            about: p.about,
            avatar_url: p.avatar_url.map(|u| client.absolute(&u)),
            avatar_small_url: p.avatar_small_url.map(|u| client.absolute(&u)),
            role: p.role.into(),
            joined_at_ms: millis(p.joined),
            posts: p.posts,
            forum_posts: p.forum_posts,
        }
    }
}

/// A change to the account's profile. `None` leaves a field as it is; an
/// empty or blank one clears it.
#[derive(Debug, Clone, Default, PartialEq, Eq, uniffi::Record)]
pub struct ProfileUpdate {
    /// One line, at most 40 characters.
    #[uniffi(default)]
    pub display_name: Option<String>,
    /// Plain text, its lines kept, at most 500 characters. Setting it needs
    /// a public server (`profiles_off`); clearing it works anywhere.
    #[uniffi(default)]
    pub about: Option<String>,
}

/// `None` stays, blank clears (JSON `null`), anything else is sent.
fn change(field: Option<String>) -> Option<Option<String>> {
    field.map(|text| Some(text).filter(|t| !t.trim().is_empty()))
}

impl From<ProfileUpdate> for starbooru_core::ProfileUpdate {
    fn from(u: ProfileUpdate) -> Self {
        Self {
            display_name: change(u.display_name),
            about: change(u.about),
        }
    }
}

/// The square to cut a profile picture from, in pixels of the picture as
/// stored, at least 16 on a side.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct AvatarCrop {
    pub x: u32,
    pub y: u32,
    pub size: u32,
}

impl From<AvatarCrop> for starbooru_core::AvatarCrop {
    fn from(c: AvatarCrop) -> Self {
        Self {
            x: c.x,
            y: c.y,
            size: c.size,
        }
    }
}

/// The votes on one of a post's tags.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct PostTagVote {
    pub tag: String,
    /// Votes for minus votes against.
    pub score: i32,
    /// The reader's own: -1, 0 or 1.
    pub mine: i16,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_profile_gets_absolute_pictures_and_its_time_in_milliseconds() {
        let client = Client::new("https://booru.example/sub").unwrap();
        let profile: starbooru_core::Profile = serde_json::from_value(serde_json::json!({
            "name": "ann", "display_name": "Ann", "about": "I tag skies.",
            "avatar_url": "/media/avatar/9f.jpg?sig=1", "avatar_small_url": "/media/avatar64/9f.jpg?sig=2",
            "role": "user", "joined": "2026-11-24T00:00:00Z", "posts": 12, "forum_posts": 3
        }))
        .unwrap();
        let profile = Profile::from_core(profile, &client);
        assert_eq!(
            profile.avatar_url.as_deref(),
            Some("https://booru.example/sub/media/avatar/9f.jpg?sig=1")
        );
        assert_eq!(
            profile.avatar_small_url.as_deref(),
            Some("https://booru.example/sub/media/avatar64/9f.jpg?sig=2")
        );
        assert_eq!(profile.role, Role::User);
        assert_eq!(profile.joined_at_ms, 1_795_478_400_000);
        let card: starbooru_core::UserCard = serde_json::from_value(serde_json::json!({
            "name": "bob", "display_name": null, "avatar_url": null,
            "avatar_small_url": "/media/avatar64/c4.jpg"
        }))
        .unwrap();
        let card = UserCard::from_core(card, &client);
        assert_eq!(card.avatar_url, None);
        assert_eq!(
            card.avatar_small_url.as_deref(),
            Some("https://booru.example/sub/media/avatar64/c4.jpg")
        );
    }

    #[test]
    fn an_update_leaves_out_what_stays_and_clears_with_null() {
        let send = |u: ProfileUpdate| {
            serde_json::to_value(starbooru_core::ProfileUpdate::from(u)).unwrap()
        };
        assert_eq!(send(ProfileUpdate::default()), serde_json::json!({}));
        assert_eq!(
            send(ProfileUpdate {
                display_name: Some("Ann".into()),
                about: Some("  \n ".into()),
            }),
            serde_json::json!({"display_name": "Ann", "about": null})
        );
        assert_eq!(
            send(ProfileUpdate {
                display_name: Some(String::new()),
                about: None,
            }),
            serde_json::json!({"display_name": null})
        );
    }
}
