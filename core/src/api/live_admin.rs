//! A check of the administration, other boorus, imports, library imports
//! and the blacklist against a real server, run by hand with the other live
//! checks:
//!
//! ```sh
//! cargo test -p starbooru-android-core -- --ignored --nocapture
//! ```
//!
//! It signs in as admin/admin at `STARBOORU_LIVE_SERVER` (or
//! `http://127.0.0.1:8080`) and mostly reads. What it writes it writes back
//! as it was (the settings, admin's blacklist), or makes and removes (a
//! throwaway account, a saved search on another booru, an account for a
//! site that doesn't exist), even when a check fails. It never switches the
//! mode, makes or deletes a backup, installs anything or asks GitHub. A
//! call that would change something goes only to an id or a name that
//! doesn't exist, to see the refusal.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use super::live::server;
use super::*;
use crate::blacklist::{blacklist_join, blacklist_split, blacklist_still_hidden};
use crate::error::CoreError;
use crate::jobs::{imports_by_source, imports_poll_every};
use crate::model::{
    AiMode, BooruApi, ImportItem, LibrarySite, LibrarySource, Mode, NewBooruAccount,
    NewRemoteSearch, QuotaChange, RemoteSearch, RemoteSite, Role, ScoreKind, ServerSettings,
    UserUpdate,
};

