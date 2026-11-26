//! Files in and out: uploads with progress, imports by address, and
//! downloads of a post's file.
//!
//! An upload runs on a thread of its own. The client reports progress
//! through a plain `&dyn Fn`, which makes its upload a future that can't
//! move between threads, and UniFFI needs one that can. The thread has its
//! own runtime and its own client, so no connection outlives it.

use std::cell::Cell;
use std::io::SeekFrom;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use bytes::Bytes;
use starbooru_client::{Client, FileUpload};
use tokio::io::{AsyncReadExt, AsyncSeekExt};
use tokio::sync::oneshot;

use super::Api;
use crate::error::{CoreError, Result};
use crate::model::{Post, Rating};

/// Hears how an upload is going. Kotlin implements it.
#[uniffi::export(with_foreign)]
pub trait UploadProgress: Send + Sync {
    /// `bytes` of the file's `total` are on the server. Called from a
    /// background thread, with `0` first and `total` once it is done; a
    /// file up to 2 MiB goes in one piece, with nothing in between.
    fn sent(&self, bytes: u64, total: u64);
}

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// Uploads the file at `path`, read a piece at a time; one over 2 MiB
    /// goes in chunks, several at once. The server tells the type from the
    /// content, so `file_name` is only a label. `rating` `None` is safe;
    /// `autotag` `None` leaves the AI to the server's setting.
    ///
    /// A file the server has already is `CoreError::Api` with code
    /// `duplicate`; `duplicate_of(reason)` names the post. Cancelling the
    /// call stops the upload. An upload can't be picked up again after the
    /// app dies: start it over, and the server drops the unfinished one
    /// within the hour.
    #[allow(clippy::too_many_arguments)]
    pub async fn upload_file(
        &self,
        path: String,
        file_name: String,
        tags: Vec<String>,
        rating: Option<Rating>,
        source: Option<String>,
        autotag: Option<bool>,
        progress: Arc<dyn UploadProgress>,
    ) -> Result<Post> {
        let path = PathBuf::from(path);
        let size = tokio::fs::metadata(&path).await?.len();
        let upload = FileUpload {
            file_name,
            size,
            tags,
            rating: rating.map(Into::into).unwrap_or_default(),
            source,
            autotag,
        };
        let client = Client::new(self.client.base_url())?
            .with_token(self.client.token().map(ToOwned::to_owned));
        let (done, answer) = oneshot::channel();
        std::thread::Builder::new()
            .name("starbooru-upload".into())
            .spawn(move || upload_on_this_thread(client, upload, path, progress, done))?;
        let post = answer.await.map_err(|_| CoreError::Io {
            detail: "the upload stopped before it finished".to_owned(),
        })??;
        Ok(Post::from_core(post, &self.client))
    }

    /// Asks the server to download `url` and add it as a post. A post's
    /// page on another booru brings its file, tags and rating. `source`
    /// `None` is `url`.
    pub async fn import_url(
        &self,
        url: String,
        tags: Vec<String>,
        rating: Option<Rating>,
        source: Option<String>,
        autotag: Option<bool>,
    ) -> Result<Post> {
        let request = starbooru_core::ImportRequest {
            url,
            tags,
            rating: rating.map(Into::into).unwrap_or_default(),
            source,
            autotag,
        };
        let post = self.client.import_url(&request).await?;
        Ok(Post::from_core(post, &self.client))
    }

    /// Saves a post's file as `dest_path` and answers its size. `url` is
    /// the post's `file_url` (or `sample_url`), as this `Api` gave it. The
    /// file is written to `<dest_path>.part` first, so a failed download
    /// leaves nothing half-written at `dest_path`.
    pub async fn download_to(&self, url: String, dest_path: String) -> Result<u64> {
        let path =
            server_path(self.client.base_url(), &url).ok_or_else(|| CoreError::InvalidAddress {
                detail: "the file isn't on this server".to_owned(),
            })?;
        Ok(self
            .client
            .download_to(&path, Path::new(&dest_path))
            .await?)
    }
}

