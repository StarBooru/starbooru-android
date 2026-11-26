//! A check of the account, sharing, people, duplicates, editor and
//! public-server calls against a real server, run by hand with the other
//! live check:
//!
//! ```sh
//! cargo test -p starbooru-android-core -- --ignored --nocapture
//! ```
//!
//! It signs in as admin/admin at `STARBOORU_LIVE_SERVER` (or
//! `http://127.0.0.1:8080`). What would change admin's own account (its
//! password, its other sessions, its profile) it tries on a throwaway
//! account instead. It only signs out sessions it made, and removes what it
//! made even when a check fails: the throwaway account, share links, the API
//! key, forum threads and categories, its posts (purged), and the sharing
//! default as it was. Posts already there are only read.

use std::sync::{Arc, Mutex};
use std::time::Duration;

use starbooru_client::Client;

use super::live::{png, scratch, server};
use super::*;
use crate::error::CoreError;
use crate::model::{
    AvatarCrop, Crop, EditKind, EditProblem, FaceBox, Figure, FigureKind, FigureSlot, LinkPreview,
    MediaEdit, Mode, NewApiKey, NewCategory, NewShare, NewThread, PeopleOrder, PeopleQuery,
    ProfileUpdate, ResolveDuplicates, Role, ShareLink, ShareUpdate, SharingSettings, SlotTags,
    TRASH_QUERY, ThreadUpdate, default_duplicate_distance, edit_kind, edit_problem, face_box_fits,
    share_link,
};

/// What the checks made or changed, to put back.
#[derive(Default)]
struct Made {
    posts: Vec<i64>,
    shares: Vec<i64>,
    keys: Vec<i64>,
    /// Admin sessions signed in here, by token.
    tokens: Vec<String>,
    threads: Vec<i64>,
    categories: Vec<i64>,
    /// The throwaway account.
    user: Option<i64>,
    /// The sharing default before the check.
    sharing: Option<bool>,
}

struct Quiet;

impl UploadProgress for Quiet {
    fn sent(&self, _: u64, _: u64) {}
}

/// The status and code of a refusal; anything else fails the check.
fn refused<T: std::fmt::Debug>(what: &str, outcome: Result<T>) -> (u16, String) {
    match outcome {
        Err(CoreError::Api {
            status,
            code,
            reason,
        }) => {
            println!("  {what}: {status} {code} \"{reason}\"");
            (status, code)
        }
        other => panic!("{what}: expected a refusal, got {other:?}"),
    }
}

fn signed_out<T: std::fmt::Debug>(what: &str, outcome: Result<T>) {
    assert!(
        matches!(outcome, Err(CoreError::Unauthorized { .. })),
        "{what}: {outcome:?}"
    );
}

async fn upload(api: &Api, name: &str, bytes: Vec<u8>, tags: Vec<String>) -> Post {
    let path = scratch(name);
    std::fs::write(&path, bytes).unwrap();
    let post = api
        .upload_file(
            path.to_string_lossy().into_owned(),
            format!("{name}.png"),
            tags,
            None,
            None,
            Some(false),
            Arc::new(Quiet),
        )
        .await;
    let _ = std::fs::remove_file(&path);
    post.unwrap()
}

async fn in_bin(api: &Api, id: i64) -> bool {
    let bin = api.posts(TRASH_QUERY.into(), None, 1, 200).await.unwrap();
    bin.posts.iter().any(|p| p.id == id)
}

