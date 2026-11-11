package com.example.device_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat

/**
 * [UpdateInstaller]가 commit한 설치 세션의 결과.
 *
 * 성공이면 시스템이 곧 프로세스를 내리므로 할 일이 없다.
 * 실패 사유는 [UpdatePrefs]에 남겨 두고, Dart가 읽어서 수동 설치 버튼으로 넘어간다.
 */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "업데이트 설치 성공. 곧 새 버전으로 다시 시작")
            }

            // 기기 소유자가 아닐 때만 온다. 시스템 확인 화면을 띄워 준다.
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(
                    intent, Intent.EXTRA_INTENT, Intent::class.java
                )

                if (confirm == null) {
                    UpdatePrefs.setInstallError(context, "사용자 확인이 필요하지만 확인 화면이 없음")
                    return
                }

                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                try {
                    context.startActivity(confirm)
                } catch (e: Exception) {
                    Log.w(TAG, "설치 확인 화면 열기 실패", e)
                    UpdatePrefs.setInstallError(context, "설치 확인 화면 열기 실패: ${e.message}")
                }
            }

            else -> {
                Log.e(TAG, "업데이트 설치 실패 (status=$status): $message")
                UpdatePrefs.setInstallError(context, "status=$status $message".trim())
            }
        }
    }

    private companion object {
        const val TAG = "DeviceAppUpdater"
    }
}
