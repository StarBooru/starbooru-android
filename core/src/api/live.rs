//! A check of the daily-use calls against a real server, run by hand:
//!
//! ```sh
//! cargo test -p starbooru-android-core -- --ignored --nocapture
//! ```
//!
//! It signs in as admin/admin at `STARBOORU_LIVE_SERVER` (or
//! `http://127.0.0.1:8080`), and leaves the server as it found it: its
//! uploads are purged, its saved search deleted, its vote taken back and
//! its session ended, even when a check fails.

use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use tokio::io::{AsyncReadExt, AsyncWriteExt};

use super::*;
use crate::error::CoreError;
use crate::model::{BulkEdit, PostUpdate, Rating, TRASH_QUERY, TagOrder, TagUpdate, duplicate_of};

fn server() -> String {
    std::env::var("STARBOORU_LIVE_SERVER").unwrap_or_else(|_| "http://127.0.0.1:8080".into())
}

/// What the checks made or changed, to put back.
#[derive(Default)]
struct Made {
    posts: Vec<i64>,
    searches: Vec<i64>,
    /// The post voted on, and the vote it had.
    vote: Option<(i64, i16)>,
}

#[derive(Default)]
struct Seen(Mutex<Vec<(u64, u64)>>);

impl UploadProgress for Seen {
    fn sent(&self, bytes: u64, total: u64) {
        self.0.lock().unwrap().push((bytes, total));
    }
}

fn crc32(bytes: &[u8]) -> u32 {
    let mut crc = 0xffff_ffff_u32;
    for &b in bytes {
        crc ^= u32::from(b);
        for _ in 0..8 {
            let mask = (crc & 1).wrapping_neg();
            crc = (crc >> 1) ^ (0xedb8_8320 & mask);
        }
    }
    !crc
}

/// A PNG of `width` × `height` whose pixels depend on `seed`, so each run
/// uploads a file the server hasn't seen. Stored, not compressed.
fn png(width: u32, height: u32, seed: u64) -> Vec<u8> {
    let mut raw = Vec::new();
    for y in 0..height {
        raw.push(0);
        for x in 0..width {
            let v = u64::from(x) * 31 + u64::from(y) * 17 + seed;
            raw.extend([
                v as u8,
                (v >> 8) as u8 ^ x as u8,
                (seed >> ((y % 8) * 8)) as u8,
            ]);
        }
    }
    let mut zlib = vec![0x78, 0x01];
    let blocks: Vec<&[u8]> = raw.chunks(65_535).collect();
    for (i, block) in blocks.iter().enumerate() {
        zlib.push(u8::from(i + 1 == blocks.len()));
        let len = block.len() as u16;
        zlib.extend(len.to_le_bytes());
        zlib.extend((!len).to_le_bytes());
        zlib.extend_from_slice(block);
    }
    let (mut a, mut b) = (1u32, 0u32);
    for &byte in &raw {
        a = (a + u32::from(byte)) % 65_521;
        b = (b + a) % 65_521;
    }
    zlib.extend(((b << 16) | a).to_be_bytes());

    let mut out = b"\x89PNG\r\n\x1a\n".to_vec();
    let mut chunk = |kind: &[u8], data: &[u8]| {
        out.extend((data.len() as u32).to_be_bytes());
        let mut body = kind.to_vec();
        body.extend_from_slice(data);
        out.extend_from_slice(&body);
        out.extend(crc32(&body).to_be_bytes());
    };
    let mut header = Vec::new();
    header.extend(width.to_be_bytes());
    header.extend(height.to_be_bytes());
    header.extend([8, 2, 0, 0, 0]);
    chunk(b"IHDR", &header);
    chunk(b"IDAT", &zlib);
    chunk(b"IEND", &[]);
    out
}

fn scratch(name: &str) -> PathBuf {
    std::env::temp_dir().join(format!("starbooru-live-{}-{name}", std::process::id()))
}

/// Serves `body` to whoever asks, for the import check.
async fn serve(body: Vec<u8>) -> String {
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let url = format!("http://{}/live-check.png", listener.local_addr().unwrap());
    tokio::spawn(async move {
        while let Ok((mut sock, _)) = listener.accept().await {
            let body = body.clone();
            tokio::spawn(async move {
                let mut buf = [0u8; 4096];
                let _ = sock.read(&mut buf).await;
                let head = format!(
                    "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                    body.len()
                );
                let _ = sock.write_all(head.as_bytes()).await;
                let _ = sock.write_all(&body).await;
                let _ = sock.shutdown().await;
            });
        }
    });
    url
}

