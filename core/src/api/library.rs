//! Library imports: a whole library from another self-hosted booru, which
//! the server walks by itself. Any account on a private server, admins only
//! on a public one (`forbidden`); with URL import off, checking, starting
//! and retrying are `bad_request`. Ask `library_imports` again every few
//! seconds while one is `active`.

use super::Api;
use crate::error::Result;
use crate::model::{LibraryCheck, LibraryFailure, LibraryImport, LibrarySite};

#[uniffi::export(async_runtime = "tokio")]
impl Api {
    /// Reads the site's first page once: how many posts it has, and
    /// whether aliases and implications would come too.
    pub async fn check_library(&self, site: LibrarySite) -> Result<LibraryCheck> {
        Ok(self.client.check_library(&site.into()).await?.into())
    }

    /// The account's latest 50 imports, newest first.
    pub async fn library_imports(&self) -> Result<Vec<LibraryImport>> {
        let imports = self.client.library_imports().await?;
        Ok(imports.into_iter().map(Into::into).collect())
    }

    /// Queues an import. Past `max_active_library_imports()` waiting or
    /// running it is `forbidden`.
    pub async fn start_library_import(&self, site: LibrarySite) -> Result<LibraryImport> {
        Ok(self.client.start_library_import(&site.into()).await?.into())
    }

    /// Stops it where it is; what came across stays.
    pub async fn stop_library_import(&self, id: i64) -> Result<LibraryImport> {
        Ok(self.client.stop_library_import(id).await?.into())
    }

    /// Walks the site again for what's missing or failed. The account comes
    /// again, as an import forgets it once it ends. One going is `running`.
    pub async fn retry_library_import(
        &self,
        id: i64,
        login: Option<String>,
        secret: Option<String>,
    ) -> Result<LibraryImport> {
        let retry = starbooru_core::LibraryRetry { login, secret };
        Ok(self.client.retry_library_import(id, &retry).await?.into())
    }

    /// Forgets a finished import; its posts stay. One going is `running`.
    pub async fn delete_library_import(&self, id: i64) -> Result<()> {
        Ok(self.client.delete_library_import(id).await?)
    }

    /// Up to 500 posts it couldn't bring, with why.
    pub async fn library_failures(&self, id: i64) -> Result<Vec<LibraryFailure>> {
        let failures = self.client.library_failures(id).await?;
        Ok(failures.into_iter().map(Into::into).collect())
    }
}
