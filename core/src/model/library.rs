//! Library imports: a whole library moved over from another self-hosted
//! booru. The server walks the site by itself, so an import goes on with
//! the app closed; `Api::library_imports` is how it's followed.

use super::{Rating, millis};

/// The most library imports one account may have waiting or running.
#[uniffi::export]
pub fn max_active_library_imports() -> u32 {
    u32::try_from(starbooru_core::MAX_ACTIVE_LIBRARY_IMPORTS).unwrap_or(u32::MAX)
}

/// The systems a library can come from.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum LibrarySource {
    Szurubooru,
    Shimmie2,
    Danbooru,
    Stash,
    Blombooru,
    Monbooru,
    Lanbooru,
}

impl From<starbooru_core::LibrarySource> for LibrarySource {
    fn from(s: starbooru_core::LibrarySource) -> Self {
        use starbooru_core::LibrarySource as S;
        match s {
            S::Szurubooru => LibrarySource::Szurubooru,
            S::Shimmie2 => LibrarySource::Shimmie2,
            S::Danbooru => LibrarySource::Danbooru,
            S::Stash => LibrarySource::Stash,
            S::Blombooru => LibrarySource::Blombooru,
            S::Monbooru => LibrarySource::Monbooru,
            S::Lanbooru => LibrarySource::Lanbooru,
        }
    }
}

impl From<LibrarySource> for starbooru_core::LibrarySource {
    fn from(s: LibrarySource) -> Self {
        use starbooru_core::LibrarySource as S;
        match s {
            LibrarySource::Szurubooru => S::Szurubooru,
            LibrarySource::Shimmie2 => S::Shimmie2,
            LibrarySource::Danbooru => S::Danbooru,
            LibrarySource::Stash => S::Stash,
            LibrarySource::Blombooru => S::Blombooru,
            LibrarySource::Monbooru => S::Monbooru,
            LibrarySource::Lanbooru => S::Lanbooru,
        }
    }
}

/// What the form for a system asks: its name, and which account fields it
/// takes.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct LibrarySourceForm {
    /// As people know it ("Shimmie2", "LANBooru").
    pub name: String,
    /// A user name next to the key (szurubooru, Danbooru).
    pub login: bool,
    /// A key at all (not LANBooru, whose reads are open).
    pub secret: bool,
}

#[uniffi::export]
pub fn library_source_form(source: LibrarySource) -> LibrarySourceForm {
    let source = starbooru_core::LibrarySource::from(source);
    LibrarySourceForm {
        name: source.name().to_owned(),
        login: source.takes_login(),
        secret: source.takes_secret(),
    }
}

/// The site to read.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct LibrarySite {
    pub source: LibrarySource,
    /// Like `http://192.168.1.4:8080`.
    pub base: String,
    /// Sent only to that site, and kept only while the import goes.
    #[uniffi(default)]
    pub login: Option<String>,
    #[uniffi(default)]
    pub secret: Option<String>,
    /// The rating of posts the site doesn't rate; `None` is questionable.
    #[uniffi(default)]
    pub unrated: Option<Rating>,
}

impl From<LibrarySite> for starbooru_core::LibrarySite {
    fn from(s: LibrarySite) -> Self {
        Self {
            source: s.source.into(),
            base: s.base,
            login: s.login,
            secret: s.secret,
            unrated: s
                .unrated
                .map_or(starbooru_core::Rating::Questionable, Into::into),
        }
    }
}

/// What the site's first page said.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct LibraryCheck {
    /// Posts on the site, when it says.
    pub total: Option<u64>,
    /// The import would bring aliases and implications (admins only).
    pub rules: bool,
}

