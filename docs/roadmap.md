# Roadmap

_Status as of 2026-11-27: Foundation (v0.1.0) and Daily use (v0.2.0) done. Scope comes from starbooru-server's `docs/checklist.md` › android app. starbooru-web already has everything the server offers an app (its `docs/roadmap.md` and `docs/architecture.md`), so this app follows it screen by screen and adds what a phone or a tablet does better. Items marked (server) need starbooru-server first._

## Phones and tablets

The app is built for touch, on phones, tablets and every window in between: split screen, a foldable open or closed, a desktop window. Layouts follow the window's width in Material 3's size classes, never the kind of device. An app targeting Android 16 can't lock its orientation or size on screens 600 dp and wider, so no screen may assume it is upright or full size.

| Window width | For example | Navigation | Viewer | Settings, Tags, the forum, the boorus |
|---|---|---|---|---|
| Under 600 dp | A phone held upright, half a split screen | A bar at the bottom | The details under the picture | One page at a time, with a Back |
| 600 to 839 dp | A small tablet or an open foldable held upright, a phone on its side | A floating rail on the hand's side | The details under the picture | One page at a time |
| 840 dp and up | A tablet on its side, a big phone on its side, a desktop window | A floating rail on the hand's side | The details beside the picture, on the hand's side | The list beside the open page |

The gallery's columns follow the width as on the web, or the number the user picks. The *Hand* setting (either, left or right) moves the rail, the viewer's panel and its zoom tools to its side, and the viewer's Back to the other. On a phone, the gallery's arrow back to the top sits above the bar on that side, under the thumb.

An item below is done when it works at all three widths, with either hand, in light and dark, and keeps its place through a rotation, a fold and a resize.

## 1. Foundation

- [x] Gradle project, the Rust core with UniFFI, lints, CI that builds and tests the debug APK
- [x] The frame: edge to edge, the bar or the rail by width, predictive back, and the Hand setting that later screens place their controls by
- [x] State that survives a rotation, a fold, split screen, a resize and the system closing the app in the background: the page, its scroll, a half-typed search or tag
- [x] Theme: the wallpaper's colours on Android 12 and later, the seed hue otherwise; light, dark and system, changed without a restart (the reference restarted); the reference's filled, rounded icons
- [x] Sign-in with a password and through the identity provider, the token in the Keystore; private and public servers from `/info`, and guests where a public server takes them
- [x] Gallery: the staggered grid read left to right, infinite scroll, sorts, the web's tile shapes, each tile in its picture's colour until the thumbnail arrives
- [x] Viewer: image, GIF and video in the web's player design; pinch and double-tap zoom; swipes that follow the finger to the previous and next post; pull down to close; the post grows out of its tile and shrinks back into it

## 2. Daily use

- [x] Search in the top bar with tag autocomplete, typo correction and the cheat sheet
- [x] A tile's menu on a long press, swipes on tiles chosen per direction, a drag across tiles to select several, mass edit
- [x] Tag editor with chips and a new tag's category, the Tags page and each tag's page
- [x] Explore: recently viewed, People, On this day and random picks; Trending, Popular tags and Popular characters on a public server; the top bar's search narrows it
- [x] Saved searches that reopen where they were left, votes as likes or stars, related posts in one row to run a thumb along
- [x] Upload from the photo picker, a folder, the share sheet, links and the clipboard, in chunks under WorkManager with a progress notification, finishing after the app closes (the reference couldn't)
- [x] Download a post or a selection to the phone's gallery, never over a file of the same name, with a notice that opens it
- [x] Recycle bin
- [x] Media cache on disk keyed by SHA-256, with its limit, size and Clear in Settings. Offline, what's cached still opens and each page says it can't reach the server
- [x] Rate limits: `starbooru-client` waits out a short `429`; a longer one says when to try again

## 3. Everything else the web app has

- [x] Settings in the web's seven groups, and the top bar's search finding settings
- [ ] Share links, with a custom expiry and link previews, through Android's share sheet, which also takes the file itself; a public server shares a post's own address
- [x] A StarBooru address shared to the app opens there: a post, a search or a share page
- [ ] People: the card, faces outlined on the picture, the editor with face boxes drawn by finger, Find people, the people page
- [ ] Image and video editor: crop handles sized for a finger, straightening, turns, flips, trims
- [ ] Other boorus and library imports
- [ ] Duplicate finder
- [x] Reverse search on Google Lens, iqdb, SauceNAO and trace.moe
- [ ] Public servers: the account button, profiles and their picture's crop, the forum, votes on tags
- [ ] Tag blacklist, with covered posts in the viewer
- [ ] The home page and its drifting background
- [ ] Administration, the server's updates included
- [x] Privacy and terms pages

## 4. Phones

- [ ] Fullscreen in the viewer hides the system bars; a landscape video in fullscreen turns the screen to landscape
- [ ] Picture-in-picture for a video when the app is left, and the screen stays on while one plays
- [ ] Haptics on a long press, on the edges of a drag selection and where letting go would close the viewer, following the system's touch feedback setting

## 5. Tablets, foldables and desktop windows

- [ ] Foldables: nothing sits under the hinge. Half folded on a table, the picture or video sits above the fold and its controls below
- [ ] Keyboard: the web's shortcuts (the arrows, J and L, +, - and 0, E, F, Esc), Ctrl+V on Upload, Tab through every control
- [ ] Mouse and trackpad: right-click for the menus, wheel and trackpad zoom, hover states, a video tile's preview on hover
- [ ] Drag and drop between apps in split screen: files and links dropped on the gallery or Upload are uploaded, and a post dragged out gives its file
- [ ] Desktop windows (ChromeOS, Samsung DeX, Android's desktop mode) at any size, resized live

## 6. Android

- [ ] Originals on dense screens, samples elsewhere: the viewer shows the sample only while it has every pixel the screen can show, and the original once zoomed in
- [ ] Effects (automatic, full, reduced): glass on the bars and the viewer's floating controls from Android 12, plain surfaces on older versions; automatic drops the glass and the glows on a phone that keeps missing frames
- [ ] Battery saver (automatic, always, never): automatic follows the system's battery saver and Data Saver. While on, no autoplay, no prefetching of the next post, no originals for a dense screen and shorter animations. Videos pause whenever the app is hidden
- [ ] Sweet animations: the reference's springs, shared elements, predictive back that shows where it leads; the system's Remove animations turns them off
- [ ] Smooth: a baseline profile, and the gallery keeping up with the screen's refresh rate over thousands of posts, checked on a low-end phone
- [ ] Accessibility: TalkBack names every control, text scales to 200%, touch targets are at least 48 dp
- [ ] English and Italian from the web's catalogues, with a test that keeps `strings.xml` in step; the language can also be set in Android's settings for the app
- [ ] Updates in the app: a new release is offered with what's new, downloaded, checked and installed through the system's installer
- [ ] Sync between devices (server)
- [ ] Forum notifications (server)

## 7. Release

- [ ] Signed APKs on GitHub Releases, with the reference's package and key, so they update its Android app in place
- [ ] The update keeps the server, the session and the settings the reference left in the app's files
- [ ] The first release waits until the app does all the reference did on Android, since it replaces it

## Ideas

Looked at once the rest is done, and past the owner first.

- The search box at the bottom on a phone, within the thumb's reach (the reference has it at the top)
- Pull down on the gallery to refresh it
- Launcher shortcuts: Upload, Search, Explore
- A home screen widget with On this day or random posts
- A post as the wallpaper
- The gallery and the viewer side by side on a wide tablet
- A post in a second window
- F-Droid or IzzyOnDroid, for updates through a store
