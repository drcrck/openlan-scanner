package io.github.drcrck.openlan.ui.traceroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drcrck.openlan.AppContainer
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.scanner.Hop
import io.github.drcrck.openlan.ui.common.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TracerouteState(
    val target: String = "8.8.8.8",
    val running: Boolean = false,
    val hops: List<Hop> = emptyList(),
    val error: UiMessage? = null,
    val finished: Boolean = false,
    /** Salti finali senza risposta nascosti (dopo l'ultimo che ha risposto). */
    val hiddenSilent: Int = 0,
    /** Nessun salto ha risposto: la rete o Android filtrano le risposte ICMP. */
    val allSilent: Boolean = false,
)

class TracerouteViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TracerouteState())
    val state: StateFlow<TracerouteState> = _state.asStateFlow()
    private var job: Job? = null

    fun setTarget(t: String) = _state.update { it.copy(target = t) }

    fun start() {
        val target = _state.value.target.trim()
        if (target.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { TracerouteState(target = it.target, running = true) }
            try {
                val hops = container.traceroute.trace(target) { h -> _state.update { it.copy(hops = h) } }
                // Onestà prima di tutto: niente righe "* nessuna risposta" a vuoto
                val lastAnswer = hops.indexOfLast { it.ip != null }
                val shown = if (lastAnswer < 0) emptyList() else hops.subList(0, lastAnswer + 1)
                _state.update {
                    it.copy(
                        running = false, hops = shown, finished = true,
                        hiddenSilent = hops.size - shown.size, allSilent = lastAnswer < 0,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(running = false, error = UiMessage(R.string.trace_unresolved, listOf(target))) }
            } catch (e: Exception) {
                _state.update { it.copy(running = false, error = UiMessage(R.string.msg_error, listOf(e.message ?: "?"))) }
            }
        }
    }
}
