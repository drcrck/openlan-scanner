package io.github.drcrck.openlan.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Versione "foss": nessuna pubblicità e nessuna libreria di terze parti. */
object Ads {
    const val enabled = false

    fun init(activity: Activity) = Unit

    fun privacyOptionsRequired(activity: Activity): Boolean = false

    fun showPrivacyOptions(activity: Activity) = Unit

    @Composable
    fun Banner(modifier: Modifier = Modifier) = Unit
}
