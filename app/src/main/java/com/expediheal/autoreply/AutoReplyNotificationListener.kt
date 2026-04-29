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
            Regex("^WhatsApp Business$", RegexOption.IGNORE_CASE),
            Regex("^Checking for new messages", RegexOption.IGNORE_CASE),
            Regex("^Backing up", RegexOption.IGNORE_CASE),
            Regex("^Backup", RegexOption.IGNORE_CASE),
            Regex("^You$", RegexOption.IGNORE_CASE),
            Regex("^Anda$", RegexOption.IGNORE_CASE),
            Regex("^Tu$", RegexOption.IGNORE_CASE),
            Regex("^Du$", RegexOption.IGNORE_CASE),
            Regex("^Bạn$", RegexOption.IGNORE_CASE),
            Regex("^คุณ$", RegexOption.IGNORE_CASE),
            Regex("^你$", RegexOption.IGNORE_CASE),
            Regex("^あなた$", RegexOption.IGNORE_CASE)
        )

        private val pendingUsers = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        private val handler = Handler(Looper.getMainLooper())
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
            DebugLog.add(applicationContext, "ERROR: ${e.message}")
        }
    }

    private fun normalizeTitle(rawTitle: String): String {
        var t = rawTitle.trim()
        t = t.replace(Regex("\\s*\\(\\d+\\)\\s*$"), "")
        t = t.replace(Regex("\\s*•\\s*\\d+\\s+new\\s*$", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\s*:\\s*\\d+\\s*$"), "")
        return t.trim()
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        val notif = sbn.notification ?: return
        val extras: Bundle = notif.extras ?: return

        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (rawTitle.isBlank() || text.isBlank()) {
            DebugLog.add(applicationContext, "skip: empty title or text")
            return
        }

        if (IGNORE_TITLE_PATTERNS.any { it.containsMatchIn(rawTitle) }) {
            DebugLog.add(applicationContext, "skip system/echo title: '$rawTitle'")
            return
        }

        if (text.startsWith("You: ", ignoreCase = true) ||
            text.startsWith("Anda: ", ignoreCase = true)) {
            DebugLog.add(applicationContext, "skip own-message echo: '$rawTitle'")
            return
        }

        val replyAction = findReplyAction(notif)
        if (replyAction == null) {
            DebugLog.add(applicationContext, "no reply action: '$rawTitle' (group chat?)")
            return
        }

        val normalizedTitle = normalizeTitle(rawTitle)
        val userKey = "${sbn.packageName}::$normalizedTitle"

        DebugLog.add(applicationContext,
            "got msg from '$normalizedTitle': \"${text.take(50)}\"")

        if (Settings.hasAlreadyRepliedToMessage(applicationContext, userKey, text)) {
            DebugLog.add(applicationContext, "  → SKIP: already replied to this exact message")
            return
        }

        synchronized(pendingUsers) {
            if (userKey in pendingUsers) {
                DebugLog.add(applicationContext, "  → BLOCKED: reply already pending")
                return
            }
            if (Settings.isInCooldown(applicationContext, userKey)) {
                DebugLog.add(applicationContext, "  → BLOCKED: cooldown active")
                return
            }
            pendingUsers.add(userKey)
        }

        val templates = Settings.getTemplates(applicationContext)
        if (templates.isEmpty()) {
            DebugLog.add(applicationContext, "  → no templates configured")
            pendingUsers.remove(userKey)
            return
        }
        val replyText = templates[Random.nextInt(templates.size)]

        val minSec = Settings.getDelayMinSeconds(applicationContext)
        val maxSec = Settings.getDelayMaxSeconds(applicationContext).coerceAtLeast(minSec)
        val delayMs = Random.nextLong(minSec * 1000L, (maxSec + 1) * 1000L)

        DebugLog.add(applicationContext,
            "  → SCHEDULED: reply in ${delayMs/1000}s with: \"${replyText.take(40)}\"")

        Settings.markReplied(applicationContext, userKey)
        Settings.markMessageReplied(applicationContext, userKey, text)

        handler.postDelayed({
            try {
                if (!Settings.isEnabled(applicationContext)) {
                    DebugLog.add(applicationContext, "  → ABORTED: disabled before fire")
                    return@postDelayed
                }
                sendReply(replyAction, replyText)
                DebugLog.add(applicationContext, "  ✓ SENT to '$normalizedTitle'")

                // Dismiss the notification we just replied to.
                // This stops WA from re-posting it AND prevents our own reply
                // from showing as a new notification (avoiding the echo loop).
                try {
                    cancelNotification(sbn.key)
                    DebugLog.add(applicationContext, "  ✓ dismissed notification")
                } catch (e: Exception) {
                    DebugLog.add(applicationContext, "  ! failed to dismiss: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed to send reply", e)
                DebugLog.add(applicationContext, "  ✗ FAILED: ${e.message}")
            } finally {
                pendingUsers.remove(userKey)
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
        DebugLog.add(applicationContext, "service connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        DebugLog.add(applicationContext, "service disconnected")
    }
}
