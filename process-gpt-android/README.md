# Process GPT 안드로이드 앱

Vue3와 Capacitor를 사용한 Process GPT 모바일 앱입니다.

## 설정 방법

### 1. Firebase 프로젝트 설정

푸시 알림을 사용하기 위해 Firebase 프로젝트 설정이 필요합니다:

1. [Firebase 콘솔](https://console.firebase.google.com/)에서 프로젝트 생성
2. 안드로이드 앱 추가 (패키지 이름: `com.process.gpt`)
3. `google-services.json` 파일 다운로드
4. 다운로드한 `google-services.json` 파일을 `android/app/` 디렉토리에 추가

### 2. 빌드 및 실행

```bash
# 의존성 설치
npm install

# 웹 애플리케이션 빌드
npm run build

# Capacitor 동기화
npm run capacitor:sync

# 안드로이드 스튜디오 열기
npm run capacitor:android
```

## 앱 구성 설정

`capacitor.config.json` 파일에서 웹뷰의 URL을 설정할 수 있습니다:

```json
{
  "server": {
    "url": "https://jhyg.process-gpt.io",
    "cleartext": true
  }
}
```

## 푸시 알림 테스트

푸시 알림 테스트는 Firebase 콘솔에서 테스트 메시지를 보내거나, 다음 API를 사용하여 테스트할 수 있습니다:

```
POST https://fcm.googleapis.com/fcm/send
Headers:
  Authorization: key=YOUR_SERVER_KEY
  Content-Type: application/json

Body:
{
  "to": "DEVICE_TOKEN",
  "notification": {
    "title": "테스트 제목",
    "body": "테스트 내용"
  },
  "data": {
    "url": "https://jhyg.process-gpt.io/some-path"
  }
}
```
