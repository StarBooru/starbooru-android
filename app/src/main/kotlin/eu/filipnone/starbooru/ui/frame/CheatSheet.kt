package eu.filipnone.starbooru.ui.frame

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.filipnone.starbooru.R

/** One example: the search, and what it finds. Each one runs when tapped. */
private data class Example(val query: String, @param:StringRes val meaning: Int)

/** The cheat sheet's sections, as starbooru-web's `/help` lists them. */
private fun sections(stars: Boolean): List<Pair<Int, List<Example>>> =
    listOf(
        R.string.help_tags to
            listOf(
                Example("blue_sky", R.string.help_tag),
                Example("blue_sky night", R.string.help_tag_all),
                Example("-cloud", R.string.help_tag_not),
                Example("footwear", R.string.help_tag_tree),
                Example("artist:some_name", R.string.help_tag_category),
                Example("{girl blonde_hair boots}", R.string.help_figure),
                Example("blue sky", R.string.help_tag_spaces),
                Example("highres -sound", R.string.help_tag_meta),
            ),
        R.string.help_filters to
            listOf(
                Example("rating:safe", R.string.help_rating),
                Example("rating:safe rating:q", R.string.help_rating_any),
                Example("-rating:e", R.string.help_rating_not),
                Example("type:image", R.string.help_type),
                Example("status:deleted", R.string.help_deleted),
                Example("date:2024-03", R.string.help_date),
                Example("date:2023..2024", R.string.help_date_range),
                Example("date:>2023-06", R.string.help_date_compare),
                Example("date:>30d", R.string.help_date_age),
                Example("width:>1920", R.string.help_size),
                Example("mpixels:>8", R.string.help_mpixels),
                Example("ratio:16:9", R.string.help_ratio),
                Example("-height:<1000", R.string.help_size_not),
                Example("color:red", R.string.help_color),
                Example("color:2060e0", R.string.help_color_hex),
                Example("-color:white", R.string.help_color_not),
                Example("exact:true", R.string.help_exact),
            ),
        R.string.help_text to
            listOf(
                Example("text:hello", R.string.help_text_word),
                Example("text:\"no parking\"", R.string.help_text_phrase),
                Example("-text:sale", R.string.help_text_not),
            ),
        R.string.help_sorting to
            listOf(
                Example("order:new", R.string.help_order_new),
                // Under stars, the fair rating.
                Example("order:score", if (stars) R.string.help_order_rating else R.string.help_order_score),
                Example("order:size", R.string.help_order_size),
                Example("order:pixels", R.string.help_order_pixels),
                Example("order:longside", R.string.help_order_longside),
                Example("order:tags", R.string.help_order_tags),
                Example("order:updated", R.string.help_order_updated),
                Example("order:size_asc", R.string.help_order_direction),
                Example("order:random", R.string.help_order_random),
            ),
        R.string.help_combined to
            listOf(
                Example("cat -dog rating:safe order:score", R.string.help_combined_cats),
                Example("type:video order:size", R.string.help_combined_videos),
            ),
    )

/** Every term of the search language, each a tap away from running. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheatSheet(
    stars: Boolean,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        LazyColumn(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            item {
                Text(
                    stringResource(R.string.help_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).semantics { heading() },
                )
            }
            sections(stars).forEach { (title, examples) ->
                item {
                    Text(
                        stringResource(title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
                items(examples) { example ->
                    val label = stringResource(R.string.help_search_for, example.query)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button, onClickLabel = label) { onSearch(example.query) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                example.query,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(example.meaning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            painterResource(R.drawable.ic_search),
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(8.dp).padding(top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.help_tips),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    listOf(R.string.help_tip_normalise, R.string.help_tip_aliases, R.string.help_tip_terms).forEach {
                        Text(
                            stringResource(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
