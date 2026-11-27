//! The tag blacklist's rules, as starbooru-web's `logic::blacklist` has
//! them: what's typed into the box that adds, which entries are ratings,
//! the list after a chip or a toggle changes, which of a post's entries the
//! search it was opened from doesn't name, and a guest's own lists. Kotlin
//! keeps those lists on the device; these functions only say what they
//! become. A guest's list goes to the server with `Api::guest`.

use std::collections::HashMap;

use starbooru_core::blacklist::{BlacklistError, Entry, MAX_BLACKLIST, MAX_EXCLUDE, split_entries};
use starbooru_core::parse_query;
use starbooru_core::query::QueryError;
use starbooru_core::tag::TagError;

use crate::model::{PostTag, Rating};

/// The most guest lists the device keeps, one per server.
pub const MAX_GUEST_LISTS: usize = 8;

/// What each tag a search asked for names in the tag tree: the tag it is
/// (an alias resolves to its tag) and the tags above it, as `Api::tag`
/// says, for the tags looked up so far. A tag the server doesn't have
/// names nothing more.
pub type Above = HashMap<String, Vec<String>>;

/// The most entries an account's list holds.
#[uniffi::export]
pub fn max_blacklist_entries() -> u32 {
    u32::try_from(MAX_BLACKLIST).unwrap_or(u32::MAX)
}

/// The most entries a guest's list holds, and the admin's default, which
/// guests get.
#[uniffi::export]
pub fn max_guest_blacklist_entries() -> u32 {
    u32::try_from(MAX_EXCLUDE).unwrap_or(u32::MAX)
}

/// The most guest lists the device keeps; the oldest go.
#[uniffi::export]
pub fn max_guest_blacklists() -> u32 {
    u32::try_from(MAX_GUEST_LISTS).unwrap_or(u32::MAX)
}

// ---------- the box that adds ----------

/// Why a word can't go on the list.
#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum EntryProblem {
    /// A search term that is neither a tag nor a rating, such as
    /// `type:video` or `order:score`.
    NotATag,
    /// `rating:` with a rating the server doesn't know (g, s, q and e are).
    BadRating,
    /// It starts with `-` or `~`.
    BadPrefix,
    /// Longer than `max_tag_chars()`.
    TooLong,
    /// The list would hold more entries than it may.
    TooMany,
    /// A control character, or nothing but underscores.
    Other,
}

impl From<&TagError> for EntryProblem {
    fn from(error: &TagError) -> Self {
        match error {
            TagError::BadPrefix(_) => EntryProblem::BadPrefix,
            TagError::TooLong => EntryProblem::TooLong,
            TagError::TooMany => EntryProblem::TooMany,
            TagError::ControlChar | TagError::Empty => EntryProblem::Other,
        }
    }
}

impl From<&BlacklistError> for EntryProblem {
    fn from(error: &BlacklistError) -> Self {
        match error {
            BlacklistError::TooMany(_) => EntryProblem::TooMany,
            BlacklistError::NotATag(_) => EntryProblem::NotATag,
            BlacklistError::Tag(e) | BlacklistError::Query(QueryError::Tag(e)) => e.into(),
            BlacklistError::Query(QueryError::BadRating(_)) => EntryProblem::BadRating,
            // `type:x`, `date:soon` and the like: a search term all the same.
            BlacklistError::Query(_) => EntryProblem::NotATag,
        }
    }
}

/// A word that can't go on the list, as typed, and why. `word` is empty
/// when the trouble is the length of the list.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct BadEntry {
    pub word: String,
    pub problem: EntryProblem,
}

/// A list's tags, in its order, and its ratings; or what the box that adds
/// turned into: tags for chips, ratings to hide.
#[derive(Clone, Debug, Default, PartialEq, Eq, uniffi::Record)]
pub struct BlacklistParts {
    pub tags: Vec<String>,
    pub ratings: Vec<Rating>,
}

/// What was typed in the box that adds: its tags and ratings, or the first
/// word that can't go on the list (and then nothing else).
#[derive(Clone, Debug, Default, PartialEq, Eq, uniffi::Record)]
pub struct TypedEntries {
    pub typed: BlacklistParts,
    pub bad: Option<BadEntry>,
}

