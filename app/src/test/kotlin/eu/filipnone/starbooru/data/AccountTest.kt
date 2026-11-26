package eu.filipnone.starbooru.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eu.filipnone.starbooru.FakeApi
import eu.filipnone.starbooru.FakeApis
import eu.filipnone.starbooru.FlakyStore
import eu.filipnone.starbooru.SERVER
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.Role
import eu.filipnone.starbooru.preferencesIn
import eu.filipnone.starbooru.serverInfo
import eu.filipnone.starbooru.signedIn
import eu.filipnone.starbooru.thrown
import eu.filipnone.starbooru.user
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AccountTest {
    @get:Rule val tmp = TemporaryFolder()

    private val apis = FakeApis()
    private val secrets = MemorySecretStore()

    /** Whether writes to the stores fail, as on a full disk. */
    private var diskFull = false

    private fun TestScope.store(): DataStore<Preferences> = FlakyStore(preferencesIn(tmp.root, backgroundScope)) { diskFull }

    private fun TestScope.account(store: DataStore<Preferences> = store()) = Account(store, secrets, apis)

    private val Account.fake: FakeApi?
        get() = api.value as? FakeApi

    /** Runs [block] on an Account over its own store, then closes that store, as when the process dies. */
    private suspend fun TestScope.inProcess(block: suspend (Account) -> Unit) {
        val job = Job(backgroundScope.coroutineContext.job)
        val store = preferencesIn(tmp.root, CoroutineScope(backgroundScope.coroutineContext + job))
        val account = Account(FlakyStore(store) { diskFull }, secrets, apis)
        account.load()
        block(account)
        job.cancel()
        job.join()
    }

    @Test
    fun startsBeforeAnythingIsRead() =
        runTest {
            assertEquals(AccountState.Starting, account().state.value)
        }

    @Test
    fun nothingStoredIsSignedOutWithNoServer() =
        runTest {
            val account = account()
            account.load()
            assertEquals(AccountState.SignedOut(lastServer = ""), account.state.value)
            assertNull(account.api.value)
            assertTrue(apis.made.isEmpty())
        }

    @Test
    fun signingInKeepsTheTokenAndMakesAnApiWithIt() =
        runTest {
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn(token = "t1"))

            assertEquals(AccountState.SignedIn(SERVER, Identity(1, "admin", admin = true)), account.state.value)
            assertEquals("t1", secrets.read())
            val api = account.fake
            assertNotNull(api)
            assertEquals("t1", api?.token)
            assertEquals(SERVER, api?.server())
            assertSame(apis.made.last(), api)
        }

    @Test
    fun aUserIsNoAdmin() =
        runTest {
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn(user = user(id = 7, username = "ada", role = Role.USER)))
            assertEquals(AccountState.SignedIn(SERVER, Identity(7, "ada", admin = false)), account.state.value)
        }

    @Test
    fun signingOutEndsTheSessionOnTheServerAndKeepsTheAddress() =
        runTest {
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn(token = "t1"))
            account.signOut()

            assertEquals(listOf("t1"), apis.backend.signedOut)
            assertEquals(AccountState.SignedOut(SERVER, expired = false), account.state.value)
            assertNull(account.api.value)
            assertNull(secrets.read())
        }

    @Test
    fun signingOutOfflineStillSignsOut() =
        runTest {
            apis.backend.signOut = { throw CoreException.Unreachable("no network") }
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn())
            account.signOut()

            assertEquals(AccountState.SignedOut(SERVER), account.state.value)
            assertNull(secrets.read())
        }

    @Test
    fun callPassesTheApiThrough() =
        runTest {
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn())
            assertEquals(SERVER, account.call { it.server() })
        }

    @Test
    fun aRefusedTokenEndsTheSessionAsExpired() =
        runTest {
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn())

            val e = thrown<CoreException.Unauthorized> { account.call { throw CoreException.Unauthorized("revoked") } }

            assertEquals("revoked", e.reason)
            assertEquals(AccountState.SignedOut(SERVER, expired = true), account.state.value)
            assertNull(account.api.value)
            assertNull(secrets.read())
            // Nothing to tell a server that already forgot the token.
            assertTrue(apis.backend.signedOut.isEmpty())
        }

    @Test
    fun otherFailuresKeepTheSession() =
        runTest {
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn())

            thrown<CoreException.Unreachable> { account.call { throw CoreException.Unreachable("timeout") } }

            assertTrue(account.state.value is AccountState.SignedIn)
            assertEquals("token-admin", secrets.read())
        }

    @Test
    fun callWhileSignedOutIsUnauthorized() =
        runTest {
            val account = account()
            account.load()
            thrown<CoreException.Unauthorized> { account.call { it.server() } }
            assertEquals(AccountState.SignedOut(""), account.state.value)
        }

    @Test
    fun browsingAsAGuestMakesAnApiWithoutAToken() =
        runTest {
            val account = account()
            account.load()
            account.browseAsGuest(SERVER)

            assertEquals(AccountState.Guest(SERVER), account.state.value)
            val api = account.fake
            assertNotNull(api)
            assertNull(api?.token)
        }

    @Test
    fun toSignInGoesBackFromAGuestKeepingTheServer() =
        runTest {
            val account = account()
            account.load()
            account.browseAsGuest(SERVER)
            account.toSignIn()

            assertEquals(AccountState.SignedOut(SERVER), account.state.value)
            assertNull(account.api.value)
        }

    @Test
    fun toSignInWhileSignedOutDoesNothing() =
        runTest {
            val account = account()
            account.load()
            account.toSignIn()
            assertEquals(AccountState.SignedOut(""), account.state.value)
        }

    @Test
    fun aGuestIsSentToSignInWhenTheServerStopsTakingGuests() =
        runTest {
            apis.backend.info = { serverInfo(guests = false) }
            val account = account()
            account.load()
            account.browseAsGuest(SERVER)

            thrown<CoreException.Unauthorized> { account.call { throw CoreException.Unauthorized("guests off") } }

            assertEquals(AccountState.SignedOut(SERVER), account.state.value)
        }

    @Test
    fun aGuestStaysWhileTheServerStillTakesGuests() =
        runTest {
            apis.backend.info = { serverInfo(guests = true) }
            val account = account()
            account.load()
            account.browseAsGuest(SERVER)

            thrown<CoreException.Unauthorized> { account.call { throw CoreException.Unauthorized("private post") } }

            assertEquals(AccountState.Guest(SERVER), account.state.value)
        }

    @Test
    fun anAddressTheCoreRefusesStaysSignedOut() =
        runTest {
            apis.refuse = CoreException.InvalidAddress("bad")
            val account = account()
            account.load()
            account.browseAsGuest("ftp://nope")

            assertEquals(AccountState.SignedOut("ftp://nope"), account.state.value)
            assertNull(account.api.value)
        }

    @Test
    fun infoIsKeptWhenTheServerStopsAnswering() =
        runTest {
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn())
            assertNull(account.info.value)

            account.refreshInfo()
            assertEquals(serverInfo(), account.info.value)

            apis.backend.info = { throw CoreException.Unreachable("down") }
            account.refreshInfo()
            assertEquals(serverInfo(), account.info.value)
        }

    @Test
    fun infoIsForgottenWithTheServer() =
        runTest {
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn())
            account.refreshInfo()
            account.signOut()
            assertNull(account.info.value)
        }

    // ---------- after the process dies ----------

    @Test
    fun aFreshAccountRestoresTheSession() =
        runTest {
            inProcess { it.signedIn(SERVER, signedIn(token = "t1", user = user(id = 3, username = "ada", role = Role.USER))) }
            inProcess { account ->
                assertEquals(AccountState.SignedIn(SERVER, Identity(3, "ada", admin = false)), account.state.value)
                assertEquals("t1", account.fake?.token)
            }
        }

    @Test
    fun aFreshAccountRestoresAnAdmin() =
        runTest {
            inProcess { it.signedIn(SERVER, signedIn()) }
            inProcess { assertEquals(AccountState.SignedIn(SERVER, Identity(1, "admin", admin = true)), it.state.value) }
        }

    @Test
    fun aFreshAccountRestoresAGuest() =
        runTest {
            inProcess { it.browseAsGuest(SERVER) }
            inProcess { account ->
                assertEquals(AccountState.Guest(SERVER), account.state.value)
                assertNull(account.fake?.token)
            }
        }

    @Test
    fun aFreshAccountAfterSigningOutRemembersTheServer() =
        runTest {
            inProcess {
                it.signedIn(SERVER, signedIn())
                it.signOut()
            }
            inProcess { assertEquals(AccountState.SignedOut(SERVER), it.state.value) }
        }

    @Test
    fun aFreshAccountAfterExpiryIsSignedOut() =
        runTest {
            inProcess { it.signedIn(SERVER, signedIn()) }
            inProcess { account -> thrown<CoreException.Unauthorized> { account.call { throw CoreException.Unauthorized("x") } } }
            inProcess { assertEquals(AccountState.SignedOut(SERVER), it.state.value) }
        }

    @Test
    fun aLostTokenAsksToSignInAgain() =
        runTest {
            inProcess { it.signedIn(SERVER, signedIn()) }
            // The Keystore key went away: the token no longer reads.
            secrets.write(null)
            inProcess { account ->
                assertEquals(AccountState.SignedOut(SERVER), account.state.value)
                assertNull(account.api.value)
            }
        }

    // ---------- a late answer to an earlier session ----------

    /** A request on the session in use that the server refuses once [refuse] completes. What it threw, if anything. */
    private fun TestScope.refusedLater(
        account: Account,
        refuse: CompletableDeferred<Unit>,
    ): Deferred<Throwable?> =
        async {
            runCatching {
                account.call<Unit> {
                    refuse.await()
                    throw CoreException.Unauthorized("old token")
                }
            }.exceptionOrNull()
        }.also { runCurrent() }

    @Test
    fun aLate401FromAnEarlierSessionLeavesTheNewOne() =
        runTest {
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn(token = "old"))
            val refuse = CompletableDeferred<Unit>()
            val late = refusedLater(account, refuse)

            account.signOut()
            account.signedIn(SERVER, signedIn(token = "new"))
            refuse.complete(Unit)

            // Whoever asked still hears of it.
            assertTrue(late.await() is CoreException.Unauthorized)
            assertEquals(AccountState.SignedIn(SERVER, Identity(1, "admin", admin = true)), account.state.value)
            assertEquals("new", account.fake?.token)
            assertEquals("new", secrets.read())
        }

    @Test
    fun aLate401AfterSigningOutIsNoExpiry() =
        runTest {
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn())
            val refuse = CompletableDeferred<Unit>()
            val late = refusedLater(account, refuse)

            account.signOut()
            refuse.complete(Unit)

            assertTrue(late.await() is CoreException.Unauthorized)
            assertEquals(AccountState.SignedOut(SERVER, expired = false), account.state.value)
        }

    @Test
    fun aLate401FromAGuestLeavesTheSessionSignedInSince() =
        runTest {
            apis.backend.info = { serverInfo(guests = false) }
            val account = account()
            account.load()
            account.browseAsGuest(SERVER)
            val refuse = CompletableDeferred<Unit>()
            val late = refusedLater(account, refuse)

            account.signedIn(SERVER, signedIn())
            refuse.complete(Unit)

            assertTrue(late.await() is CoreException.Unauthorized)
            assertTrue(account.state.value is AccountState.SignedIn)
            // Nor does it ask the server whether it still takes guests.
            assertTrue(apis.backend.log.none { it.startsWith("info") })
        }

    // ---------- a full disk ----------

    @Test
    fun aFullDiskStillSignsInForThisRun() =
        runTest {
            inProcess { account ->
                diskFull = true
                account.signedIn(SERVER, signedIn(token = "t1"))
                assertEquals(AccountState.SignedIn(SERVER, Identity(1, "admin", admin = true)), account.state.value)
                assertEquals("t1", account.fake?.token)
            }
            diskFull = false
            inProcess { assertEquals(AccountState.SignedOut(""), it.state.value) }
        }

    @Test
    fun aFullDiskStillLetsAGuestIn() =
        runTest {
            val account = account()
            account.load()
            diskFull = true
            account.browseAsGuest(SERVER)
            assertEquals(AccountState.Guest(SERVER), account.state.value)
        }

    @Test
    fun aFullDiskStillGoesBackToSignIn() =
        runTest {
            val account = account()
            account.load()
            account.browseAsGuest(SERVER)
            diskFull = true
            account.toSignIn()
            assertEquals(AccountState.SignedOut(SERVER), account.state.value)
        }

    @Test
    fun aSignOutOnAFullDiskHoldsAfterARestart() =
        runTest {
            inProcess { it.signedIn(SERVER, signedIn()) }
            inProcess { account ->
                diskFull = true
                account.signOut()
                assertEquals(AccountState.SignedOut(SERVER), account.state.value)
                assertNull(account.api.value)
                assertNull(secrets.read())
            }
            // The session is still written down, but its token is gone.
            inProcess { assertEquals(AccountState.SignedOut(SERVER), it.state.value) }
        }

    @Test
    fun aFullDiskStillEndsAnExpiredSession() =
        runTest {
            val account = account()
            account.load()
            account.signedIn(SERVER, signedIn())
            diskFull = true

            thrown<CoreException.Unauthorized> { account.call { throw CoreException.Unauthorized("revoked") } }

            assertEquals(AccountState.SignedOut(SERVER, expired = true), account.state.value)
        }
}
