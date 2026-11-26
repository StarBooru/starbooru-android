package eu.filipnone.starbooru

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.filipnone.starbooru.core.ApiInterface
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.core.Mode
import eu.filipnone.starbooru.core.Neighbors
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.PostPage
import eu.filipnone.starbooru.core.ProviderSignIn
import eu.filipnone.starbooru.core.Rating
import eu.filipnone.starbooru.core.Role
import eu.filipnone.starbooru.core.ScoreKind
import eu.filipnone.starbooru.core.ServerInfo
import eu.filipnone.starbooru.core.SignedIn
import eu.filipnone.starbooru.core.TagCorrection
import eu.filipnone.starbooru.core.User
import eu.filipnone.starbooru.data.ApiFactory
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** The address the test phone's app points at, as the core writes it. */
const val SERVER = "http://127.0.0.1:8085"

fun post(
    id: Long,
    tags: List<String> = emptyList(),
): Post =
    Post(
        id = id,
        kind = MediaKind.IMAGE,
        mime = "image/png",
        fileSize = 1_000,
        width = 640,
        height = 480,
        durationMs = null,
        sha256 = "%064x".format(id),
        rating = Rating.SAFE,
        score = 0,
        myVote = 0,
        stars = null,
        votes = 0u,
        myStars = 0u,
        source = null,
        tags = tags,
        createdAtMs = 1_700_000_000_000 + id,
        fileDateMs = null,
        palette = emptyList(),
        deleted = false,
        fileUrl = "$SERVER/media/$id.png",
        thumbUrl = null,
        sampleUrl = null,
        ocrText = null,
        tagDetails = emptyList(),
        blacklisted = emptyList(),
    )

fun posts(ids: Iterable<Long>): List<Post> = ids.map { post(it) }

fun page(
    posts: List<Post>,
    page: UInt = 1u,
    hasMore: Boolean = false,
    corrected: List<TagCorrection> = emptyList(),
) = PostPage(posts = posts, page = page, hasMore = hasMore, corrected = corrected)

fun serverInfo(
    hasUsers: Boolean = true,
    registrationOpen: Boolean = false,
    guests: Boolean = false,
    name: String = "Test booru",
) = ServerInfo(
    name = name,
    version = "0.1.0",
    hasUsers = hasUsers,
    registrationOpen = registrationOpen,
    maxUploadBytes = 100_000_000u,
    urlImport = false,
    oidc = null,
    videoEditing = false,
    mode = if (guests) Mode.PUBLIC else Mode.PRIVATE,
    scoreKind = ScoreKind.LIKES,
    guests = guests,
    home = false,
    linkPreviews = false,
)

fun user(
    id: Long = 1,
    username: String = "admin",
    role: Role = Role.ADMIN,
) = User(id = id, username = username, role = role, createdAtMs = 1_700_000_000_000)

fun signedIn(
    token: String = "token-admin",
    user: User = user(),
) = SignedIn(token = token, user = user)

/** One `posts` request, as the fake server saw it. */
data class PostsCall(val query: String, val seed: Long?, val page: UInt, val limit: UInt)

/**
 * A pretend starbooru-server behind every [FakeApi] that [FakeApis] makes.
 * Each answer is a lambda a test can swap. The defaults: an admin account,
 * admin/admin, and [total] posts with ids 1 to [total].
 */
class FakeServer(var total: Int = 0) {
    var info: suspend (server: String) -> ServerInfo = { serverInfo() }
    var signIn: suspend (username: String, password: String) -> SignedIn = { name, password ->
        if (name == "admin" && password == "admin") signedIn() else throw CoreException.Unauthorized("wrong password")
    }
    var register: suspend (username: String, password: String) -> SignedIn = { name, _ ->
        signedIn(token = "token-$name", user = user(id = 2, username = name, role = Role.USER))
    }
    var signOut: suspend (token: String?) -> Unit = {}
    var posts: suspend (PostsCall) -> PostPage = { c -> slice(c) }
    var post: suspend (id: Long) -> Post = { id ->
        // Qualified: plain `post` here is this property.
        if (id in 1..total) eu.filipnone.starbooru.post(id) else throw CoreException.Api(404u, "not_found", "no such post")
    }
    var neighbors: suspend (id: Long, query: String, seed: Long?) -> Neighbors = { id, _, _ ->
        Neighbors(prev = (id - 1).takeIf { it >= 1 }, next = (id + 1).takeIf { it <= total })
    }