/// The list after what was typed went on it, or as it was with what's
/// wrong.
#[derive(Clone, Debug, Default, PartialEq, Eq, uniffi::Record)]
pub struct BlacklistAdd {
    pub entries: Vec<String>,
    pub bad: Option<BadEntry>,
}

/// What the box that adds turned into, in the server's own terms.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Typed {
    pub tags: Vec<String>,
    pub ratings: Vec<starbooru_core::Rating>,
}

/// A word that can't go on the list, with the server's own error.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Refused {
    pub word: String,
    pub error: BlacklistError,
}

impl From<Refused> for BadEntry {
    fn from(r: Refused) -> Self {
        Self {
            problem: (&r.error).into(),
            word: r.word,
        }
    }
}

/// Reads what was typed in the box that adds, a word an entry, as the
/// server will: `Blue_Sky` is `blue_sky`, `artist:name` is `name`, and
/// `rating:e` hides explicit posts. Other search terms are refused.
pub fn read_typed(text: &str) -> Result<Typed, Refused> {
    let mut typed = Typed::default();
    for word in text.split_whitespace() {
        match Entry::parse(word) {
            Ok(Entry::Tag(tag)) => {
                if !typed.tags.contains(&tag) {
                    typed.tags.push(tag);
                }
            }
            Ok(Entry::Rating(rating)) => {
                if !typed.ratings.contains(&rating) {
                    typed.ratings.push(rating);
                }
            }
            Err(error) => {
                return Err(Refused {
                    word: word.to_owned(),
                    error,
                });
            }
        }
    }
    Ok(typed)
}

/// A list's tags, in its order, and its ratings.
pub fn split(entries: &[String]) -> (Vec<String>, Vec<starbooru_core::Rating>) {
    split_entries(entries)
}

/// The list as it's sent and kept: its tags, then its ratings in their
/// usual order, each once.
pub fn join(tags: &[String], ratings: &[starbooru_core::Rating]) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    for tag in tags {
        if !out.contains(tag) {
            out.push(tag.clone());
        }
    }
    out.extend(
        starbooru_core::Rating::ALL
            .into_iter()
            .filter(|r| ratings.contains(r))
            .map(|r| Entry::Rating(r).as_entry()),
    );
    out
}

/// `entries` with `typed` added: new tags at the end of the tags, and its
/// ratings hidden too.
pub fn with_typed(entries: &[String], typed: &Typed) -> Vec<String> {
    let (mut tags, mut ratings) = split(entries);
    for tag in &typed.tags {
        if !tags.contains(tag) {
            tags.push(tag.clone());
        }
    }
    ratings.extend(typed.ratings.iter().copied());
    join(&tags, &ratings)
}

/// `entries` without `entry`, a tag or `rating:…`.
pub fn without(entries: &[String], entry: &str) -> Vec<String> {
    entries.iter().filter(|e| *e != entry).cloned().collect()
}

/// `entries` with `rating` hidden, or no longer hidden.
pub fn toggled(entries: &[String], rating: starbooru_core::Rating) -> Vec<String> {
    let (tags, mut ratings) = split(entries);
    if ratings.contains(&rating) {
        ratings.retain(|r| *r != rating);
    } else {
        ratings.push(rating);
    }
    join(&tags, &ratings)
}

/// `entries` with the tag `from` in its place as `to`, as a "did you mean"
/// puts it.
pub fn replaced(entries: &[String], from: &str, to: &str) -> Vec<String> {
    let (tags, ratings) = split(entries);
    let tags: Vec<String> = tags
        .into_iter()
        .map(|t| if t == from { to.to_owned() } else { t })
        .collect();
    join(&tags, &ratings)
}

/// `entry` put back where it was in `before`, into the list as it is `now`:
/// Undo of a removal.
pub fn put_back(now: &[String], before: &[String], entry: &str) -> Vec<String> {
    if now.iter().any(|e| e == entry) {
        return now.to_vec();
    }
    let at = before.iter().position(|e| e == entry).unwrap_or(now.len());
    let mut out = now.to_vec();
    out.insert(at.min(out.len()), entry.to_owned());
    // Ratings stay after the tags.
    let (tags, ratings) = split(&out);
    join(&tags, &ratings)
}

