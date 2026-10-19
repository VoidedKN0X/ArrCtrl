package com.vknxworks.arrctrl.data.notify

import com.vknxworks.arrctrl.container
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Receives UnifiedPush events from your distributor (e.g. the ntfy app).
 * A message is only a wake-up call: its content is ignored and ArrCtrl checks your servers itself,
 * so nothing in the message is trusted or shown.
 */
class PushReceiver : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val c = applicationContext.container
        c.appScope.launch { c.settings.update { it.copy(pushEndpoint = endpoint.url) } }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        NotifyChecker.markPush(applicationContext)
        NotifySchedule.checkNow(applicationContext, push = true)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        val c = applicationContext.container
        c.appScope.launch { c.settings.update { it.copy(pushEnabled = false, pushEndpoint = null) } }
    }

    override fun onUnregistered(instance: String) {
        val c = applicationContext.container
        c.appScope.launch { c.settings.update { it.copy(pushEnabled = false, pushEndpoint = null) } }
    }
}
