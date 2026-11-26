//! The viewer's rules that need no device: what the details card says, the
//! name a download gets and how the tags group. It follows the parts of
//! starbooru-web's `logic::viewer` that don't need its i18n or its routes.
//! The words around the numbers live in `strings.xml`.

use std::fmt::Display;

use chrono::{DateTime, FixedOffset, TimeZone, Utc};
use starbooru_core::{SECTIONS, with_color};

use crate::model::{MediaKind, PostTag, TagCategory};

// ---------- the details card ----------

/// The unit a size reads in.
#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum SizeUnit {
    B,
    Kb,
    Mb,
    Gb,
    Tb,
}

/// A size's number and unit: `1536` → `("1.5", Kb)`, with `mark` before the
/// tenths. Bytes have no tenths.
pub fn size_parts(bytes: u64, mark: char) -> (String, SizeUnit) {
    const UNITS: [SizeUnit; 5] = [
        SizeUnit::B,
        SizeUnit::Kb,
        SizeUnit::Mb,
        SizeUnit::Gb,
        SizeUnit::Tb,
    ];
    let mut value = bytes as f64;
    let mut unit = 0;
    while value >= 1024.0 && unit + 1 < UNITS.len() {
        value /= 1024.0;
        unit += 1;
    }
    let name = UNITS.get(unit).copied().unwrap_or(SizeUnit::B);
    let number = if unit == 0 {
        format!("{value:.0}")
    } else {
        format!("{value:.1}").replace('.', mark.encode_utf8(&mut [0; 4]))
    };
    (number, name)
}

/// `m:ss`, or `h:mm:ss` from an hour. `None` for nonsense.
#[uniffi::export]
pub fn format_duration(ms: i64) -> Option<String> {
    if ms < 0 {
        return None;
    }
    let s = ms.saturating_add(500) / 1000;
    let (h, m, s) = (s / 3600, s / 60 % 60, s % 60);
    Some(if h > 0 {
        format!("{h}:{m:02}:{s:02}")
    } else {
        format!("{m}:{s:02}")
    })
}

/// The search for posts in colour `hex` (`#f581fa`).
#[uniffi::export]
pub fn color_query(hex: &str) -> String {
    with_color("", Some(hex.trim().trim_start_matches('#')))
}

/// `1400 × 900`, or a dash when the server doesn't know.
#[uniffi::export]
pub fn dimensions(width: Option<i32>, height: Option<i32>) -> String {
    match (width, height) {
        (Some(w), Some(h)) if w > 0 && h > 0 => format!("{w} × {h}"),
        _ => "—".to_owned(),
    }
}

const TIME_FORMAT: &str = "%Y-%m-%d %H:%M";

/// A moment as the clock in `zone` showed it.
pub fn when<Tz: TimeZone>(at: &DateTime<Utc>, zone: &Tz) -> String
where
    Tz::Offset: Display,
{
    at.with_timezone(zone).format(TIME_FORMAT).to_string()
}

/// The date in a post's file. A picture's is the camera's own clock, stored as
/// if it were UTC because EXIF has no time zone, so it shows as it is; a
/// video's is a real moment, shown in `zone`.
pub fn file_date<Tz: TimeZone>(kind: MediaKind, at: &DateTime<Utc>, zone: &Tz) -> String
where
    Tz::Offset: Display,
{
    match kind {
        MediaKind::Video => when(at, zone),
        MediaKind::Image | MediaKind::Animation => at.naive_utc().format(TIME_FORMAT).to_string(),
    }
}

/// The source to link to, when it is a web address and nothing else.
pub fn source_link(source: &str) -> Option<&str> {
    let s = source.trim();
    let (scheme, rest) = s.split_once("://")?;
    let web = scheme.eq_ignore_ascii_case("http") || scheme.eq_ignore_ascii_case("https");
    (web && !rest.is_empty() && !s.contains(char::is_whitespace)).then_some(s)
}

// ---------- downloads ----------