/// The entries a post matched that the search it was opened from doesn't
/// name, so they still cover it. A search names a rating entry with the
/// same `rating:`, and a tag entry with the tag, with a tag of the post
/// whose tree it heads (`details` has each tag's top), or with a tag under
/// it (`above` has what was looked up). The server shows such a post in
/// that search's results; with one entry left unnamed it wouldn't.
pub fn still_hidden(
    blacklisted: &[String],
    q: &str,
    details: &[PostTag],
    above: &Above,
) -> Vec<String> {
    let Ok(search) = parse_query(q) else {
        return blacklisted.to_vec();
    };
    let names_tag = |entry: &str| {
        search.include.iter().any(|asked| {
            asked == entry
                || details
                    .iter()
                    .any(|t| t.name == *asked && t.section.as_deref() == Some(entry))
                || above
                    .get(asked)
                    .is_some_and(|names| names.iter().any(|n| n == entry))
        })
    };
    blacklisted
        .iter()
        .filter(|entry| match Entry::parse(entry) {
            Ok(Entry::Rating(rating)) => !search.ratings.contains(&rating),
            Ok(Entry::Tag(tag)) => !names_tag(&tag),
            Err(_) => true,
        })
        .cloned()
        .collect()
}

/// The tags of search `q` to look up in the tree before a post it opened
/// can be shown uncovered: none while only ratings cover it, or once each
/// is in `above`. A tag in the middle of the tree has its top in the post's
/// details already, but not the tags between.
pub fn to_look_up(
    blacklisted: &[String],
    q: &str,
    details: &[PostTag],
    above: &Above,
) -> Vec<String> {
    let covered_by_a_tag = still_hidden(blacklisted, q, details, above)
        .iter()
        .any(|e| matches!(Entry::parse(e), Ok(Entry::Tag(_))));
    if !covered_by_a_tag {
        return Vec::new();
    }
    parse_query(q)
        .map(|search| {
            search
                .include
                .into_iter()
                .filter(|t| !above.contains_key(t))
                .collect()
        })
        .unwrap_or_default()
}

fn to_core(ratings: &[Rating]) -> Vec<starbooru_core::Rating> {
    ratings.iter().copied().map(Into::into).collect()
}

fn from_core(ratings: Vec<starbooru_core::Rating>) -> Vec<Rating> {
    ratings.into_iter().map(Into::into).collect()
}

/// [`read_typed`] for Kotlin.
#[uniffi::export]
pub fn blacklist_read_typed(text: &str) -> TypedEntries {
    match read_typed(text) {
        Ok(typed) => TypedEntries {
            typed: BlacklistParts {
                tags: typed.tags,
                ratings: from_core(typed.ratings),
            },
            bad: None,
        },
        Err(refused) => TypedEntries {
            typed: BlacklistParts::default(),
            bad: Some(refused.into()),
        },
    }
}

/// What the box that adds does on a space or Enter: `entries` with `text`
/// on it, unless a word can't go there or the list would pass `max`
/// (`max_blacklist_entries()` for an account, `max_guest_blacklist_entries()`
/// for a guest or the admin's default).
#[uniffi::export]
pub fn blacklist_add_typed(entries: &[String], text: &str, max: u32) -> BlacklistAdd {
    let refused = |bad: BadEntry| BlacklistAdd {
        entries: entries.to_vec(),
        bad: Some(bad),
    };
    match read_typed(text) {
        Err(r) => refused(r.into()),
        Ok(typed) => {
            let added = with_typed(entries, &typed);
            if added.len() > usize::try_from(max).unwrap_or(usize::MAX) {
                refused(BadEntry {
                    word: String::new(),
                    problem: EntryProblem::TooMany,
                })
            } else {
                BlacklistAdd {
                    entries: added,
                    bad: None,
                }
            }
        }
    }
}

/// A list's tags, in its order, and its ratings. An entry that doesn't
/// read is in neither.
#[uniffi::export]
pub fn blacklist_split(entries: &[String]) -> BlacklistParts {
    let (tags, ratings) = split(entries);
    BlacklistParts {
        tags,
        ratings: from_core(ratings),
    }
}

