package io.github.drcrck.openlan.ui.about

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.drcrck.openlan.BuildConfig
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.ads.Ads
import io.github.drcrck.openlan.ui.common.SectionCard
import io.github.drcrck.openlan.ui.detail.PortActions
import io.github.drcrck.openlan.ui.theme.Brand

const val REPO_URL = "https://github.com/drcrck/openlan-scanner"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun open(url: String) = PortActions.openUrl(context, url)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Brand.heroGradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(140.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(50), color = Brand.Teal.copy(alpha = 0.16f)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.about_badge),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_promises), icon = Icons.Filled.VerifiedUser) {
                    Promise(stringResource(R.string.promise_open))
                    Promise(stringResource(R.string.promise_local))
                    Promise(stringResource(R.string.promise_noaccount))
                    Promise(stringResource(if (Ads.enabled) R.string.promise_ads_play else R.string.promise_ads_foss))
                }
            }

            item {
                SectionCard {
                    Link(Icons.Filled.Code, stringResource(R.string.link_source), REPO_URL) { open(REPO_URL) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Link(Icons.Filled.BugReport, stringResource(R.string.link_issues), "$REPO_URL/issues") { open("$REPO_URL/issues") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Link(Icons.Filled.Gavel, stringResource(R.string.link_license), "GPL-3.0-or-later") { open("$REPO_URL/blob/main/LICENSE") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Link(Icons.Filled.Policy, stringResource(R.string.link_privacy), null) { open("$REPO_URL/blob/main/PRIVACY.md") }
                    if (Ads.enabled && activity != null && Ads.privacyOptionsRequired(activity)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Link(Icons.Filled.Tune, stringResource(R.string.link_ad_consent), null) { Ads.showPrivacyOptions(activity) }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_credits), icon = Icons.Filled.PrivacyTip) {
                    Text(stringResource(R.string.credits_body), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Text(
                    "© 2026 Diego Rigoni",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Promise(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Check, null, tint = Brand.Online, modifier = Modifier.padding(end = 8.dp).size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Link(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
    }
}
