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
            _state.update { it.copy(running = true, hops = emptyList(), error = null, finished = false) }
            try {
                val hops = container.traceroute.trace(target) { h -> _state.update { it.copy(hops = h) } }
                _state.update { it.copy(running = false, hops = hops, finished = true) }
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