/// The name a post's original is saved under: `<sha256>.<ext>`, the extension
/// from the file's address, else from its type.
#[uniffi::export]
pub fn download_name(sha256: &str, file_url: &str, mime: &str) -> String {
    let clean = |s: &str| -> String {
        s.chars()
            .filter(char::is_ascii_alphanumeric)
            .collect::<String>()
            .to_ascii_lowercase()
    };
    let path = file_url.split(['?', '#']).next().unwrap_or_default();
    let file = path.rsplit('/').next().unwrap_or_default();
    let from_path = file.rsplit_once('.').map(|(_, ext)| ext);
    // `image/svg+xml` → `svg`.
    let from_mime = mime
        .split_once('/')
        .and_then(|(_, sub)| sub.split(['+', ';']).next());
    let ext = [from_path, from_mime]
        .into_iter()
        .flatten()
        .map(clean)
        .find(|e| !e.is_empty() && e.len() <= 8)
        .unwrap_or_else(|| "bin".to_owned());
    let stem = clean(sha256);
    let stem = if stem.is_empty() {
        "download".to_owned()
    } else {
        stem
    };
    format!("{stem}.{ext}")
}

// ---------- tags ----------

/// A heading in the viewer's tag card.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Group {
    Category(TagCategory),
    /// General tags under a section of the tag tree, such as `hair`.
    Section(String),
    /// General tags outside any tree.
    Other,
}

impl Group {
    /// Artist, copyright, character, the built-in sections in order, other
    /// sections by name, general tags without one, meta.
    fn rank(&self) -> (u8, usize, &str) {
        match self {
            Group::Category(TagCategory::Artist) => (0, 0, ""),
            Group::Category(TagCategory::Copyright) => (1, 0, ""),
            Group::Category(TagCategory::Character) => (2, 0, ""),
            Group::Section(name) => match SECTIONS.iter().position(|s| s == name) {
                Some(i) => (3, i, ""),
                None => (4, 0, name),
            },
            Group::Other | Group::Category(TagCategory::General) => (5, 0, ""),
            Group::Category(TagCategory::Meta) => (6, 0, ""),
        }
    }
}

/// A post's tags under their headings, each group sorted by name. The web
/// app's `group_tags`; that name went to the export for Kotlin.
pub fn grouped(tags: &[PostTag]) -> Vec<(Group, Vec<PostTag>)> {
    let mut groups: Vec<(Group, Vec<PostTag>)> = Vec::new();
    for tag in tags {
        let group = match (&tag.category, &tag.section) {
            (TagCategory::General, Some(section)) => Group::Section(section.clone()),
            (TagCategory::General, None) => Group::Other,
            (category, _) => Group::Category(*category),
        };
        match groups.iter_mut().find(|(g, _)| *g == group) {
            Some((_, list)) => list.push(tag.clone()),
            None => groups.push((group, vec![tag.clone()])),
        }
    }
    groups.sort_by(|a, b| a.0.rank().cmp(&b.0.rank()));
    for (_, list) in &mut groups {
        list.sort_by(|a, b| a.name.cmp(&b.name));
    }
    groups
}

/// Whether the groups need their headings: one saying "Other" says nothing.
pub fn headed(groups: &[(Group, Vec<PostTag>)]) -> bool {
    !matches!(groups, [(Group::Other, _)])
}

// ---------- for Kotlin ----------

/// `size_parts` as a record.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct SizeParts {
    pub number: String,
    pub unit: SizeUnit,
}

/// A file's size in `bytes` as a number and a unit, with the first letter of
/// `decimal_mark` (`.` when it's empty) before the tenths. Below zero reads
/// as nothing.
#[uniffi::export]
pub fn file_size_parts(bytes: i64, decimal_mark: &str) -> SizeParts {
    let mark = decimal_mark.chars().next().unwrap_or('.');
    let (number, unit) = size_parts(bytes.try_into().unwrap_or(0), mark);
    SizeParts { number, unit }
}

/// The zone `offset_seconds` east of UTC, or `None` past a day either way.
fn zone(offset_seconds: i32) -> Option<FixedOffset> {
    FixedOffset::east_opt(offset_seconds)
}

/// `when` for a time in milliseconds since the epoch, in the zone
/// `offset_seconds` east of UTC. `None` for a time or a zone out of range.
#[uniffi::export]
pub fn time_text(at_ms: i64, offset_seconds: i32) -> Option<String> {
    let at = DateTime::<Utc>::from_timestamp_millis(at_ms)?;
    Some(when(&at, &zone(offset_seconds)?))
}