async fn sessions(admin: &Api, base: &str, made: &Mutex<Made>) {
    let before = admin.sessions().await.unwrap();
    assert!(before.first().is_some_and(|s| s.current), "{before:?}");
    assert_eq!(before.iter().filter(|s| s.current).count(), 1);
    let guest = Api::new(base.to_owned(), None).unwrap();
    let second = guest.sign_in("admin".into(), "admin".into()).await.unwrap();
    made.lock().unwrap().tokens.push(second.token.clone());
    let other = Api::new(base.to_owned(), Some(second.token)).unwrap();
    let seen = other.sessions().await.unwrap();
    let mine = seen.iter().find(|s| s.current).cloned().unwrap();
    assert_eq!(seen.first().map(|s| s.id), Some(mine.id));
    assert!(before.iter().all(|s| s.id != mine.id));
    assert!(mine.expires_at_ms > mine.created_at_ms);
    let listed = admin.sessions().await.unwrap();
    assert!(listed.iter().any(|s| s.id == mine.id && !s.current));
    admin.revoke_session(mine.id).await.unwrap();
    signed_out("a revoked session", other.me().await);
    made.lock().unwrap().tokens.clear();
    println!(
        "sessions: {} before {:?}; #{} \"{}\" signed in, seen as current from itself and \
         not from here, and signed out",
        before.len(),
        before
            .iter()
            .map(|s| (s.id, s.client.as_str(), s.current))
            .collect::<Vec<_>>(),
        mine.id,
        mine.client
    );

    let methods = admin.sign_in_methods().await.unwrap();
    assert!(methods.password);
    print!("sign-in methods: {methods:?}; linking a provider account:");
    match admin.begin_provider_link().await {
        Ok(link) => {
            assert!(link.url().starts_with("http"), "{}", link.url());
            link.cancel().await;
            println!(" started at {} and cancelled", link.url());
        }
        Err(e) => {
            println!();
            refused("begin_provider_link", Err::<(), _>(e));
        }
    }
}

async fn api_keys(admin: &Api, base: &str, post_id: i64, made: &Mutex<Made>) {
    let name = format!("live check {}", std::process::id());
    let created = admin
        .create_api_key(NewApiKey {
            name: name.clone(),
            read_only: true,
            expires_days: Some(1),
        })
        .await
        .unwrap();
    made.lock().unwrap().keys.push(created.key.id);
    let key = &created.key;
    assert!(created.token.starts_with(&key.prefix), "{key:?}");
    assert!(key.read_only && key.last_used_at_ms.is_none());
    let lasts = key.expires_at_ms.unwrap() - key.created_at_ms;
    assert!((lasts - 86_400_000).abs() < 60_000, "{lasts}");
    let listed = admin.api_keys().await.unwrap();
    assert!(listed.iter().any(|k| k.id == key.id && k.name == name));

    let by_key = Api::new(base.to_owned(), Some(created.token.clone())).unwrap();
    assert_eq!(by_key.me().await.unwrap().username, "admin");
    let keys_by_key = refused("api_keys with a key", by_key.api_keys().await);
    assert_eq!(keys_by_key, (403, "forbidden".to_owned()));
    let write = by_key
        .create_share(NewShare {
            post_ids: vec![post_id],
            title: None,
            days: Some(1),
            preview: None,
        })
        .await;
    if let Ok(share) = &write {
        made.lock().unwrap().shares.push(share.id);
    }
    assert_eq!(
        refused("a write with a read-only key", write),
        (403, "forbidden".to_owned())
    );

    admin.delete_api_key(key.id).await.unwrap();
    made.lock().unwrap().keys.clear();
    signed_out("a deleted key", by_key.me().await);
    println!(
        "api keys: #{} \"{name}\" ({}…) made, used, refused what it may not do, deleted",
        key.id, key.prefix
    );
}

