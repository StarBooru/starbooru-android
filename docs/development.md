# Development

## Toolchain

- Rust 1.88 or newer, with `rustup target add aarch64-linux-android x86_64-linux-android`.
- cargo-ndk 4: `cargo install cargo-ndk --locked`.
- JDK 21, the Android SDK and NDK, with `ANDROID_HOME` and `ANDROID_NDK_HOME` set.
- A local starbooru-server (its `docs/development.md` › Run the server).

## Build

The Gradle build runs cargo-ndk for each ABI, then UniFFI's bindgen for the Kotlin side, before it compiles the app:

```sh
./gradlew assembleDebug          # arm64-v8a and x86_64
./gradlew assembleRelease        # unsigned; see Releasing
```

## Run on a phone

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk   # -r keeps the app's data
adb shell am start -n eu.filipnone.starbooru/.MainActivity
adb reverse tcp:8085 tcp:8080    # the test phone's app points at http://127.0.0.1:8085
```

- Start the app with `am start`, never `adb shell monkey`: monkey sends a random input event, which once pasted the clipboard into the server field.
- `adb reverse` is lost when the phone is unplugged.
- A dozing phone screenshots black: `adb shell input keyevent KEYCODE_WAKEUP`, then `adb shell wm dismiss-keyguard`.
- Without a phone, [Waydroid](https://waydro.id) works as the device (`adb connect <its IP>:5555`, x86_64 build).

## Tests

- `cargo test --workspace` for the core.
- `./gradlew testDebugUnitTest` for ViewModels and repositories, with a fake core behind the same interface.
- `./gradlew connectedDebugAndroidTest` for Compose UI tests, on a device or emulator.

Check by hand, on a real phone, what tests can't reach: back at every step down to closing the app, a tap right after a swipe, a picture slower than 10 s to load, and switching the system theme while the app is open (the reference restarted there).

## Releasing

Releases are signed on the owner's machine, so the key never reaches GitHub:

```sh
K=~/.config/starbooru-release
zipalign -f -p 4 app-release-unsigned.apk aligned.apk
apksigner sign --ks "$K/starbooru-release.jks" --ks-pass "file:$K/keystore-password" \
  --ks-key-alias starbooru --out StarBooru-<version>-android-arm64.apk aligned.apk
apksigner verify --print-certs StarBooru-<version>-android-arm64.apk
```

The certificate's SHA-256 must be `fb4481c6c698fb00a43085ce002f79baf14f7df0ba0de3e009cf02ebda31de16`. Android refuses an update signed with any other key, and the only way past that is to uninstall, which deletes the user's settings.