/// `file_date` for a time in milliseconds since the epoch, as `time_text`.
#[uniffi::export]
pub fn file_date_text(kind: MediaKind, at_ms: i64, offset_seconds: i32) -> Option<String> {
    let at = DateTime::<Utc>::from_timestamp_millis(at_ms)?;
    Some(file_date(kind, &at, &zone(offset_seconds)?))
}

/// The source to link to, when it is a web address and nothing else.
#[uniffi::export]
pub fn source_url(source: &str) -> Option<String> {
    source_link(source).map(str::to_owned)
}

/// What a group of the tag card is headed by.
#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum TagGroupKind {
    Artist,
    Copyright,
    Character,
    /// General tags under `TagGroup::section`.
    Section,
    /// General tags outside any section.
    Other,
    Meta,
}

/// A heading of the tag card and its tags, sorted by name.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct TagGroup {
    pub kind: TagGroupKind,
    /// The section's name, only for `TagGroupKind::Section`.
    pub section: Option<String>,
    pub tags: Vec<PostTag>,
}

impl TagGroup {
    fn new(group: Group, tags: Vec<PostTag>) -> Self {
        let (kind, section) = match group {
            Group::Category(TagCategory::Artist) => (TagGroupKind::Artist, None),
            Group::Category(TagCategory::Copyright) => (TagGroupKind::Copyright, None),
            Group::Category(TagCategory::Character) => (TagGroupKind::Character, None),
            Group::Category(TagCategory::Meta) => (TagGroupKind::Meta, None),
            Group::Section(name) => (TagGroupKind::Section, Some(name)),
            Group::Other | Group::Category(TagCategory::General) => (TagGroupKind::Other, None),
        };
        Self {
            kind,
            section,
            tags,
        }
    }
}

/// A post's tags under the tag card's headings, in the card's order: artist,
/// copyright, character, the sections people, hair, eyes, skin, body,
/// expression, pose, clothing, setting and style, other sections by name,
/// general tags without one, meta.
#[uniffi::export]
pub fn group_tags(tags: Vec<PostTag>) -> Vec<TagGroup> {
    grouped(&tags)
        .into_iter()
        .map(|(group, tags)| TagGroup::new(group, tags))
        .collect()
}