async fn sharing(admin: &Api, info: &ServerInfo, posts: &[Post], made: &Mutex<Made>) {
    let base = admin.server();
    let settings = admin.sharing().await.unwrap();
    made.lock().unwrap().sharing = Some(settings.previews);
    let flipped = admin
        .set_sharing(SharingSettings {
            previews: !settings.previews,
        })
        .await
        .unwrap();
    assert_eq!(flipped.previews, !settings.previews);
    assert_eq!(admin.set_sharing(settings).await.unwrap(), settings);
    made.lock().unwrap().sharing = None;
    println!(
        "sharing: previews {} by default, flipped and back",
        settings.previews
    );

    let (first, second) = (&posts[0], &posts[1]);
    let made_share = admin
        .create_share(NewShare {
            post_ids: vec![first.id, second.id, first.id],
            title: Some("  live check  ".into()),
            days: Some(1),
            preview: None,
        })
        .await;
    if info.mode == Mode::Public {
        refused("create_share on a public server", made_share);
        return;
    }
    let share = made_share.unwrap();
    made.lock().unwrap().shares.push(share.id);
    assert_eq!(share.posts, 2);
    assert_eq!(share.title.as_deref(), Some("live check"));
    assert_eq!(share.preview, LinkPreview::Account);
    assert_eq!(share.preview_on, info.link_previews && settings.previews);
    assert_eq!(share.url, format!("{base}/s/{}", share.token));
    assert_eq!(
        share_link(&share.url),
        Some(ShareLink {
            server: base.clone(),
            token: share.token.clone(),
        })
    );
    let lasts = share.expires_at_ms.unwrap() - share.created_at_ms;
    assert!((lasts - 86_400_000).abs() < 60_000, "{lasts}");
    assert!(share.cover_url.iter().all(|u| u.starts_with(&base)));

    let shown = admin
        .update_share(
            share.id,
            ShareUpdate {
                preview: Some(LinkPreview::Shown),
            },
        )
        .await
        .unwrap();
    assert_eq!(shown.preview, LinkPreview::Shown);
    assert_eq!(shown.preview_on, info.link_previews);
    let follows = admin
        .update_share(
            share.id,
            ShareUpdate {
                preview: Some(LinkPreview::Account),
            },
        )
        .await
        .unwrap();
    assert_eq!(follows.preview, LinkPreview::Account);
    let same = admin
        .update_share(share.id, ShareUpdate::default())
        .await
        .unwrap();
    assert_eq!(same.preview, LinkPreview::Account);

    let guest = Api::new(base.clone(), None).unwrap();
    let view = guest.shared(share.token.clone()).await.unwrap();
    assert_eq!(view.posts.len(), 2);
    assert_eq!(view.server, info.name);
    assert_eq!(view.title.as_deref(), Some("live check"));
    assert_eq!(view.posts[0].mime, first.mime);
    for post in &view.posts {
        assert!(post.file_url.starts_with(&base), "{}", post.file_url);
        assert!(post.thumb_url.iter().all(|u| u.starts_with(&base)));
    }
    // Signed in, it still goes out as a guest.
    let again = admin.shared(share.token.clone()).await.unwrap();
    assert_eq!(again.posts.len(), 2);
    let listed = admin.shares().await.unwrap();
    let opened = listed.iter().find(|s| s.id == share.id).unwrap();
    assert!(opened.views >= 2 && opened.last_viewed_at_ms.is_some());
    signed_out("shares as a guest", guest.shares().await);

    admin.delete_share(share.id).await.unwrap();
    made.lock().unwrap().shares.clear();
    let gone = refused("a removed link", guest.shared(share.token.clone()).await);
    assert_eq!(gone, (404, "not_found".to_owned()));
    refused("an unknown link", guest.shared("0".repeat(32)).await);
    println!(
        "share #{} of #{} and #{}: {} opened {} times as a guest and signed in, previews \
         shown and back, deleted",
        share.id, first.id, second.id, share.url, opened.views
    );
}

async fn people(admin: &Api) {
    let base = admin.server();
    let page = admin
        .people(PeopleQuery {
            order: PeopleOrder::Posts,
            q: String::new(),
            franchise: None,
            page: 1,
            limit: 10,
        })
        .await
        .unwrap();
    for person in &page.people {
        for url in person.face_url.iter().chain(&person.thumb_url) {
            assert!(url.starts_with(&base), "{url}");
        }
    }
    let without = admin
        .people(PeopleQuery {
            order: PeopleOrder::Franchise,
            q: String::new(),
            franchise: Some(String::new()),
            page: 1,
            limit: 10,
        })
        .await
        .unwrap();
    let franchises = admin.franchises(String::new(), 1, 10).await.unwrap();
    println!(
        "people: {:?} (more: {}); {} without a franchise; franchises {:?}, {} people without",
        page.people.iter().map(|p| &p.name).collect::<Vec<_>>(),
        page.has_more,
        without.people.len(),
        franchises
            .franchises
            .iter()
            .map(|f| (&f.name, f.people))
            .collect::<Vec<_>>(),
        franchises.without
    );
}