impl From<starbooru_core::LibraryCheck> for LibraryCheck {
    fn from(c: starbooru_core::LibraryCheck) -> Self {
        Self {
            total: c.total,
            rules: c.rules,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum LibraryState {
    Queued,
    Running,
    Done,
    Stopped,
    Failed,
}

impl From<starbooru_core::LibraryState> for LibraryState {
    fn from(s: starbooru_core::LibraryState) -> Self {
        use starbooru_core::LibraryState as S;
        match s {
            S::Queued => LibraryState::Queued,
            S::Running => LibraryState::Running,
            S::Done => LibraryState::Done,
            S::Stopped => LibraryState::Stopped,
            S::Failed => LibraryState::Failed,
        }
    }
}

/// What an import is reading, in this order.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum LibraryPhase {
    /// Aliases, implications and the tag tree.
    Rules,
    Posts,
    /// Pools, albums and favourites.
    Groups,
}

impl From<starbooru_core::LibraryPhase> for LibraryPhase {
    fn from(p: starbooru_core::LibraryPhase) -> Self {
        use starbooru_core::LibraryPhase as P;
        match p {
            P::Rules => LibraryPhase::Rules,
            P::Posts => LibraryPhase::Posts,
            P::Groups => LibraryPhase::Groups,
        }
    }
}

/// One library import. Its account never comes back.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct LibraryImport {
    pub id: i64,
    pub source: LibrarySource,
    pub base: String,
    pub state: LibraryState,
    /// Queued or running: worth asking about again soon.
    pub active: bool,
    pub phase: LibraryPhase,
    /// Posts on the site, when it says.
    pub total: Option<u64>,
    pub imported: u64,
    /// Files here already, whose tags went on the post here.
    pub merged: u64,
    pub failed: u64,
    /// Aliases, implications and parents added.
    pub rules: u64,
    /// Posts dealt with so far, failed ones included.
    pub handled: u64,
    /// How far the posts are, 0 to 1, with a total.
    pub fraction: Option<f64>,
    /// What was left out, in the server's English.
    pub note: Option<String>,
    /// Why it stopped, when it failed, in the server's English.
    pub error: Option<String>,
    pub created_at_ms: i64,
    pub finished_at_ms: Option<i64>,
}

impl From<starbooru_core::LibraryImport> for LibraryImport {
    fn from(i: starbooru_core::LibraryImport) -> Self {
        Self {
            handled: i.handled(),
            fraction: i.fraction(),
            active: i.state.is_active(),
            id: i.id,
            source: i.source.into(),
            base: i.base,
            state: i.state.into(),
            phase: i.phase.into(),
            total: i.total,
            imported: i.imported,
            merged: i.merged,
            failed: i.failed,
            rules: i.rules,
            note: i.note,
            error: i.error,
            created_at_ms: millis(i.created_at),
            finished_at_ms: i.finished_at.map(millis),
        }
    }
}

/// A post an import couldn't bring, and why.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct LibraryFailure {
    /// Its id on the other site.
    pub remote_id: String,
    pub error: String,
}

impl From<starbooru_core::LibraryFailure> for LibraryFailure {
    fn from(f: starbooru_core::LibraryFailure) -> Self {
        Self {
            remote_id: f.remote_id,
            error: f.error,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn an_import_says_how_far_it_got() {
        let import: starbooru_core::LibraryImport = serde_json::from_value(serde_json::json!({
            "id": 3, "source": "shimmie2", "base": "http://192.168.1.4", "state": "running",
            "phase": "posts", "total": 200, "imported": 40, "merged": 8, "failed": 2,
            "rules": 5, "created_at": "2026-11-24T00:00:00Z"
        }))
        .unwrap();
        let import = LibraryImport::from(import);
        assert_eq!(import.source, LibrarySource::Shimmie2);
        assert!(import.active);
        assert_eq!(import.handled, 50);
        assert_eq!(import.fraction, Some(0.25));
        assert_eq!(import.finished_at_ms, None);
    }

    #[test]
    fn a_site_the_form_leaves_unrated_is_questionable() {
        let site = LibrarySite {
            source: LibrarySource::Lanbooru,
            base: "http://192.168.1.4:8080".into(),
            login: None,
            secret: None,
            unrated: None,
        };
        let json = serde_json::to_value(starbooru_core::LibrarySite::from(site)).unwrap();
        assert_eq!(
            json,
            serde_json::json!({"source": "lanbooru", "base": "http://192.168.1.4:8080",
                "unrated": "questionable"})
        );
        let form = library_source_form(LibrarySource::Lanbooru);
        assert_eq!(
            (form.name.as_str(), form.login, form.secret),
            ("LANBooru", false, false)
        );
        assert!(library_source_form(LibrarySource::Danbooru).login);
        assert_eq!(max_active_library_imports(), 5);
    }
}
