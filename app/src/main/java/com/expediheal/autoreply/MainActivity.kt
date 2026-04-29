package com.expediheal.autoreply

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.expediheal.autoreply.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: TemplateAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUi()
        refreshFromSettings()
    }

    override fun onResume() {
        super.onResume()
        refreshFromSettings()
        updatePermissionStatus()
    }

    private fun setupUi() {
        // Master toggle
        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            if (checked && !isNotificationListenerEnabled()) {
                // Need permission first
                binding.switchEnabled.isChecked = false
                showPermissionPrompt()
                return@setOnCheckedChangeListener
            }
            Settings.setEnabled(this, checked)
            updateStatusText(checked)
        }

        // Permission button
        binding.btnGrantAccess.setOnClickListener { openNotificationAccessSettings() }

        // Templates list
        adapter = TemplateAdapter(
            onEdit = { idx, current -> showTemplateDialog(idx, current) },
            onDelete = { idx -> deleteTemplate(idx) }
        )
        binding.recyclerTemplates.layoutManager = LinearLayoutManager(this)
        binding.recyclerTemplates.adapter = adapter

        binding.btnAddTemplate.setOnClickListener { showTemplateDialog(null, "") }

        // Delay range
        binding.btnEditDelay.setOnClickListener { showDelayDialog() }

        // Cooldown
        binding.btnEditCooldown.setOnClickListener { showCooldownDialog() }
    }

    private fun refreshFromSettings() {
        binding.switchEnabled.isChecked = Settings.isEnabled(this)
        updateStatusText(Settings.isEnabled(this))

        val templates = Settings.getTemplates(this)
        adapter.submit(templates)

        val minS = Settings.getDelayMinSeconds(this)
        val maxS = Settings.getDelayMaxSeconds(this)
        binding.txtDelayValue.text = getString(R.string.delay_format, minS, maxS)

        val cdMin = Settings.getCooldownMinutes(this)
        binding.txtCooldownValue.text = resources.getQuantityString(R.plurals.cooldown_minutes, cdMin, cdMin)
    }

    private fun updateStatusText(enabled: Boolean) {
        binding.txtStatus.text = if (enabled) {
            getString(R.string.status_running)
        } else {
            getString(R.string.status_stopped)
        }
    }

    private fun updatePermissionStatus() {
        val granted = isNotificationListenerEnabled()
        binding.txtPermissionStatus.text = if (granted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_missing)
        }
        binding.btnGrantAccess.visibility =
            if (granted) android.view.View.GONE else android.view.View.VISIBLE
    }

    // ---------- Templates ----------
    private fun showTemplateDialog(index: Int?, current: String) {
        val edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(current)
            setSelection(current.length)
            minLines = 2
            maxLines = 6
        }
        val container = androidx.appcompat.widget.LinearLayoutCompat(this).apply {
            orientation = androidx.appcompat.widget.LinearLayoutCompat.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(edit)
        }

        AlertDialog.Builder(this)
            .setTitle(if (index == null) R.string.add_template else R.string.edit_template)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val text = edit.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, R.string.template_empty_warning, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val list = Settings.getTemplates(this)
                if (index == null) list.add(text) else list[index] = text
                Settings.setTemplates(this, list)
                adapter.submit(list)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteTemplate(index: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_template)
            .setMessage(R.string.delete_template_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                val list = Settings.getTemplates(this)
                if (index in list.indices) {
                    list.removeAt(index)
                    Settings.setTemplates(this, list)
                    adapter.submit(list)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- Delay ----------
    private fun showDelayDialog() {
        val minEdit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(Settings.getDelayMinSeconds(this@MainActivity).toString())
            hint = "min seconds"
        }
        val maxEdit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(Settings.getDelayMaxSeconds(this@MainActivity).toString())
            hint = "max seconds"
        }
        val container = androidx.appcompat.widget.LinearLayoutCompat(this).apply {
            orientation = androidx.appcompat.widget.LinearLayoutCompat.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(minEdit)
            addView(maxEdit)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.delay_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val mn = minEdit.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 6
                val mx = maxEdit.text.toString().toIntOrNull()?.coerceAtLeast(mn) ?: 10
                Settings.setDelayRange(this, mn, mx)
                refreshFromSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- Cooldown ----------
    private fun showCooldownDialog() {
        val edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(Settings.getCooldownMinutes(this@MainActivity).toString())
        }
        val container = androidx.appcompat.widget.LinearLayoutCompat(this).apply {
            orientation = androidx.appcompat.widget.LinearLayoutCompat.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(edit)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.cooldown_dialog_title)
            .setMessage(R.string.cooldown_dialog_message)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val cd = edit.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 30
                Settings.setCooldownMinutes(this, cd)
                refreshFromSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- Permission ----------
    private fun isNotificationListenerEnabled(): Boolean {
        val flat = AndroidSettings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val cn = ComponentName(this, AutoReplyNotificationListener::class.java)
        val cnString = cn.flattenToString()
        return flat.split(":").any { it == cnString }
    }

    private fun showPermissionPrompt() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_dialog_title)
            .setMessage(R.string.permission_dialog_message)
            .setPositiveButton(R.string.permission_open_settings) { _, _ ->
                openNotificationAccessSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cant_open_settings, Toast.LENGTH_LONG).show()
        }
    }
}
