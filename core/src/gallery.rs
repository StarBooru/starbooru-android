//! The gallery's rules, as in starbooru-web's `logic::gallery`: a search's
//! title and sort, rewriting the search for a sort, a tile's colour and the
//! shuffle an `order:random` search keeps.

use starbooru_core::{Order, SortKey, parse_query, with_order};

/// Results come in pages of this many.
pub const PAGE_SIZE: u32 = 60;

/// The sort menu's entries, as `order:` values.
pub const SORTS: [&str; 11] = [
    "new", "old", "score", "updated", "size", "size_asc", "pixels", "longside", "tags", "tags_asc",
    "random",
];

#[uniffi::export]
pub fn page_size() -> u32 {
    PAGE_SIZE
}

#[uniffi::export]
pub fn sorts() -> Vec<String> {
    SORTS.iter().map(|s| (*s).to_owned()).collect()
}

/// The sort menu's entry for search `q`: `new` unless it asks for another
/// order the menu has.
#[uniffi::export]
pub fn current_sort(q: &str) -> String {
    let order = parse_query(q).map(|p| p.order).unwrap_or_default();
    SORTS
        .into_iter()
        .find(|v| Order::parse(v) == Some(order))
        .unwrap_or("new")
        .to_owned()
}

/// Search `q` sorted by menu entry `value`. The default order leaves no
/// `order:` term behind.
#[uniffi::export]
pub fn sorted(q: &str, value: &str) -> String {
    with_order(q, Order::parse(value))
}

/// The page's title for search `q`, without its order: the sort menu shows that.
#[uniffi::export]
pub fn search_title(q: &str) -> String {
    with_order(q, None)
}

/// `order:random` comes out different each time, so it needs a seed.
#[uniffi::export]
pub fn is_random(q: &str) -> bool {
    parse_query(q).is_ok_and(|p| p.order.key == SortKey::Random)
}

/// A post's main colour for its tile as `0xRRGGBB`, if it's `#rrggbb`.
#[uniffi::export]
pub fn tint(palette: Vec<String>) -> Option<u32> {
    let colour = palette.first()?;
    let hex = colour.strip_prefix('#')?;
    if hex.len() != 6 || !hex.bytes().all(|b| b.is_ascii_hexdigit()) {
        return None;
    }
    u32::from_str_radix(hex, 16).ok()
}

/// A seed for a new shuffle, from the clock and a count of seeds so far.
/// Mixed (splitmix64) so seeds a moment apart don't come out alike.
#[uniffi::export]
pub fn mix_seed(nanos: i64, count: u64) -> i64 {
    let mut x = u64::from_ne_bytes(nanos.to_ne_bytes()) ^ count.rotate_left(32);
    x = x.wrapping_add(0x9e37_79b9_7f4a_7c15);
    x = (x ^ (x >> 30)).wrapping_mul(0xbf58_476d_1ce4_e5b9);
    x = (x ^ (x >> 27)).wrapping_mul(0x94d0_49bb_1331_11eb);
    i64::from_ne_bytes((x ^ (x >> 31)).to_ne_bytes())
}

/// Whether less than half of a tile from `top`, `height` tall, shows between
/// `from` and `to`.
#[uniffi::export]
pub fn mostly_hidden(top: f64, height: f64, from: f64, to: f64) -> bool {
    // `min` and `max` pass over a NaN, which would count as all of it seen.
    if !top.is_finite() || !height.is_finite() {
        return true;
    }
    let seen = (top + height).min(to) - top.max(from);
    seen < height / 2.0
}

/// How far to scroll for that tile to stand in the middle of what shows.
#[uniffi::export]
pub fn to_middle(top: f64, height: f64, from: f64, to: f64) -> f64 {
    top + height / 2.0 - (from + to) / 2.0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_sort_menu_shows_the_searchs_order() {
        assert_eq!(current_sort(""), "new");
        assert_eq!(current_sort("cat"), "new");
        assert_eq!(current_sort("cat order:score"), "score");
        assert_eq!(current_sort("order:oldest cat"), "old");
        assert_eq!(current_sort("sort:size_asc"), "size_asc");
        assert_eq!(current_sort("order:random"), "random");
        assert_eq!(current_sort("order:score_asc"), "new");
        assert_eq!(current_sort("cat {"), "new");
        for value in SORTS {
            assert_eq!(current_sort(&sorted("cat", value)), value, "{value}");
        }
    }

    #[test]
    fn a_sort_rewrites_only_the_order() {
        assert_eq!(sorted("cat dog", "score"), "cat dog order:score");
        assert_eq!(sorted("cat order:score dog", "old"), "cat dog order:old");
        assert_eq!(sorted("cat sort:size", "random"), "cat order:random");
        assert_eq!(sorted("cat order:score", "new"), "cat");
        assert_eq!(sorted("", "new"), "");
    }

    #[test]
    fn the_title_leaves_the_order_out() {
        assert_eq!(search_title("cat order:score"), "cat");
        assert_eq!(search_title(""), "");
        assert!(is_random("cat order:random"));
        assert!(!is_random("cat"));
    }

    #[test]
    fn only_a_plain_colour_tints_a_tile() {
        let one = |c: &str| vec![c.to_owned()];
        assert_eq!(tint(one("#41ab78")), Some(0x41ab78));
        assert_eq!(tint(one("#41AB78")), Some(0x41ab78));
        assert_eq!(tint(vec![]), None);
        for bad in ["41ab78", "#41ab7", "#41ab788", "#41ab7g", "red"] {
            assert_eq!(tint(one(bad)), None, "{bad}");
        }
    }

    #[test]
    fn seeds_differ() {
        assert_ne!(mix_seed(1, 0), mix_seed(2, 0));
        assert_ne!(mix_seed(1, 0), mix_seed(1, 1));
    }

    #[test]
    fn a_tile_half_out_of_sight_is_mostly_hidden() {
        assert!(!mostly_hidden(100.0, 100.0, 0.0, 1000.0));
        assert!(mostly_hidden(-60.0, 100.0, 0.0, 1000.0));
        assert!(!mostly_hidden(-40.0, 100.0, 0.0, 1000.0));
        assert!(mostly_hidden(f64::NAN, 100.0, 0.0, 1000.0));
        assert_eq!(to_middle(900.0, 100.0, 0.0, 1000.0), 450.0);
    }
}
