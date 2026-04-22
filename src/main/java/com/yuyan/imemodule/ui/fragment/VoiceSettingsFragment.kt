package com.yuyan.imemodule.ui.fragment

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import com.yuyan.imemodule.R
import com.yuyan.imemodule.agent.AgentDebugLogStore
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.ui.fragment.base.ManagedPreferenceFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().voice) {
    private lateinit var updatedPref: Preference
    private lateinit var channelPref: Preference
    private lateinit var urlPref: Preference
    private lateinit var statusPref: Preference
    private lateinit var elapsedPref: Preference
    private lateinit var errorPref: Preference
    private lateinit var responsePref: Preference
    private lateinit var promptPref: Preference
    private lateinit var screenshotPathPref: Preference
    private lateinit var screenshotResultPref: Preference

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val ctx = requireContext()
        val category = PreferenceCategory(ctx).apply {
            setTitle(R.string.ai_debug_logs)
            isIconSpaceReserved = false
        }
        screen.addPreference(category)

        category.addPreference(Preference(ctx).apply {
            setTitle(R.string.ai_open_accessibility_settings)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                runCatching {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }.onFailure {
                    Toast.makeText(ctx, R.string.ai_open_accessibility_settings_failed, Toast.LENGTH_SHORT).show()
                }
                true
            }
        })
        category.addPreference(Preference(ctx).apply {
            setTitle(R.string.ai_debug_refresh)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                renderDebugInfo()
                true
            }
        })
        category.addPreference(Preference(ctx).apply {
            setTitle(R.string.ai_debug_clear)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                AgentDebugLogStore.clear()
                renderDebugInfo()
                true
            }
        })

        updatedPref = createReadOnlyPreference(ctx, R.string.ai_debug_updated).also(category::addPreference)
        channelPref = createReadOnlyPreference(ctx, R.string.ai_debug_channel).also(category::addPreference)
        urlPref = createReadOnlyPreference(ctx, R.string.ai_debug_url).also(category::addPreference)
        statusPref = createReadOnlyPreference(ctx, R.string.ai_debug_status).also(category::addPreference)
        elapsedPref = createReadOnlyPreference(ctx, R.string.ai_debug_elapsed).also(category::addPreference)
        errorPref = createReadOnlyPreference(ctx, R.string.ai_debug_error).also(category::addPreference)
        responsePref = createReadOnlyPreference(ctx, R.string.ai_debug_response).also(category::addPreference)
        promptPref = createReadOnlyPreference(ctx, R.string.ai_debug_prompt).also(category::addPreference)
        screenshotPathPref = createReadOnlyPreference(ctx, R.string.ai_debug_screenshot_path).also(category::addPreference)
        screenshotResultPref = createReadOnlyPreference(ctx, R.string.ai_debug_screenshot_result).also(category::addPreference)
        renderDebugInfo()
    }

    override fun onResume() {
        super.onResume()
        renderDebugInfo()
    }

    private fun createReadOnlyPreference(context: android.content.Context, titleRes: Int): Preference {
        return Preference(context).apply {
            setTitle(titleRes)
            isSelectable = false
            isIconSpaceReserved = false
            summary = getString(R.string.ai_debug_empty)
        }
    }

    private fun renderDebugInfo() {
        val snapshot = AgentDebugLogStore.read()
        if (snapshot.updatedAtMs <= 0L) {
            val empty = getString(R.string.ai_debug_empty)
            updatedPref.summary = empty
            channelPref.summary = empty
            urlPref.summary = empty
            statusPref.summary = empty
            elapsedPref.summary = empty
            errorPref.summary = empty
            responsePref.summary = empty
            promptPref.summary = empty
            screenshotPathPref.summary = empty
            screenshotResultPref.summary = empty
            return
        }
        val timeString = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(snapshot.updatedAtMs))
        updatedPref.summary = timeString
        channelPref.summary = snapshot.channel.ifBlank { "-" }
        urlPref.summary = snapshot.url.ifBlank { "-" }
        statusPref.summary = snapshot.statusCode.toString()
        elapsedPref.summary = "${snapshot.elapsedMs} ms"
        errorPref.summary = snapshot.error.ifBlank { "-" }
        responsePref.summary = snapshot.responsePreview.ifBlank { "-" }
        promptPref.summary = snapshot.requestPrompt.ifBlank { "-" }
        screenshotPathPref.summary = snapshot.screenshotFilePath.ifBlank { "-" }
        screenshotResultPref.summary = snapshot.screenshotUnderstandingResult.ifBlank { "-" }
    }
}