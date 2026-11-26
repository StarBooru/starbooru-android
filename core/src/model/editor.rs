//! The image and video editor. An edit never changes a file: the server
//! saves the result as a new post with the original's tags, rating, source
//! and people, and can move the original to the recycle bin.

use super::MediaKind;

/// What the editor offers for a post.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum EditKind {
    /// Turn, flip, straighten, crop.
    Image,
    /// Trim.
    Video,
}

impl From<starbooru_core::EditKind> for EditKind {
    fn from(k: starbooru_core::EditKind) -> Self {
        match k {
            starbooru_core::EditKind::Image => EditKind::Image,
            starbooru_core::EditKind::Video => EditKind::Video,
        }
    }
}

impl From<EditKind> for starbooru_core::EditKind {
    fn from(k: EditKind) -> Self {
        match k {
            EditKind::Image => starbooru_core::EditKind::Image,
            EditKind::Video => starbooru_core::EditKind::Video,
        }
    }
}

/// The editor for a post, or `None` for one that can't be edited (GIF,
/// AVIF, JPEG XL). A video also needs `ServerInfo::video_editing`.
#[uniffi::export]
pub fn edit_kind(kind: MediaKind, mime: &str) -> Option<EditKind> {
    starbooru_core::edit_kind(kind.into(), mime).map(Into::into)
}

/// The furthest an image straightens either way, in tenths of a degree.
#[uniffi::export]
pub fn max_straighten() -> i16 {
    starbooru_core::MAX_STRAIGHTEN
}

/// The shortest clip a trim may leave.
#[uniffi::export]
pub fn min_trim_ms() -> i64 {
    starbooru_core::edit::MIN_TRIM_MS
}

/// A rectangle in pixels of the turned and flipped image.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct Crop {
    pub x: u32,
    pub y: u32,
    pub width: u32,
    pub height: u32,
}

/// The part of a video to keep.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct Trim {
    pub start_ms: i64,
    pub end_ms: i64,
}

/// One edit, applied in this order: turn, flip, straighten, crop. A video
/// takes only `trim`.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, uniffi::Record)]
pub struct MediaEdit {
    /// Clockwise degrees: 0, 90, 180 or 270.
    #[uniffi(default)]
    pub rotate: u16,
    /// Mirror left to right, after turning.
    #[uniffi(default)]
    pub flip_h: bool,
    /// Mirror top to bottom, after turning.
    #[uniffi(default)]
    pub flip_v: bool,
    /// A small clockwise turn after the flips, in tenths of a degree, up to
    /// `max_straighten()` either way. The picture grows by
    /// `straighten_scale` to fill its frame.
    #[uniffi(default)]
    pub straighten: i16,
    #[uniffi(default)]
    pub crop: Option<Crop>,
    #[uniffi(default)]
    pub trim: Option<Trim>,
    /// Move the original to the recycle bin once the edit is saved; needs
    /// the right to delete it.
    #[uniffi(default)]
    pub bin_original: bool,
}

impl From<MediaEdit> for starbooru_core::MediaEdit {
    fn from(e: MediaEdit) -> Self {
        Self {
            rotate: e.rotate,
            flip_h: e.flip_h,
            flip_v: e.flip_v,
            straighten: e.straighten,
            crop: e.crop.map(|c| starbooru_core::Crop {
                x: c.x,
                y: c.y,
                width: c.width,
                height: c.height,
            }),
            trim: e.trim.map(|t| starbooru_core::Trim {
                start_ms: t.start_ms,
                end_ms: t.end_ms,
            }),
            bin_original: e.bin_original,
        }
    }
}

impl From<starbooru_core::MediaEdit> for MediaEdit {
    fn from(e: starbooru_core::MediaEdit) -> Self {
        Self {
            rotate: e.rotate,
            flip_h: e.flip_h,
            flip_v: e.flip_v,
            straighten: e.straighten,
            crop: e.crop.map(|c| Crop {
                x: c.x,
                y: c.y,
                width: c.width,
                height: c.height,
            }),
            trim: e.trim.map(|t| Trim {
                start_ms: t.start_ms,
                end_ms: t.end_ms,
            }),
            bin_original: e.bin_original,
        }
    }
}

/// `edit` turned a quarter more, clockwise or not: the flips swap with it
/// and the crop is dropped, as the server reads them.
#[uniffi::export]
pub fn turn_edit(edit: MediaEdit, clockwise: bool) -> MediaEdit {
    let mut edit = starbooru_core::MediaEdit::from(edit);
    edit.turn(clockwise);
    edit.into()
}

/// How much a `width` × `height` frame's picture grows once straightened,
/// so the turned picture still covers the frame; 1 when it isn't.
#[uniffi::export]
pub fn straighten_scale(edit: MediaEdit, width: u32, height: u32) -> f64 {
    starbooru_core::MediaEdit::from(edit).straighten_scale(width, height)
}

/// Why the server would refuse an edit.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum EditProblem {
    NotEditable,
    NoChange,
    /// A turn other than a quarter, or straightened too far.
    BadRotation,
    CropOutside,
    /// A trim on an image, or a turn, flip or crop on a video.
    WrongKind,
    TrimOutside,
    TrimTooShort,
}

