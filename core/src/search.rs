//! The search box's rules, as in starbooru-web's `logic::search`: the word at
//! the caret in the search and in a tag box, what completing it does, short
//! counts, the hints that still fit and the words of the correction banner.
//! Also the searches a tag's menu leads to, and the tags in a tag box, from
//! its `logic::tags`.
//!
//! The rules count in bytes, as the web does. Kotlin's text fields count
//! UTF-16 units, so the exported functions take and return those.

use starbooru_core::{normalize_tag, split_category};

use crate::model::{TagCategory, TagCorrection};

// ---------- the word at the caret ----------

/// The tag being typed where the caret is. `start..end` is the whole tag in
/// the search, in bytes, without what leads it (`-`, `{`) or a `}` closing
/// a group; `typed` is its part before the caret, which the suggestions
/// start with.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Word {
    pub start: usize,
    pub end: usize,
    pub typed: String,
}

/// Where the word around `caret` starts and ends in `value`, in bytes: words
/// are what's between spaces and line breaks.
fn around(value: &str, caret: usize) -> Option<(usize, usize)> {
    let before = value.get(..caret)?;
    let after = value.get(caret..)?;
    let start = before
        .char_indices()
        .rev()
        .find(|(_, c)| c.is_whitespace())
        .map_or(0, |(i, c)| i + c.len_utf8());
    let end = caret + after.find(char::is_whitespace).unwrap_or(after.len());
    Some((start, end))
}

/// The tag under the caret, `caret` bytes into `value`, if there is one to
/// complete. A `key:value` term, a word in the quotes of `text:"…"` and the
/// space after a word have none.
pub fn word_at(value: &str, caret: usize) -> Option<Word> {
    let (start, end) = around(value, caret)?;
    if in_quotes(value.get(..start)?) {
        return None;
    }
    let word = value.get(start..end)?;
    let lead = word.len() - word.trim_start_matches(['-', '{']).len();
    let body = word.trim_end_matches('}').len().max(lead);
    let (tag_start, tag_end) = (start + lead, start + body);
    // In what leads the tag, or past the brace that closes its group.
    if !(tag_start..=tag_end).contains(&caret) {
        return None;
    }
    let tag = value.get(tag_start..tag_end)?;
    let typed = value.get(tag_start..caret)?;
    if typed.is_empty() || tag.contains([':', '"', '{', '}']) {
        return None;
    }
    Some(Word {
        start: tag_start,
        end: tag_end,
        typed: typed.to_owned(),
    })
}

/// Whether `text` ends inside the quotes of a `text:"…"` term, as
/// `starbooru_core`'s parser splits a search: the quote closes at a word
/// that ends with `"`.
fn in_quotes(text: &str) -> bool {
    let mut open = false;
    for word in text.split_whitespace() {
        if open {
            open = !word.ends_with('"');
            continue;
        }
        let lower = word.to_ascii_lowercase();
        let body = lower.strip_prefix('-').unwrap_or(&lower);
        if let Some(rest) = body.strip_prefix("text:\"") {
            open = rest.is_empty() || !rest.ends_with('"');
        }
    }
    open
}

/// The tag under the caret in a tag box, where every word is a tag. A
/// category prefix (`artist:`) stays in front and the rest is completed; a
/// word with any other colon in it has nothing to complete.
pub fn tag_word_at(value: &str, caret: usize) -> Option<Word> {
    let (start, end) = around(value, caret)?;
    let word = value.get(start..end)?;
    let (_, tag) = split_category(word);
    let tag_start = start + (word.len() - tag.len());
    // In the prefix itself, nothing is typed yet.
    if caret < tag_start || tag.contains(':') {
        return None;
    }
    let typed = value.get(tag_start..caret)?;
    (!typed.is_empty()).then(|| Word {
        start: tag_start,
        end,
        typed: typed.to_owned(),
    })
}

