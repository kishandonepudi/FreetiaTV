package com.freetia.tv.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetia.tv.BuildConfig
import com.freetia.tv.R
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingSectionHeader
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.theme.LocalBrand

internal const val PRIVACY_URL = "https://freetia-tv.github.io/freetia-tv/privacy.html"
internal const val CONTENT_NOTICE_URL = "https://freetia-tv.github.io/freetia-tv/content-notice.html"

internal val OPEN_SOURCE_LICENCES = listOf(
    "Jetpack Compose and AndroidX" to "Apache-2.0",
    "Media3 (ExoPlayer)" to "Apache-2.0",
    "OkHttp" to "Apache-2.0",
    "Coil" to "Apache-2.0",
    "zxing (QR codes)" to "Apache-2.0",
    "Kotlin and kotlinx.coroutines" to "Apache-2.0",
)

/** Settings > About: who made the app, its version, the open-source licences it uses and the legal links. */
@Composable
fun AboutScreen(nav: Navigator) {
    val context = LocalContext.current
    val toast = LocalBrandToast.current
    val brand = LocalBrand.current

    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast.show("No browser on this device. The address is shown on this page.", isError = true)
        }
    }

    SettingsScaffold(
        title = "About",
        hints = listOf("↑↓" to "Move", "OK" to "Open link", "Back" to "Return"),
    ) {
        item {
            // Tile is 48dp with 12dp (25%) clear space on every side.
            Row(
                Modifier.padding(12.dp).semantics { contentDescription = "Freetia TV, version ${BuildConfig.VERSION_NAME}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    "Freetia TV", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = brand.textPrimary,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
        item {
            SettingActionRow(
                "Version", onClick = {}, value = BuildConfig.VERSION_NAME, showChevron = false,
                subtitle = "Free and ad-free. You bring your own playlists.",
                modifier = Modifier.describe("Version ${BuildConfig.VERSION_NAME}"),
            )
        }
        item { SettingSectionHeader("Privacy and content") }
        item {
            SettingActionRow(
                "Privacy policy", onClick = { open(PRIVACY_URL) }, subtitle = PRIVACY_URL, value = "Open",
                modifier = Modifier.describe("Privacy policy", "Opens in your browser"),
            )
        }
        item {
            SettingActionRow(
                "Content notice", onClick = { open(CONTENT_NOTICE_URL) }, subtitle = CONTENT_NOTICE_URL, value = "Open",
                modifier = Modifier.describe("Content notice", "Opens in your browser"),
            )
        }
        item { SettingSectionHeader("Open-source licences") }
        OPEN_SOURCE_LICENCES.forEach { (name, licence) ->
            item(key = name) {
                SettingActionRow(
                    name, onClick = {}, value = licence, showChevron = false,
                    modifier = Modifier.describe(name, "Licence $licence"),
                )
            }
        }
    }
}
