package com.example.device_app

import android.content.Context
import android.content.SharedPreferences

/**
 * 업데이트 전후로 넘겨야 하는 작은 값들.
 *
 * 앱이 새 버전으로 바뀌면 프로세스가 통째로 다시 뜨므로
 * 메모리에 두면 사라진다. SharedPreferences에 적어 둔다.
 */
object UpdatePrefs {
    private const val NAME = "device_app_update"

    private const val KEY_RELAUNCH_AFTER_UPDATE = "relaunch_after_update"
    private const val KEY_WORKSTATION = "workstation"
    private const val KEY_INSTALL_ERROR = "install_error"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 업데이트 뒤 자동으로 다시 켜진 실행인지 표시한다. */
    fun markRelaunchAfterUpdate(context: Context) {
        prefs(context).edit().putBoolean(KEY_RELAUNCH_AFTER_UPDATE, true).apply()
    }

    /** 표시를 읽고 바로 지운다. 한 번만 true. */
    fun consumeRelaunchAfterUpdate(context: Context): Boolean {
        val p = prefs(context)
        val value = p.getBoolean(KEY_RELAUNCH_AFTER_UPDATE, false)
        if (value) p.edit().remove(KEY_RELAUNCH_AFTER_UPDATE).apply()
        return value
    }

    /** 마지막으로 고른 조립대 번호. 업데이트 뒤 재실행 때 복원한다. */
    fun saveWorkstation(context: Context, number: String?) {
        prefs(context).edit().apply {
            if (number == null) remove(KEY_WORKSTATION) else putString(KEY_WORKSTATION, number)
        }.apply()
    }

    fun savedWorkstation(context: Context): String? =
        prefs(context).getString(KEY_WORKSTATION, null)

    /** 무인 설치가 실패했을 때 설치 서비스가 남기는 사유. */
    fun setInstallError(context: Context, message: String) {
        prefs(context).edit().putString(KEY_INSTALL_ERROR, message).apply()
    }

    fun consumeInstallError(context: Context): String? {
        val p = prefs(context)
        val value = p.getString(KEY_INSTALL_ERROR, null)
        if (value != null) p.edit().remove(KEY_INSTALL_ERROR).apply()
        return value
    }
}