/// `value` with `word` replaced by `tag`, and where the caret goes: after a
/// space that ends the tag, or before the `}` that closes its group. `None`
/// when `word` isn't in `value` any more.
pub fn complete(value: &str, word: &Word, tag: &str) -> Option<(String, usize)> {
    let head = value.get(..word.start)?;
    let tail = value.get(word.end..)?;
    let mut out = String::with_capacity(value.len() + tag.len() + 1);
    out.push_str(head);
    out.push_str(tag);
    let mut caret = out.len();
    match tail.chars().next() {
        None => {
            out.push(' ');
            caret += 1;
        }
        Some(c) if c.is_whitespace() => caret += c.len_utf8(),
        Some(_) => {}
    }
    out.push_str(tail);
    Some((out, caret))
}

/// The byte offset in `value` of a caret `units` UTF-16 code units in, as the
/// browser counts them. Past the end, the end.
pub fn byte_index(value: &str, units: usize) -> usize {
    let mut counted = 0;
    for (i, c) in value.char_indices() {
        if counted >= units {
            return i;
        }
        counted += c.len_utf16();
    }
    value.len()
}

/// The browser's caret for `byte` bytes into `value`: UTF-16 code units.
pub fn utf16_index(value: &str, byte: usize) -> usize {
    let upto = value.get(..byte.min(value.len())).unwrap_or(value);
    upto.encode_utf16().count()
}

// ---------- the same in UTF-16, for Kotlin ----------

/// A [`Word`] counted in UTF-16 units, as a text field's selection is.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct TypedWord {
    pub start: u32,
    pub end: u32,
    pub typed: String,
}

/// The text after a completion, and the caret in UTF-16 units.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct Completion {
    pub value: String,
    pub caret: u32,
}

/// The byte offset of a caret `units` UTF-16 units into `value`. `None` past
/// the end or between the two halves of a surrogate pair.
fn byte_at(value: &str, units: u32) -> Option<usize> {
    let units = usize::try_from(units).ok()?;
    let byte = byte_index(value, units);
    (utf16_index(value, byte) == units).then_some(byte)
}

fn units_at(value: &str, byte: usize) -> Option<u32> {
    u32::try_from(utf16_index(value, byte)).ok()
}

fn typed_word(value: &str, word: Word) -> Option<TypedWord> {
    Some(TypedWord {
        start: units_at(value, word.start)?,
        end: units_at(value, word.end)?,
        typed: word.typed,
    })
}

/// The tag under the caret in the search box, if there is one to complete.
#[uniffi::export]
pub fn search_word_at(value: &str, caret_utf16: u32) -> Option<TypedWord> {
    let caret = byte_at(value, caret_utf16)?;
    typed_word(value, word_at(value, caret)?)
}

/// The tag under the caret in a tag box, after its category prefix.
#[uniffi::export]
pub fn tag_box_word_at(value: &str, caret_utf16: u32) -> Option<TypedWord> {
    let caret = byte_at(value, caret_utf16)?;
    typed_word(value, tag_word_at(value, caret)?)
}

/// `value` with `word` replaced by `tag`, and where the caret goes. Take
/// `word` from the text as it is now; `None` when it no longer fits in it.
#[uniffi::export]
pub fn complete_word(value: &str, word: TypedWord, tag: &str) -> Option<Completion> {
    if word.start > word.end {
        return None;
    }
    let word = Word {
        start: byte_at(value, word.start)?,
        end: byte_at(value, word.end)?,
        typed: word.typed,
    };
    let (value, caret) = complete(value, &word, tag)?;
    Some(Completion {
        caret: units_at(&value, caret)?,
        value,
    })
}

// ---------- suggestions ----------

/// How many suggestions the list shows.
pub const SUGGESTIONS: u32 = 8;

/// How long the typing has to pause before the server is asked, in
/// milliseconds. A word typed in one go then costs one request.
pub const TYPING_PAUSE_MS: u32 = 150;

#[uniffi::export]
pub fn suggestion_count() -> u32 {
    SUGGESTIONS
}

#[uniffi::export]
pub fn typing_pause_ms() -> u32 {
    TYPING_PAUSE_MS
}

