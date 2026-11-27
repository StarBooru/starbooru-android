package eu.filipnone.starbooru.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import eu.filipnone.starbooru.R
import kotlinx.serialization.Serializable

/** The app's places. Each is kept, with its arguments, across process death. */
@Serializable
sealed interface Route : NavKey

/** The gallery: the results of [query], all posts when it's empty. */
@Serializable
data class GalleryRoute(val query: String = "") : Route

/** A post, opened from the results of [query] (shuffled by [seed]). */
@Serializable
data class PostRoute(val id: Long, val query: String = "", val seed: Long? = null) : Route

@Serializable
data object ExploreRoute : Route

@Serializable
data object TagsRoute : Route

@Serializable
data object UploadRoute : Route

@Serializable
data object SettingsRoute : Route

@Serializable
data object ForumRoute : Route

/** A tag's page. */
@Serializable
data class TagRoute(val name: String) : Route

/** The privacy policy, or the terms. */
@Serializable
data class LegalRoute(val privacy: Boolean) : Route

/** A share link's page, from any server. */
@Serializable
data class SharedRoute(val server: String, val token: String) : Route

/** The recycle bin, opened from Settings. */
@Serializable
data object TrashRoute : Route

/**
 * The navigation's items, five at most. A private server has [ALL], or a
 * guest [GUEST] and a way to sign in after them; a public one [PUBLIC], the
 * forum in Upload's place (as in starbooru-web's `route::Tab`).
 */
@Serializable
enum class Tab(val root: Route, @param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Gallery(GalleryRoute(), R.string.nav_gallery, R.drawable.ic_gallery),
    Explore(ExploreRoute, R.string.nav_explore, R.drawable.ic_explore),
    Tags(TagsRoute, R.string.nav_tags, R.drawable.ic_label),
    Forum(ForumRoute, R.string.nav_forum, R.drawable.ic_forum),
    Upload(UploadRoute, R.string.nav_upload, R.drawable.ic_upload),
    Settings(SettingsRoute, R.string.nav_settings_tab, R.drawable.ic_tune),
    ;

    companion object {
        val ALL = listOf(Gallery, Explore, Tags, Upload, Settings)
        val GUEST = listOf(Gallery, Explore, Tags, Settings)
        val PUBLIC = listOf(Gallery, Explore, Tags, Forum)

        /** The items on a public or a private server, for a guest or an account. */
        fun shown(
            public: Boolean,
            guest: Boolean,
        ): List<Tab> =
            when {
                public -> PUBLIC
                guest -> GUEST
                else -> ALL
            }
    }
}