/// The list as it's sent and kept: the tags, then the ratings in their
/// usual order (`rating:safe` first), each once.
#[uniffi::export]
pub fn blacklist_join(parts: BlacklistParts) -> Vec<String> {
    join(&parts.tags, &to_core(&parts.ratings))
}

/// `entries` with `typed` added: new tags at the end of the tags, and its
/// ratings hidden too.
#[uniffi::export]
pub fn blacklist_with_typed(entries: &[String], typed: BlacklistParts) -> Vec<String> {
    let typed = Typed {
        tags: typed.tags,
        ratings: to_core(&typed.ratings),
    };
    with_typed(entries, &typed)
}

/// `entries` without `entry`, a tag or `rating:…`: a chip's ×.
#[uniffi::export]
pub fn blacklist_without(entries: &[String], entry: &str) -> Vec<String> {
    without(entries, entry)
}

/// `entries` with `rating` hidden, or no longer hidden: its toggle.
#[uniffi::export]
pub fn blacklist_toggled(entries: &[String], rating: Rating) -> Vec<String> {
    toggled(entries, rating.into())
}

/// `entries` with the tag `from` in its place as `to`: a "did you mean"
/// taken.
#[uniffi::export]
pub fn blacklist_replaced(entries: &[String], from: &str, to: &str) -> Vec<String> {
    replaced(entries, from, to)
}

/// Undo of a removal: `entry` back where it was in `before`, in the list
/// as it is `now`.
#[uniffi::export]
pub fn blacklist_put_back(now: &[String], before: &[String], entry: &str) -> Vec<String> {
    put_back(now, before, entry)
}

/// Which of a post's `blacklisted` entries the search `q` it was opened
/// from doesn't name, so the viewer still covers it. `details` is the
/// post's `tag_details`; `above` holds, for each tag of `q` looked up with
/// `Api::tag`, the tag it is and the tags above it (see
/// `blacklist_to_look_up`). Empty: show the post.
#[uniffi::export]
pub fn blacklist_still_hidden(
    blacklisted: &[String],
    q: &str,
    details: &[PostTag],
    above: HashMap<String, Vec<String>>,
) -> Vec<String> {
    still_hidden(blacklisted, q, details, &above)
}

/// The tags of `q` to look up with `Api::tag` before the post can be
/// shown uncovered: none while only ratings cover it, or once each is in
/// `above`. Put each answer in `above` as its name, its aliases' tag and
/// its ancestors (`TagDetail`), then ask `blacklist_still_hidden` again.
#[uniffi::export]
pub fn blacklist_to_look_up(
    blacklisted: &[String],
    q: &str,
    details: &[PostTag],
    above: HashMap<String, Vec<String>>,
) -> Vec<String> {
    to_look_up(blacklisted, q, details, &above)
}

// ---------- a guest's own list ----------

/// A guest's own list on one server, kept on the device. Until one is
/// kept, the server's default applies.
#[derive(Clone, Debug, Default, PartialEq, Eq, uniffi::Record)]
pub struct GuestBlacklist {
    /// `server|guest`, as `guest_blacklist_owner` makes it.
    pub owner: String,
    pub entries: Vec<String>,
}

/// Whose a guest's list is: the guests of one server (`Api::server()`).
#[uniffi::export]
pub fn guest_blacklist_owner(server: &str) -> String {
    format!("{server}|guest")
}

/// The list kept for `owner`, or `None` while the guest hasn't changed
/// theirs: then `Api::guest(server, None)` gets the server's default.
#[uniffi::export]
pub fn guest_blacklist(lists: &[GuestBlacklist], owner: &str) -> Option<Vec<String>> {
    lists
        .iter()
        .find(|l| l.owner == owner)
        .map(|l| l.entries.clone())
}

