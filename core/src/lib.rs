//! The Rust core of StarBooru for Android.
//!
//! Kotlin reaches it through UniFFI. It holds what is shared with the other
//! apps or testable off the device: the API (through `starbooru-client`), the
//! server address, sign-in through a provider, and the rules the screens
//! follow. It keeps no Android types; paths and the like come in as
//! arguments.

uniffi::setup_scaffolding!();

pub mod address;
pub mod api;
pub mod blacklist;
pub mod error;
pub mod gallery;
pub mod jobs;
pub mod layout;
pub mod model;
pub mod oidc;
pub mod saved;
pub mod search;
pub mod tags;
pub mod viewer;

pub use api::Api;
pub use error::CoreError;

/// The core's own version, for the about screen and bug reports.
#[uniffi::export]
pub fn core_version() -> String {
    env!("CARGO_PKG_VERSION").to_owned()
}
