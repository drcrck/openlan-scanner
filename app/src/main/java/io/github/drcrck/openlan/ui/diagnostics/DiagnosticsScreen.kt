package io.github.drcrck.openlan.ui.diagnostics

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.ui.theme.Mono
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.drcrck.openlan.ui.detail.PortActions
import kotlinx.coroutines.launch

/** Testo grezzo della diagnostica dell'ultima scansione, da copiare o condividere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(text: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val content = text ?: stringResource(R.string.diag_empty)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    if (text != null) {
                        IconButton(onClick = {
                            PortActions.copy(context, text)
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.diag_copied)) }
                        }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_copy)) }
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.diagnostics)))
                        }) { Icon(Icons.Filled.Share, stringResource(R.string.action_share)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        SelectionContainer(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()).padding(12.dp)
        ) {
            Text(content, fontFamily = Mono, fontSize = 11.sp, style = MaterialTheme.typography.bodySmall)
        }
    }
}