/// The lists to keep after `owner`'s became `entries`, first, or was
/// forgotten for `None`. The oldest go past `max_guest_blacklists()`. An
/// empty list hides nothing, which isn't the same as none.
#[uniffi::export]
pub fn set_guest_blacklist(
    mut lists: Vec<GuestBlacklist>,
    owner: &str,
    entries: Option<Vec<String>>,
) -> Vec<GuestBlacklist> {
    lists.retain(|l| l.owner != owner);
    if let Some(entries) = entries {
        lists.insert(
            0,
            GuestBlacklist {
                owner: owner.to_owned(),
                entries,
            },
        );
    }
    lists.truncate(MAX_GUEST_LISTS);
    lists
}

#[cfg(test)]
mod tests {
    use starbooru_core::Rating as R;

    use super::*;
    use crate::model::TagCategory;

    fn list(items: &[&str]) -> Vec<String> {
        items.iter().map(|s| (*s).to_owned()).collect()
    }

    fn tag(name: &str, section: Option<&str>) -> PostTag {
        PostTag {
            name: name.into(),
            category: TagCategory::General,
            section: section.map(str::to_owned),
        }
    }

    #[test]
    fn a_typed_word_becomes_a_tag_or_a_rating() {
        let typed = read_typed("  Blue_Sky artist:Some_One rating:e cat cat RATING:Q ").unwrap();
        assert_eq!(typed.tags, ["blue_sky", "some_one", "cat"]);
        assert_eq!(typed.ratings, [R::Explicit, R::Questionable]);
        assert_eq!(read_typed("").unwrap(), Typed::default());
        // A search term isn't an entry, and says which word.
        let bad = read_typed("cat type:video").unwrap_err();
        assert_eq!(bad.word, "type:video");
        assert!(matches!(bad.error, BlacklistError::NotATag(_)));
        assert!(matches!(
            read_typed("-dog").unwrap_err().error,
            BlacklistError::Tag(_)
        ));
        assert!(matches!(
            read_typed("rating:x").unwrap_err().error,
            BlacklistError::Query(_)
        ));
    }

    #[test]
    fn tags_come_first_and_ratings_after_in_their_order() {
        let entries = list(&["cat", "rating:explicit", "dog", "rating:safe"]);
        let (tags, ratings) = split(&entries);
        assert_eq!(tags, ["cat", "dog"]);
        assert_eq!(ratings, [R::Explicit, R::Safe]);
        assert_eq!(
            join(&tags, &ratings),
            ["cat", "dog", "rating:safe", "rating:explicit"]
        );
        assert_eq!(join(&list(&["a", "a"]), &[]), ["a"]);
    }

    #[test]
    fn the_list_changes_a_chip_or_a_toggle_at_a_time() {
        let entries = list(&["cat", "rating:explicit"]);
        let typed = Typed {
            tags: list(&["dog", "cat"]),
            ratings: vec![R::Questionable],
        };
        assert_eq!(
            with_typed(&entries, &typed),
            ["cat", "dog", "rating:questionable", "rating:explicit"]
        );
        assert_eq!(without(&entries, "cat"), ["rating:explicit"]);
        assert_eq!(without(&entries, "rating:explicit"), ["cat"]);
        assert_eq!(toggled(&entries, R::Explicit), ["cat"]);
        assert_eq!(
            toggled(&entries, R::Safe),
            ["cat", "rating:safe", "rating:explicit"]
        );
        assert_eq!(
            replaced(&list(&["catt", "dog"]), "catt", "cat"),
            ["cat", "dog"]
        );
        // Corrected into a tag already there: once.
        assert_eq!(replaced(&list(&["catt", "cat"]), "catt", "cat"), ["cat"]);
    }

    #[test]
    fn undo_puts_an_entry_back_where_it_was() {
        let before = list(&["a", "b", "c", "rating:explicit"]);
        let now = without(&before, "b");
        assert_eq!(put_back(&now, &before, "b"), before);
        // Something added meanwhile stays.
        let now = list(&["a", "c", "d"]);
        assert_eq!(put_back(&now, &before, "b"), ["a", "b", "c", "d"]);
        assert_eq!(put_back(&before, &before, "b"), before);
        let now = without(&before, "rating:explicit");
        assert_eq!(put_back(&now, &before, "rating:explicit"), before);
    }