async fn upload(api: &Api, path: &std::path::Path, seen: Arc<Seen>) -> Result<Post> {
    api.upload_file(
        path.to_string_lossy().into_owned(),
        "live-check.png".into(),
        Vec::new(),
        None,
        None,
        Some(false),
        seen,
    )
    .await
}

async fn checks(api: Arc<Api>, made: Arc<Mutex<Made>>) {
    let base = api.server();
    let info = api.info().await.unwrap();
    let me = api.me().await.unwrap();
    let usage = api.usage().await.unwrap();
    println!(
        "server {} {} ({:?}, {:?}); me {} {:?}; usage {} posts, {} bytes, quota {:?}",
        info.name,
        info.version,
        info.mode,
        info.score_kind,
        me.username,
        me.role,
        usage.posts,
        usage.bytes,
        usage.quota_bytes
    );

    // Tags.
    let top = api.tags(String::new(), 10).await.unwrap();
    assert!(!top.is_empty(), "the server has no tags in use");
    assert!(top.windows(2).all(|w| w[0].post_count >= w[1].post_count));
    let first = top[0].clone();
    let detail = api.tag(first.name.clone()).await.unwrap();
    assert_eq!(detail.tag.name, first.name);
    let page = api
        .tag_list(String::new(), TagOrder::Name, 1, 5)
        .await
        .unwrap();
    assert!(page.tags.windows(2).all(|w| w[0].name <= w[1].name));
    let page2 = api
        .tag_list(String::new(), TagOrder::Name, 2, 5)
        .await
        .unwrap();
    assert!(page2.tags.iter().all(|t| !page.tags.contains(t)));
    let typo = format!("{}x", first.name);
    let corrections = api.tag_corrections(vec![typo.clone()]).await.unwrap();
    assert!(corrections.iter().all(|c| c.from == typo));
    let same = api
        .update_tag(
            first.name.clone(),
            TagUpdate {
                category: Some(first.category),
                parent: None,
            },
        )
        .await
        .unwrap();
    assert_eq!(same.category, first.category);
    let prefix: String = first.name.chars().take(2).collect();
    let completions = api.tags(prefix.clone(), 5).await.unwrap();
    println!(
        "tags: top {:?} ({} posts); {} aliases, {} implies, {} ancestors, {} children; \
         by name {:?} then {:?} (more: {}); {typo} -> {:?}; '{prefix}' -> {:?}",
        first.name,
        first.post_count,
        detail.aliases.len(),
        detail.implies.len(),
        detail.ancestors.len(),
        detail.children.len(),
        page.tags.iter().map(|t| &t.name).collect::<Vec<_>>(),
        page2.tags.iter().map(|t| &t.name).collect::<Vec<_>>(),
        page.has_more,
        corrections.iter().map(|c| &c.to).collect::<Vec<_>>(),
        completions.iter().map(|t| &t.name).collect::<Vec<_>>(),
    );

    // Explore and the home page.
    let explore = api.explore(7, String::new()).await.unwrap();
    for post in explore
        .trending
        .iter()
        .chain(&explore.on_this_day)
        .chain(&explore.random)
    {
        assert!(post.file_url.starts_with(&base), "{}", post.file_url);
    }
    for person in explore.people.iter().chain(&explore.popular_characters) {
        for url in person.face_url.iter().chain(&person.thumb_url) {
            assert!(url.starts_with(&base), "{url}");
        }
    }
    let home = api.home().await.unwrap();
    println!(
        "explore {}d: {} trending, {} tags, {} on this day, {} random, {} people, {} popular; \
         home on: {} ({:?}, {} posts)",
        explore.days,
        explore.trending.len(),
        explore.tags.len(),
        explore.on_this_day.len(),
        explore.random.len(),
        explore.people.len(),
        explore.popular_characters.len(),
        home.enabled,
        home.title,
        home.posts.len()
    );

    // Saved searches.
    let name = format!("live check {}", std::process::id());
    let saved = api
        .save_search(name.clone(), "rating:safe order:score".into())
        .await
        .unwrap();
    made.lock().unwrap().searches.push(saved.id);
    assert!(saved.created_at_ms > 0);
    let listed = api.saved_searches().await.unwrap();
    assert!(listed.iter().any(|s| s.id == saved.id && s.name == name));
    api.delete_search(saved.id).await.unwrap();
    made.lock().unwrap().searches.clear();
    let listed = api.saved_searches().await.unwrap();
    assert!(listed.iter().all(|s| s.id != saved.id));
    println!("saved searches: saved, listed and deleted #{}", saved.id);

    // A post: related, and a vote taken back.
    let posts = api.posts(String::new(), None, 1, 5).await.unwrap();
    let post = posts.posts.first().cloned().unwrap();
    let related = api.related(post.id, 6).await.unwrap();
    assert!(related.len() <= 6 && related.iter().all(|p| p.id != post.id));
    made.lock().unwrap().vote = Some((post.id, post.my_vote));
    let value = if post.my_vote == 1 { -1 } else { 1 };
    let voted = api.vote(post.id, value).await.unwrap();
    assert_eq!(voted.my_vote, value);
    let back = api.vote(post.id, post.my_vote).await.unwrap();
    assert_eq!(back.my_vote, post.my_vote);
    assert_eq!(back.score, post.score);
    made.lock().unwrap().vote = None;
    println!(
        "post #{}: {} related {:?}; voted {value} (score {} -> {}) and back to {}",
        post.id,
        related.len(),
        related.iter().map(|p| p.id).collect::<Vec<_>>(),
        post.score,
        voted.score,
        back.my_vote
    );

    // The blacklist, written back as it was.
    let blacklist = api.blacklist().await.unwrap();
    let kept = api.set_blacklist(blacklist.clone()).await.unwrap();
    assert_eq!(kept, blacklist);
    println!("blacklist: {blacklist:?}");

    // Uploads: one in a piece, then a duplicate, then one in chunks.
    let seed = chrono::Utc::now().timestamp_nanos_opt().unwrap_or_default() as u64;
    let small_bytes = png(16, 16, seed);
    let small_path = scratch("small.png");
    std::fs::write(&small_path, &small_bytes).unwrap();
    let seen = Arc::new(Seen::default());
    let small = upload(&api, &small_path, seen.clone()).await.unwrap();
    made.lock().unwrap().posts.push(small.id);
    let total = small_bytes.len() as u64;
    assert_eq!(*seen.0.lock().unwrap(), [(0, total), (total, total)]);
    assert_eq!(small.mime, "image/png");
    assert_eq!(small.width, Some(16));
    assert!(small.file_url.starts_with(&base));

    let again = upload(&api, &small_path, Arc::new(Seen::default())).await;
    let Err(CoreError::Api {
        status,
        code,
        reason,
    }) = again
    else {
        panic!("a second upload of the same file wasn't refused: {again:?}");
    };
    assert_eq!((status, code.as_str()), (409, "duplicate"));
    let duplicate = duplicate_of(&reason).unwrap();
    assert_eq!(duplicate.post_id, small.id);
    assert!(!duplicate.in_recycle_bin);
    println!(
        "upload: #{} in one piece, progress {:?}; again: {status} {code} \"{reason}\" -> {duplicate:?}",
        small.id,
        seen.0.lock().unwrap()
    );

    let big_bytes = png(1100, 1100, seed ^ 0x5a5a);
    let big_path = scratch("big.png");
    std::fs::write(&big_path, &big_bytes).unwrap();
    let seen = Arc::new(Seen::default());
    let big = upload(&api, &big_path, seen.clone()).await.unwrap();
    made.lock().unwrap().posts.push(big.id);
    let total = big_bytes.len() as u64;
    let steps = seen.0.lock().unwrap().clone();
    assert!(steps.len() >= 3, "{steps:?}");
    assert!(steps.windows(2).all(|w| w[0].0 < w[1].0));
    assert_eq!(steps.first(), Some(&(0, total)));
    assert_eq!(steps.last(), Some(&(total, total)));
    assert_eq!(big.file_size, total as i64);
    println!(
        "upload: #{} of {total} bytes in chunks, progress {steps:?}",
        big.id
    );

    // Editing them.
    let edited = api
        .update_post(
            small.id,
            PostUpdate {
                tags: Some(vec![first.name.clone()]),
                rating: Some(Rating::Questionable),
                source: Some("https://example.com/live-check".into()),
            },
        )
        .await
        .unwrap();
    assert_eq!(edited.rating, Rating::Questionable);
    assert!(edited.tags.contains(&first.name));
    assert_eq!(
        edited.source.as_deref(),
        Some("https://example.com/live-check")
    );
    let bulk = api
        .bulk_edit(BulkEdit {
            ids: vec![small.id, big.id],
            rating: Some(Rating::Sensitive),
            ..BulkEdit::default()
        })
        .await
        .unwrap();
    assert_eq!((bulk.updated, bulk.skipped), (2, 0));
    assert_eq!(api.post(big.id).await.unwrap().rating, Rating::Sensitive);
    println!(
        "edits: #{} tagged {:?}; bulk {bulk:?}",
        small.id, edited.tags
    );

    // Downloading one back.
    let dest = scratch("download.png");
    let size = api
        .download_to(small.file_url.clone(), dest.to_string_lossy().into_owned())
        .await
        .unwrap();
    assert_eq!(size, small_bytes.len() as u64);
    assert_eq!(std::fs::read(&dest).unwrap(), small_bytes);
    let _ = std::fs::remove_file(&dest);
    println!("download: {size} bytes, the same as uploaded");

    // The recycle bin.
    api.delete_post(small.id).await.unwrap();
    let bin = api.posts(TRASH_QUERY.into(), None, 1, 200).await.unwrap();
    assert!(bin.posts.iter().any(|p| p.id == small.id && p.deleted));
    let restored = api.restore_post(small.id).await.unwrap();
    assert!(!restored.deleted);
    api.delete_post(small.id).await.unwrap();
    let again = upload(&api, &small_path, Arc::new(Seen::default())).await;
    let binned = match &again {
        Err(CoreError::Api { reason, .. }) => duplicate_of(reason),
        _ => None,
    };
    assert_eq!(
        binned.map(|d| (d.post_id, d.in_recycle_bin)),
        Some((small.id, true))
    );
    println!(
        "bin: #{} binned, restored and binned again; re-upload says {binned:?}",
        small.id
    );

    // An import from this machine, which the server may not reach.
    let url = serve(png(8, 8, seed ^ 0xa5a5)).await;
    match api
        .import_url(url, Vec::new(), Some(Rating::Safe), None, Some(false))
        .await
    {
        Ok(imported) => {
            made.lock().unwrap().posts.push(imported.id);
            println!("import: #{} from this machine", imported.id);
        }
        Err(CoreError::Api {
            status,
            code,
            reason,
        }) => {
            assert_eq!(status, 400, "{code} {reason}");
            println!("import: refused as it should be, {status} {code} \"{reason}\"");
        }
        Err(other) => panic!("import: {other}"),
    }

    let _ = std::fs::remove_file(&small_path);
    let _ = std::fs::remove_file(&big_path);
}

