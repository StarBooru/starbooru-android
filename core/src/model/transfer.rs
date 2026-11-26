//! What an upload refused as a duplicate points at.

/// The post a refused upload already is.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Record)]
pub struct DuplicatePost {
    pub post_id: i64,
    /// Restoring it from the bin brings it back; the upload never will.
    pub in_recycle_bin: bool,
}

/// The post a `duplicate` refusal names, from its `reason`. The server
/// sends only a code and words, and its words name the post
/// (`this file is already post #12`). `None` when they don't.
#[uniffi::export]
pub fn duplicate_of(reason: &str) -> Option<DuplicatePost> {
    let (_, after) = reason.split_once("post #")?;
    let digits: String = after.chars().take_while(char::is_ascii_digit).collect();
    let post_id = digits.parse::<i64>().ok().filter(|id| *id > 0)?;
    Some(DuplicatePost {
        post_id,
        in_recycle_bin: reason.contains("recycle bin"),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_the_post_out_of_the_servers_words() {
        assert_eq!(
            duplicate_of("this file is already post #12"),
            Some(DuplicatePost {
                post_id: 12,
                in_recycle_bin: false
            })
        );
        assert_eq!(
            duplicate_of("this file is post #7, in the recycle bin: restore it from there"),
            Some(DuplicatePost {
                post_id: 7,
                in_recycle_bin: true
            })
        );
    }

    #[test]
    fn words_without_a_post_name_none() {
        assert_eq!(duplicate_of("this file was already uploaded"), None);
        assert_eq!(duplicate_of("post #"), None);
        assert_eq!(duplicate_of("post #0"), None);
        assert_eq!(duplicate_of("post #99999999999999999999"), None);
    }
}
