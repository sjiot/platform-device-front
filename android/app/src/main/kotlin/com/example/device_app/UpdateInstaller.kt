package com.example.device_app

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/**
 * 원격 업데이트 APK 무인 설치.
 *
 * 안드로이드는 일반 앱이 사용자 확인 없이 설치하는 것을 막지만,
 * 기기 소유자(device owner)로 등록된 앱은 PackageInstaller로 조용히 설치할 수 있다.
 * 설치가 끝나면 시스템이 이 앱의 프로세스를 내리고,
 * MY_PACKAGE_REPLACED를 받은 [PackageReplacedReceiver]가 새 버전을 다시 켠다.
 *
 * 설치 결과는 [UpdateStatusReceiver]로 온다.
 */
object UpdateInstaller {
    const val ACTION_INSTALL_STATUS = "com.example.device_app.INSTALL_STATUS"

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /**
     * [apk]를 설치 세션에 넣고 commit한다. 성공하면 곧 프로세스가 종료된다.
     *
     * 기기 소유자가 아니면 IllegalStateException. (그때는 MainActivity.installApk로)
     * 시간이 걸리는 작업(60MB 복사)이라 메인 스레드 밖에서 부른다.
     */
    fun installSilently(context: Context, apk: File) {
        check(isDeviceOwner(context)) { "기기 소유자가 아니라 무인 설치를 할 수 없음" }
        require(apk.exists()) { "APK 파일이 없음: ${apk.path}" }

        val installer = context.packageManager.packageInstaller

        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)

        installer.openSession(sessionId).use { session ->
            session.openWrite("device_app.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out, 1 shl 16) }
                session.fsync(out)
            }

            val statusIntent = Intent(context, UpdateStatusReceiver::class.java)
                .setAction(ACTION_INSTALL_STATUS)

            // 시스템이 결과(extra)를 채워 넣어야 하므로 MUTABLE
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }

            val pending = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)

            session.commit(pending.intentSender)
        }
    }
}
