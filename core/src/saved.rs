//! Saved searches, as starbooru-web's `logic::saved` compares them: one key
//! for every way of writing a search.

use std::fmt::Debug;

use starbooru_core::parse_query;

/// A search as it is saved: the server collapses the spaces too.
#[uniffi::export]
pub fn tidy_search(q: &str) -> String {
    q.split_whitespace().collect::<Vec<_>>().join(" ")
}

/// What `q` finds, as a string to compare: spaces, the order of the terms,
/// a tag's case or category and a repeated term don't change it. A search
/// the parser turns down is its tidy form.
#[uniffi::export]
pub fn search_key(q: &str) -> String {
    let Ok(mut p) = parse_query(q) else {
        return tidy_search(q);
    };
    for figure in p.figures.iter_mut().chain(p.exclude_figures.iter_mut()) {
        figure.tags.sort();
    }
    sort(&mut p.include);
    sort(&mut p.exclude);
    sort(&mut p.ratings);
    sort(&mut p.exclude_ratings);
    sort(&mut p.text);
    sort(&mut p.exclude_text);
    sort(&mut p.figures);
    sort(&mut p.exclude_figures);
    sort(&mut p.dates);
    sort(&mut p.exclude_dates);
    sort(&mut p.sizes);
    sort(&mut p.exclude_sizes);
    sort(&mut p.colors);
    sort(&mut p.exclude_colors);
    format!("{p:?}")
}

/// The terms in one order, whatever order they were typed in.
fn sort<T: Debug>(terms: &mut [T]) {
    terms.sort_by_cached_key(|t| format!("{t:?}"));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn one_search_written_two_ways_has_one_key() {
        assert_eq!(search_key("cat  dog"), search_key("dog cat"));
        assert_eq!(search_key("Cat -dog"), search_key("-dog cat"));
        assert_eq!(search_key("cat cat"), search_key("cat"));
        assert_ne!(search_key("cat"), search_key("dog"));
        assert_ne!(search_key("cat"), search_key("cat order:old"));
    }

    #[test]
    fn a_search_the_parser_refuses_keys_as_typed() {
        assert_eq!(search_key("  cat   {  "), "cat {");
        assert_eq!(tidy_search(" a  b "), "a b");
    }
}