    #[test]
    fn a_search_that_names_an_entry_uncovers_it() {
        let flags = list(&["cat", "rating:explicit"]);
        let details = [tag("cat", None), tag("boots", Some("footwear"))];
        let none = Above::new();
        let hidden = |entries: &[String], q: &str| still_hidden(entries, q, &details, &none);
        assert_eq!(hidden(&flags, ""), flags);
        // Only what the search names: the rest still covers it.
        assert_eq!(hidden(&flags, "cat sky"), ["rating:explicit"]);
        assert!(hidden(&flags, "cat rating:e").is_empty());
        assert!(hidden(&flags, "rating:explicit artist:cat").is_empty());
        // Leaving a tag out, or the rating, names nothing.
        assert_eq!(hidden(&flags, "-cat -rating:e"), flags);
        // A tag under the entry's tree, as the post's tags say.
        let footwear = list(&["footwear"]);
        assert!(hidden(&footwear, "boots").is_empty());
        assert_eq!(hidden(&footwear, "sandals"), footwear);
        // A search that doesn't read names nothing.
        assert_eq!(hidden(&flags, "rating:zzz cat"), flags);
    }

    #[test]
    fn a_tag_in_the_middle_of_the_tree_is_named_by_the_tags_under_it() {
        // clothing › footwear › boots: the post's details know only the top.
        let details = [tag("boots", Some("clothing")), tag("sky", None)];
        let footwear = list(&["footwear"]);
        let mut above = Above::new();
        assert_eq!(still_hidden(&footwear, "boots", &details, &above), footwear);
        assert_eq!(
            to_look_up(&footwear, "boots sky", &details, &above),
            ["boots", "sky"]
        );
        above.insert("boots".into(), list(&["boots", "clothing", "footwear"]));
        above.insert("sky".into(), list(&["sky"]));
        assert!(still_hidden(&footwear, "boots", &details, &above).is_empty());
        assert!(to_look_up(&footwear, "boots sky", &details, &above).is_empty());
        // The top is named as before, and a tag not under it names nothing.
        assert!(still_hidden(&list(&["clothing"]), "boots", &details, &above).is_empty());
        assert_eq!(still_hidden(&footwear, "sky", &details, &above), footwear);
        // An alias stands for its tag.
        above.insert("kitty".into(), list(&["cat"]));
        assert!(still_hidden(&list(&["cat"]), "kitty", &details, &above).is_empty());
        // Nothing to look up while the search names every entry, only a
        // rating covers the post, or the search has no tags.
        let rated = list(&["rating:explicit"]);
        assert!(to_look_up(&rated, "boots", &details, &Above::new()).is_empty());
        assert!(to_look_up(&list(&["boots"]), "boots", &details, &Above::new()).is_empty());
        assert!(to_look_up(&footwear, "rating:s", &details, &Above::new()).is_empty());
        // Only what isn't known yet.
        assert_eq!(
            to_look_up(&footwear, "sky moon", &details, &above),
            ["moon"]
        );
        // A rating left unnamed still covers it, whatever the tree says.
        let both = list(&["footwear", "rating:explicit"]);
        assert_eq!(
            still_hidden(&both, "boots", &details, &above),
            ["rating:explicit"]
        );
    }

    #[test]
    fn a_guest_keeps_a_list_per_server() {
        let mut lists = Vec::new();
        let a = guest_blacklist_owner("https://a.example");
        assert_eq!(a, "https://a.example|guest");
        assert_eq!(guest_blacklist(&lists, &a), None);
        lists = set_guest_blacklist(lists, &a, Some(list(&["cat"])));
        assert_eq!(guest_blacklist(&lists, &a), Some(list(&["cat"])));
        // An empty list hides nothing; the default doesn't come back.
        lists = set_guest_blacklist(lists, &a, Some(Vec::new()));
        assert_eq!(guest_blacklist(&lists, &a), Some(Vec::new()));
        lists = set_guest_blacklist(lists, &a, None);
        assert_eq!(guest_blacklist(&lists, &a), None);
        for i in 0..MAX_GUEST_LISTS + 3 {
            let owner = guest_blacklist_owner(&format!("s{i}"));
            lists = set_guest_blacklist(lists, &owner, Some(Vec::new()));
        }
        assert_eq!(lists.len(), MAX_GUEST_LISTS);
        assert_eq!(guest_blacklist(&lists, &guest_blacklist_owner("s0")), None);
        assert!(guest_blacklist(&lists, &guest_blacklist_owner("s10")).is_some());
        // The latest first.
        assert_eq!(lists[0].owner, guest_blacklist_owner("s10"));
    }