/// Runs one upload to the end, or until the caller stops waiting.
fn upload_on_this_thread(
    client: Client,
    upload: FileUpload,
    path: PathBuf,
    progress: Arc<dyn UploadProgress>,
    mut done: oneshot::Sender<Result<starbooru_core::Post>>,
) {
    let runtime = match tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
    {
        Ok(runtime) => runtime,
        Err(e) => {
            let _ = done.send(Err(e.into()));
            return;
        }
    };
    let reporter = Reporter::new(progress, upload.size);
    let read = |offset, len| {
        let path = path.clone();
        async move {
            read_piece(&path, offset, len)
                .await
                .map_err(|e| e.to_string())
        }
    };
    let report = |bytes| reporter.report(bytes);
    let outcome = runtime.block_on(async {
        reporter.report(0);
        tokio::select! {
            sent = client.upload_file(upload, read, &report) => Some(sent),
            () = done.closed() => None,
        }
    });
    if let Some(sent) = outcome {
        if sent.is_ok() {
            reporter.report(reporter.total);
        }
        let _ = done.send(sent.map_err(Into::into));
    }
}

/// Passes progress on, each figure once and always further along.
struct Reporter {
    sink: Arc<dyn UploadProgress>,
    total: u64,
    last: Cell<Option<u64>>,
}

impl Reporter {
    fn new(sink: Arc<dyn UploadProgress>, total: u64) -> Self {
        Self {
            sink,
            total,
            last: Cell::new(None),
        }
    }

    fn report(&self, bytes: u64) {
        let bytes = bytes.min(self.total);
        if self.last.get().is_some_and(|last| bytes <= last) {
            return;
        }
        self.last.set(Some(bytes));
        self.sink.sent(bytes, self.total);
    }
}

/// `len` bytes of the file at `path` from `offset`, or fewer where it ends.
async fn read_piece(path: &Path, offset: u64, len: u64) -> std::io::Result<Bytes> {
    let mut file = tokio::fs::File::open(path).await?;
    file.seek(SeekFrom::Start(offset)).await?;
    let mut piece = Vec::with_capacity(usize::try_from(len).unwrap_or(0));
    file.take(len).read_to_end(&mut piece).await?;
    Ok(Bytes::from(piece))
}

/// The server path of `url`, an address this server's `Api` made
/// (`base` is `Client::base_url`). A path is kept as it is.
fn server_path(base: &str, url: &str) -> Option<String> {
    if url.starts_with('/') {
        return Some(url.to_owned());
    }
    let rest = url.strip_prefix(base)?;
    rest.starts_with('/').then(|| rest.to_owned())
}

#[cfg(test)]
mod tests {
    use std::sync::Mutex;
    use std::time::Duration;

    use super::*;

    #[derive(Default)]
    struct Seen(Mutex<Vec<(u64, u64)>>);

    impl UploadProgress for Seen {
        fn sent(&self, bytes: u64, total: u64) {
            self.0.lock().unwrap().push((bytes, total));
        }
    }

    fn scratch(name: &str) -> PathBuf {
        std::env::temp_dir().join(format!(
            "starbooru-core-{}-{name}-{}",
            std::process::id(),
            chrono::Utc::now().timestamp_nanos_opt().unwrap_or_default()
        ))
    }

    #[test]
    fn progress_only_goes_forward() {
        let seen = Arc::new(Seen::default());
        let reporter = Reporter::new(seen.clone(), 10);
        for bytes in [0, 4, 4, 2, 10, 10, 99] {
            reporter.report(bytes);
        }
        assert_eq!(*seen.0.lock().unwrap(), [(0, 10), (4, 10), (10, 10)]);
    }

    #[test]
    fn an_empty_file_still_says_where_it_is() {
        let seen = Arc::new(Seen::default());
        let reporter = Reporter::new(seen.clone(), 0);
        reporter.report(0);
        reporter.report(0);
        assert_eq!(*seen.0.lock().unwrap(), [(0, 0)]);
    }