async fn duplicates(admin: &Api, a: &Post, b: &Post) {
    let base = admin.server();
    let both =
        |posts: &[Post]| posts.iter().any(|p| p.id == a.id) && posts.iter().any(|p| p.id == b.id);
    let mut found = None;
    for _ in 0..30 {
        let list = admin
            .duplicates(default_duplicate_distance(), 100)
            .await
            .unwrap();
        if let Some(group) = list.groups.iter().find(|g| both(&g.posts)).cloned() {
            found = Some((list, group));
            break;
        }
        tokio::time::sleep(Duration::from_millis(500)).await;
    }
    let (list, group) = found.expect("two posts that look alike never made a group");
    assert!(group.posts.iter().all(|p| p.file_url.starts_with(&base)));
    admin.dismiss_duplicates(vec![a.id, b.id]).await.unwrap();
    let after = admin
        .duplicates(default_duplicate_distance(), 100)
        .await
        .unwrap();
    assert!(after.groups.iter().all(|g| !both(&g.posts)));
    let wrong = refused(
        "resolve keeping a post it removes",
        admin
            .resolve_duplicates(ResolveDuplicates {
                keep: a.id,
                remove: vec![a.id],
                merge_tags: false,
            })
            .await,
    );
    assert_eq!(wrong.0, 400);
    let kept = admin
        .resolve_duplicates(ResolveDuplicates {
            keep: a.id,
            remove: vec![b.id],
            merge_tags: true,
        })
        .await
        .unwrap();
    assert_eq!(kept.id, a.id);
    assert!(in_bin(admin, b.id).await);
    println!(
        "duplicates: #{} and #{} grouped (distance {}, {} groups, {} unhashed), dismissed, \
         then #{} kept and #{} binned",
        a.id, b.id, group.distance, list.total, list.unhashed, a.id, b.id
    );
}

async fn figures(admin: &Api, a: &Post) {
    let base = admin.server();
    for name in ["1girl", "solo", "long_hair"] {
        if admin.tag(name.into()).await.is_err() {
            println!("figures: skipped, as saving would make the tag {name}");
            return;
        }
    }
    let face = FaceBox {
        x: 250,
        y: 200,
        w: 300,
        h: 400,
    };
    let girl = Figure {
        kind: FigureKind::Girl,
        character: None,
        cosplay: None,
        franchise: None,
        tags: vec![SlotTags {
            slot: FigureSlot::Hair,
            tags: vec!["long_hair".into()],
        }],
        face: Some(face),
        face_url: None,
    };
    let saved = admin.set_figures(a.id, vec![girl.clone()]).await.unwrap();
    assert_eq!(saved.figures.len(), 1);
    assert_eq!(saved.figures[0].face, Some(face));
    for tag in ["1girl", "solo", "long_hair"] {
        assert!(saved.tags.iter().any(|t| t == tag), "{:?}", saved.tags);
    }
    let mut crop = None;
    for _ in 0..20 {
        let post = admin.post(a.id).await.unwrap();
        crop = post.figures.first().and_then(|f| f.face_url.clone());
        if crop.is_some() {
            break;
        }
        tokio::time::sleep(Duration::from_millis(250)).await;
    }
    if let Some(url) = &crop {
        assert!(url.starts_with(&base), "{url}");
    }
    let outside = FaceBox {
        x: 900,
        y: 0,
        w: 200,
        h: 100,
    };
    assert!(!face_box_fits(outside));
    let bad = Figure {
        face: Some(outside),
        ..girl
    };
    let (status, _) = refused(
        "a face outside the picture",
        admin.set_figures(a.id, vec![bad]).await,
    );
    assert_eq!(status, 400);
    let cleared = admin.set_figures(a.id, Vec::new()).await.unwrap();
    assert!(cleared.figures.is_empty());
    assert!(
        !cleared
            .tags
            .iter()
            .any(|t| t == "long_hair" || t == "1girl")
    );
    println!(
        "figures on #{}: a girl saved with tags {:?}, her face crop {}, then cleared",
        a.id,
        saved.tags,
        crop.as_deref().unwrap_or("not made within 5 s")
    );
}

