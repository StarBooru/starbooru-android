//! Updates of the server: the version running, the newest release, how this
//! server updates and the update under way. Admins only.

use super::millis;

/// A published release.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Release {
    /// Without the tag's `v`: `0.8.0`.
    pub version: String,
    pub name: String,
    pub published_at_ms: Option<i64>,
    /// Markdown.
    pub notes: String,
    /// The release's page.
    pub url: String,
}

impl From<starbooru_core::Release> for Release {
    fn from(r: starbooru_core::Release) -> Self {
        Self {
            version: r.version,
            name: r.name,
            published_at_ms: r.published_at.map(millis),
            notes: r.notes,
            url: r.url,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct UpdateSettings {
    /// Ask for new releases at start and every 6 hours; off asks nothing.
    pub check: bool,
    /// Install new releases by themselves, with a backup first.
    pub auto: bool,
    /// The hour they start at, 0 to 23, on the server's clock.
    pub auto_hour: u8,
}

impl From<starbooru_core::UpdateSettings> for UpdateSettings {
    fn from(s: starbooru_core::UpdateSettings) -> Self {
        Self {
            check: s.check,
            auto: s.auto,
            auto_hour: s.auto_hour,
        }
    }
}

impl From<UpdateSettings> for starbooru_core::UpdateSettings {
    fn from(s: UpdateSettings) -> Self {
        Self {
            check: s.check,
            auto: s.auto,
            auto_hour: s.auto_hour,
        }
    }
}

/// How the server runs, which says how it updates.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum UpdateKind {
    Docker,
    Swarm,
    /// The server package under a service manager.
    Package,
    /// Anything else, such as a development build.
    Manual,
}

impl From<starbooru_core::UpdateKind> for UpdateKind {
    fn from(k: starbooru_core::UpdateKind) -> Self {
        use starbooru_core::UpdateKind as K;
        match k {
            K::Docker => UpdateKind::Docker,
            K::Swarm => UpdateKind::Swarm,
            K::Package => UpdateKind::Package,
            K::Manual => UpdateKind::Manual,
        }
    }
}

/// Why the server can't install a release by itself.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum UpdateBlock {
    /// A container without the Docker socket: `docker compose pull && docker
    /// compose up -d` by hand.
    NoDockerSocket,
    DockerUnreachable,
    /// An image tag that is neither `latest` nor a version.
    UnknownImage,
    /// `docker run --rm`.
    RemovedOnStop,
    DevelopmentBuild,
    /// No service manager would start it again.
    NotSupervised,
    ReadOnlyFolder,
    Unsupported,
}

impl From<starbooru_core::UpdateBlock> for UpdateBlock {
    fn from(b: starbooru_core::UpdateBlock) -> Self {
        use starbooru_core::UpdateBlock as B;
        match b {
            B::NoDockerSocket => UpdateBlock::NoDockerSocket,
            B::DockerUnreachable => UpdateBlock::DockerUnreachable,
            B::UnknownImage => UpdateBlock::UnknownImage,
            B::RemovedOnStop => UpdateBlock::RemovedOnStop,
            B::DevelopmentBuild => UpdateBlock::DevelopmentBuild,
            B::NotSupervised => UpdateBlock::NotSupervised,
            B::ReadOnlyFolder => UpdateBlock::ReadOnlyFolder,
            B::Unsupported => UpdateBlock::Unsupported,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct UpdateMethod {
    pub kind: UpdateKind,
    /// The server can install a release by itself.
    pub can_update: bool,
    /// Why it can't, when it can't.
    pub reason: Option<UpdateBlock>,
    /// The same in English, for a reason the app doesn't know.
    pub reason_text: Option<String>,
    /// The image the container runs.
    pub image: Option<String>,
    /// The image names a version, so a redeploy goes back to it until the
    /// compose file changes.
    pub pinned: bool,
}

impl From<starbooru_core::UpdateMethod> for UpdateMethod {
    fn from(m: starbooru_core::UpdateMethod) -> Self {
        Self {
            kind: m.kind.into(),
            can_update: m.can_update,
            reason: m.reason.map(Into::into),
            reason_text: m.message,
            image: m.image,
            pinned: m.pinned,
        }
    }
}

/// Where an update has got to.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum UpdateState {
    Checking,
    Downloading,
    BackingUp,
    /// The server goes away for a moment.
    Restarting,
    Done,
    /// The version from before still runs; `UpdateRun::error` says why.
    Failed,
}

impl From<starbooru_core::UpdateState> for UpdateState {
    fn from(s: starbooru_core::UpdateState) -> Self {
        use starbooru_core::UpdateState as S;
        match s {
            S::Checking => UpdateState::Checking,
            S::Downloading => UpdateState::Downloading,
            S::BackingUp => UpdateState::BackingUp,
            S::Restarting => UpdateState::Restarting,
            S::Done => UpdateState::Done,
            S::Failed => UpdateState::Failed,
        }
    }
}

/// An update under way, or the last one's result. It outlives the restart.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct UpdateRun {
    pub state: UpdateState,
    /// Neither done nor failed: ask `update_status` again.
    pub running: bool,
    pub from: String,
    pub to: String,
    /// Started by the automatic updates.
    pub automatic: bool,
    pub started_at_ms: i64,
    pub finished_at_ms: Option<i64>,
    pub error: Option<String>,
    /// The backup made before it.
    pub backup: Option<String>,
}

impl From<starbooru_core::UpdateRun> for UpdateRun {
    fn from(r: starbooru_core::UpdateRun) -> Self {
        Self {
            running: r.state.is_running(),
            state: r.state.into(),
            from: r.from,
            to: r.to,
            automatic: r.automatic,
            started_at_ms: millis(r.started_at),
            finished_at_ms: r.finished_at.map(millis),
            error: r.error,
            backup: r.backup,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct UpdateStatus {
    /// The version running.
    pub current: String,
    /// From the last check that worked; `None` before the first.
    pub latest: Option<Release>,
    /// `latest` is newer than `current`.
    pub available: bool,
    pub checked_at_ms: Option<i64>,
    /// Why the last check failed; `latest` is then the one found before.
    pub check_error: Option<String>,
    pub settings: UpdateSettings,
    pub method: UpdateMethod,
    /// The server's clock, and its offset from UTC, which `auto_hour` is in.
    pub server_time_ms: i64,
    pub server_utc_offset_minutes: i32,
    pub run: Option<UpdateRun>,
}

impl From<starbooru_core::UpdateStatus> for UpdateStatus {
    fn from(s: starbooru_core::UpdateStatus) -> Self {
        Self {
            current: s.current,
            latest: s.latest.map(Into::into),
            available: s.available,
            checked_at_ms: s.checked_at.map(millis),
            check_error: s.check_error,
            settings: s.settings.into(),
            method: s.method.into(),
            server_time_ms: s.server_time.timestamp_millis(),
            server_utc_offset_minutes: s.server_time.offset().local_minus_utc() / 60,
            run: s.run.map(Into::into),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_status_keeps_the_servers_clock_and_says_whether_it_runs() {
        let status: starbooru_core::UpdateStatus = serde_json::from_value(serde_json::json!({
            "current": "0.2.0",
            "latest": {"version": "0.3.0", "name": "StarBooru server 0.3.0",
                       "published_at": "2026-12-01T09:00:00Z", "notes": "## New",
                       "url": "https://example.org/v0.3.0"},
            "available": true, "checked_at": "2026-12-01T10:00:00Z", "check_error": null,
            "settings": {"check": true, "auto": false, "auto_hour": 4},
            "method": {"kind": "docker", "can_update": false, "reason": "no_docker_socket",
                       "message": "no socket", "pinned": false},
            "server_time": "2026-12-01T11:00:00+01:00",
            "run": {"state": "backing_up", "from": "0.2.0", "to": "0.3.0", "automatic": false,
                    "started_at": "2026-12-01T09:59:00Z"}
        }))
        .unwrap();
        let status = UpdateStatus::from(status);
        assert_eq!(status.server_utc_offset_minutes, 60);
        assert_eq!(
            status.server_time_ms,
            status.checked_at_ms.unwrap(),
            "11:00+01:00 is 10:00 UTC"
        );
        assert_eq!(status.method.reason, Some(UpdateBlock::NoDockerSocket));
        assert_eq!(status.method.reason_text.as_deref(), Some("no socket"));
        let run = status.run.unwrap();
        assert_eq!((run.state, run.running), (UpdateState::BackingUp, true));
        assert_eq!(status.latest.map(|l| l.version).as_deref(), Some("0.3.0"));
    }
}
