package com.example.device_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 앱이 새 버전으로 바뀐 직후(MY_PACKAGE_REPLACED) 다시 켠다.
 *
 * 무인 설치든 adb 설치든 설치가 끝나면 프로세스가 내려가 있으므로
 * 여기서 띄워야 조립대 화면이 저절로 돌아온다.
 * 기기 소유자 앱은 백그라운드에서 액티비티를 띄울 수 있다.
 * (기기 소유자가 아니면 시스템이 막을 수 있는데, 그 경우는 설치 화면에서 [열기]를 누른다.)
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        // 다음 실행이 "업데이트 뒤 재실행"임을 표시 → Dart가 조립대 번호를 복원한다.
        UpdatePrefs.markRelaunchAfterUpdate(context)

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)

        if (launch == null) {
            Log.w(TAG, "실행 인텐트를 찾지 못함")
            return
        }

        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        try {
            context.startActivity(launch)
            Log.i(TAG, "업데이트 뒤 앱 재실행")
        } catch (e: Exception) {
            Log.w(TAG, "업데이트 뒤 앱 재실행 실패", e)
        }
    }

    private companion object {
        const val TAG = "DeviceAppUpdater"
    }
}
