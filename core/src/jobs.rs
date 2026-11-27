//! The server's background imports as the app follows them, after
//! starbooru-web's `logic::jobs`: how often to ask about them, the list
//! after an answer, when the ones started here are all finished, and each
//! job by the page it came from.

use std::collections::{HashMap, HashSet};

use crate::model::{ImportJob, JobState};

/// Seconds between asking while a download is going (its progress shows),
/// while some wait, and otherwise.
pub const POLL_RUNNING: u32 = 1;
pub const POLL_BUSY: u32 = 2;
pub const POLL_IDLE: u32 = 60;

/// How the watched imports ended.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, uniffi::Record)]
pub struct ImportsEnded {
    pub done: u32,
    pub duplicate: u32,
    pub failed: u32,
}

/// What `imports_finished` saw.
#[derive(Clone, Debug, Default, PartialEq, Eq, uniffi::Record)]
pub struct ImportsFinished {
    /// The imports to keep watching, for the next call.
    pub watched: Vec<i64>,
    /// How they ended, once none is left going and one of them ended
    /// somehow; `watched` is empty then.
    pub ended: Option<ImportsEnded>,
}

/// Seconds until asking for the list again, given what is on it.
#[uniffi::export]
pub fn imports_poll_every(jobs: &[ImportJob]) -> u32 {
    if jobs.iter().any(|j| j.state == JobState::Running) {
        POLL_RUNNING
    } else if jobs.iter().any(|j| j.state.is_active()) {
        POLL_BUSY
    } else {
        POLL_IDLE
    }
}

/// `old` with the `new` jobs added or put in their place, newest first.
#[uniffi::export]
pub fn imports_merged(old: &[ImportJob], new: Vec<ImportJob>) -> Vec<ImportJob> {
    let mut by_id: HashMap<i64, ImportJob> = old.iter().map(|j| (j.id, j.clone())).collect();
    for job in new {
        by_id.insert(job.id, job);
    }
    let mut list: Vec<ImportJob> = by_id.into_values().collect();
    list.sort_by_key(|j| std::cmp::Reverse(j.id));
    list
}

/// Adds `now`'s waiting and running jobs to `watched`. Once none of them is
/// left going: how the watched ones ended, and `watched` starts over.
pub fn finished(watched: &mut HashSet<i64>, now: &[ImportJob]) -> Option<ImportsEnded> {
    watched.extend(now.iter().filter(|j| j.state.is_active()).map(|j| j.id));
    if watched.is_empty() || now.iter().any(|j| j.state.is_active()) {
        return None;
    }
    let states: HashMap<i64, JobState> = now.iter().map(|j| (j.id, j.state)).collect();
    let mut ended = ImportsEnded::default();
    for id in watched.drain() {
        match states.get(&id) {
            Some(JobState::Done) => ended.done = ended.done.saturating_add(1),
            Some(JobState::Duplicate) => ended.duplicate = ended.duplicate.saturating_add(1),
            Some(JobState::Failed) => ended.failed = ended.failed.saturating_add(1),
            // Cancelled, or cleared elsewhere.
            _ => {}
        }
    }
    (ended != ImportsEnded::default()).then_some(ended)
}

/// [`finished`] for Kotlin, which keeps `watched` between answers: start
/// with the ids `Api::queue_imports` answered, or none.
#[uniffi::export]
pub fn imports_finished(watched: Vec<i64>, now: &[ImportJob]) -> ImportsFinished {
    let mut set: HashSet<i64> = watched.into_iter().collect();
    let ended = finished(&mut set, now);
    let mut watched: Vec<i64> = set.into_iter().collect();
    watched.sort_unstable();
    ImportsFinished { watched, ended }
}

