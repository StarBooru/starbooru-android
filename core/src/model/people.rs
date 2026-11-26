//! The people page: the collection's characters and their franchises.

use starbooru_client::Client;

use super::Person;

/// The most people one page holds.
pub const MAX_PEOPLE_LIMIT: u32 = starbooru_core::people::MAX_PEOPLE_LIMIT;
/// The most franchises one page holds.
pub const MAX_FRANCHISE_LIMIT: u32 = starbooru_core::people::MAX_FRANCHISE_LIMIT;

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum PeopleOrder {
    /// Most posts first.
    Posts,
    Name,
    /// By franchise, the one with the most posts first, those without one
    /// last; most posts first within each.
    Franchise,
}

impl From<PeopleOrder> for starbooru_core::PeopleOrder {
    fn from(o: PeopleOrder) -> Self {
        match o {
            PeopleOrder::Posts => starbooru_core::PeopleOrder::Posts,
            PeopleOrder::Name => starbooru_core::PeopleOrder::Name,
            PeopleOrder::Franchise => starbooru_core::PeopleOrder::Franchise,
        }
    }
}

/// Which page of people to show.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct PeopleQuery {
    pub order: PeopleOrder,
    /// Characters whose name holds this, written as a tag; empty for all.
    #[uniffi(default = "")]
    pub q: String,
    /// One franchise's people; `""` for those without one, `None` for all.
    #[uniffi(default)]
    pub franchise: Option<String>,
    /// From 1.
    #[uniffi(default = 1)]
    pub page: u32,
    /// Clamped to 1 to 100.
    #[uniffi(default = 60)]
    pub limit: u32,
}

impl From<PeopleQuery> for starbooru_core::PeopleQuery {
    fn from(q: PeopleQuery) -> Self {
        Self {
            q: q.q,
            franchise: q.franchise,
            order: q.order.into(),
            page: q.page.max(1),
            limit: q.limit.clamp(1, MAX_PEOPLE_LIMIT),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct PeoplePage {
    pub people: Vec<Person>,
    pub page: u32,
    pub has_more: bool,
}

impl PeoplePage {
    pub(crate) fn from_core(p: starbooru_core::PeoplePage, client: &Client) -> Self {
        Self {
            people: p
                .people
                .into_iter()
                .map(|person| Person::from_core(person, client))
                .collect(),
            page: p.page,
            has_more: p.has_more,
        }
    }
}

/// A franchise with people in the collection.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct Franchise {
    /// Its tag; `PeopleQuery::franchise` takes it.
    pub name: String,
    /// Its characters the reader sees.
    pub people: i64,
    /// Live posts with its tag.
    pub posts: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FranchisePage {
    /// The most people first.
    pub franchises: Vec<Franchise>,
    pub page: u32,
    pub has_more: bool,
    /// People without a franchise.
    pub without: i64,
}

impl From<starbooru_core::FranchisePage> for FranchisePage {
    fn from(p: starbooru_core::FranchisePage) -> Self {
        Self {
            franchises: p
                .franchises
                .into_iter()
                .map(|f| Franchise {
                    name: f.name,
                    people: f.people,
                    posts: f.posts,
                })
                .collect(),
            page: p.page,
            has_more: p.has_more,
            without: p.without,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_query_keeps_to_the_servers_range() {
        let query = PeopleQuery {
            order: PeopleOrder::Franchise,
            q: "miku".into(),
            franchise: Some(String::new()),
            page: 0,
            limit: 500,
        };
        let query = starbooru_core::PeopleQuery::from(query);
        assert_eq!((query.page, query.limit), (1, MAX_PEOPLE_LIMIT));
        assert_eq!(query.order, starbooru_core::PeopleOrder::Franchise);
        assert_eq!(query.franchise.as_deref(), Some(""));
        for order in [PeopleOrder::Posts, PeopleOrder::Name] {
            let back = starbooru_core::PeopleOrder::from(order);
            assert_eq!(format!("{order:?}").to_lowercase(), back.as_str());
        }
    }

    #[test]
    fn a_page_of_people_gets_absolute_faces() {
        let client = Client::new("https://booru.example").unwrap();
        let page: starbooru_core::PeoplePage = serde_json::from_value(serde_json::json!({
            "people": [{"name": "saber", "posts": 31, "franchise": "fate_(series)",
                        "face_url": "/media/face/9f.jpg?sig=1", "thumb_url": null}],
            "page": 2, "limit": 1, "has_more": true
        }))
        .unwrap();
        let page = PeoplePage::from_core(page, &client);
        assert_eq!(
            page.people[0].face_url.as_deref(),
            Some("https://booru.example/media/face/9f.jpg?sig=1")
        );
        assert_eq!((page.page, page.has_more), (2, true));
        let franchises: starbooru_core::FranchisePage = serde_json::from_value(serde_json::json!({
            "franchises": [{"name": "fate_(series)", "people": 4, "posts": 40}],
            "page": 1, "limit": 50, "has_more": false, "without": 3
        }))
        .unwrap();
        let franchises = FranchisePage::from(franchises);
        assert_eq!(franchises.franchises[0].people, 4);
        assert_eq!(franchises.without, 3);
    }
}