    #[tokio::test]
    async fn reads_a_piece_of_a_file() {
        let path = scratch("piece");
        std::fs::write(&path, b"0123456789").unwrap();
        assert_eq!(&read_piece(&path, 2, 3).await.unwrap()[..], b"234");
        // Past the end it is short, and the client says the file changed.
        assert_eq!(&read_piece(&path, 8, 5).await.unwrap()[..], b"89");
        std::fs::remove_file(&path).unwrap();
        assert!(read_piece(&path, 0, 1).await.is_err());
    }

    #[test]
    fn a_files_address_goes_back_to_its_path() {
        let base = "https://booru.example/sub";
        assert_eq!(
            server_path(
                base,
                "https://booru.example/sub/media/original/ab.png?exp=1&sig=2"
            )
            .as_deref(),
            Some("/media/original/ab.png?exp=1&sig=2")
        );
        assert_eq!(
            server_path(base, "/media/thumb/ab.webp?sig=1").as_deref(),
            Some("/media/thumb/ab.webp?sig=1")
        );
        assert_eq!(server_path(base, "https://other.example/media/x"), None);
        assert_eq!(
            server_path(base, "https://booru.example/subway/media/x"),
            None
        );
        assert_eq!(
            server_path(
                "https://booru.example",
                "https://booru.example.evil/media/x"
            ),
            None
        );
    }

    #[tokio::test]
    async fn a_download_from_elsewhere_is_refused() {
        let api = Api::new("https://booru.example".into(), None).unwrap();
        let err = api
            .download_to("https://other.example/media/x".into(), "/tmp/x".into())
            .await
            .unwrap_err();
        assert!(matches!(err, CoreError::InvalidAddress { .. }), "{err}");
    }

    #[tokio::test]
    async fn a_missing_file_is_an_io_error() {
        let api = Api::new("http://127.0.0.1:9".into(), None).unwrap();
        let err = api
            .upload_file(
                scratch("missing").to_string_lossy().into_owned(),
                "a.png".into(),
                Vec::new(),
                None,
                None,
                None,
                Arc::new(Seen::default()),
            )
            .await
            .unwrap_err();
        assert!(matches!(err, CoreError::Io { .. }), "{err}");
    }

    #[tokio::test]
    async fn an_upload_to_nowhere_comes_back_unreachable() {
        let path = scratch("nowhere");
        std::fs::write(&path, b"not much").unwrap();
        let api = Api::new("http://127.0.0.1:9".into(), Some("t".into())).unwrap();
        let seen = Arc::new(Seen::default());
        let err = api
            .upload_file(
                path.to_string_lossy().into_owned(),
                "a.png".into(),
                Vec::new(),
                None,
                None,
                None,
                seen.clone(),
            )
            .await
            .unwrap_err();
        std::fs::remove_file(&path).unwrap();
        assert!(matches!(err, CoreError::Unreachable { .. }), "{err}");
        assert_eq!(*seen.0.lock().unwrap(), [(0, 8)]);
    }

    #[tokio::test]
    async fn cancelling_an_upload_stops_its_thread() {
        // A server that takes the connection and never answers.
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = format!("http://{}", listener.local_addr().unwrap());
        tokio::spawn(async move {
            let mut held = Vec::new();
            while let Ok((sock, _)) = listener.accept().await {
                held.push(sock);
            }
        });
        let path = scratch("cancel");
        std::fs::write(&path, b"not much").unwrap();
        let api = Api::new(address, Some("t".into())).unwrap();
        let seen = Arc::new(Seen::default());
        let upload = api.upload_file(
            path.to_string_lossy().into_owned(),
            "a.png".into(),
            Vec::new(),
            None,
            None,
            None,
            seen.clone(),
        );
        assert!(
            tokio::time::timeout(Duration::from_millis(300), upload)
                .await
                .is_err()
        );
        // The thread drops its hold on `seen` when it ends.
        let mut waited = 0;
        while Arc::strong_count(&seen) > 1 && waited < 50 {
            tokio::time::sleep(Duration::from_millis(20)).await;
            waited += 1;
        }
        std::fs::remove_file(&path).unwrap();
        assert_eq!(Arc::strong_count(&seen), 1);
        assert_eq!(*seen.0.lock().unwrap(), [(0, 8)]);
    }
}