    /** Every request, in order: `info <server>`, `post 5`, `neighbors 5`, … */
    val log = mutableListOf<String>()
    val postsCalls = mutableListOf<PostsCall>()

    /** The tokens whose session was ended through `signOut`. */
    val signedOut = mutableListOf<String?>()

    /** A page of ids 1 to [total], [PostsCall.limit] long. */
    fun slice(c: PostsCall): PostPage {
        val from = (c.page.toLong() - 1) * c.limit.toLong() + 1
        val to = minOf(from + c.limit.toLong() - 1, total.toLong())
        return page(posts(from..to), page = c.page, hasMore = to < total)
    }
}

/** Answers every [ApiInterface] call no test fakes by failing it, naming the call. */
private val unsupported: ApiInterface =
    Proxy.newProxyInstance(ApiInterface::class.java.classLoader, arrayOf(ApiInterface::class.java)) { _, method, _ ->
        throw UnsupportedOperationException("FakeApi.${method.name} isn't faked")
    } as ApiInterface

class FakeApi(private val backend: FakeServer, private val address: String, val token: String?) : ApiInterface by unsupported {
    override suspend fun beginProviderSignIn(): ProviderSignIn = throw CoreException.SignInCancelled("no provider in tests")

    override suspend fun info(): ServerInfo {
        backend.log += "info $address"
        return backend.info(address)
    }

    /** No posts are alike in tests; the viewer's row stays empty. */
    override suspend fun related(
        id: Long,
        limit: UInt,
    ): List<Post> = emptyList()

    override suspend fun me(): User = token?.let { user() } ?: throw CoreException.Unauthorized("guest")

    override suspend fun neighbors(
        id: Long,
        query: String,
        seed: Long?,
    ): Neighbors {
        backend.log += "neighbors $id"
        return backend.neighbors(id, query, seed)
    }

    override suspend fun post(id: Long): Post {
        backend.log += "post $id"
        return backend.post(id)
    }

    override suspend fun posts(
        query: String,
        seed: Long?,
        page: UInt,
        limit: UInt,
    ): PostPage {
        val call = PostsCall(query, seed, page, limit)
        backend.log += "posts $page"
        backend.postsCalls += call
        return backend.posts(call)
    }

    override suspend fun register(
        username: String,
        password: String,
    ): SignedIn {
        backend.log += "register $username"
        return backend.register(username, password)
    }

    override fun server(): String = address

    override suspend fun signIn(
        username: String,
        password: String,
    ): SignedIn {
        backend.log += "signIn $username"
        return backend.signIn(username, password)
    }

    override suspend fun signOut() {
        backend.log += "signOut"
        backend.signOut(token)
        backend.signedOut += token
    }

    override fun signedIn(): Boolean = token != null
}

/** Makes [FakeApi]s over one [FakeServer], and keeps each one it made. */
class FakeApis(val backend: FakeServer = FakeServer()) : ApiFactory {
    val made = mutableListOf<FakeApi>()

    /** When set, [create] throws it, as the core does for an address it can't use. */
    var refuse: CoreException? = null

    override fun create(
        server: String,
        token: String?,
    ): ApiInterface {
        refuse?.let { throw it }
        return FakeApi(backend, server, token).also { made += it }
    }
}

/** A real Preferences DataStore in [dir], run by [scope]. */
fun preferencesIn(
    dir: File,
    scope: CoroutineScope,
    name: String = "account",
): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope, produceFile = { dir.resolve("$name.preferences_pb") })

/** [real], with writes that fail as on a full disk while [full] says so. Reads still work. */
class FlakyStore(private val real: DataStore<Preferences>, private val full: () -> Boolean) : DataStore<Preferences> {
    override val data: Flow<Preferences> get() = real.data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        if (full()) throw IOException("No space left on device") else real.updateData(transform)
}

/** What [block] threw, failing the test if it threw nothing or something else. */
inline fun <reified T : Throwable> thrown(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("expected ${T::class.simpleName}, got $e", e)
    }
    throw AssertionError("expected ${T::class.simpleName}, nothing was thrown")
}

/**
 * Runs `Dispatchers.Main`, and so `viewModelScope`, on a test dispatcher.
 * [scope] runs stores on it too, outside `backgroundScope`, whose work
 * `advanceUntilIdle` doesn't wait for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    private val job = SupervisorJob()
    val scope = CoroutineScope(dispatcher + job)

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        job.cancel()
        Dispatchers.resetMain()
    }
}