/// What the checks made or changed, to put back.
#[derive(Default)]
struct Made {
    /// The settings as they were, while a write of them is out.
    settings: Option<ServerSettings>,
    /// Admin's blacklist as it was, while a write of it is out.
    blacklist: Option<Vec<String>>,
    user: Option<i64>,
    remote_searches: Vec<i64>,
    booru_accounts: Vec<i64>,
    imports: Vec<i64>,
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

async fn stats_and_users(admin: &Api, info: &ServerInfo) {
    let stats = admin.admin_stats().await.unwrap();
    assert_eq!(stats.version, info.version);
    assert_eq!(stats.posts, stats.images + stats.animations + stats.videos);
    assert_eq!(stats.uploads_by_day.len(), 30);
    let days = &stats.uploads_by_day;
    assert!(
        days.windows(2)
            .all(|w| w[1].start_ms - w[0].start_ms == 86_400_000)
    );
    assert!(stats.started_at_ms > 0);
    println!(
        "stats: {} posts ({} images, {} animations, {} videos), {} in the bin, {} tags, {} \
         users ({} disabled), {} MB of media, {} uploads in 30 days from {}",
        stats.posts,
        stats.images,
        stats.animations,
        stats.videos,
        stats.bin_posts,
        stats.tags,
        stats.users,
        stats.disabled_users,
        stats.media_bytes / 1_000_000,
        days.iter().map(|d| d.posts).sum::<u64>(),
        days.first().map(|d| d.day.as_str()).unwrap_or_default()
    );

    let users = admin.admin_users().await.unwrap();
    let me = users.iter().find(|u| u.username == "admin").unwrap();
    assert_eq!(me.role, Role::Admin);
    assert!(
        users
            .iter()
            .flat_map(|u| &u.avatar_url)
            .all(|a| a.starts_with(&admin.server()))
    );
    println!(
        "users: {:?}",
        users
            .iter()
            .map(|u| (u.id, u.username.as_str(), u.role, u.posts))
            .collect::<Vec<_>>()
    );
    // An admin can't take their own role away, or change it.
    let own = admin
        .update_user(
            me.id,
            UserUpdate {
                role: Some(Role::Admin),
                ..UserUpdate::default()
            },
        )
        .await;
    assert_eq!(refused("update_user of oneself", own).0, 400);
    let gone = refused("delete_user of oneself", admin.delete_user(me.id).await);
    assert_eq!(gone.0, 400);
}

async fn settings(admin: &Api, info: &ServerInfo, made: &Mutex<Made>) {
    let settings = admin.admin_settings().await.unwrap();
    assert_eq!(settings.name, info.name);
    assert_eq!(settings.mode, info.mode);
    assert!(settings.default_blacklist.is_some() && settings.home.is_some());
    assert_eq!(settings.link_previews, Some(info.link_previews));
    made.lock().unwrap().settings = Some(settings.clone());
    let kept = admin.set_admin_settings(settings.clone()).await.unwrap();
    assert_eq!(kept, settings);
    made.lock().unwrap().settings = None;
    println!("settings: {settings:?}, written back unchanged");

    let other = match info.mode {
        Mode::Private => Mode::Public,
        Mode::Public => Mode::Private,
    };
    let preview = admin.mode_preview(other).await.unwrap();
    assert_eq!(preview.to, other);
    let same = admin.mode_preview(info.mode).await.unwrap();
    assert!(same.changes.is_empty(), "{same:?}");
    println!(
        "going {other:?} would: {:?}",
        preview
            .changes
            .iter()
            .map(|c| (c.kind.as_str(), c.count, c.text.as_str()))
            .collect::<Vec<_>>()
    );

    let kind = match settings.score_kind {
        ScoreKind::Likes => ScoreKind::Stars,
        ScoreKind::Stars => ScoreKind::Likes,
    };
    let scores = admin.score_preview(kind).await;
    if info.mode == Mode::Public && kind == ScoreKind::Stars {
        let refusal = refused("score_preview to stars on a public server", scores);
        assert_eq!(refusal, (409, "public_counts_likes".to_owned()));
    } else {
        let scores = scores.unwrap();
        assert_eq!(scores.to, kind);
        println!(
            "counting {kind:?} would: {:?}",
            scores
                .changes
                .iter()
                .map(|c| (c.kind.as_str(), c.count))
                .collect::<Vec<_>>()
        );
    }
}

async fn backups_and_updates(admin: &Api, info: &ServerInfo) {
    let base = admin.server();
    let backups = admin.backups().await.unwrap();
    assert!(backups.items.iter().all(|b| b.url.starts_with(&base)));
    println!(
        "backups: available {}, running {}, every {} days keeping {}, {:?}",
        backups.available,
        backups.running,
        backups.schedule.every_days,
        backups.schedule.keep,
        backups
            .items
            .iter()
            .map(|b| (b.name.as_str(), b.bytes, b.automatic))
            .collect::<Vec<_>>()
    );
    let name = format!("live-check-none-{}.dump", std::process::id());
    assert!(backups.items.iter().all(|b| b.name != name));
    let gone = refused("delete_backup of none", admin.delete_backup(name).await);
    assert_eq!(gone.0, 404);

    let status = admin.update_status().await.unwrap();
    assert_eq!(status.current, info.version);
    assert!(status.settings.auto_hour <= 23);
    println!(
        "updates: {} running, latest {:?} (available {}), checked {:?}, error {:?}, {:?} \
         (can update {}, {:?} {:?}), checks {} auto {} at {}:00, server clock UTC{:+} min, \
         run {:?}",
        status.current,
        status.latest.as_ref().map(|l| &l.version),
        status.available,
        status.checked_at_ms,
        status.check_error,
        status.method.kind,
        status.method.can_update,
        status.method.reason,
        status.method.reason_text,
        status.settings.check,
        status.settings.auto,
        status.settings.auto_hour,
        status.server_utc_offset_minutes,
        status.run.as_ref().map(|r| (r.state, r.running, &r.to))
    );
}

async fn ai(admin: &Api) {
    let panel = admin.admin_ai().await.unwrap();
    println!(
        "ai: {} on {} threads, {} MB RAM, gpus {:?}; runtime ready {} ({:?}); autotag {}, \
         translate into {:?}; busy {}",
        panel.hardware.arch,
        panel.hardware.threads,
        panel.hardware.ram_total / 1_000_000,
        panel
            .hardware
            .gpus
            .iter()
            .map(|g| (&g.name, g.kind))
            .collect::<Vec<_>>(),
        panel.runtime.ready,
        panel.runtime.reason,
        panel.autotag,
        panel.auto_translate,
        panel.busy
    );
    for tool in &panel.tools {
        println!(
            "  {:?}: {:?} {:?}, {:?}, active {:?}, {} models ({} installed)",
            tool.tool,
            tool.settings.mode,
            tool.settings.preset,
            tool.state,
            tool.active,
            tool.models.len(),
            tool.models.iter().filter(|m| m.installed).count()
        );
        // Outside API mode the test is refused without a word to anyone.
        if tool.settings.mode != AiMode::Api {
            let test = refused(
                "test_ai_tool outside API mode",
                admin.test_ai_tool(tool.tool).await,
            );
            assert_eq!(test.0, 400);
        }
    }
}

async fn tag_removals_and_text(admin: &Api, post: &Post) {
    let page = admin.tag_removals(1, 20).await.unwrap();
    assert_eq!((page.page, page.limit), (1, 20));
    println!(
        "tag removals: {:?} (more {})",
        page.removals
            .iter()
            .map(|r| (r.post_id, r.tag.as_str(), r.score))
            .collect::<Vec<_>>(),
        page.has_more
    );
    let undo = refused(
        "undo_tag_removal of none",
        admin.undo_tag_removal(i64::MAX).await,
    );
    assert_eq!(undo.0, 404);

    let kept = admin.panel_translations(post.id).await.unwrap();
    println!(
        "translations of #{}: {:?}",
        post.id,
        kept.iter()
            .map(|t| (t.lang.as_str(), t.blocks.len()))
            .collect::<Vec<_>>()
    );
    let missing = refused("rescan_ocr of none", admin.rescan_ocr(i64::MAX).await);
    assert_eq!(missing.0, 404);
    if !admin.ai_status().await.unwrap().translation {
        let off = refused(
            "translate_text with translation off",
            admin.translate_text(post.id, "it".into()).await,
        );
        assert_eq!(off.0, 400);
    }
}

async fn boorus(admin: &Api, info: &ServerInfo, made: &Mutex<Made>) {
    let accounts = admin.booru_accounts().await;
    if info.mode == Mode::Public {
        let off = refused("booru_accounts on a public server", accounts);
        assert_eq!(off, (403, "remote_off".to_owned()));
        return;
    }
    let accounts = accounts.unwrap();
    let searches = admin.remote_searches().await.unwrap();
    println!(
        "other boorus: accounts {:?}, searches {:?}",
        accounts
            .iter()
            .map(|a| (a.api, a.base.as_str(), a.login.as_str(), a.usable))
            .collect::<Vec<_>>(),
        searches
            .iter()
            .map(|s| (s.name.as_str(), s.query.as_str(), s.sites.len()))
            .collect::<Vec<_>>()
    );

    let name = format!("live check {}", std::process::id());
    let saved = admin
        .save_remote_search(NewRemoteSearch {
            name: format!("  {name} "),
            query: " blue_sky ".into(),
            sites: vec![
                RemoteSite {
                    api: BooruApi::Danbooru,
                    base: "https://danbooru.donmai.us/".into(),
                },
                RemoteSite {
                    api: BooruApi::Danbooru,
                    base: "https://danbooru.donmai.us".into(),
                },
            ],
        })
        .await
        .unwrap();
    made.lock().unwrap().remote_searches.push(saved.id);
    assert_eq!(
        (saved.name.as_str(), saved.query.as_str()),
        (name.as_str(), "blue_sky")
    );
    assert_eq!(saved.sites.len(), 1);
    assert!(
        admin
            .remote_searches()
            .await
            .unwrap()
            .iter()
            .any(|s| s.id == saved.id)
    );
    admin.delete_remote_search(saved.id).await.unwrap();
    made.lock().unwrap().remote_searches.clear();
    assert!(
        admin
            .remote_searches()
            .await
            .unwrap()
            .iter()
            .all(|s| s.id != saved.id)
    );
    println!("remote search #{} saved, listed and deleted", saved.id);

    // A site that can't exist, so no account of admin's is replaced.
    let base = format!("https://live-check-{}.invalid", std::process::id());
    assert!(accounts.iter().all(|a| a.base != base));
    let account = admin
        .save_booru_account(NewBooruAccount {
            api: BooruApi::Moebooru,
            base: base.clone(),
            login: " someone ".into(),
            api_key: "not a key".into(),
        })
        .await;
    match account {
        Ok(account) => {
            made.lock().unwrap().booru_accounts.push(account.id);
            assert_eq!((account.login.as_str(), account.usable), ("someone", true));
            admin.delete_booru_account(account.id).await.unwrap();
            made.lock().unwrap().booru_accounts.clear();
            assert!(
                admin
                    .booru_accounts()
                    .await
                    .unwrap()
                    .iter()
                    .all(|a| a.id != account.id)
            );
            println!("booru account #{} for {base} saved and deleted", account.id);
        }
        Err(e) => println!("booru account not saved: {e:?}"),
    }
    let half = admin
        .save_booru_account(NewBooruAccount {
            api: BooruApi::Danbooru,
            base: base.clone(),
            login: String::new(),
            api_key: "key".into(),
        })
        .await;
    if let Ok(a) = &half {
        made.lock().unwrap().booru_accounts.push(a.id);
    }
    assert_eq!(refused("a booru account without a login", half).0, 400);

    // Nothing answers there: a refusal, not a crash.
    let dead = admin
        .remote_search(RemoteSearch {
            api: BooruApi::Danbooru,
            base: "http://127.0.0.1:9".into(),
            tags: "sky".into(),
            page: 1,
            login: None,
            api_key: None,
        })
        .await;
    refused("remote_search of a dead address", dead);
    match admin
        .remote_tags(BooruApi::Danbooru, "http://127.0.0.1:9".into(), "sk".into())
        .await
    {
        Ok(tags) => assert!(tags.is_empty(), "{tags:?}"),
        Err(e) => println!("  remote_tags of a dead address: {e:?}"),
    }
    refused(
        "remote_post of no post",
        admin.remote_post("http://127.0.0.1:9/posts/1".into()).await,
    );
}

async fn imports(admin: &Api, made: &Mutex<Made>) {
    let jobs = admin.imports().await.unwrap();
    let pages = imports_by_source(&jobs);
    println!(
        "imports: {:?}, {} pages, ask again in {} s",
        jobs.iter()
            .map(|j| (j.id, j.state, j.progress.and_then(|p| p.percent)))
            .collect::<Vec<_>>(),
        pages.len(),
        imports_poll_every(&jobs)
    );
    // A batch with one address the server won't fetch is refused whole.
    let queued = admin
        .queue_imports(vec![
            ImportItem {
                url: "ftp://live-check.invalid/a.png".into(),
                tags: Vec::new(),
                rating: None,
                source: None,
                autotag: None,
            },
            ImportItem {
                url: "https://live-check.invalid/b.png".into(),
                tags: vec!["-bad".into()],
                rating: None,
                source: None,
                autotag: Some(false),
            },
        ])
        .await;
    if let Ok(jobs) = &queued {
        made.lock()
            .unwrap()
            .imports
            .extend(jobs.iter().map(|j| j.id));
    }
    assert_eq!(refused("queue_imports of a bad address", queued).0, 400);
    let none = refused("delete_import of none", admin.delete_import(i64::MAX).await);
    assert_eq!(none.0, 404);
}

async fn library(admin: &Api) {
    let site = |base: &str| LibrarySite {
        source: LibrarySource::Szurubooru,
        base: base.into(),
        login: None,
        secret: None,
        unrated: None,
    };
    let bad = refused(
        "check_library of no address",
        admin.check_library(site("nowhere")).await,
    );
    assert_eq!(bad.0, 400);
    match admin.check_library(site("http://127.0.0.1:9")).await {
        Ok(check) => println!("  check_library of a dead address answered {check:?}"),
        Err(e) => println!("  check_library of a dead address: {e:?}"),
    }
    let imports = admin.library_imports().await.unwrap();
    println!(
        "library imports: {:?}",
        imports
            .iter()
            .map(|i| (i.id, i.source, i.state, i.handled, i.total, i.fraction))
            .collect::<Vec<_>>()
    );
    if let Some(first) = imports.first() {
        let failures = admin.library_failures(first.id).await.unwrap();
        println!("  #{} couldn't bring {} posts", first.id, failures.len());
    }
    let none = refused(
        "stop_library_import of none",
        admin.stop_library_import(i64::MAX).await,
    );
    assert_eq!(none.0, 404);
}

async fn blacklist(admin: &Api, info: &ServerInfo, made: &Mutex<Made>) {
    let base = admin.server();
    let entries = admin.blacklist().await.unwrap();
    made.lock().unwrap().blacklist = Some(entries.clone());
    let kept = admin.set_blacklist(entries.clone()).await.unwrap();
    assert_eq!(kept, entries);
    made.lock().unwrap().blacklist = None;
    // The server keeps the tags before the ratings, as the app joins them.
    assert_eq!(blacklist_join(blacklist_split(&entries)), entries);
    println!("admin's blacklist: {entries:?}, written back unchanged");

    let posts = admin.posts(String::new(), None, 1, 5).await.unwrap().posts;
    if let Some(first) = posts.first() {
        let post = admin.post(first.id).await.unwrap();
        let q = post.tags.first().cloned().unwrap_or_default();
        let hidden =
            blacklist_still_hidden(&post.blacklisted, &q, &post.tag_details, HashMap::new());
        assert!(hidden.iter().all(|e| post.blacklisted.contains(e)));
        println!(
            "post #{} matches {:?}; opened from \"{q}\" it is still covered by {hidden:?}",
            post.id, post.blacklisted
        );
    }

    let guest = Api::guest(
        base.clone(),
        Some(vec!["cat".into(), "rating:explicit".into()]),
    )
    .unwrap();
    let page = guest.posts(String::new(), None, 1, 5).await;
    if info.guests {
        let page = page.unwrap();
        println!(
            "a guest hiding cat and explicit sees {} posts",
            page.posts.len()
        );
        let bad = Api::guest(base, Some(vec!["type:video".into()])).unwrap();
        refused(
            "a guest's list with a search term",
            bad.posts(String::new(), None, 1, 5).await,
        );
    } else {
        assert!(
            matches!(page, Err(CoreError::Unauthorized { .. })),
            "a guest of a private server: {page:?}"
        );
        println!("a guest with a list of their own, on a private server: signed out, as it should");
    }
}

fn strong_password(name: &str, seed: u64) -> String {
    (0u64..)
        .map(|n| format!("Lv{:x}-quiet-harbour-{n}", seed.wrapping_add(n)))
        .find(|p| !starbooru_core::password_is_weak(p, name))
        .unwrap()
}

async fn throwaway_account(admin: &Api, info: &ServerInfo, made: &Mutex<Made>) {
    if !info.registration_open {
        println!("throwaway account: registration is closed, so its checks are skipped");
        return;
    }
    let base = admin.server();
    let guest = Api::new(base.clone(), None).unwrap();
    let name = format!("live-admin-{}", std::process::id());
    let seed = chrono::Utc::now().timestamp_nanos_opt().unwrap_or_default() as u64;
    let password = strong_password(&name, seed);
    let signed = guest.register(name.clone(), password).await.unwrap();
    let id = signed.user.id;
    made.lock().unwrap().user = Some(id);
    let mine = Api::new(base.clone(), Some(signed.token)).unwrap();

    let users = admin.admin_users().await.unwrap();
    let listed = users.iter().find(|u| u.id == id).unwrap();
    assert_eq!(
        (listed.role, listed.disabled, listed.quota_bytes),
        (Role::User, false, None)
    );

    let limited = admin
        .update_user(
            id,
            UserUpdate {
                quota: Some(QuotaChange::Limit { bytes: 1 << 20 }),
                ..UserUpdate::default()
            },
        )
        .await
        .unwrap();
    assert_eq!(limited.quota_bytes, Some(1 << 20));
    assert_eq!(mine.usage().await.unwrap().quota_bytes, Some(1 << 20));
    let disabled = admin
        .update_user(
            id,
            UserUpdate {
                disabled: Some(true),
                quota: Some(QuotaChange::Unlimited),
                ..UserUpdate::default()
            },
        )
        .await
        .unwrap();
    assert!(disabled.disabled && disabled.quota_bytes.is_none());
    assert!(matches!(
        mine.me().await,
        Err(CoreError::Unauthorized { .. })
    ));
    let enabled = admin
        .update_user(
            id,
            UserUpdate {
                disabled: Some(false),
                ..UserUpdate::default()
            },
        )
        .await
        .unwrap();
    assert!(!enabled.disabled);

    let new_password = strong_password(&name, seed ^ 0xbeef);
    admin
        .reset_user_password(id, new_password.clone())
        .await
        .unwrap();
    let again = guest.sign_in(name.clone(), new_password).await.unwrap();
    let again = Api::new(base, Some(again.token)).unwrap();
    assert_eq!(again.me().await.unwrap().username, name);
    // Only admins.
    let not_admin = again.admin_stats().await;
    assert_eq!(refused("admin_stats as a user", not_admin).0, 403);

    admin.delete_user(id).await.unwrap();
    made.lock().unwrap().user = None;
    assert!(matches!(
        again.me().await,
        Err(CoreError::Unauthorized { .. })
    ));
    assert!(
        admin
            .admin_users()
            .await
            .unwrap()
            .iter()
            .all(|u| u.id != id)
    );
    println!(
        "throwaway account {name} (#{id}): limited, disabled and back, password reset, signed \
         in, refused the administration, deleted"
    );
}

async fn checks(admin: Arc<Api>, made: Arc<Mutex<Made>>) {
    let info = admin.info().await.unwrap();
    println!(
        "server {} {} ({:?}, guests {}, registration open {})",
        info.name, info.version, info.mode, info.guests, info.registration_open
    );
    let post = admin.posts(String::new(), None, 1, 1).await.unwrap().posts;
    let post = post.first().cloned().expect("the server needs a post");

    stats_and_users(&admin, &info).await;
    settings(&admin, &info, &made).await;
    backups_and_updates(&admin, &info).await;
    ai(&admin).await;
    tag_removals_and_text(&admin, &post).await;
    boorus(&admin, &info, &made).await;
    imports(&admin, &made).await;
    library(&admin).await;
    blacklist(&admin, &info, &made).await;
    throwaway_account(&admin, &info, &made).await;
}

/// Puts back what the checks made or changed.
async fn clean_up(admin: &Api, made: &Made) {
    if let Some(settings) = &made.settings {
        let put = admin.set_admin_settings(settings.clone()).await;
        println!("clean-up: settings put back: {}", put.is_ok());
    }
    if let Some(entries) = &made.blacklist {
        let put = admin.set_blacklist(entries.clone()).await;
        println!("clean-up: blacklist put back: {}", put.is_ok());
    }
    for id in &made.remote_searches {
        let _ = admin.delete_remote_search(*id).await;
    }
    for id in &made.booru_accounts {
        let _ = admin.delete_booru_account(*id).await;
    }
    for id in &made.imports {
        let _ = admin.delete_import(*id).await;
    }
    if let Some(id) = made.user {
        let gone = admin.delete_user(id).await;
        println!(
            "clean-up: throwaway account #{id} deleted: {}",
            gone.is_ok()
        );
    }
}

#[tokio::test]
#[ignore = "needs a StarBooru server with admin/admin at STARBOORU_LIVE_SERVER or 127.0.0.1:8080"]
async fn administration_boorus_imports_and_blacklist_against_a_real_server() {
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