/// Each job by the page it came from (its source, else its address), the
/// latest for a page imported more than once. A post from another booru is
/// known by its `page_url`.
#[uniffi::export]
pub fn imports_by_source(jobs: &[ImportJob]) -> HashMap<String, ImportJob> {
    let mut map: HashMap<String, ImportJob> = HashMap::new();
    for job in jobs {
        let page = job.source.as_ref().unwrap_or(&job.url);
        if map.get(page).is_none_or(|j| j.id < job.id) {
            map.insert(page.clone(), job.clone());
        }
    }
    map
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::Rating;

    fn job(id: i64, state: JobState) -> ImportJob {
        ImportJob {
            id,
            url: format!("https://example.org/{id}.png"),
            source: None,
            tags: Vec::new(),
            rating: Rating::Safe,
            state,
            active: state.is_active(),
            post_id: None,
            error: None,
            created_at_ms: 0,
            finished_at_ms: None,
            progress: None,
        }
    }

    #[test]
    fn asks_more_often_while_a_download_shows_its_progress() {
        assert_eq!(imports_poll_every(&[]), POLL_IDLE);
        assert_eq!(imports_poll_every(&[job(1, JobState::Done)]), POLL_IDLE);
        assert_eq!(imports_poll_every(&[job(1, JobState::Queued)]), POLL_BUSY);
        let jobs = [job(1, JobState::Queued), job(2, JobState::Running)];
        assert_eq!(imports_poll_every(&jobs), POLL_RUNNING);
    }

    #[test]
    fn merge_replaces_by_id_newest_first() {
        let old = [job(1, JobState::Running), job(2, JobState::Queued)];
        let list = imports_merged(&old, vec![job(2, JobState::Done), job(3, JobState::Queued)]);
        let ids: Vec<i64> = list.iter().map(|j| j.id).collect();
        assert_eq!(ids, [3, 2, 1]);
        assert_eq!(list[1].state, JobState::Done);
    }

    #[test]
    fn counts_only_when_everything_finished() {
        let mut watched = HashSet::from([3]);
        let first = [
            job(1, JobState::Running),
            job(2, JobState::Queued),
            job(3, JobState::Failed),
            job(4, JobState::Done),
        ];
        assert_eq!(finished(&mut watched, &first), None);
        let still = [job(1, JobState::Done), job(2, JobState::Running)];
        assert_eq!(finished(&mut watched, &still), None);
        let now = [
            job(1, JobState::Done),
            job(2, JobState::Duplicate),
            job(3, JobState::Failed),
            job(4, JobState::Done),
        ];
        assert_eq!(
            finished(&mut watched, &now),
            Some(ImportsEnded {
                done: 1,
                duplicate: 1,
                failed: 1
            })
        );
        assert!(watched.is_empty());
        // Nothing was going since: nothing to say.
        assert_eq!(finished(&mut watched, &now), None);
        // A watched job cancelled or cleared elsewhere isn't counted.
        let mut watched = HashSet::from([9]);
        assert_eq!(finished(&mut watched, &[]), None);
    }

    #[test]
    fn kotlin_carries_the_watched_ids_between_answers() {
        let going = [job(2, JobState::Running), job(1, JobState::Queued)];
        let seen = imports_finished(vec![5], &going);
        assert_eq!(seen.watched, [1, 2, 5]);
        assert_eq!(seen.ended, None);
        let now = [job(2, JobState::Done), job(1, JobState::Failed)];
        let seen = imports_finished(seen.watched, &now);
        assert!(seen.watched.is_empty());
        assert_eq!(
            seen.ended,
            Some(ImportsEnded {
                done: 1,
                duplicate: 0,
                failed: 1
            })
        );
    }

    #[test]
    fn a_job_is_found_by_its_page_and_the_latest_wins() {
        let mut by_page = job(1, JobState::Failed);
        by_page.url = "https://a.org/posts/1".into();
        let mut again = job(2, JobState::Queued);
        again.url = "https://cdn.a.org/1.png".into();
        again.source = Some("https://a.org/posts/1".into());
        let other = job(3, JobState::Done);
        let map = imports_by_source(&[again.clone(), by_page, other]);
        assert_eq!(map.len(), 2);
        assert_eq!(map.get("https://a.org/posts/1").map(|j| j.id), Some(2));
        assert_eq!(map.get("https://example.org/3.png").map(|j| j.id), Some(3));
        // Whatever order the list comes in.
        let mut older = job(1, JobState::Failed);
        older.source = again.source.clone();
        let map = imports_by_source(&[older, again]);
        assert_eq!(map.get("https://a.org/posts/1").map(|j| j.id), Some(2));
    }
}
