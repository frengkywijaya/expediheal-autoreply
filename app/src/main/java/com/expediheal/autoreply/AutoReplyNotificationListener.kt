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
import androidx.core.app.RemoteInput
import kotlin.random.Random

class AutoReplyNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "WaAutoReply"

        private val WA_PACKAGES = setOf(
            "com.whatsapp",         // WhatsApp
            "com.whatsapp.w4b"      // WhatsApp Business
        )

        // Notification keys WA uses to identify summary / group / silent notifications
        // we should not auto-reply to. Title patterns that indicate batched/summary.
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

        // Skip summary/system notifications
        if (IGNORE_TITLE_PATTERNS.any { it.containsMatchIn(title) }) {
            Log.d(TAG, "skipping summary notification: $title")
            return
        }

        // Skip group chats (title contains ":" indicating "GroupName: SenderName")
        // Also skip if title indicates a "you" / "you replied" message
        if (text.startsWith("You: ", ignoreCase = true)) {
            Log.d(TAG, "skipping own message echo")
            return
        }

        // Find the RemoteInput action — this is what lets us reply
        val replyAction = findReplyAction(notif)
        if (replyAction == null) {
            Log.d(TAG, "no reply action available on notification from $title")
            return
        }

        // Compute user key (so we can rate-limit per user)
        val userKey = "${sbn.packageName}::$title"

        // Check cooldown — don't spam-reply the same person
        if (Settings.isInCooldown(applicationContext, userKey)) {
            Log.d(TAG, "user $userKey in cooldown, skipping")
            return
        }

        // Pick a random template
        val templates = Settings.getTemplates(applicationContext)
        if (templates.isEmpty()) {
            Log.d(TAG, "no templates configured")
            return
        }
        val replyText = templates[Random.nextInt(templates.size)]

        // Pick a random delay
        val minSec = Settings.getDelayMinSeconds(applicationContext)
        val maxSec = Settings.getDelayMaxSeconds(applicationContext).coerceAtLeast(minSec)
        val delayMs = Random.nextLong(minSec * 1000L, (maxSec + 1) * 1000L)

        Log.d(TAG, "scheduling reply to $title in ${delayMs}ms: $replyText")

        // Mark replied immediately so we don't queue duplicates if more notifications fire
        Settings.markReplied(applicationContext, userKey)

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

    /**
     * Find the first inline-reply action on the notification (the one with a RemoteInput).
     */
    private fun findReplyAction(notif: Notification): ReplyTarget? {
        val actions = notif.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            // We want the "Reply" / inline-text input
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
        RemoteInput.addResultsToIntent(target.allRemoteInputs.toTypedArray(), replyIntent, bundle)
        pi.send(applicationContext, 0, replyIntent)
    }

    private data class ReplyTarget(
        val actionIntent: PendingIntent,
        val remoteInput: android.app.RemoteInput,
        val allRemoteInputs: List<android.app.RemoteInput>
    )

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // no-op
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "notification listener disconnected")
    }
}
