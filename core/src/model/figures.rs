//! The people in a picture ("figures"): who they are, where their face is,
//! and their tags by slot. Saving them sets the post's tags to match.

use std::collections::BTreeMap;

use starbooru_client::Client;

/// A face box's edges are thousandths of the picture's width and height.
#[uniffi::export]
pub fn face_scale() -> u16 {
    starbooru_core::FACE_SCALE
}

/// Whether a face box is inside the picture and not empty, as saving needs.
#[uniffi::export]
pub fn face_box_fits(face: FaceBox) -> bool {
    starbooru_core::FaceBox::from(face).is_valid()
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum FigureKind {
    Girl,
    Boy,
    Other,
}

impl From<starbooru_core::FigureKind> for FigureKind {
    fn from(k: starbooru_core::FigureKind) -> Self {
        match k {
            starbooru_core::FigureKind::Girl => FigureKind::Girl,
            starbooru_core::FigureKind::Boy => FigureKind::Boy,
            starbooru_core::FigureKind::Other => FigureKind::Other,
        }
    }
}

impl From<FigureKind> for starbooru_core::FigureKind {
    fn from(k: FigureKind) -> Self {
        match k {
            FigureKind::Girl => starbooru_core::FigureKind::Girl,
            FigureKind::Boy => starbooru_core::FigureKind::Boy,
            FigureKind::Other => starbooru_core::FigureKind::Other,
        }
    }
}

/// Where on a figure a tag goes: clothing from `Head` to `Wear`, then the
/// body.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum FigureSlot {
    /// Hats, hair ornaments, headphones.
    Head,
    /// Tops, dresses, uniforms.
    Body,
    /// Skirts, trousers and legwear.
    Bottom,
    Shoes,
    /// Other clothing and accessories.
    Wear,
    /// Build.
    Shape,
    Eyes,
    Skin,
    Hair,
    Pose,
    /// Expression and anything else about the figure.
    Other,
}

impl From<starbooru_core::Slot> for FigureSlot {
    fn from(s: starbooru_core::Slot) -> Self {
        use starbooru_core::Slot;
        match s {
            Slot::Head => FigureSlot::Head,
            Slot::Body => FigureSlot::Body,
            Slot::Bottom => FigureSlot::Bottom,
            Slot::Shoes => FigureSlot::Shoes,
            Slot::Wear => FigureSlot::Wear,
            Slot::Shape => FigureSlot::Shape,
            Slot::Eyes => FigureSlot::Eyes,
            Slot::Skin => FigureSlot::Skin,
            Slot::Hair => FigureSlot::Hair,
            Slot::Pose => FigureSlot::Pose,
            Slot::Other => FigureSlot::Other,
        }
    }
}

impl From<FigureSlot> for starbooru_core::Slot {
    fn from(s: FigureSlot) -> Self {
        use starbooru_core::Slot;
        match s {
            FigureSlot::Head => Slot::Head,
            FigureSlot::Body => Slot::Body,
            FigureSlot::Bottom => Slot::Bottom,
            FigureSlot::Shoes => Slot::Shoes,
            FigureSlot::Wear => Slot::Wear,
            FigureSlot::Shape => Slot::Shape,
            FigureSlot::Eyes => Slot::Eyes,
            FigureSlot::Skin => Slot::Skin,
            FigureSlot::Hair => Slot::Hair,
            FigureSlot::Pose => Slot::Pose,
            FigureSlot::Other => Slot::Other,
        }
    }
}

/// The tags of one slot of a figure.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SlotTags {
    pub slot: FigureSlot,
    pub tags: Vec<String>,
}

/// Where a face is: from the picture's top-left corner, in thousandths of
/// its width (`x`, `w`) and height (`y`, `h`). See `face_box_fits`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct FaceBox {
    pub x: u16,
    pub y: u16,
    pub w: u16,
    pub h: u16,
}

impl From<starbooru_core::FaceBox> for FaceBox {
    fn from(b: starbooru_core::FaceBox) -> Self {
        Self {
            x: b.x,
            y: b.y,
            w: b.w,
            h: b.h,
        }
    }
}

impl From<FaceBox> for starbooru_core::FaceBox {
    fn from(b: FaceBox) -> Self {
        Self {
            x: b.x,
            y: b.y,
            w: b.w,
            h: b.h,
        }
    }
}

/// One person (or creature) in a picture.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Figure {
    pub kind: FigureKind,
    /// Who they are, as a character tag.
    #[uniffi(default)]
    pub character: Option<String>,
    /// The character they are dressed as, without `_(cosplay)`.
    #[uniffi(default)]
    pub cosplay: Option<String>,
    /// The series they come from. For someone new to the post, `None` lets
    /// the server fill it in from what it knows of the character.
    #[uniffi(default)]
    pub franchise: Option<String>,
    /// Slots in order, each once.
    #[uniffi(default)]
    pub tags: Vec<SlotTags>,
    #[uniffi(default)]
    pub face: Option<FaceBox>,
    /// The crop of the face: an absolute, signed address in a post, a
    /// `data:` URL from `find_people`. Only in answers, a moment after
    /// saving; it is never sent.
    #[uniffi(default)]
    pub face_url: Option<String>,
}

impl Figure {
    pub(crate) fn from_core(f: starbooru_core::Figure, client: &Client) -> Self {
        Self {
            kind: f.kind.into(),
            character: f.character,
            cosplay: f.cosplay,
            franchise: f.franchise,
            tags: f
                .tags
                .into_iter()
                .map(|(slot, tags)| SlotTags {
                    slot: slot.into(),
                    tags,
                })
                .collect(),
            face: f.face.map(Into::into),
            face_url: f.face_url.map(|u| crop_url(&u, client)),
        }
    }
}

