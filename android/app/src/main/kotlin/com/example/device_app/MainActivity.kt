package com.example.device_app

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.security.MessageDigest

/**
 * 조립대 단말용 화면 고정(lock task).
 *
 * 기기 소유자(device owner)로 등록돼 있지 않으면 처음 startLockTask() 때
 * 시스템이 확인 창을 한 번 띄운다. 고정 중에는 홈·최근앱이 동작하지 않고,
 * 앱에서 관리자 비밀번호를 통과했을 때만 stopLockTask()로 풀린다.
 *
 * 기기 소유자로 등록돼 있으면(KioskAdminReceiver) 확인 창 없이 바로 고정된다.
 *
 * 원격 업데이트(device_app/updater):
 *   - 기기 소유자면 UpdateInstaller로 사용자 확인 없이 설치한다.
 *   - 아니면 Dart가 받아 둔 APK의 설치 화면을 연다.
 *     화면 고정 중에는 설치 화면(다른 앱)이 뜰 수 없어서 여기서 먼저 고정을 푼다.
 */
class MainActivity : FlutterActivity() {
    private val channelName = "device_app/kiosk"
    private val updaterChannelName = "device_app/updater"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 앱이 화면에 떠 있는 동안 기기의 화면 꺼짐 시간과 무관하게 화면을 켜 둔다.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startLockTask" -> {
                        try {
                            if (!isInLockTaskMode()) {
                                allowLockTaskIfDeviceOwner()
                                startLockTask()
                            }
                            result.success(true)
                        } catch (e: Exception) {
                            result.error("LOCK_TASK_FAILED", e.message, null)
                        }
                    }

                    "stopLockTask" -> {
                        try {
                            if (isInLockTaskMode()) {
                                stopLockTask()
                            }
                            result.success(true)
                        } catch (e: Exception) {
                            result.error("UNLOCK_TASK_FAILED", e.message, null)
                        }
                    }

                    "isLocked" -> result.success(isInLockTaskMode())

                    // 앱이 켜질 때 한 번: 업데이트 뒤 재실행인지, 저장된 조립대 번호
                    "launchInfo" -> result.success(
                        mapOf(
                            "afterUpdate" to UpdatePrefs.consumeRelaunchAfterUpdate(this),
                            "savedWorkstation" to UpdatePrefs.savedWorkstation(this),
                        )
                    )

                    "saveWorkstation" -> {
                        UpdatePrefs.saveWorkstation(this, call.argument<String>("number"))
                        result.success(true)
                    }

                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, updaterChannelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    // 업데이트 APK를 받아 둘 폴더 (res/xml/update_file_paths.xml과 같은 경로)
                    "updateDir" -> {
                        val dir = File(cacheDir, "updates").apply { mkdirs() }
                        result.success(dir.absolutePath)
                    }

                    // 받은 APK 검증용. 60MB 넘는 파일이라 메인 스레드 밖에서 계산한다.
                    "sha256" -> {
                        val path = call.argument<String>("path")

                        if (path == null) {
                            result.error("BAD_ARGS", "path가 없음", null)
                        } else {
                            Thread {
                                try {
                                    val hex = sha256Of(File(path))
                                    runOnUiThread { result.success(hex) }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        result.error("SHA256_FAILED", e.message, null)
                                    }
                                }
                            }.start()
                        }
                    }

                    "installApk" -> {
                        val path = call.argument<String>("path")

                        if (path == null) {
                            result.error("BAD_ARGS", "path가 없음", null)
                        } else {
                            try {
                                installApk(File(path))
                                result.success(true)
                            } catch (e: Exception) {
                                result.error("INSTALL_FAILED", e.message, null)
                            }
                        }
                    }

                    "isDeviceOwner" -> result.success(UpdateInstaller.isDeviceOwner(this))

                    // 기기 소유자 전용 무인 설치. 성공(true)은 설치 세션 commit까지를 뜻한다.
                    // 실제 결과는 UpdateStatusReceiver → consumeInstallError로 확인한다.
                    "installApkSilently" -> {
                        val path = call.argument<String>("path")

                        if (path == null) {
                            result.error("BAD_ARGS", "path가 없음", null)
                        } else {
                            Thread {
                                try {
                                    UpdateInstaller.installSilently(this, File(path))
                                    runOnUiThread { result.success(true) }
                                } catch (e: Exception) {
                                    Log.e("DeviceAppUpdater", "무인 설치 실패", e)
                                    runOnUiThread {
                                        result.error("SILENT_INSTALL_FAILED", e.message, null)
                                    }
                                }
                            }.start()
                        }
                    }

                    "consumeInstallError" -> result.success(UpdatePrefs.consumeInstallError(this))

                    else -> result.notImplemented()
                }
            }
    }

    /**
     * 안드로이드 설치 화면을 연다.
     *
     * 처음 한 번은 "출처를 알 수 없는 앱 설치" 허용 화면이 먼저 뜬다.
     * 설치를 취소하고 돌아오면 Dart 쪽 resumed 처리에서 다시 고정한다.
     */
    private fun installApk(file: File) {
        require(file.exists()) { "APK 파일이 없음: ${file.path}" }

        val uri = FileProvider.getUriForFile(this, "$packageName.update_provider", file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (isInLockTaskMode()) {
            stopLockTask()
        }

        // 고정 해제가 반영된 뒤에 설치 화면을 띄운다.
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("DeviceAppUpdater", "설치 화면 열기 실패", e)
            }
        }, 500)
    }

    /**
     * 기기 소유자면 이 앱을 화면 고정 허용 목록에 올린다.
     * 그래야 startLockTask()가 확인 창 없이 바로 고정되고,
     * 업데이트 뒤 자동 재실행 때도 사람 손이 필요 없다.
     */
    private fun allowLockTaskIfDeviceOwner() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (!dpm.isDeviceOwnerApp(packageName)) return

        try {
            val admin = ComponentName(this, KioskAdminReceiver::class.java)
            dpm.setLockTaskPackages(admin, arrayOf(packageName))
        } catch (e: Exception) {
            Log.w("DeviceAppKiosk", "화면 고정 허용 목록 설정 실패", e)
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)

            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isInLockTaskMode(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            am.isInLockTaskMode
        }
    }
}
