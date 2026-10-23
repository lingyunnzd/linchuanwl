package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences

class RemoteAnnouncementPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAcknowledgedVersion(): String {
        return prefs.getString(KEY_ACKNOWLEDGED_VERSION, "") ?: ""
    }

    fun setAcknowledgedVersion(version: String) {
        prefs.edit().putString(KEY_ACKNOWLEDGED_VERSION, version).apply()
    }

    companion object {
        private const val PREFS_NAME = "remote_announcement_preferences"
        private const val KEY_ACKNOWLEDGED_VERSION = "acknowledged_version"
    }
}
