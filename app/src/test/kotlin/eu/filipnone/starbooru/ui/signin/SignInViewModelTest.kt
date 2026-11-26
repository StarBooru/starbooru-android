package eu.filipnone.starbooru.ui.signin

import androidx.lifecycle.SavedStateHandle
import eu.filipnone.starbooru.FakeApis
import eu.filipnone.starbooru.FakeServer
import eu.filipnone.starbooru.FlakyStore
import eu.filipnone.starbooru.MainDispatcherRule
import eu.filipnone.starbooru.SERVER
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.ServerInfo
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.AccountState
import eu.filipnone.starbooru.data.Identity
import eu.filipnone.starbooru.data.MemorySecretStore
import eu.filipnone.starbooru.preferencesIn
import eu.filipnone.starbooru.serverInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private val backend = FakeServer()
    private val apis = FakeApis(backend)
    private lateinit var account: Account

    /** Whether the account's writes fail, as on a full disk. */
    private var diskFull = false

    private suspend fun signIn(saved: SavedStateHandle = SavedStateHandle()): SignInViewModel {
        account = Account(FlakyStore(preferencesIn(tmp.root, main.scope)) { diskFull }, MemorySecretStore(), apis)
        account.load()
        return SignInViewModel(account, apis, saved)
    }

    /** Types [server] and waits out the pause before it's checked. */
    private fun TestScope.typed(
        vm: SignInViewModel,
        server: String = "127.0.0.1:8085",
    ) {
        vm.setServer(server)
        advanceUntilIdle()
    }

    private fun known(info: ServerInfo = serverInfo()) = SignInState(check = ServerCheck.Known(SERVER, info))

    // ---------- checking the server ----------

    @Test
    fun theServerIsCheckedAfterThePause() =
        runTest {
            val vm = signIn()
            vm.setServer("127.0.0.1:8085")
            advanceTimeBy(599)
            runCurrent()
            assertEquals(ServerCheck.Empty, vm.state.value.check)
            assertTrue(backend.log.isEmpty())

            advanceTimeBy(1)
            runCurrent()
            assertEquals(ServerCheck.Known(SERVER, serverInfo()), vm.state.value.check)
            assertEquals(listOf("info $SERVER"), backend.log)
        }

    @Test
    fun typingAgainRestartsThePause() =
        runTest {
            val vm = signIn()
            vm.setServer("127.0.0.1:808")
            advanceTimeBy(400)
            vm.setServer("127.0.0.1:8085")
            advanceTimeBy(400)
            runCurrent()
            assertTrue(backend.log.isEmpty())

            advanceUntilIdle()
            assertEquals(listOf("info $SERVER"), backend.log)
        }

    @Test
    fun checkingShowsWhileTheServerAnswers() =
        runTest {
            val answer = CompletableDeferred<ServerInfo>()
            backend.info = { answer.await() }
            val vm = signIn()
            vm.setServer("127.0.0.1:8085")
            advanceTimeBy(601)
            assertEquals(ServerCheck.Checking, vm.state.value.check)

            answer.complete(serverInfo(name = "Slow booru"))
            runCurrent()
            assertEquals(ServerCheck.Known(SERVER, serverInfo(name = "Slow booru")), vm.state.value.check)
        }

    @Test
    fun aServerThatDoesNotAnswerFails() =
        runTest {
            val down = CoreException.Unreachable("connection refused")
            backend.info = { throw down }
            val vm = signIn()
            typed(vm)
            assertEquals(ServerCheck.Failed(SERVER, down), vm.state.value.check)
            assertNull(vm.state.value.known)
        }

    @Test
    fun anAddressTheCoreRefusesFails() =
        runTest {
            val bad = CoreException.InvalidAddress("no host")
            apis.refuse = bad
            val vm = signIn()
            typed(vm)
            assertEquals(ServerCheck.Failed(SERVER, bad), vm.state.value.check)
        }

    @Test
    fun retryChecksAtOnce() =
        runTest {
            backend.info = { throw CoreException.Unreachable("down") }
            val vm = signIn()
            typed(vm)
            backend.info = { serverInfo() }

            vm.retryCheck()
            runCurrent()
            assertEquals(ServerCheck.Known(SERVER, serverInfo()), vm.state.value.check)
        }

    @Test
    fun aBlankServerIsNotChecked() =
        runTest {
            val vm = signIn()
            typed(vm)
            typed(vm, "   ")
            assertEquals(ServerCheck.Empty, vm.state.value.check)
            assertEquals(1, backend.log.size)
        }

    // ---------- what the form offers ----------

    @Test
    fun theFirstAccountOnAServerIsMadeNotSignedInto() {
        val s = known(serverInfo(hasUsers = false, registrationOpen = false))
        assertTrue(s.firstAccount)
        assertTrue(s.canRegister)
        assertTrue(s.makingAccount)
        assertTrue(s.copy(registering = true).makingAccount)
    }

    @Test
    fun registeringNeedsOpenRegistration() {
        val open = known(serverInfo(hasUsers = true, registrationOpen = true))
        assertFalse(open.firstAccount)
        assertTrue(open.canRegister)
        assertFalse(open.makingAccount)
        assertTrue(open.copy(registering = true).makingAccount)

        val closed = known(serverInfo(hasUsers = true, registrationOpen = false))
        assertFalse(closed.canRegister)
        assertFalse(closed.copy(registering = true).makingAccount)
    }

    @Test
    fun nothingIsOfferedBeforeTheServerIsKnown() {
        for (check in listOf(ServerCheck.Empty, ServerCheck.Checking, ServerCheck.Failed(SERVER, CoreException.Unreachable("x")))) {
            val s = SignInState(username = "admin", password = "admin", registering = true, check = check)
            assertFalse(s.firstAccount)
            assertFalse(s.canRegister)
            assertFalse(s.makingAccount)
            assertFalse(s.canSubmit)
        }
    }

    @Test
    fun submittingNeedsAUsernameAPasswordAndAnIdleForm() {
        val ready = known().copy(username = "admin", password = "admin")
        assertTrue(ready.canSubmit)
        assertFalse(ready.copy(username = "").canSubmit)
        assertFalse(ready.copy(username = "   ").canSubmit)
        assertFalse(ready.copy(password = "").canSubmit)
        assertTrue(ready.copy(password = " ").canSubmit)
        assertFalse(ready.copy(busy = true).canSubmit)
    }

    @Test
    fun theFormFollowsTheServerAnswer() =
        runTest {
            backend.info = { serverInfo(hasUsers = false) }
            val vm = signIn()
            typed(vm)
            assertTrue(vm.state.value.firstAccount)
            assertTrue(vm.state.value.makingAccount)
        }

    // ---------- submitting ----------

    @Test
    fun submittingSignsIn() =
        runTest {
            val vm = signIn()
            typed(vm)
            vm.setUsername(" admin ")
            vm.setPassword("admin")
            vm.submit()
            assertTrue(vm.state.value.busy)
            advanceUntilIdle()

            assertEquals(AccountState.SignedIn(SERVER, Identity(1, "admin", admin = true)), account.state.value)
            assertEquals(listOf("info $SERVER", "signIn admin"), backend.log)
            assertEquals("token-admin", apis.made.last().token)
        }

    @Test
    fun theFirstAccountIsRegistered() =
        runTest {
            backend.info = { serverInfo(hasUsers = false) }
            val vm = signIn()
            typed(vm)
            vm.setUsername("ada")
            vm.setPassword("secret")
            vm.submit()
            advanceUntilIdle()

            assertEquals(listOf("info $SERVER", "register ada"), backend.log)
            assertEquals(AccountState.SignedIn(SERVER, Identity(2, "ada", admin = false)), account.state.value)
        }

    @Test
    fun aWrongPasswordShowsTheErrorAndKeepsTheForm() =
        runTest {
            val vm = signIn()
            typed(vm)
            vm.setUsername("admin")
            vm.setPassword("nope")
            vm.submit()
            advanceUntilIdle()

            val s = vm.state.value
            assertTrue(s.error is CoreException.Unauthorized)
            assertFalse(s.busy)
            assertEquals("127.0.0.1:8085", s.server)
            assertEquals("admin", s.username)
            assertEquals("nope", s.password)
            assertEquals(ServerCheck.Known(SERVER, serverInfo()), s.check)
            assertEquals(AccountState.SignedOut(""), account.state.value)
        }

    @Test
    fun typingClearsTheError() =
        runTest {
            val vm = signIn()
            typed(vm)
            vm.setUsername("admin")
            vm.setPassword("nope")
            vm.submit()
            advanceUntilIdle()

            vm.setPassword("admin")
            assertNull(vm.state.value.error)
        }

    @Test
    fun submittingAnIncompleteFormDoesNothing() =
        runTest {
            val vm = signIn()
            vm.setUsername("admin")
            vm.setPassword("admin")
            vm.submit()
            advanceUntilIdle()

            assertFalse(vm.state.value.busy)
            assertFalse(backend.log.any { it.startsWith("signIn") })
        }

    @Test
    fun browsingAsAGuestUsesTheCheckedServer() =
        runTest {
            backend.info = { serverInfo(guests = true) }
            val vm = signIn()
            typed(vm)
            vm.browseAsGuest()
            advanceUntilIdle()
            assertEquals(AccountState.Guest(SERVER), account.state.value)
        }

    // ---------- arriving and coming back ----------

    @Test
    fun arrivingAfterExpiryShowsWhyAndFillsTheServer() =
        runTest {
            val saved = SavedStateHandle()
            val vm = signIn(saved)
            vm.arrive(SERVER, expired = true)

            assertTrue(vm.state.value.error is CoreException.Unauthorized)
            assertEquals(SERVER, vm.state.value.server)
            assertEquals(SERVER, saved.get<String>("server"))
            // No pause: the server was not typed.
            runCurrent()
            assertEquals(ServerCheck.Known(SERVER, serverInfo()), vm.state.value.check)
        }

    @Test
    fun arrivingAfterASignOutShowsNoError() =
        runTest {
            val vm = signIn()
            vm.arrive(SERVER, expired = false)
            assertNull(vm.state.value.error)
            assertEquals(SERVER, vm.state.value.server)
        }

    @Test
    fun arrivingKeepsAServerTypedAlready() =
        runTest {
            val vm = signIn()
            typed(vm, "booru.example")
            vm.arrive(SERVER, expired = false)
            assertEquals("booru.example", vm.state.value.server)
        }

    @Test
    fun arrivingClearsThePassword() =
        runTest {
            val vm = signIn()
            typed(vm)
            vm.setPassword("admin")
            vm.arrive(SERVER, expired = true)
            assertEquals("", vm.state.value.password)
            assertFalse(vm.state.value.busy)
        }

    @Test
    fun theFormSurvivesTheProcessButNotThePassword() =
        runTest {
            val saved = SavedStateHandle()
            val first = signIn(saved)
            typed(first)
            first.setUsername("admin")
            first.setPassword("admin")
            first.setRegistering(true)

            val again = SignInViewModel(account, apis, saved)
            val s = again.state.value
            assertEquals("127.0.0.1:8085", s.server)
            assertEquals("admin", s.username)
            assertEquals("", s.password)
            assertTrue(s.registering)
            // A restored server is checked at once.
            runCurrent()
            assertEquals(ServerCheck.Known(SERVER, serverInfo()), again.state.value.check)
        }

    // ---------- a server typed over another ----------

    @Test
    fun aNewServerDropsTheOldAnswerAtOnce() =
        runTest {
            val vm = signIn()
            typed(vm)
            vm.setUsername("admin")
            vm.setPassword("admin")

            vm.setServer("booru.example")
            assertEquals(ServerCheck.Empty, vm.state.value.check)
            assertFalse(vm.state.value.canSubmit)
            vm.submit()
            runCurrent()
            assertFalse(backend.log.any { it.startsWith("signIn") })
            assertEquals(AccountState.SignedOut(""), account.state.value)

            // Once the new one answers, that's where the account goes.
            advanceUntilIdle()
            vm.submit()
            advanceUntilIdle()
            assertEquals(AccountState.SignedIn("https://booru.example", Identity(1, "admin", admin = true)), account.state.value)
        }

    @Test
    fun aNewServerKeepsGuestsAndProvidersOffTheOldOne() =
        runTest {
            backend.info = { serverInfo(guests = true) }
            val vm = signIn()
            typed(vm)

            vm.setServer("booru.example")
            vm.browseAsGuest()
            vm.signInWithProvider()
            runCurrent()

            assertEquals(AccountState.SignedOut(""), account.state.value)
            assertFalse(vm.state.value.busy)
            assertFalse(vm.state.value.viaProvider)
            assertNull(vm.state.value.error)
            assertNull(vm.openUrl.value)
        }

    @Test
    fun aNewServerDropsAnOldFailure() =
        runTest {
            val down = CoreException.Unreachable("connection refused")
            backend.info = { server -> if (server == SERVER) throw down else serverInfo() }
            val vm = signIn()
            typed(vm)
            assertEquals(ServerCheck.Failed(SERVER, down), vm.state.value.check)

            vm.setServer("booru.example")
            assertEquals(ServerCheck.Empty, vm.state.value.check)
        }

    @Test
    fun retypingTheSameServerKeepsItsAnswer() =
        runTest {
            val vm = signIn()
            typed(vm)
            vm.setUsername("admin")
            vm.setPassword("admin")

            for (same in listOf("127.0.0.1:8085/", "http://127.0.0.1:8085", " 127.0.0.1:8085/api/v1 ")) {
                vm.setServer(same)
                assertEquals(same, ServerCheck.Known(SERVER, serverInfo()), vm.state.value.check)
                assertTrue(same, vm.state.value.canSubmit)
            }
        }

    // ---------- a full disk ----------

    @Test
    fun aFullDiskStillSignsIn() =
        runTest {
            val vm = signIn()
            typed(vm)
            vm.setUsername("admin")
            vm.setPassword("admin")
            diskFull = true

            vm.submit()
            advanceUntilIdle()

            assertEquals(AccountState.SignedIn(SERVER, Identity(1, "admin", admin = true)), account.state.value)
            assertNull(vm.state.value.error)
        }

    @Test
    fun aFullDiskStillLetsAGuestIn() =
        runTest {
            backend.info = { serverInfo(guests = true) }
            val vm = signIn()
            typed(vm)
            diskFull = true

            vm.browseAsGuest()
            advanceUntilIdle()

            assertEquals(AccountState.Guest(SERVER), account.state.value)
        }
}