async fn ai(admin: &Api, a: &Post) {
    let ai = admin.ai_status().await.unwrap();
    println!("ai: {ai:?}");
    match admin.find_people(a.id).await {
        Ok(found) => {
            assert!(ai.people);
            println!("  find_people: {} by {}", found.figures.len(), found.model);
        }
        Err(e) => {
            refused("find_people", Err::<(), _>(e));
        }
    }
    match admin.suggest_tags(a.id).await {
        Ok(found) => {
            assert!(ai.tagging);
            println!("  suggest_tags: {:?}", found.tags);
        }
        Err(e) => {
            refused("suggest_tags", Err::<(), _>(e));
        }
    }
}

async fn editor(admin: &Api, a: &Post, tag: &str, made: &Mutex<Made>) {
    assert_eq!(edit_kind(a.kind, &a.mime), Some(EditKind::Image));
    let edit = MediaEdit {
        rotate: 90,
        crop: Some(Crop {
            x: 0,
            y: 0,
            width: 64,
            height: 32,
        }),
        ..MediaEdit::default()
    };
    assert_eq!(edit_problem(edit, EditKind::Image, 64, 64, None), None);
    let turned = admin.edit_post(a.id, edit).await.unwrap();
    made.lock().unwrap().posts.push(turned.id);
    assert_ne!(turned.id, a.id);
    assert_eq!((turned.width, turned.height), (Some(64), Some(32)));
    assert!(turned.tags.iter().any(|t| t == tag), "{:?}", turned.tags);
    let flipped = admin
        .edit_post(
            turned.id,
            MediaEdit {
                flip_h: true,
                bin_original: true,
                ..MediaEdit::default()
            },
        )
        .await
        .unwrap();
    made.lock().unwrap().posts.push(flipped.id);
    assert!(in_bin(admin, turned.id).await);
    assert!(!in_bin(admin, a.id).await);
    assert_eq!(
        edit_problem(MediaEdit::default(), EditKind::Image, 64, 64, None),
        Some(EditProblem::NoChange)
    );
    let (status, _) = refused(
        "an edit that changes nothing",
        admin.edit_post(a.id, MediaEdit::default()).await,
    );
    assert_eq!(status, 400);
    println!(
        "editor: #{} turned and cropped into #{} (64 × 32), flipped into #{} with #{} binned",
        a.id, turned.id, flipped.id, turned.id
    );
}

async fn tag_vote(admin: &Api, a: &Post, tag: &str) {
    match admin.vote_tag(a.id, tag.to_owned(), 1).await {
        Ok(voted) => {
            let vote = voted.tag_votes.iter().find(|v| v.tag == tag).unwrap();
            assert_eq!(vote.mine, 1);
            let back = admin.vote_tag(a.id, tag.to_owned(), 0).await.unwrap();
            assert!(back.tag_votes.iter().all(|v| v.tag != tag || v.mine == 0));
            println!("tag vote: {tag} on #{} voted for and back", a.id);
        }
        Err(e) => {
            let refusal = refused("vote_tag", Err::<(), _>(e));
            assert_eq!(refusal, (403, "tag_votes_off".to_owned()));
        }
    }
}

