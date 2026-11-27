//! Background imports: addresses the server downloads by itself, so they go
//! on with the app closed. `Api::imports` is how they're followed; the
//! rules for how often to ask are in `jobs`.

use super::{Rating, millis};

/// One address to import, as `Api::queue_imports` takes it.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ImportItem {
    /// A picture's or video's address, or a post's page on a booru.
    pub url: String,
    #[uniffi(default)]
    pub tags: Vec<String>,
    /// `None` is safe, or the booru's own rating for a post's page.
    #[uniffi(default)]
    pub rating: Option<Rating>,
    /// Where it comes from; `None` is `url`. For a post on another booru,
    /// its `page_url`.
    #[uniffi(default)]
    pub source: Option<String>,
    /// Whether the AI tags the new post; `None` leaves it to the server.
    #[uniffi(default)]
    pub autotag: Option<bool>,
}

impl From<ImportItem> for starbooru_core::ImportRequest {
    fn from(i: ImportItem) -> Self {
        Self {
            url: i.url,
            tags: i.tags,
            rating: i.rating.map(Into::into).unwrap_or_default(),
            source: i.source,
            autotag: i.autotag,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum JobState {
    Queued,
    Running,
    Done,
    /// The file was here already; `error` says which post.
    Duplicate,
    Failed,
}

impl JobState {
    /// Still waiting or running.
    pub fn is_active(self) -> bool {
        matches!(self, JobState::Queued | JobState::Running)
    }
}

impl From<starbooru_core::JobState> for JobState {
    fn from(s: starbooru_core::JobState) -> Self {
        match s {
            starbooru_core::JobState::Queued => JobState::Queued,
            starbooru_core::JobState::Running => JobState::Running,
            starbooru_core::JobState::Done => JobState::Done,
            starbooru_core::JobState::Duplicate => JobState::Duplicate,
            starbooru_core::JobState::Failed => JobState::Failed,
        }
    }
}

/// How far a download has got.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct ImportProgress {
    /// Bytes so far.
    pub done: u64,
    /// The file's size, when the site said.
    pub total: Option<u64>,
    /// Bytes a second lately; 0 until measured.
    pub speed: u64,
    /// 0 to 100, with a total.
    pub percent: Option<u8>,
}

impl From<starbooru_core::Transfer> for ImportProgress {
    fn from(t: starbooru_core::Transfer) -> Self {
        Self {
            percent: t.percent(),
            done: t.done,
            total: t.total,
            speed: t.speed,
        }
    }
}

/// One background import.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct ImportJob {
    pub id: i64,
    pub url: String,
    pub source: Option<String>,
    pub tags: Vec<String>,
    pub rating: Rating,
    pub state: JobState,
    /// Queued or running: it can still change.
    pub active: bool,
    /// The new post when done; the post it already is, when the server says.
    pub post_id: Option<i64>,
    /// Why it failed, or which post a duplicate is, in the server's words. A
    /// queued import the site asked to wait has a note here too.
    pub error: Option<String>,
    pub created_at_ms: i64,
    pub finished_at_ms: Option<i64>,
    /// While it downloads.
    pub progress: Option<ImportProgress>,
}

impl From<starbooru_core::ImportJob> for ImportJob {
    fn from(j: starbooru_core::ImportJob) -> Self {
        let state = JobState::from(j.state);
        Self {
            id: j.id,
            url: j.url,
            source: j.source,
            tags: j.tags,
            rating: j.rating.into(),
            state,
            active: state.is_active(),
            post_id: j.post_id,
            error: j.error,
            created_at_ms: millis(j.created_at),
            finished_at_ms: j.finished_at.map(millis),
            progress: j.progress.map(Into::into),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_running_job_shows_how_far_it_got() {
        let job: starbooru_core::ImportJob = serde_json::from_value(serde_json::json!({
            "id": 4, "url": "https://a.example/1.png", "source": null, "tags": ["sky"],
            "rating": "general", "state": "running", "post_id": null, "error": null,
            "created_at": "2026-11-24T00:00:00Z", "finished_at": null,
            "progress": {"done": 512, "total": 2048, "speed": 100}
        }))
        .unwrap();
        let job = ImportJob::from(job);
        assert_eq!((job.state, job.active), (JobState::Running, true));
        assert_eq!(job.rating, Rating::Safe);
        let progress = job.progress.unwrap();
        assert_eq!(progress.percent, Some(25));
        assert_eq!(progress.speed, 100);
        assert_eq!(job.created_at_ms, 1_795_478_400_000);
    }

    #[test]
    fn an_item_leaves_the_source_and_the_ai_to_the_server() {
        let item = ImportItem {
            url: "https://a.example/1.png".into(),
            tags: vec!["sky".into()],
            rating: None,
            source: None,
            autotag: None,
        };
        let json = serde_json::to_value(starbooru_core::ImportRequest::from(item)).unwrap();
        assert_eq!(
            json,
            serde_json::json!({"url": "https://a.example/1.png", "tags": ["sky"], "rating": "safe"})
        );
    }
}
