#!/usr/bin/env bash
#
# 연결된 모든 안드로이드 기기에 DeviceApp을 설치하고 기기 소유자로 등록한 뒤 켠다.
#
#   tools/deploy_devices.sh              Releases(device-app-latest)의 최신 APK를 받아 설치
#   tools/deploy_devices.sh app.apk      지정한 APK 설치
#
# 필요: adb(Android SDK platform-tools), gh(GitHub CLI, 최신 APK를 받을 때만)
# 기기: USB 디버깅 켜기 → 연결 → "이 컴퓨터를 항상 허용" 체크
#
# 기기 소유자 등록은 기기에 계정이 하나도 없어야 성공한다. 실패해도 설치는 끝난 상태이며,
# 그 기기는 관리자가 업데이트 버튼으로 설치하는 방식으로 동작한다.

set -euo pipefail

REPO="sjiot/platform-device-front"
RELEASE_TAG="device-app-latest"
PACKAGE="com.example.device_app"
ADMIN="$PACKAGE/.KioskAdminReceiver"

ADB="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"

if [ ! -x "$ADB" ]; then
  echo "adb를 찾지 못했습니다. Android SDK platform-tools를 설치하거나 ADB=경로 로 지정하세요." >&2
  exit 1
fi

# 1. APK 준비
if [ $# -ge 1 ]; then
  APK="$1"
else
  WORK="$(mktemp -d)"
  echo "▶ $REPO 의 $RELEASE_TAG 에서 최신 APK 내려받기"
  gh release download "$RELEASE_TAG" -R "$REPO" -p 'device_app-*.apk' -p version.json -D "$WORK" --clobber
  APK="$(ls "$WORK"/device_app-*.apk | head -n1)"
  echo "   $(cat "$WORK/version.json" | tr -d '\n' | sed 's/  */ /g')"
fi

if [ ! -f "$APK" ]; then
  echo "APK가 없습니다: $APK" >&2
  exit 1
fi

echo "▶ 설치할 APK: $APK ($(du -h "$APK" | cut -f1))"

# 2. 기기 목록
DEVICES=()
while IFS= read -r LINE; do
  [ -n "$LINE" ] && DEVICES+=("$LINE")
done < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')

if [ ${#DEVICES[@]} -eq 0 ]; then
  echo "연결된 기기가 없습니다. USB 디버깅을 켜고 연결한 뒤 기기에서 허용을 눌러 주세요." >&2
  "$ADB" devices
  exit 1
fi

echo "▶ 기기 ${#DEVICES[@]}대: ${DEVICES[*]}"

# 3. 기기마다 설치 → 기기 소유자 등록 → 실행
for SERIAL in "${DEVICES[@]}"; do
  MODEL="$("$ADB" -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
  echo
  echo "━━━ $SERIAL ($MODEL)"

  echo "  설치 중..."
  "$ADB" -s "$SERIAL" install -r -d "$APK" | sed 's/^/    /'

  if "$ADB" -s "$SERIAL" shell dumpsys device_policy | grep -q "$PACKAGE"; then
    echo "  기기 소유자: 이미 등록됨"
  else
    echo "  기기 소유자 등록 시도..."
    if OUT="$("$ADB" -s "$SERIAL" shell dpm set-device-owner "$ADMIN" 2>&1)"; then
      echo "    $OUT" | head -n1
    else
      echo "    실패: $(echo "$OUT" | tail -n1)"
      echo "    → 기기에 계정이 있으면 설정 → 계정에서 지운 뒤 다시 실행하세요."
      echo "    → 등록 없이도 앱은 동작하며, 업데이트는 관리자가 버튼으로 설치합니다."
    fi
  fi

  echo "  앱 실행"
  "$ADB" -s "$SERIAL" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1 || true

  INSTALLED="$("$ADB" -s "$SERIAL" shell dumpsys package "$PACKAGE" | grep -m1 versionCode | tr -d '\r' | sed 's/^ *//')"
  echo "  설치됨: $INSTALLED"
done

echo
echo "완료. 이후에는 main 브랜치에 push만 하면 기기가 10분 안에 스스로 업데이트합니다."