/// A count in few characters, for sites with tags on millions of posts:
/// `1234` → `1.2k`, `4217021` → `4.2M`.
#[uniffi::export]
pub fn short_count(n: u64) -> String {
    let short = |unit: u64, letter: &str| {
        let (whole, tenth) = (n / unit, (n % unit).saturating_mul(10) / unit);
        if whole >= 100 {
            format!("{whole}{letter}")
        } else {
            format!("{whole}.{tenth}{letter}")
        }
    };
    match n {
        ..1_000 => n.to_string(),
        1_000..1_000_000 => short(1_000, "k"),
        _ => short(1_000_000, "M"),
    }
}

/// What the list still shows while the server is asked about a longer
/// word: the names that start with it.
#[uniffi::export]
pub fn fitting(mut names: Vec<String>, typed: &str) -> Vec<String> {
    let typed = typed.to_lowercase();
    names.retain(|name| name.starts_with(&typed));
    names
}

/// The correction banner's list: `fractl → fractal, blu → blue`.
#[uniffi::export]
pub fn corrections_text(list: Vec<TagCorrection>) -> String {
    list.iter()
        .map(|c| format!("{} → {}", c.from, c.to))
        .collect::<Vec<_>>()
        .join(", ")
}

// ---------- a tag's menu ----------

/// The words of a search, a quoted phrase (`text:"two words"`) staying one.
pub fn words(q: &str) -> Vec<&str> {
    let mut out = Vec::new();
    let mut start = None;
    let mut quoted = false;
    for (i, c) in q.char_indices() {
        if c == '"' {
            quoted = !quoted;
        }
        if c.is_whitespace() && !quoted {
            if let Some(from) = start.take() {
                out.extend(q.get(from..i));
            }
        } else if start.is_none() {
            start = Some(i);
        }
    }
    if let Some(from) = start {
        out.extend(q.get(from..));
    }
    out
}

/// `q` without `name`, whether it was wanted or left out, and then `word` at
/// the end.
fn with_word(q: &str, name: &str, word: &str) -> String {
    let excluded = format!("-{name}");
    let mut kept: Vec<&str> = words(q)
        .into_iter()
        .filter(|w| *w != name && *w != excluded)
        .collect();
    kept.push(word);
    kept.join(" ")
}

/// The search narrowed to posts that also have `name`.
#[uniffi::export]
pub fn include_tag(q: &str, name: &str) -> String {
    with_word(q, name, name)
}

/// The search without the posts that have `name`.
#[uniffi::export]
pub fn exclude_tag(q: &str, name: &str) -> String {
    with_word(q, name, &format!("-{name}"))
}

// ---------- the tags in a tag box ----------

/// A tag from a tag box, as the server will store it: `name` without its
/// prefix, and the category the prefix gives it.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct TypedTag {
    pub name: String,
    pub category: Option<TagCategory>,
}

/// The tags in a tag box, one per word, normalised as the server does, each
/// once in the order typed. `Artist:Lavfi` is `lavfi` with the artist
/// category. A tag typed bare and again with a prefix takes the first
/// prefix's category. Words that can't be tags are left out.
#[uniffi::export]
pub fn tags_in_box(text: &str) -> Vec<TypedTag> {
    let mut out: Vec<TypedTag> = Vec::new();
    for word in text.split_whitespace() {
        let Ok(tag) = normalize_tag(word) else {
            continue;
        };
        let (category, name) = split_category(&tag);
        let category = category.map(TagCategory::from);
        match out.iter_mut().find(|t| t.name == name) {
            Some(seen) => {
                if seen.category.is_none() {
                    seen.category = category;
                }
            }
            None => out.push(TypedTag {
                name: name.to_owned(),
                category,
            }),
        }
    }
    out
}

