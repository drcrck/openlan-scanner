package io.github.drcrck.openlan

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.drcrck.openlan.ui.theme.Brand
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.drcrck.openlan.ads.Ads
import io.github.drcrck.openlan.ui.about.AboutScreen
import io.github.drcrck.openlan.ui.detail.DeviceDetailScreen
import io.github.drcrck.openlan.ui.detail.DeviceDetailViewModel
import io.github.drcrck.openlan.ui.devices.DevicesScreen
import io.github.drcrck.openlan.ui.diagnostics.DiagnosticsScreen
import io.github.drcrck.openlan.ui.devices.DevicesViewModel
import io.github.drcrck.openlan.ui.theme.OpenLanTheme
import io.github.drcrck.openlan.ui.traceroute.TracerouteScreen
import io.github.drcrck.openlan.ui.traceroute.TracerouteViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OpenLanApp).container

        setContent {
            OpenLanTheme {
                // Navigazione minima: "devices", "traceroute", "diagnostics", "about".
                // Il dispositivo aperto è [selected]: a schermo intero sul telefono, nel pannello destro su schermo largo.
                var route by rememberSaveable { mutableStateOf("devices") }
                var selected by rememberSaveable { mutableStateOf<Long?>(null) }
                BackHandler(enabled = route != "devices" || selected != null) {
                    if (route != "devices") route = "devices" else selected = null
                }
                val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP

                // Icone della barra di stato: chiare sopra l'intestazione scura, altrimenti secondo il tema
                val dark = isSystemInDarkTheme()
                val view = LocalView.current
                val overHero = route == "devices" && (wide || selected == null)
                SideEffect {
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !overHero && !dark
                }

                val devicesVm: DevicesViewModel = viewModel { DevicesViewModel(container) }

                // Il permesso posizione serve solo per leggere nome (SSID) e BSSID della rete
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    devicesVm.refreshNetwork()
                    // Dopo il permesso (mai due finestre insieme): consenso pubblicità, solo nella versione Play
                    Ads.init(this@MainActivity)
                }
                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }

                when {
                    route == "traceroute" -> {
                        val vm: TracerouteViewModel = viewModel { TracerouteViewModel(container) }
                        val state by vm.state.collectAsStateWithLifecycle()
                        TracerouteScreen(
                            state = state,
                            onBack = { route = "devices" },
                            onTargetChange = vm::setTarget,
                            onStart = vm::start,
                        )
                    }

                    route == "about" -> AboutScreen(onBack = { route = "devices" })

                    route == "diagnostics" -> DiagnosticsScreen(container.lastDiagnostics, onBack = { route = "devices" })

                    else -> {
                        val state by devicesVm.uiState.collectAsStateWithLifecycle()
                        val list = @Composable {
                            DevicesScreen(
                                state = state,
                                onScan = devicesVm::scan,
                                onStop = devicesVm::stopScan,
                                onOpenDevice = { selected = it },
                                onOpenTraceroute = { route = "traceroute" },
                                onOpenDiagnostics = { route = "diagnostics" },
                                onOpenAbout = { route = "about" },
                                onMessageShown = devicesVm::messageShown,
                                onQueryChange = devicesVm::setQuery,
                                onToggleFilter = devicesVm::toggleFilter,
                                onClearFilters = devicesVm::clearFilters,
                                onModeChange = devicesVm::setMode,
                                selectedId = if (wide) selected else null,
                            )
                        }
                        if (wide) {
                            // Schermo largo (tablet, orizzontale): lista e dettaglio affiancati
                            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                Box(Modifier.width(LIST_PANE_DP.dp).fillMaxHeight()) { list() }
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    val id = selected
                                    if (id != null) DeviceDetail(container, id, asPane = true, onBack = { selected = null })
                                    else DetailPlaceholder()
                                    // Fascia scura sotto la barra di stato, continua con l'intestazione della lista
                                    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Brand.Night))
                                }
                            }
                        } else {
                            val id = selected
                            if (id != null) DeviceDetail(container, id, asPane = false, onBack = { selected = null })
                            else list()
                        }
                    }
                }
            }
        }
    }
}

/** Da questa larghezza (dp) lista e dettaglio stanno affiancati. */
private const val WIDE_DP = 720
private const val LIST_PANE_DP = 400

@Composable
private fun DeviceDetail(container: AppContainer, id: Long, asPane: Boolean, onBack: () -> Unit) {
    val vm: DeviceDetailViewModel = viewModel(key = "device:$id") { DeviceDetailViewModel(container, id) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val ports by vm.ports.collectAsStateWithLifecycle()
    val ping by vm.ping.collectAsStateWithLifecycle()
    DeviceDetailScreen(
        detail = detail,
        ping = ping,
        ports = ports,
        onBack = onBack,
        onScanPorts = vm::scanPorts,
        onStopPorts = vm::stopPortScan,
        onRename = vm::rename,
        asPane = asPane,
    )
}

@Composable
private fun DetailPlaceholder() {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.TouchApp, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.detail_placeholder),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
