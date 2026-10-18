package com.vknxworks.arrctrl.data.server

import com.vknxworks.arrctrl.data.net.ConnectionTest
import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.net.TestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Last connection check for each saved server, shown in the Servers list. */
class ServerStatus(private val http: Http, private val scope: CoroutineScope) {
    sealed interface Check {
        data object Checking : Check
        data class Done(val result: TestResult) : Check
    }

    private val _state = MutableStateFlow<Map<String, Check>>(emptyMap())
    val state: StateFlow<Map<String, Check>> = _state

    private val jobs = mutableMapOf<String, Job>()

    /** Re-checks every server in [servers] at once and drops results for servers that are gone. */
    @Synchronized
    fun refresh(servers: List<Server>) {
        val ids = servers.map { it.id }.toSet()
        _state.update { old -> old.filterKeys { it in ids } }
        servers.forEach(::check)
    }

    @Synchronized
    fun check(server: Server) {
        jobs.remove(server.id)?.cancel()
        _state.update { it + (server.id to Check.Checking) }
        jobs[server.id] = scope.launch {
            val r = ConnectionTest.run(http, server, draft = false)
            _state.update { it + (server.id to Check.Done(r)) }
        }
    }
}
