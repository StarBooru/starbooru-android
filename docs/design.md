# Design

The archived [StarBooru](https://github.com/StarBooru/StarBooru) app is the visual reference: how each page looks, what it holds and how it moves. It is read-only. Clone it to look, to run it, or to take files from it.

| In the reference | For |
|---|---|
| `docs/showcase/phone-*.webp` | The gallery, viewer and explore on a phone, light and dark |
| `apps/app/assets/main.css` | The colour roles from one seed hue, radii, easing and durations |
| `apps/app/src/pages/` | What each page holds and does |
| `apps/app/src/motion.rs`, `gesture.rs`, `taps.rs` | How things move: a post grows out of its tile, swipes follow the finger 1:1, a tap right after a swipe acts at once |
| `apps/app/locales/` | The UI text in English and Italian |
| `apps/app/android-res/` | The launcher icon, with the monochrome layer for themed icons |
| `docs/branding.md`, `docs/brand/` | The logo, its two cuts and its colours |
| `docs/roadmap.md` › Android app | What the Android build did when the apps split |

## What carries over

- The layout: a three-column grid, the navigation at the bottom on phones and at the side on tablets, a viewer stacked under the picture.
- The *Hand* setting, which moves the rail, the viewer's panel and its zoom tools to one side.
- Dynamic colour from the wallpaper by default, a fixed accent as the alternative.
- Glass only on the bars and the viewer's floating controls, and gone with reduced effects.
- The battery saver: no autoplay, no prefetching of the next post, shorter animations.
- The icons and their attribution rows (MingCute and the others in the reference's `ATTRIBUTION.md`).

## What changes

The reference was a web page in a WebView, so its gestures and animations were JavaScript fighting the page. Here they are Compose: real shared element transitions, predictive back, and touch handled before anything else on the frame. Use Material 3 components where one fits, and keep StarBooru's shapes and motion on top. Anything that looks different from the reference on purpose goes past the owner first.
