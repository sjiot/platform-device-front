package com.example.device_app

import android.app.admin.DeviceAdminReceiver

/**
 * 기기 소유자(device owner) 등록용 관리자 컴포넌트.
 *
 * 기기를 처음 세팅할 때 adb로 한 번 등록한다. (tools/deploy_devices.sh)
 *   adb shell dpm set-device-owner com.example.device_app/.KioskAdminReceiver
 *
 * 등록돼 있으면
 *   - 화면 고정(lock task)을 확인 창 없이 건다.
 *   - 원격 업데이트 APK를 사용자 확인 없이 설치한다. (UpdateInstaller)
 * 등록돼 있지 않아도 앱은 동작하며, 그때는 관리자가 업데이트 버튼으로 설치한다.
 */
class KioskAdminReceiver : DeviceAdminReceiver()
