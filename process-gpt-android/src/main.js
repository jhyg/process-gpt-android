import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import axios from 'axios'
import { Capacitor } from '@capacitor/core'
import { PushNotifications } from '@capacitor/push-notifications'

// axios 기본 설정
axios.defaults.baseURL = Capacitor.isNativePlatform() 
  ? 'https://jhyg.process-gpt.io' 
  : window.location.origin;

// 푸시 알림 초기화 (네이티브 플랫폼에서만)
if (Capacitor.isNativePlatform()) {
  // 푸시 알림 권한 요청 및 초기화
  PushNotifications.requestPermissions().then(result => {
    if (result.receive === 'granted') {
      // 푸시 알림 등록
      PushNotifications.register();
      console.log('푸시 알림 권한 승인 및 등록 완료');
    } else {
      console.log('푸시 알림 권한 거부됨');
    }
  }).catch(err => {
    console.error('푸시 알림 권한 요청 오류:', err);
  });

  // 푸시 알림 이벤트 리스너
  PushNotifications.addListener('registration', (token) => {
    console.log('Push registration success: ' + token.value);
    // 여기에서 토큰을 서버로 전송하는 코드를 추가할 수 있습니다.
  });

  PushNotifications.addListener('registrationError', (error) => {
    console.error('Push registration error: ' + JSON.stringify(error));
  });

  PushNotifications.addListener('pushNotificationReceived', (notification) => {
    console.log('Push notification received: ' + JSON.stringify(notification));
    // 앱이 포그라운드에 있을 때 알림을 처리합니다.
  });

  PushNotifications.addListener('pushNotificationActionPerformed', (notification) => {
    console.log('Push notification action performed: ' + JSON.stringify(notification));
    // 알림 클릭 이벤트를 처리합니다.
  });
  
  // 네이티브에서 보내는 이벤트도 처리 (FCMService에서 triggerWindowJSEvent로 전송)
  window.addEventListener('pushNotificationReceived', (event) => {
    console.log('Window event - 푸시 알림 수신:', event.detail);
    // 이벤트 디테일에서 알림 정보 추출
    try {
      const notification = JSON.parse(event.detail);
      console.log('푸시 알림 내용:', notification);
      // 여기서 알림 처리 (예: 메시지 표시 등)
    } catch (e) {
      console.error('알림 파싱 오류:', e);
    }
  });
  
  window.addEventListener('pushNotificationToken', (event) => {
    console.log('Window event - 푸시 토큰 수신:', event.detail);
    try {
      const tokenInfo = JSON.parse(event.detail);
      console.log('FCM 토큰:', tokenInfo.value);
      // 토큰을 서버에 전송하는 코드를 여기에 추가
    } catch (e) {
      console.error('토큰 파싱 오류:', e);
    }
  });
}

// 앱 생성 및 마운트
const app = createApp(App)

// axios를 전역으로 사용 가능하게 설정
app.config.globalProperties.$axios = axios

// 라우터 사용 (라우터가 있는 경우)
if (router) {
  app.use(router)
}

app.mount('#app')