async fn forum(admin: &Api, info: &ServerInfo, made: &Mutex<Made>) {
    let me = admin.my_profile().await.unwrap();
    assert_eq!((me.name.as_str(), me.role), ("admin", Role::Admin));
    println!("my profile: {me:?}");
    if info.mode == Mode::Private {
        for (what, outcome) in [
            ("profile", admin.profile("admin".into()).await.map(|_| ())),
            ("forum", admin.forum().await.map(|_| ())),
            (
                "forum_threads",
                admin
                    .forum_threads(None, String::new(), 1, 30)
                    .await
                    .map(|_| ()),
            ),
            (
                "forum_thread",
                admin.forum_thread(1, 1, 50).await.map(|_| ()),
            ),
            (
                "create_forum_category",
                admin
                    .create_forum_category(NewCategory {
                        name: "live check".into(),
                        description: String::new(),
                        position: None,
                    })
                    .await
                    .map(|_| ()),
            ),
            (
                "create_forum_thread",
                admin
                    .create_forum_thread(NewThread {
                        category_id: 1,
                        title: "live check".into(),
                        body: "live check".into(),
                    })
                    .await
                    .map(|_| ()),
            ),
        ] {
            let (status, code) = refused(what, outcome);
            assert_eq!(status, 403, "{what}");
            assert!(
                code == "profiles_off" || code == "forum_off",
                "{what}: {code}"
            );
        }
        return;
    }
    let profile = admin.profile("ADMIN".into()).await.unwrap();
    assert_eq!(profile.name, "admin");
    let category = admin
        .create_forum_category(NewCategory {
            name: format!("live check {}", std::process::id()),
            description: "made by the live check".into(),
            position: None,
        })
        .await
        .unwrap();
    made.lock().unwrap().categories.push(category.id);
    let thread = admin
        .create_forum_thread(NewThread {
            category_id: category.id,
            title: "live check".into(),
            body: "first, see >>1".into(),
        })
        .await
        .unwrap();
    made.lock().unwrap().threads.push(thread.id);
    let reply = admin
        .reply_in_forum(thread.id, "a reply".into())
        .await
        .unwrap();
    let edited = admin
        .edit_forum_post(reply.id, "an edited reply".into())
        .await
        .unwrap();
    assert!(edited.edited_at_ms.is_some());
    let pinned = admin
        .update_forum_thread(
            thread.id,
            ThreadUpdate {
                pinned: Some(true),
                ..ThreadUpdate::default()
            },
        )
        .await
        .unwrap();
    assert!(pinned.pinned);
    admin.delete_forum_post(reply.id).await.unwrap();
    let view = admin.forum_thread(thread.id, 1, 50).await.unwrap();
    assert_eq!(view.posts.len(), 2);
    assert!(view.posts[1].deleted && view.posts[1].body.is_empty());
    let found = admin
        .forum_threads(Some(category.id), String::new(), 1, 30)
        .await
        .unwrap();
    assert_eq!(found.threads.first().map(|t| t.id), Some(thread.id));
    admin.delete_forum_thread(thread.id).await.unwrap();
    made.lock().unwrap().threads.clear();
    admin.delete_forum_category(category.id).await.unwrap();
    made.lock().unwrap().categories.clear();
    println!(
        "forum: category #{}, thread #{} with a reply edited and deleted, all removed",
        category.id, thread.id
    );
}

/// A password the server takes for a new account.
fn strong_password(username: &str, seed: u64) -> String {
    (0..64u64)
        .map(|i| format!("Lc{:x}!q", seed.rotate_left(7 * i as u32) ^ i))
        .find(|p| !starbooru_core::password_is_weak(p, username))
        .unwrap()
}