impl From<Figure> for starbooru_core::Figure {
    fn from(f: Figure) -> Self {
        let mut tags: BTreeMap<starbooru_core::Slot, Vec<String>> = BTreeMap::new();
        for SlotTags { slot, tags: names } in f.tags {
            tags.entry(slot.into()).or_default().extend(names);
        }
        tags.retain(|_, names| !names.is_empty());
        Self {
            kind: f.kind.into(),
            character: f.character,
            cosplay: f.cosplay,
            franchise: f.franchise,
            tags,
            face: f.face.map(Into::into),
            // The server makes the crop; one sent back is ignored.
            face_url: None,
        }
    }
}

pub(crate) fn figure_list(list: Vec<starbooru_core::Figure>, client: &Client) -> Vec<Figure> {
    list.into_iter()
        .map(|f| Figure::from_core(f, client))
        .collect()
}

/// A kept crop's path made absolute; a preview's `data:` URL as it is.
fn crop_url(url: &str, client: &Client) -> String {
    if url.starts_with("data:") {
        url.to_owned()
    } else {
        client.absolute(url)
    }
}

/// Who `find_people` saw in a picture. Nothing is saved until
/// `set_figures`.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FoundPeople {
    /// Left to right, each with its face box and a preview of its crop.
    pub figures: Vec<Figure>,
    /// The model that found them.
    pub model: String,
}

impl FoundPeople {
    pub(crate) fn from_core(f: starbooru_core::FoundPeople, client: &Client) -> Self {
        Self {
            figures: figure_list(f.figures, client),
            model: f.model,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn client() -> Client {
        Client::new("https://booru.example/sub").unwrap()
    }

    #[test]
    fn a_kept_crop_gets_an_absolute_address_and_a_preview_stays_as_it_is() {
        let found: starbooru_core::FoundPeople = serde_json::from_value(serde_json::json!({
            "model": "YuNet",
            "figures": [
                {"kind": "girl", "franchise": null, "face": {"x": 1, "y": 2, "w": 3, "h": 4},
                 "face_url": "data:image/jpeg;base64,/9j/4AAQ+x="},
                {"kind": "boy", "character": "link", "franchise": "zelda", "face": null,
                 "tags": {"shoes": ["boots"], "hair": ["blonde_hair"]},
                 "face_url": "/media/face/ab.jpg?sig=1"}
            ]
        }))
        .unwrap();
        let found = FoundPeople::from_core(found, &client());
        assert_eq!(found.model, "YuNet");
        let [girl, boy] = &found.figures[..] else {
            panic!("{found:?}")
        };
        assert_eq!(
            girl.face_url.as_deref(),
            Some("data:image/jpeg;base64,/9j/4AAQ+x=")
        );
        assert_eq!(
            girl.face,
            Some(FaceBox {
                x: 1,
                y: 2,
                w: 3,
                h: 4
            })
        );
        assert_eq!(
            boy.face_url.as_deref(),
            Some("https://booru.example/sub/media/face/ab.jpg?sig=1")
        );
        assert_eq!(boy.kind, FigureKind::Boy);
        // Slots come in the server's order: clothing, then the body.
        assert_eq!(
            boy.tags,
            [
                SlotTags {
                    slot: FigureSlot::Shoes,
                    tags: vec!["boots".into()]
                },
                SlotTags {
                    slot: FigureSlot::Hair,
                    tags: vec!["blonde_hair".into()]
                },
            ]
        );
    }

    #[test]
    fn a_figure_sent_back_merges_its_slots_and_leaves_its_crop_out() {
        let figure = Figure {
            kind: FigureKind::Other,
            character: None,
            cosplay: Some("saber".into()),
            franchise: None,
            tags: vec![
                SlotTags {
                    slot: FigureSlot::Hair,
                    tags: vec!["long_hair".into()],
                },
                SlotTags {
                    slot: FigureSlot::Head,
                    tags: Vec::new(),
                },
                SlotTags {
                    slot: FigureSlot::Hair,
                    tags: vec!["braid".into()],
                },
            ],
            face: Some(FaceBox {
                x: 10,
                y: 20,
                w: 30,
                h: 40,
            }),
            face_url: Some("https://booru.example/media/face/ab.jpg".into()),
        };
        let json = serde_json::to_value(starbooru_core::FiguresUpdate {
            figures: vec![figure.into()],
        })
        .unwrap();
        assert_eq!(
            json,
            serde_json::json!({"figures": [{
                "kind": "other", "cosplay": "saber", "franchise": null,
                "tags": {"hair": ["long_hair", "braid"]},
                "face": {"x": 10, "y": 20, "w": 30, "h": 40}
            }]})
        );
    }

    #[test]
    fn kinds_and_slots_map_both_ways() {
        for kind in starbooru_core::FigureKind::ALL {
            assert_eq!(
                starbooru_core::FigureKind::from(FigureKind::from(kind)),
                kind
            );
        }
        for slot in starbooru_core::Slot::ALL {
            assert_eq!(starbooru_core::Slot::from(FigureSlot::from(slot)), slot);
        }
    }

    #[test]
    fn a_face_box_has_to_fit() {
        let at = |x, y, w, h| FaceBox { x, y, w, h };
        assert_eq!(face_scale(), 1000);
        assert!(face_box_fits(at(0, 0, 1000, 1000)));
        assert!(face_box_fits(at(999, 0, 1, 10)));
        assert!(!face_box_fits(at(1, 0, 1000, 10)));
        assert!(!face_box_fits(at(0, 0, 0, 10)));
    }
}
