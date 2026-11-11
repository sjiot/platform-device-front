import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../debug/app_log.dart';

/// 앱이 켜질 때 네이티브가 알려 주는 실행 정보.
class LaunchInfo {
  /// 업데이트 설치 직후 자동으로 다시 켜진 실행인지.
  final bool afterUpdate;

  /// 마지막으로 골랐던 조립대 번호. 아직 고른 적이 없으면 null.
  final String? savedWorkstation;

  const LaunchInfo({required this.afterUpdate, this.savedWorkstation});

  static const LaunchInfo none = LaunchInfo(afterUpdate: false);
}

/// 안드로이드 화면 고정(lock task) 제어.
///
/// 고정 중에는 홈·최근앱 버튼이 동작하지 않아 앱 밖으로 나갈 수 없고,
/// 관리자 비밀번호를 통과했을 때만 [stop]으로 풀고 종료한다.
/// 안드로이드가 아니면 모두 무시한다.
///
/// 조립대 번호 저장([saveWorkstation] · [launchInfo])도 여기서 맡는다.
/// 업데이트가 끝나 앱이 저절로 다시 켜졌을 때 조립대 화면으로 바로 돌아가기 위한 것.
class KioskService {
  static const MethodChannel _channel = MethodChannel('device_app/kiosk');

  static bool get _supported => !kIsWeb && Platform.isAndroid;

  /// 화면 고정 시작. 기기 소유자로 등록돼 있지 않으면
  /// 시스템이 확인 창을 한 번 띄운다. (등록돼 있으면 바로 고정)
  static Future<bool> start() async {
    if (!_supported) return false;

    try {
      final bool? ok = await _channel.invokeMethod<bool>('startLockTask');

      AppLog.i(LogTag.kiosk, '화면 고정 시작: $ok');

      return ok ?? false;
    } catch (e) {
      AppLog.w(LogTag.kiosk, '화면 고정 실패: $e');

      return false;
    }
  }

  /// 화면 고정 해제.
  static Future<bool> stop() async {
    if (!_supported) return false;

    try {
      final bool? ok = await _channel.invokeMethod<bool>('stopLockTask');

      AppLog.i(LogTag.kiosk, '화면 고정 해제: $ok');

      return ok ?? false;
    } catch (e) {
      AppLog.w(LogTag.kiosk, '화면 고정 해제 실패: $e');

      return false;
    }
  }

  /// 지금 고정 상태인지.
  static Future<bool> isLocked() async {
    if (!_supported) return false;

    try {
      return await _channel.invokeMethod<bool>('isLocked') ?? false;
    } catch (e) {
      AppLog.w(LogTag.kiosk, '화면 고정 상태 조회 실패: $e');

      return false;
    }
  }

  /// 앱이 켜질 때 한 번 읽는다. "업데이트 뒤 재실행" 표시는 읽으면서 지워진다.
  static Future<LaunchInfo> launchInfo() async {
    if (!_supported) return LaunchInfo.none;

    try {
      final Map<Object?, Object?>? map = await _channel
          .invokeMethod<Map<Object?, Object?>>('launchInfo');

      if (map == null) return LaunchInfo.none;

      return LaunchInfo(
        afterUpdate: map['afterUpdate'] == true,
        savedWorkstation: map['savedWorkstation']?.toString(),
      );
    } catch (e) {
      AppLog.w(LogTag.kiosk, '실행 정보 조회 실패: $e');

      return LaunchInfo.none;
    }
  }

  /// 조립대 번호를 기기에 저장한다. (업데이트 뒤 재실행 때 복원용)
  static Future<void> saveWorkstation(String number) async {
    if (!_supported) return;

    try {
      await _channel.invokeMethod<bool>('saveWorkstation', {'number': number});
    } catch (e) {
      AppLog.w(LogTag.kiosk, '조립대 번호 저장 실패: $e');
    }
  }
}