async fn throwaway_account(admin: &Api, info: &ServerInfo, seed: u64, made: &Mutex<Made>) {
    let base = admin.server();
    let public = info.mode == Mode::Public;
    let guest = Api::new(base.clone(), None).unwrap();
    let name = format!("live-check-{}", std::process::id());
    let password = strong_password(&name, seed);
    let signed = match guest.register(name.clone(), password.clone()).await {
        Ok(signed) => signed,
        Err(e) => {
            println!("throwaway account: not made, so its checks are skipped: {e:?}");
            return;
        }
    };
    made.lock().unwrap().user = Some(signed.user.id);
    assert_eq!(signed.user.role, Role::User);
    let first = Api::new(base.clone(), Some(signed.token)).unwrap();

    let new_password = strong_password(&name, seed ^ 0xfeed);
    let (status, _) = refused(
        "change_password with a wrong one",
        first
            .change_password("not the password".into(), new_password.clone())
            .await,
    );
    assert_eq!(status, 400);
    first
        .change_password(password.clone(), new_password.clone())
        .await
        .unwrap();
    assert_eq!(first.me().await.unwrap().username, name);
    signed_out(
        "the old password",
        guest.sign_in(name.clone(), password).await,
    );
    let second = guest
        .sign_in(name.clone(), new_password.clone())
        .await
        .unwrap();
    let second = Api::new(base.clone(), Some(second.token)).unwrap();
    let third = guest.sign_in(name.clone(), new_password).await.unwrap();
    let third = Api::new(base.clone(), Some(third.token)).unwrap();
    assert_eq!(third.revoke_other_sessions().await.unwrap(), 2);
    signed_out("the first session", first.me().await);
    signed_out("the second session", second.me().await);
    let left = third.sessions().await.unwrap();
    assert!(left.len() == 1 && left[0].current, "{left:?}");

    let methods = third.sign_in_methods().await.unwrap();
    assert!(methods.password && methods.identities.is_empty());
    refused(
        "unlink_identity of none",
        third.unlink_identity(i64::MAX).await,
    );

    let named = third
        .update_profile(ProfileUpdate {
            display_name: Some("  Live   Check ".into()),
            about: None,
        })
        .await
        .unwrap();
    assert_eq!(named.display_name.as_deref(), Some("Live Check"));
    let about = third
        .update_profile(ProfileUpdate {
            display_name: None,
            about: Some("I check things.".into()),
        })
        .await;
    if public {
        assert_eq!(about.unwrap().about.as_deref(), Some("I check things."));
    } else {
        let refusal = refused("update_profile's about", about);
        assert_eq!(refusal, (403, "profiles_off".to_owned()));
    }
    let cleared = third
        .update_profile(ProfileUpdate {
            display_name: Some(String::new()),
            about: Some(" ".into()),
        })
        .await
        .unwrap();
    assert_eq!((cleared.display_name, cleared.about), (None, None));

    let picture = scratch("avatar.png");
    std::fs::write(&picture, png(32, 32, seed ^ 7)).unwrap();
    let avatar = third
        .set_avatar(
            picture.to_string_lossy().into_owned(),
            Some(AvatarCrop {
                x: 4,
                y: 4,
                size: 16,
            }),
        )
        .await;
    let _ = std::fs::remove_file(&picture);
    if public {
        let profile = avatar.unwrap();
        assert!(profile.avatar_url.unwrap().starts_with(&base));
    } else {
        let refusal = refused("set_avatar", avatar);
        assert_eq!(refusal, (403, "profiles_off".to_owned()));
    }
    let plain = third.remove_avatar().await.unwrap();
    assert_eq!(plain.avatar_url, None);
    let forum = third.forum().await;
    if !public {
        refused("forum for an account", forum);
    }

    let id = signed.user.id;
    Client::new(&base)
        .unwrap()
        .with_token(admin_token(admin))
        .delete_user(id)
        .await
        .unwrap();
    made.lock().unwrap().user = None;
    signed_out("a deleted account", third.me().await);
    println!(
        "throwaway account {name} (#{id}): password changed, two sessions signed out from the \
         third, display name set and cleared, picture removed, account deleted"
    );
}

/// The token an `Api` goes out with, for the admin calls this core doesn't
/// wrap.
fn admin_token(api: &Api) -> Option<String> {
    api.client.token().map(ToOwned::to_owned)
}

