# Architecture

```text
 Compose screens ── ViewModels ── Kotlin bindings ══ UniFFI ══ Rust core ── HTTPS /api/v1 ──▶ starbooru-server
        │                                                                                         ▲
        └── Coil ─────────────────────── signed /media URLs ──────────────────────────────────────┘
```

API calls go through the Rust core. Pictures and videos load straight from the signed `/media` URLs the API returns, as they need no token.

## Rust core

`core/` is one crate, `starbooru-android-core`, built as a `cdylib` for `arm64-v8a` and `x86_64` with cargo-ndk.

| Crate | Role |
|---|---|
| `starbooru-core`, `starbooru-client` | From starbooru-server, pinned to a tag |
| `uniffi` 0.32 | Generates the Kotlin bindings from `#[uniffi::export]` items |
| `tokio` | The runtime behind `starbooru-client` |

It exports a small, app-shaped API: a `Session` object with async methods (`sign_in`, `posts`, `post`, `upload`, …), plain records for the data, and one error enum. UniFFI turns async functions into Kotlin `suspend` functions and the error enum into a sealed exception class. Panics never cross the bridge: the lints keep them out, and UniFFI turns any that slip through into an exception.

The core keeps no Android types. Paths, the token store and anything else from the device come in as arguments.

## Kotlin app

```text
app/src/main/kotlin/eu/filipnone/starbooru/
  MainActivity.kt   edge to edge, the theme, navigation
  ui/theme/         colour scheme from the wallpaper or the seed hue
  ui/<screen>/      one package per screen: the composables and their ViewModel
  data/             repositories over the core, the preferences (DataStore)
  work/             uploads in WorkManager, so they finish after the app closes
```

- **Navigation.** Compose Navigation, with the reference's places: gallery, explore, post, tags, upload, settings, recycle bin, duplicates, help, administration.
- **Lists.** `LazyVerticalStaggeredGrid` for the gallery, with stable keys from post ids.
- **Media.** Coil 3 with a disk cache keyed by each file's SHA-256, so a picture stays cached when its signed URL changes every 6 hours. Video through Media3 ExoPlayer.
- **Motion.** Shared element transitions from a tile into the viewer and back, predictive back, and gestures that follow the finger.
- **Token.** Kept encrypted with a key in the Android Keystore, never in plain preferences.
- **Sign-in with a provider.** A Custom Tab opens the provider and the answer comes back on a loopback listener, as the server's `return_to` rule allows.
