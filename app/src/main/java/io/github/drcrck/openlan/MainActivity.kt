package io.github.drcrck.openlan

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
                // Navigazione minima: "devices", "traceroute", "diagnostics", "about" oppure "device:<id>"
                var route by rememberSaveable { mutableStateOf("devices") }
                BackHandler(enabled = route != "devices") { route = "devices" }

                // Icone della barra di stato: chiare sull'intestazione scura della lista, altrimenti secondo il tema
                val dark = isSystemInDarkTheme()
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = route != "devices" && !dark
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
                    route.startsWith("device:") -> {
                        val id = route.removePrefix("device:").toLong()
                        val vm: DeviceDetailViewModel = viewModel(key = route) { DeviceDetailViewModel(container, id) }
                        val detail by vm.detail.collectAsStateWithLifecycle()
                        val ports by vm.ports.collectAsStateWithLifecycle()
                        val ping by vm.ping.collectAsStateWithLifecycle()
                        DeviceDetailScreen(
                            detail = detail,
                            ping = ping,
                            ports = ports,
                            onBack = { route = "devices" },
                            onScanPorts = vm::scanPorts,
                            onStopPorts = vm::stopPortScan,
                            onRename = vm::rename,
                        )
                    }

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
                        DevicesScreen(
                            state = state,
                            onScan = devicesVm::scan,
                            onOpenDevice = { route = "device:$it" },
                            onOpenTraceroute = { route = "traceroute" },
                            onOpenDiagnostics = { route = "diagnostics" },
                            onOpenAbout = { route = "about" },
                            onMessageShown = devicesVm::messageShown,
                        )
                    }
                }
            }
        }
    }
}