/// Whether the card shows the headings of `groups`: one saying "Other" says
/// nothing.
#[uniffi::export]
pub fn tag_groups_headed(groups: &[TagGroup]) -> bool {
    !matches!(groups, [only] if only.kind == TagGroupKind::Other)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tag(name: &str, category: TagCategory, section: Option<&str>) -> PostTag {
        PostTag {
            name: name.into(),
            category,
            section: section.map(str::to_owned),
        }
    }

    /// `human_size` in the web app, which puts the unit's word around these.
    fn size(bytes: i64, mark: &str) -> (String, SizeUnit) {
        let parts = file_size_parts(bytes, mark);
        (parts.number, parts.unit)
    }

    #[test]
    fn sizes_read_in_the_nearest_unit() {
        let size = |bytes| size(bytes, ".");
        assert_eq!(size(0), ("0".to_owned(), SizeUnit::B));
        assert_eq!(size(1023), ("1023".to_owned(), SizeUnit::B));
        assert_eq!(size(1536), ("1.5".to_owned(), SizeUnit::Kb));
        assert_eq!(size(5 * 1024 * 1024), ("5.0".to_owned(), SizeUnit::Mb));
        assert_eq!(size(1_336_381), ("1.3".to_owned(), SizeUnit::Mb));
        assert_eq!(size(-4), ("0".to_owned(), SizeUnit::B));
        assert_eq!(size(3 << 30), ("3.0".to_owned(), SizeUnit::Gb));
        assert_eq!(size(i64::MAX).1, SizeUnit::Tb);
        assert_eq!(size_parts(u64::MAX, '.').1, SizeUnit::Tb);
        assert_eq!(dimensions(Some(1400), Some(900)), "1400 × 900");
        assert_eq!(dimensions(None, Some(900)), "—");
        assert_eq!(dimensions(Some(0), Some(900)), "—");
    }

    #[test]
    fn a_size_has_the_languages_decimal_mark() {
        assert_eq!(size_parts(1536, ','), ("1,5".to_owned(), SizeUnit::Kb));
        assert_eq!(size_parts(1536, '.'), ("1.5".to_owned(), SizeUnit::Kb));
        // Whole bytes have no mark to put.
        assert_eq!(size_parts(999, ','), ("999".to_owned(), SizeUnit::B));
        assert_eq!(size_parts(1024, ','), ("1,0".to_owned(), SizeUnit::Kb));
        assert_eq!(size_parts(5 << 20, ','), ("5,0".to_owned(), SizeUnit::Mb));
        assert_eq!(size_parts(1 << 40, '.'), ("1.0".to_owned(), SizeUnit::Tb));
        // Past terabytes it stays in them.
        assert_eq!(
            size_parts(1 << 50, '.'),
            ("1024.0".to_owned(), SizeUnit::Tb)
        );
        // Italian.
        assert_eq!(size(1536, ","), ("1,5".to_owned(), SizeUnit::Kb));
        assert_eq!(size(12, ","), ("12".to_owned(), SizeUnit::B));
        assert_eq!(size(1_336_381, ","), ("1,3".to_owned(), SizeUnit::Mb));
        // Only the first letter counts, and none is a point.
        assert_eq!(size(1536, ",x"), ("1,5".to_owned(), SizeUnit::Kb));
        assert_eq!(size(1536, ""), ("1.5".to_owned(), SizeUnit::Kb));
    }

    #[test]
    fn durations_read_like_a_player() {
        assert_eq!(format_duration(6000).as_deref(), Some("0:06"));
        assert_eq!(format_duration(0).as_deref(), Some("0:00"));
        // Rounded to the nearest second.
        assert_eq!(format_duration(1_499).as_deref(), Some("0:01"));
        assert_eq!(format_duration(1_500).as_deref(), Some("0:02"));
        assert_eq!(format_duration(59_499).as_deref(), Some("0:59"));
        assert_eq!(format_duration(59_500).as_deref(), Some("1:00"));
        assert_eq!(format_duration(65_000).as_deref(), Some("1:05"));
        assert_eq!(format_duration(599_000).as_deref(), Some("9:59"));
        assert_eq!(format_duration(754_000).as_deref(), Some("12:34"));
        assert_eq!(format_duration(3_600_000).as_deref(), Some("1:00:00"));
        assert_eq!(format_duration(3_723_000).as_deref(), Some("1:02:03"));
        assert_eq!(format_duration(-1), None);
        assert!(format_duration(i64::MAX).is_some());
    }

    #[test]
    fn a_swatch_searches_its_colour() {
        assert_eq!(color_query("#f581fa"), "color:f581fa");
        assert_eq!(color_query(" ae82e2"), "color:ae82e2");
    }

    #[test]
    fn times_show_in_the_users_zone_and_a_cameras_as_it_was() {
        let at: DateTime<Utc> = "2026-01-02T03:04:05Z".parse().unwrap();
        let rome = FixedOffset::east_opt(2 * 3600).unwrap();
        let utc = FixedOffset::east_opt(0).unwrap();
        assert_eq!(when(&at, &rome), "2026-01-02 05:04");
        assert_eq!(when(&at, &utc), "2026-01-02 03:04");
        // Across midnight, to the day before.
        let west = FixedOffset::west_opt(5 * 3600).unwrap();
        assert_eq!(when(&at, &west), "2026-01-01 22:04");
        // A camera's clock has no zone: shown as it was.
        assert_eq!(file_date(MediaKind::Image, &at, &rome), "2026-01-02 03:04");
        assert_eq!(
            file_date(MediaKind::Animation, &at, &rome),
            "2026-01-02 03:04"
        );
        assert_eq!(file_date(MediaKind::Video, &at, &rome), "2026-01-02 05:04");
    }

    #[test]
    fn kotlin_gives_times_in_milliseconds() {
        let at = "2026-01-02T03:04:05Z"
            .parse::<DateTime<Utc>>()
            .unwrap()
            .timestamp_millis();
        assert_eq!(time_text(at, 7200).as_deref(), Some("2026-01-02 05:04"));
        assert_eq!(
            time_text(at, -5 * 3600).as_deref(),
            Some("2026-01-01 22:04")
        );
        assert_eq!(
            file_date_text(MediaKind::Image, at, 7200).as_deref(),
            Some("2026-01-02 03:04")
        );
        assert_eq!(
            file_date_text(MediaKind::Video, at, 7200).as_deref(),
            Some("2026-01-02 05:04")
        );
        // A zone more than a day off, or a time past what the clock holds.
        assert_eq!(time_text(at, 86_400), None);
        assert_eq!(file_date_text(MediaKind::Video, at, i32::MIN), None);
        assert_eq!(time_text(i64::MAX, 0), None);
        assert_eq!(file_date_text(MediaKind::Image, i64::MIN, 0), None);
    }

    #[test]
    fn only_a_web_address_is_a_link() {
        assert_eq!(
            source_link(" https://ffmpeg.org/ffmpeg-filters.html "),
            Some("https://ffmpeg.org/ffmpeg-filters.html")
        );
        assert_eq!(
            source_link("HTTP://Example.com"),
            Some("HTTP://Example.com")
        );
        // A letter of more than one byte early on is no trouble.
        assert_eq!(source_link("http://é.example"), Some("http://é.example"));
        assert_eq!(source_link("hTTpS://é"), Some("hTTpS://é"));
        for not_one in [
            "",
            "my camera",
            "javascript:alert(1)",
            "https://",
            "ftp://example.com",
            "https://a b",
            "data:text/html,x",
            "//example.com",
            "é://example.com",
            "httpé://example.com",
            "xhttp://example.com",
            "http:/example.com",
        ] {
            assert_eq!(source_link(not_one), None, "{not_one}");
            assert_eq!(source_url(not_one), None, "{not_one}");
        }
        assert_eq!(
            source_url(" https://example.com/a ").as_deref(),
            Some("https://example.com/a")
        );
    }

    #[test]
    fn a_download_is_named_by_its_hash_and_type() {
        assert_eq!(
            download_name("39fb", "/media/original/39fb.png?exp=1&sig=2", "image/png"),
            "39fb.png"
        );
        // The file's own extension comes first.
        assert_eq!(
            download_name("ab", "/media/original/ab.jpg?exp=1", "image/jpeg"),
            "ab.jpg"
        );
        assert_eq!(
            download_name("ab", "/media/original/ab?exp=1", "image/jpeg"),
            "ab.jpeg"
        );
        assert_eq!(download_name("ab", "", "image/svg+xml"), "ab.svg");
        assert_eq!(download_name("ab", "", "video/mp4; codecs=x"), "ab.mp4");
        assert_eq!(download_name("ab", "", ""), "ab.bin");
        assert_eq!(download_name("ab", "/x/ab.PNG", ""), "ab.png");
        // Nothing that could lead out of the folder, or say nothing.
        assert_eq!(download_name("../../etc", "/a/b.p/n?g", "a/b"), "etc.b");
        assert_eq!(download_name("", "", ""), "download.bin");
        assert_eq!(
            download_name("ab", "/x/ab.thisistoolong", "image/gif"),
            "ab.gif"
        );
        // An absolute address, as the core hands them to Kotlin.
        assert_eq!(
            download_name(
                "39fb",
                "https://booru.example/sub/media/original/39fb.webm?sig=1#t=2",
                "video/webm"
            ),
            "39fb.webm"
        );
    }

    /// Post 5 of the test server.
    fn post_five() -> Vec<PostTag> {
        vec![
            tag("blue_sky", TagCategory::General, None),
            tag("ffmpeg", TagCategory::Copyright, None),
            tag("fractal", TagCategory::General, None),
            tag("hd", TagCategory::Meta, None),
            tag("lavfi", TagCategory::Artist, None),
            tag("mandelbrot", TagCategory::General, None),
            tag("sky_blue_hair", TagCategory::General, Some("hair")),
            tag("smile", TagCategory::General, Some("expression")),
            tag("1girl", TagCategory::General, Some("people")),
            tag("gear", TagCategory::General, Some("my_section")),
            tag("alice", TagCategory::Character, None),
        ]
    }

    #[test]
    fn tags_group_by_category_in_the_references_order() {
        let groups = grouped(&post_five());
        let heads: Vec<Group> = groups.iter().map(|(g, _)| g.clone()).collect();
        assert_eq!(
            heads,
            [
                Group::Category(TagCategory::Artist),
                Group::Category(TagCategory::Copyright),
                Group::Category(TagCategory::Character),
                Group::Section("people".into()),
                Group::Section("hair".into()),
                Group::Section("expression".into()),
                Group::Section("my_section".into()),
                Group::Other,
                Group::Category(TagCategory::Meta),
            ]
        );
        let other: Vec<&str> = groups
            .iter()
            .find(|(g, _)| *g == Group::Other)
            .map(|(_, t)| t.iter().map(|t| t.name.as_str()).collect())
            .unwrap();
        assert_eq!(other, ["blue_sky", "fractal", "mandelbrot"]);
        assert!(headed(&groups));
        assert!(grouped(&[]).is_empty());
    }

    #[test]
    fn plain_tags_alone_need_no_heading() {
        let tags = [
            tag("b", TagCategory::General, None),
            tag("a", TagCategory::General, None),
        ];
        let groups = grouped(&tags);
        assert!(!headed(&groups));
        assert_eq!(groups.first().map(|(_, t)| t.len()), Some(2));
    }

    #[test]
    fn kotlin_gets_the_same_groups() {
        let groups = group_tags(post_five());
        let heads: Vec<(TagGroupKind, Option<&str>)> = groups
            .iter()
            .map(|g| (g.kind, g.section.as_deref()))
            .collect();
        assert_eq!(
            heads,
            [
                (TagGroupKind::Artist, None),
                (TagGroupKind::Copyright, None),
                (TagGroupKind::Character, None),
                (TagGroupKind::Section, Some("people")),
                (TagGroupKind::Section, Some("hair")),
                (TagGroupKind::Section, Some("expression")),
                (TagGroupKind::Section, Some("my_section")),
                (TagGroupKind::Other, None),
                (TagGroupKind::Meta, None),
            ]
        );
        let names = |kind| -> Vec<String> {
            groups
                .iter()
                .filter(|g| g.kind == kind)
                .flat_map(|g| g.tags.iter().map(|t| t.name.clone()))
                .collect()
        };
        assert_eq!(
            names(TagGroupKind::Other),
            ["blue_sky", "fractal", "mandelbrot"]
        );
        assert_eq!(names(TagGroupKind::Artist), ["lavfi"]);
        // Every tag lands in one group.
        let count: usize = groups.iter().map(|g| g.tags.len()).sum();
        assert_eq!(count, post_five().len());
        assert!(tag_groups_headed(&groups));
        assert!(group_tags(Vec::new()).is_empty());
        assert!(tag_groups_headed(&[]));

        let plain = group_tags(vec![
            tag("b", TagCategory::General, None),
            tag("a", TagCategory::General, None),
        ]);
        assert!(!tag_groups_headed(&plain));
        let names: Vec<&str> = plain
            .iter()
            .flat_map(|g| g.tags.iter().map(|t| t.name.as_str()))
            .collect();
        assert_eq!(names, ["a", "b"]);
        // One section alone still says something.
        let hair = group_tags(vec![tag("red_hair", TagCategory::General, Some("hair"))]);
        assert!(tag_groups_headed(&hair));
    }

    #[test]
    fn every_section_has_its_place() {
        // In the order the server lists them, after the categories and before
        // sections of the user's own.
        let mut tags: Vec<PostTag> = SECTIONS
            .iter()
            .rev()
            .map(|s| tag(&format!("{s}_tag"), TagCategory::General, Some(*s)))
            .collect();
        tags.push(tag("zz", TagCategory::General, Some("aaa_mine")));
        tags.push(tag("yy", TagCategory::General, Some("zzz_mine")));
        tags.push(tag("x", TagCategory::Character, None));
        let sections: Vec<String> = group_tags(tags)
            .into_iter()
            .filter_map(|g| g.section)
            .collect();
        let mut expected: Vec<String> = SECTIONS.iter().map(|s| (*s).to_owned()).collect();
        expected.extend(["aaa_mine".to_owned(), "zzz_mine".to_owned()]);
        assert_eq!(sections, expected);
    }
}
