package io.github.drcrck.openlan.ads

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import io.github.drcrck.openlan.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Versione "play": un solo banner AdMob, in fondo alla scheda di un dispositivo.
 * Prima chiede il consenso (UMP, obbligatorio in UE/UK); gli annunci partono solo se è possibile richiederli.
 */
object Ads {
    const val enabled = true

    private val started = AtomicBoolean(false)
    private val ready = mutableStateOf(false)

    fun init(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    if (info.canRequestAds()) start(activity)
                }
            },
            { _ -> if (info.canRequestAds()) start(activity) },
        )
        // Consenso già dato in una sessione precedente: si può partire subito
        if (info.canRequestAds()) start(activity)
    }

    private fun start(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        val app = activity.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(app) { ready.value = true }
        }
    }

    fun privacyOptionsRequired(activity: Activity): Boolean =
        UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ -> }
    }

    @Composable
    fun Banner(modifier: Modifier = Modifier) {
        val isReady by ready
        if (!isReady) return
        val widthDp = LocalConfiguration.current.screenWidthDp
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.getPortraitAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                    adUnitId = BuildConfig.ADMOB_BANNER_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
