package eu.filipnone.starbooru.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.SizeUnit
import eu.filipnone.starbooru.core.fileSizeParts
import java.text.DecimalFormatSymbols

/** A size in bytes as the web writes it, in 1024s: 1.5 KB, 512.0 MB. */
@Composable
fun fileSize(bytes: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val mark = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator.toString() }
    val parts = fileSizeParts(bytes, mark)
    return stringResource(
        when (parts.unit) {
            SizeUnit.B -> R.string.size_b
            SizeUnit.KB -> R.string.size_kb
            SizeUnit.MB -> R.string.size_mb
            SizeUnit.GB -> R.string.size_gb
            SizeUnit.TB -> R.string.size_tb
        },
        parts.number,
    )
}