async fn checks(admin: Arc<Api>, made: Arc<Mutex<Made>>) {
    let base = admin.server();
    let info = admin.info().await.unwrap();
    println!(
        "server {} {} ({:?}, link previews {}, oidc {:?})",
        info.name, info.version, info.mode, info.link_previews, info.oidc
    );
    let seed = chrono::Utc::now().timestamp_nanos_opt().unwrap_or_default() as u64;
    let existing = admin.posts(String::new(), None, 1, 2).await.unwrap().posts;
    assert!(existing.len() == 2, "the server needs two posts to share");

    sessions(&admin, &base, &made).await;
    api_keys(&admin, &base, existing[0].id, &made).await;
    sharing(&admin, &info, &existing, &made).await;
    people(&admin).await;

    // A tag in use that the figures below don't give or take.
    let tag = admin
        .tags(String::new(), 20)
        .await
        .unwrap()
        .into_iter()
        .map(|t| t.name)
        .find(|t| t != "long_hair" && !starbooru_core::is_count_tag(t))
        .unwrap();
    let a = upload(&admin, "look-alike-a", png(64, 64, seed), vec![tag.clone()]).await;
    made.lock().unwrap().posts.push(a.id);
    let b = upload(&admin, "look-alike-b", png(64, 64, seed ^ 1), Vec::new()).await;
    made.lock().unwrap().posts.push(b.id);
    assert_ne!(a.sha256, b.sha256);

    duplicates(&admin, &a, &b).await;
    figures(&admin, &a).await;
    ai(&admin, &a).await;
    editor(&admin, &a, &tag, &made).await;
    tag_vote(&admin, &a, &tag).await;
    forum(&admin, &info, &made).await;
    throwaway_account(&admin, &info, seed, &made).await;
}

/// Puts back what the checks made or changed.
async fn clean_up(admin: &Api, made: &Made) {
    if let Some(previews) = made.sharing {
        let _ = admin.set_sharing(SharingSettings { previews }).await;
    }
    for id in &made.shares {
        let _ = admin.delete_share(*id).await;
    }
    for id in &made.keys {
        let _ = admin.delete_api_key(*id).await;
    }
    for token in &made.tokens {
        if let Ok(api) = Api::new(server(), Some(token.clone())) {
            let _ = api.sign_out().await;
        }
    }
    for id in &made.threads {
        let _ = admin.delete_forum_thread(*id).await;
    }
    for id in &made.categories {
        let _ = admin.delete_forum_category(*id).await;
    }
    if let (Some(id), Ok(client)) = (made.user, Client::new(&server())) {
        let gone = client.with_token(admin_token(admin)).delete_user(id).await;
        println!(
            "clean-up: throwaway account #{id} deleted: {}",
            gone.is_ok()
        );
    }
    for id in &made.posts {
        let _ = admin.delete_post(*id).await;
        match admin.purge_post(*id).await {
            Ok(()) => println!("clean-up: purged #{id}"),
            Err(e) => println!("clean-up: #{id} not purged: {e}"),
        }
    }
}

#[tokio::test]
#[ignore = "needs a StarBooru server with admin/admin at STARBOORU_LIVE_SERVER or 127.0.0.1:8080"]
async fn account_sharing_people_and_public_calls_against_a_real_server() {
    let guest = Api::new(server(), None).unwrap();
    let session = guest.sign_in("admin".into(), "admin".into()).await.unwrap();
    let admin = Api::new(server(), Some(session.token)).unwrap();
    let made = Arc::new(Mutex::new(Made::default()));

    // The checks run as a task, so a failed one still gets cleaned up after.
    let outcome = tokio::spawn(checks(admin.clone(), made.clone())).await;
    let made = std::mem::take(&mut *made.lock().unwrap());
    clean_up(&admin, &made).await;
    admin.sign_out().await.unwrap();
    if let Err(e) = outcome {
        std::panic::resume_unwind(e.into_panic());
    }
}