/// `word` as the server stores it, or `None` when it can't be a tag. A
/// category prefix stays.
#[uniffi::export]
pub fn normalized_tag(word: &str) -> Option<String> {
    normalize_tag(word).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The word at `|` in `marked`.
    fn at(marked: &str) -> Option<String> {
        let caret = marked.find('|').unwrap();
        let value = marked.replace('|', "");
        word_at(&value, caret).map(|w| w.typed)
    }

    /// Accepting `tag` at `|` in `marked`, with the caret shown as `|`.
    fn accept(marked: &str, tag: &str) -> String {
        let caret = marked.find('|').unwrap();
        let value = marked.replace('|', "");
        let word = word_at(&value, caret).unwrap();
        let (mut out, at) = complete(&value, &word, tag).unwrap();
        out.insert(at, '|');
        out
    }

    #[test]
    fn the_word_at_the_end_is_completed() {
        assert_eq!(at("fra|").as_deref(), Some("fra"));
        assert_eq!(at("blue_sky fra|").as_deref(), Some("fra"));
        assert_eq!(accept("fra|", "fractal"), "fractal |");
        assert_eq!(accept("blue_sky fra|", "fractal"), "blue_sky fractal |");
        for nothing in ["|", "   |", "fractal |", "-|", "{|", "-{|"] {
            assert_eq!(at(nothing), None, "{nothing}");
        }
    }

    #[test]
    fn a_minus_and_a_group_stay_in_front_of_the_tag() {
        assert_eq!(at("night -clo|").as_deref(), Some("clo"));
        assert_eq!(accept("-fra|", "fractal"), "-fractal |");
        assert_eq!(at("{girl boo|").as_deref(), Some("boo"));
        assert_eq!(at("-{bo|").as_deref(), Some("bo"));
        assert_eq!(accept("-{bo|", "boots"), "-{boots |");
        // Inside a closed group the brace stays, and the caret before it.
        assert_eq!(at("{girl boo|}").as_deref(), Some("boo"));
        assert_eq!(accept("{girl boo|}", "boots"), "{girl boots|}");
        assert_eq!(at("{girl boots}|"), None);
        // On the lead itself there's nothing typed yet.
        assert_eq!(at("|-fra"), None);
        assert_eq!(at("-|fra"), None);
    }

    #[test]
    fn filters_and_quoted_text_get_no_suggestions() {
        for term in [
            "rating:s|",
            "order:sc|",
            "-rating:e|",
            "width:>19|",
            "artist:lav|",
            "text:\"no par|",
            "text:\"no par|king\"",
            "cat text:hel|",
        ] {
            assert_eq!(at(term), None, "{term}");
        }
        // Words after a closed quote are tags again; inside an open one, text.
        assert_eq!(at("text:\"no parking\" fra|").as_deref(), Some("fra"));
        assert_eq!(at("text:\"one\" fra|").as_deref(), Some("fra"));
        assert_eq!(at("-text:\"no parking fra|"), None);
        assert_eq!(at("TEXT:\"no fra|"), None);
    }

    #[test]
    fn the_caret_in_the_middle_completes_its_own_word() {
        assert_eq!(at("fra| blue_sky").as_deref(), Some("fra"));
        assert_eq!(accept("fra| blue_sky", "fractal"), "fractal |blue_sky");
        // Halfway into a word: what's before the caret is looked up, and the
        // whole word is replaced.
        assert_eq!(at("blue_sky fra|ctl order:score").as_deref(), Some("fra"));
        assert_eq!(
            accept("blue_sky fra|ctl order:score", "fractal"),
            "blue_sky fractal |order:score"
        );
        assert_eq!(at("|fra"), None);
        assert_eq!(accept("f|ra  cat", "fractal"), "fractal | cat");
    }

    #[test]
    fn letters_outside_ascii_are_counted_as_the_browser_counts_them() {
        let value = "猫 😀 fra";
        // The browser says the caret is 7 units in: 猫 is one, 😀 two.
        assert_eq!(value.encode_utf16().count(), 8);
        let caret = byte_index(value, 7);
        assert_eq!(value.get(caret..), Some("a"));
        assert_eq!(utf16_index(value, caret), 7);
        assert_eq!(utf16_index(value, value.len()), 8);
        assert_eq!(byte_index(value, 999), value.len());
        assert_eq!(utf16_index(value, 999), 8);
        assert_eq!(
            word_at(value, value.len()).map(|w| w.typed).as_deref(),
            Some("fra")
        );
        // A word after a wide space, and a caret off a character's boundary.
        assert_eq!(
            word_at("猫\u{3000}fr", "猫\u{3000}fr".len())
                .map(|w| w.typed)
                .as_deref(),
            Some("fr")
        );
        assert_eq!(word_at("猫", 1), None);
        assert_eq!(word_at("cat", 9), None);
    }

    /// The tag box's word at `|` in `marked`, as `(typed, start)`.
    fn in_box(marked: &str) -> Option<(String, usize)> {
        let caret = marked.find('|').unwrap();
        let value = marked.replace('|', "");
        tag_word_at(&value, caret).map(|w| (w.typed, w.start))
    }

    /// Accepting `tag` at `|` in a tag box.
    fn accept_in_box(marked: &str, tag: &str) -> String {
        let caret = marked.find('|').unwrap();
        let value = marked.replace('|', "");
        let word = tag_word_at(&value, caret).unwrap();
        let (mut out, at) = complete(&value, &word, tag).unwrap();
        out.insert(at, '|');
        out
    }

    #[test]
    fn a_tag_box_completes_each_word_and_keeps_a_category_prefix() {
        assert_eq!(in_box("blue_sk|"), Some(("blue_sk".into(), 0)));
        assert_eq!(in_box("cat blu| dog"), Some(("blu".into(), 4)));
        // Lines are words apart too.
        assert_eq!(in_box("cat\nchar:ri|"), Some(("ri".into(), 9)));
        assert_eq!(in_box("artist:mi|"), Some(("mi".into(), 7)));
        assert_eq!(accept_in_box("artist:mi|", "mika"), "artist:mika |");
        assert_eq!(
            accept_in_box("cat blu| dog", "blue_sky"),
            "cat blue_sky |dog"
        );
        assert_eq!(
            accept_in_box("cat\nblu|\ndog", "blue_sky"),
            "cat\nblue_sky\n|dog"
        );
        // Halfway into a word, as in the search.
        assert_eq!(in_box("cat bl|u dog"), Some(("bl".into(), 4)));
        // A minus or a brace is part of the tag here: there are no groups.
        assert_eq!(in_box("{fra|"), Some(("{fra".into(), 0)));
        for nothing in [
            "|",
            "cat |",
            "|cat dog",
            "cat | dog",
            "artist:|",
            "arti|st:mi",
            "rating:s|",
            "a:b:c|",
        ] {
            assert_eq!(in_box(nothing), None, "{nothing}");
        }
    }

    #[test]
    fn a_word_that_moved_is_not_replaced() {
        let word = Word {
            start: 4,
            end: 20,
            typed: "x".into(),
        };
        assert_eq!(complete("cat", &word, "xylophone"), None);
    }

    fn word(start: u32, end: u32, typed: &str) -> TypedWord {
        TypedWord {
            start,
            end,
            typed: typed.into(),
        }
    }

    #[test]
    fn kotlin_gets_the_word_in_utf16_units() {
        // café is 4 units and 5 bytes, ネコ 2 and 6, 😀 2 and 4.
        let value = "café ネコ 😀 fra";
        assert_eq!(value.encode_utf16().count(), 14);
        assert_eq!(search_word_at(value, 14), Some(word(11, 14, "fra")));
        assert_eq!(search_word_at(value, 12), Some(word(11, 14, "f")));
        assert_eq!(search_word_at(value, 4), Some(word(0, 4, "café")));
        assert_eq!(search_word_at(value, 6), Some(word(5, 7, "ネ")));
        assert_eq!(search_word_at(value, 10), Some(word(8, 10, "😀")));
        // Between the halves of the emoji, past the end, after a space.
        assert_eq!(search_word_at(value, 9), None);
        assert_eq!(search_word_at(value, 15), None);
        assert_eq!(search_word_at(value, 5), None);
        assert_eq!(search_word_at("", 0), None);
        // A filter stays a filter in any script.
        assert_eq!(search_word_at("ネコ rating:s", 11), None);
        assert_eq!(search_word_at("-ネコ", 3), Some(word(1, 3, "ネコ")));
    }

    #[test]
    fn kotlin_gets_the_completion_in_utf16_units() {
        let value = "café ネコ 😀 fra";
        let done = |caret, tag| {
            let w = search_word_at(value, caret).unwrap();
            complete_word(value, w, tag).unwrap()
        };
        let end = done(14, "fractal");
        assert_eq!(end.value, "café ネコ 😀 fractal ");
        assert_eq!(end.caret, 19);
        assert_eq!(end.caret as usize, end.value.encode_utf16().count());
        // In the middle the caret goes past the space after the tag.
        let middle = done(6, "ネコミミ");
        assert_eq!(middle.value, "café ネコミミ 😀 fra");
        assert_eq!(middle.caret, 10);
        let emoji = done(10, "😀_face");
        assert_eq!(emoji.value, "café ネコ 😀_face fra");
        assert_eq!(emoji.caret, 16);
        // Before a closing brace.
        let group = complete_word("{ネ}", search_word_at("{ネ}", 2).unwrap(), "ネコ").unwrap();
        assert_eq!(group.value, "{ネコ}");
        assert_eq!(group.caret, 3);
    }

    #[test]
    fn a_tag_box_word_keeps_its_prefix_in_utf16_units() {
        let value = "artist:ミク café";
        assert_eq!(tag_box_word_at(value, 9), Some(word(7, 9, "ミク")));
        assert_eq!(tag_box_word_at(value, 14), Some(word(10, 14, "café")));
        assert_eq!(tag_box_word_at(value, 7), None);
        let w = tag_box_word_at(value, 9).unwrap();
        let done = complete_word(value, w, "ミク_(vocaloid)").unwrap();
        assert_eq!(done.value, "artist:ミク_(vocaloid) café");
        assert_eq!(done.caret, 21);
        let emoji = tag_box_word_at("😀😀", 4).unwrap();
        assert_eq!(emoji, word(0, 4, "😀😀"));
    }

    #[test]
    fn a_word_that_no_longer_fits_is_not_completed() {
        assert_eq!(complete_word("cat", word(4, 20, "x"), "xylophone"), None);
        assert_eq!(complete_word("cat", word(2, 1, "x"), "xylophone"), None);
        // An end between the halves of a surrogate pair.
        assert_eq!(complete_word("😀", word(0, 1, "x"), "xylophone"), None);
        assert_eq!(
            complete_word("😀", word(0, 2, "😀"), "😀_face"),
            Some(Completion {
                value: "😀_face ".into(),
                caret: 8
            })
        );
    }

    #[test]
    fn counts_stay_short() {
        assert_eq!(short_count(0), "0");
        assert_eq!(short_count(999), "999");
        assert_eq!(short_count(1_000), "1.0k");
        assert_eq!(short_count(1_234), "1.2k");
        assert_eq!(short_count(123_456), "123k");
        assert_eq!(short_count(4_217_021), "4.2M");
        assert_eq!(short_count(u64::MAX), "18446744073709M");
    }

    #[test]
    fn hints_that_still_fit_stay_while_the_server_is_asked() {
        fn names(list: &[&str]) -> Vec<String> {
            list.iter().map(|n| (*n).to_owned()).collect()
        }
        let hints = fitting(names(&["fractal", "frame", "fox"]), "FRA");
        assert_eq!(hints, names(&["fractal", "frame"]));
        assert_eq!(fitting(hints, "fract"), names(&["fractal"]));
        assert_eq!(
            fitting(names(&["ネコミミ", "ネズミ"]), "ネコ"),
            names(&["ネコミミ"])
        );
    }

    #[test]
    fn the_constants_match_the_web() {
        assert_eq!(suggestion_count(), 8);
        assert_eq!(typing_pause_ms(), 150);
    }

    #[test]
    fn the_banner_lists_each_correction() {
        let fix = |from: &str, to: &str| TagCorrection {
            from: from.into(),
            to: to.into(),
        };
        assert_eq!(
            corrections_text(vec![fix("fractl", "fractal")]),
            "fractl → fractal"
        );
        assert_eq!(
            corrections_text(vec![fix("fractl", "fractal"), fix("blu_sky", "blue_sky")]),
            "fractl → fractal, blu_sky → blue_sky"
        );
        assert_eq!(corrections_text(Vec::new()), "");
    }

    #[test]
    fn a_quoted_phrase_is_one_word() {
        assert_eq!(
            words("  blue  text:\"two words\" -red "),
            ["blue", "text:\"two words\"", "-red"]
        );
        assert!(words("").is_empty());
        // A quote that never closes runs to the end.
        assert_eq!(words("a \"b c"), ["a", "\"b c"]);
    }

    #[test]
    fn a_tag_narrows_or_leaves_out_of_the_search() {
        assert_eq!(include_tag("", "sky"), "sky");
        assert_eq!(include_tag("blue order:new", "sky"), "blue order:new sky");
        // Left out before: now wanted.
        assert_eq!(include_tag("blue -sky", "sky"), "blue sky");
        assert_eq!(exclude_tag("blue sky", "sky"), "blue -sky");
        assert_eq!(exclude_tag("", "sky"), "-sky");
        assert_eq!(exclude_tag("-sky blue", "sky"), "blue -sky");
        // In a phrase, the word is the phrase's.
        assert_eq!(exclude_tag("text:\"a sky\"", "sky"), "text:\"a sky\" -sky");
        assert_eq!(include_tag("skyline", "sky"), "skyline sky");
        assert_eq!(exclude_tag("café ネコ", "ネコ"), "café -ネコ");
    }

    fn typed(name: &str, category: Option<TagCategory>) -> TypedTag {
        TypedTag {
            name: name.into(),
            category,
        }
    }

    #[test]
    fn a_tag_box_becomes_tags_with_their_categories() {
        assert_eq!(
            tags_in_box("  Artist:Lavfi blue_sky\nBlue_Sky gen:x\t-bad _ ネコ char:ミク 😀 "),
            [
                typed("lavfi", Some(TagCategory::Artist)),
                typed("blue_sky", None),
                typed("x", Some(TagCategory::General)),
                typed("ネコ", None),
                typed("ミク", Some(TagCategory::Character)),
                typed("😀", None),
            ]
        );
        // A prefix later gives the tag its category; the first one stays.
        assert_eq!(
            tags_in_box("lavfi artist:lavfi meta:lavfi"),
            [typed("lavfi", Some(TagCategory::Artist))]
        );
        assert_eq!(
            tags_in_box("copy:touhou series:fate"),
            [
                typed("touhou", Some(TagCategory::Copyright)),
                typed("fate", Some(TagCategory::Copyright)),
            ]
        );
        // A prefix alone, or another colon, is a tag of its own.
        assert_eq!(
            tags_in_box("artist: a:b"),
            [typed("artist:", None), typed("a:b", None)]
        );
        assert!(tags_in_box(" \n ").is_empty());
    }

    #[test]
    fn a_typed_tag_is_normalised_as_the_server_does() {
        assert_eq!(normalized_tag("  Blue Sky ").as_deref(), Some("blue_sky"));
        assert_eq!(
            normalized_tag("Artist:Lavfi").as_deref(),
            Some("artist:lavfi")
        );
        assert_eq!(normalized_tag("CAFÉ").as_deref(), Some("café"));
        assert_eq!(normalized_tag("a__b").as_deref(), Some("a_b"));
        assert_eq!(normalized_tag("-dog"), None);
        assert_eq!(normalized_tag("__"), None);
        assert_eq!(normalized_tag(&"x".repeat(129)), None);
    }
}