    #[test]
    fn kotlin_gets_each_problem_by_name() {
        let problem = |text: &str| blacklist_read_typed(text).bad.map(|b| (b.word, b.problem));
        assert_eq!(problem("cat rating:e"), None);
        let read = blacklist_read_typed("Cat rating:e");
        assert_eq!(read.typed.tags, ["cat"]);
        assert_eq!(read.typed.ratings, [Rating::Explicit]);
        assert_eq!(
            problem("cat type:video"),
            Some(("type:video".into(), EntryProblem::NotATag))
        );
        assert_eq!(
            problem("type:x"),
            Some(("type:x".into(), EntryProblem::NotATag))
        );
        assert_eq!(
            problem("rating:x"),
            Some(("rating:x".into(), EntryProblem::BadRating))
        );
        assert_eq!(
            problem("-dog"),
            Some(("-dog".into(), EntryProblem::BadPrefix))
        );
        let long = "a".repeat(starbooru_core::tag::MAX_TAG_CHARS + 1);
        assert_eq!(problem(&long), Some((long.clone(), EntryProblem::TooLong)));
        assert_eq!(problem("___"), Some(("___".into(), EntryProblem::Other)));
        // Nothing comes back with a word that's refused.
        assert_eq!(
            blacklist_read_typed("cat -dog").typed,
            BlacklistParts::default()
        );
    }

    #[test]
    fn adding_stops_at_the_lists_size() {
        let entries = list(&["cat", "rating:explicit"]);
        let added = blacklist_add_typed(&entries, "dog rating:q", 4);
        assert_eq!(
            added,
            BlacklistAdd {
                entries: list(&["cat", "dog", "rating:questionable", "rating:explicit"]),
                bad: None
            }
        );
        let full = blacklist_add_typed(&entries, "dog bird rating:q", 4);
        assert_eq!(full.entries, entries);
        assert_eq!(
            full.bad,
            Some(BadEntry {
                word: String::new(),
                problem: EntryProblem::TooMany
            })
        );
        // What's there already doesn't count again.
        assert!(
            blacklist_add_typed(&entries, "cat rating:e", 2)
                .bad
                .is_none()
        );
        let bad = blacklist_add_typed(&entries, "dog order:new", 100);
        assert_eq!(bad.entries, entries);
        assert_eq!(bad.bad.map(|b| b.problem), Some(EntryProblem::NotATag));
        assert_eq!(max_blacklist_entries(), 500);
        assert_eq!(max_guest_blacklist_entries(), 100);
    }

    #[test]
    fn the_exported_rules_take_the_apps_ratings() {
        let entries = list(&["cat", "rating:explicit", "dog"]);
        let parts = blacklist_split(&entries);
        assert_eq!(parts.tags, ["cat", "dog"]);
        assert_eq!(parts.ratings, [Rating::Explicit]);
        assert_eq!(
            blacklist_join(parts.clone()),
            ["cat", "dog", "rating:explicit"]
        );
        assert_eq!(
            blacklist_toggled(&entries, Rating::Safe),
            ["cat", "dog", "rating:safe", "rating:explicit"]
        );
        let typed = BlacklistParts {
            tags: list(&["bird"]),
            ratings: vec![Rating::Sensitive],
        };
        assert_eq!(
            blacklist_with_typed(&entries, typed),
            ["cat", "dog", "bird", "rating:sensitive", "rating:explicit"]
        );
        let details = [tag("boots", Some("clothing"))];
        let flags = list(&["footwear"]);
        let mut above = HashMap::new();
        assert_eq!(
            blacklist_to_look_up(&flags, "boots", &details, above.clone()),
            ["boots"]
        );
        above.insert("boots".to_owned(), list(&["boots", "clothing", "footwear"]));
        assert!(blacklist_still_hidden(&flags, "boots", &details, above).is_empty());
    }
}
