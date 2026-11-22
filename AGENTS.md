# AGENTS.md: guide for AI agents and contributors

starbooru-android is the StarBooru app for Android. The interface is Kotlin and Jetpack Compose (Material 3). Underneath, a Rust core talks to [starbooru-server](https://github.com/StarBooru/starbooru-server) through the same crates as the other apps, and reaches Kotlin through [UniFFI](https://mozilla.github.io/uniffi-rs/).

There is no code yet, only these docs. Read this file first, then `docs/`.

## 1. Orientation

| Read | Why |
|---|---|
| `docs/architecture.md` | What lives in Rust and what in Kotlin, the layout, the bridge |
| `docs/development.md` | Toolchain, building, running on a phone, tests |
| `docs/design.md` | How to use the archived app as the visual reference |
| `docs/roadmap.md` | What to build, in order |

The API is in starbooru-server's `docs/api.md`. The owner's backlog is its `docs/checklist.md`.

## 2. Related repositories

| Repository | Role |
|---|---|
| [starbooru-server](https://github.com/StarBooru/starbooru-server) | The server, plus `starbooru-core` (API types, search parser, validators) and `starbooru-client` (typed HTTP client) |
| [StarBooru/StarBooru](https://github.com/StarBooru/StarBooru) | The first app, in Dioxus, which ran on Android in a WebView. Archived and read-only: the visual reference. Never push to it. |
| `starbooru-web`, `starbooru-windows`, `starbooru-linux` | The other apps |

## 3. Golden rules

### Never crash

- Rust: no `unwrap()`, `expect()`, `panic!`, `unreachable!`, `todo!` or `unimplemented!` outside tests. Copy the workspace lints and `clippy.toml` from starbooru-server. Our code sets `unsafe_code = "forbid"`; the only `unsafe` is what UniFFI generates.
- Kotlin: no `!!` and no unchecked casts. Errors from the core arrive as typed exceptions; the ViewModel catches them and the screen says what went wrong.
- A slow network, a dead server or a rotated screen never takes the app down.

### Project rules

1. **The API is the contract.** The core uses `starbooru-core` and `starbooru-client` from starbooru-server, pinned to a tag. Kotlin never builds an API request itself. A missing endpoint is added in starbooru-server first.
2. **Rust for what is shared or testable off the device**: the API, the session, search parsing, upload chunks, cache rules. **Kotlin for the device**: screens, the photo picker, sharing, downloads, notifications, background work.
3. **Same package, same key.** The id stays `eu.filipnone.starbooru` and releases are signed with the key in `~/.config/starbooru-release`, so the app updates the old one in place. Never lose or replace that key.
4. **Material You.** Colours come from the wallpaper on Android 12 and later, and from the user's seed hue otherwise. No colour literals in screens.
5. **Verify UI changes visually** on a phone or emulator: light and dark, phone and tablet widths, left and right hand. Before committing anything that changes how the app looks, show the owner a preview and commit only once they confirm.
6. **Tests are the gate.** Rust logic gets unit tests; ViewModels get JVM tests; screens get Compose UI tests where behaviour matters.
7. **Privacy.** Never log tokens, passwords or file contents. Test images are generated or public domain, never personal photos.
8. **Assets.** Third-party assets must be permissively licensed and get a row in `ATTRIBUTION.md`. Never commit font files.
9. **Text** lives in `strings.xml`, English and Italian, taken from the reference's Fluent catalogues.

## 4. Before you finish a task

```sh
cargo fmt --all
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
./gradlew ktlintCheck lint testDebugUnitTest assembleDebug
```

Then append a terse entry to `log/devlog.md`, which stays local (`/log` goes in `.gitignore` with the first code): what landed, what you verified, what's still open. Update `docs/roadmap.md` when an item changes state.

## 5. Notes

- License: AGPL-3.0-or-later, like the server. Add `LICENSE` with the first code.
- The product name is written StarBooru in user-facing text. Machine names stay lowercase.