/// Puts back what the checks made or changed.
async fn clean_up(api: &Api, made: &Made) {
    if let Some((id, vote)) = made.vote {
        let _ = api.vote(id, vote).await;
    }
    for id in &made.searches {
        let _ = api.delete_search(*id).await;
    }
    for id in &made.posts {
        let _ = api.delete_post(*id).await;
        match api.purge_post(*id).await {
            Ok(()) => println!("clean-up: purged #{id}"),
            Err(e) => println!("clean-up: #{id} not purged: {e}"),
        }
    }
    let bin = api.posts(TRASH_QUERY.into(), None, 1, 200).await;
    if let Ok(bin) = bin {
        assert!(bin.posts.iter().all(|p| !made.posts.contains(&p.id)));
    }
}

#[tokio::test]
#[ignore = "needs a StarBooru server with admin/admin at STARBOORU_LIVE_SERVER or 127.0.0.1:8080"]
async fn daily_use_against_a_real_server() {
    let guest = Api::new(server(), None).unwrap();
    let session = guest.sign_in("admin".into(), "admin".into()).await.unwrap();
    let api = Api::new(server(), Some(session.token)).unwrap();
    let made = Arc::new(Mutex::new(Made::default()));

    // The checks run as a task, so a failed one still gets cleaned up after.
    let outcome = tokio::spawn(checks(api.clone(), made.clone())).await;
    let made = std::mem::take(&mut *made.lock().unwrap());
    clean_up(&api, &made).await;
    api.sign_out().await.unwrap();
    if let Err(e) = outcome {
        std::panic::resume_unwind(e.into_panic());
    }
}
