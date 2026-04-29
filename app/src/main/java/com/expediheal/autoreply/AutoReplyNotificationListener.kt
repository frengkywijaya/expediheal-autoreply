package com.expediheal.autoreply

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlin.random.Random

class AutoReplyNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "WaAutoReply"

        private val WA_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )

        private val IGNORE_TITLE_PATTERNS = listOf(
            Regex("^\\d+ new messages? from \\d+ chats?", RegexOption.IGNORE_CASE),
            Regex("^WhatsApp$", RegexOption.IGNORE_CASE),
            Regex("^Checking for new messages", RegexOption.IGNORE_CASE),
            Regex("^Backing up", RegexOption.IGNORE_CASE),
            Regex("^Backup", RegexOption.IGNORE_CASE)
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        if (pkg !in WA_PACKAGES) return
        if (!Settings.isEnabled(applicationContext)) return

        try {
            handleNotification(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "error handling notification", e)
        }
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        val notif = sbn.notification ?: return
        val extras: Bundle = notif.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() || text.isBlank()) return

        if (IGNORE_TITLE_PATTERNS.any { it.containsMatchIn(title) }) {
            Log.d(TAG, "skipping summary notification: $title")
            return
        }

        if (text.startsWith("You: ", ignoreCase = true)) {
            Log.d(TAG, "skipping own message echo")
            return
        }

        val replyAction = findReplyAction(notif)
        if (replyAction == null) {
            Log.d(TAG, "no reply action available on notification from $title")
            return
        }

        val userKey = "${sbn.packageName}::$title"

        // DEDUP: have we already replied to this exact message text?
        if (Settings.hasAlreadyRepliedToMessage(applicationContext, userKey, text)) {
            Log.d(TAG, "already replied to this exact message from $userKey, skipping")
            return
        }

        if (Settings.isInCooldown(applicationContext, userKey)) {
            Log.d(TAG, "user $userKey in cooldown, skipping")
            return
        }

        val templates = Settings.getTemplates(applicationContext)
        if (templates.isEmpty()) {
            Log.d(TAG, "no templates configured")
            return
        }
        val replyText = templates[Random.nextInt(templates.size)]

        val minSec = Settings.getDelayMinSeconds(applicationContext)
        val maxSec = Settings.getDelayMaxSeconds(applicationContext).coerceAtLeast(minSec)
        val delayMs = Random.nextLong(minSec * 1000L, (maxSec + 1) * 1000L)

        Log.d(TAG, "scheduling reply to $title in ${delayMs}ms: $replyText")

        Settings.markReplied(applicationContext, userKey)
        Settings.markMessageReplied(applicationContext, userKey, text)

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (!Settings.isEnabled(applicationContext)) {
                    Log.d(TAG, "auto-reply disabled before send fired, aborting")
                    return@postDelayed
                }
                sendReply(replyAction, replyText)
                Log.d(TAG, "sent reply to $title")
            } catch (e: Exception) {
                Log.e(TAG, "failed to send reply", e)
            }
        }, delayMs)
    }

    private fun findReplyAction(notif: Notification): ReplyTarget? {
        val actions = notif.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (ri in remoteInputs) {
                if (ri.allowFreeFormInput) {
                    return ReplyTarget(action.actionIntent, ri, action.remoteInputs.toList())
                }
            }
        }
        return null
    }

    private fun sendReply(target: ReplyTarget, text: String) {
        val pi: PendingIntent = target.actionIntent
        val replyIntent = Intent()
        val bundle = Bundle().apply {
            putCharSequence(target.remoteInput.resultKey, text)
        }
        android.app.RemoteInput.addResultsToIntent(
            target.allRemoteInputs.toTypedArray(),
            replyIntent,
            bundle
        )
        pi.send(applicationContext, 0, replyIntent)
    }

    private data class ReplyTarget(
        val actionIntent: PendingIntent,
        val remoteInput: android.app.RemoteInput,
        val allRemoteInputs: List<android.app.RemoteInput>
    )

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "notification listener disconnected")
    }
}