impl From<starbooru_core::EditError> for EditProblem {
    fn from(e: starbooru_core::EditError) -> Self {
        use starbooru_core::EditError;
        match e {
            EditError::NotEditable => EditProblem::NotEditable,
            EditError::NoChange => EditProblem::NoChange,
            EditError::BadRotation => EditProblem::BadRotation,
            EditError::CropOutside => EditProblem::CropOutside,
            EditError::WrongKind => EditProblem::WrongKind,
            EditError::TrimOutside => EditProblem::TrimOutside,
            EditError::TrimTooShort => EditProblem::TrimTooShort,
        }
    }
}

/// What is wrong with `edit` for a post of `kind`, `width` × `height` as
/// shown and `duration_ms` long, or `None` when the server will take it.
#[uniffi::export]
pub fn edit_problem(
    edit: MediaEdit,
    kind: EditKind,
    width: u32,
    height: u32,
    duration_ms: Option<i64>,
) -> Option<EditProblem> {
    starbooru_core::MediaEdit::from(edit)
        .check(kind.into(), width, height, duration_ms)
        .err()
        .map(Into::into)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn an_edit_sends_what_the_server_reads() {
        let edit = MediaEdit {
            rotate: 90,
            flip_h: true,
            straighten: -25,
            crop: Some(Crop {
                x: 1,
                y: 2,
                width: 30,
                height: 40,
            }),
            bin_original: true,
            ..MediaEdit::default()
        };
        let json = serde_json::to_value(starbooru_core::MediaEdit::from(edit)).unwrap();
        assert_eq!(
            json,
            serde_json::json!({
                "rotate": 90, "flip_h": true, "flip_v": false, "straighten": -25,
                "crop": {"x": 1, "y": 2, "width": 30, "height": 40}, "trim": null,
                "bin_original": true
            })
        );
        let trim = MediaEdit {
            trim: Some(Trim {
                start_ms: 100,
                end_ms: 900,
            }),
            ..MediaEdit::default()
        };
        let json = serde_json::to_value(starbooru_core::MediaEdit::from(trim)).unwrap();
        assert_eq!(
            json["trim"],
            serde_json::json!({"start_ms": 100, "end_ms": 900})
        );
    }

    #[test]
    fn a_turn_swaps_the_flips_and_drops_the_crop() {
        let edit = MediaEdit {
            rotate: 270,
            flip_h: true,
            crop: Some(Crop {
                x: 0,
                y: 0,
                width: 1,
                height: 1,
            }),
            ..MediaEdit::default()
        };
        let turned = turn_edit(edit, true);
        assert_eq!(turned.rotate, 0);
        assert!(!turned.flip_h && turned.flip_v);
        assert_eq!(turned.crop, None);
        assert_eq!(turn_edit(MediaEdit::default(), false).rotate, 270);
    }

    #[test]
    fn straightening_grows_the_picture() {
        assert_eq!(straighten_scale(MediaEdit::default(), 400, 300), 1.0);
        let tilted = MediaEdit {
            straighten: 100,
            ..MediaEdit::default()
        };
        assert!(straighten_scale(tilted, 400, 300) > 1.1);
        assert_eq!(max_straighten(), 450);
        assert_eq!(min_trim_ms(), 100);
    }

    #[test]
    fn says_what_the_server_would_refuse() {
        let image = |edit| edit_problem(edit, EditKind::Image, 400, 300, None);
        assert_eq!(image(MediaEdit::default()), Some(EditProblem::NoChange));
        let turned = MediaEdit {
            rotate: 90,
            ..MediaEdit::default()
        };
        assert_eq!(image(turned), None);
        let outside = MediaEdit {
            rotate: 90,
            crop: Some(Crop {
                x: 0,
                y: 0,
                width: 400,
                height: 300,
            }),
            ..MediaEdit::default()
        };
        // Turned, the picture is 300 wide.
        assert_eq!(image(outside), Some(EditProblem::CropOutside));
        let odd = MediaEdit {
            rotate: 45,
            ..MediaEdit::default()
        };
        assert_eq!(image(odd), Some(EditProblem::BadRotation));
        let trim = |start_ms, end_ms| MediaEdit {
            trim: Some(Trim { start_ms, end_ms }),
            ..MediaEdit::default()
        };
        assert_eq!(image(trim(0, 10)), Some(EditProblem::WrongKind));
        let video = |edit| edit_problem(edit, EditKind::Video, 640, 360, Some(5000));
        assert_eq!(video(trim(100, 4000)), None);
        assert_eq!(video(trim(0, 6000)), Some(EditProblem::TrimOutside));
        assert_eq!(video(trim(0, 50)), Some(EditProblem::TrimTooShort));
        assert_eq!(video(trim(0, 5000)), Some(EditProblem::NoChange));
        assert_eq!(video(turned), Some(EditProblem::WrongKind));
    }

    #[test]
    fn only_some_files_can_be_edited() {
        assert_eq!(
            edit_kind(MediaKind::Image, "image/jpeg"),
            Some(EditKind::Image)
        );
        assert_eq!(
            edit_kind(MediaKind::Video, "video/mp4"),
            Some(EditKind::Video)
        );
        assert_eq!(edit_kind(MediaKind::Animation, "image/gif"), None);
        assert_eq!(edit_kind(MediaKind::Image, "image/avif"), None);
        for kind in [EditKind::Image, EditKind::Video] {
            assert_eq!(EditKind::from(starbooru_core::EditKind::from(kind)), kind);
        }
    }
}
