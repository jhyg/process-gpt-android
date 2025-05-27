package com.process.gpt;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.app.NotificationManager;
import android.provider.Settings;
import android.content.Intent;
import android.app.NotificationChannel;
import com.getcapacitor.BridgeActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.getcapacitor.JSObject;
import com.getcapacitor.Bridge;
import java.util.ArrayList;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.ComponentName;
import android.app.NotificationChannel;
import android.provider.Settings.Secure;
import android.os.Handler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "ProcessGPTMain";
    private static MainActivity instance;
    private static final int NOTIFICATION_PERMISSION_CODE = 123;
    private static final String CHANNEL_ID = "process_gpt_channel";
    private String pendingToken = null;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        
        // Android 13 이상에서 알림 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            } else {
                enableNotificationSettings();
            }
        } else {
            enableNotificationSettings();
        }
        
        // Firebase 초기화
        FirebaseApp.initializeApp(this);
        
        // WebViewClient 설정
        WebView webView = getBridge().getWebView();
        
        // JavaScript Interface 추가
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void changeTenant(String tenantId) {
                runOnUiThread(() -> {
                    webView.loadUrl("https://" + tenantId + ".process-gpt.io");
                });
            }
        }, "AndroidBridge");
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                
                // 페이지 로드 완료 후 5초 대기 (main.ts 실행 시간 확보)
                new Handler().postDelayed(() -> {
                    // FCM 토큰 가져오기
                    FirebaseMessaging.getInstance().getToken()
                        .addOnCompleteListener(task -> {
                            if (!task.isSuccessful()) {
                                System.err.println(TAG + ": FCM 토큰 획득 실패");
                                return;
                            }
                            String token = task.getResult();
                            System.out.println(TAG + ": FCM 토큰: " + token);

                            // 웹뷰로 이벤트 전송
                            Bridge bridge = getBridge();
                             if (bridge != null) {
                                try {
                                    JSObject eventData = new JSObject();
                                    eventData.put("token", token);
                                    bridge.triggerWindowJSEvent("fcmTokenReceived", eventData.toString());
                                    System.out.println(TAG + ": 웹뷰 준비완료 후 토큰 전송");
                                } catch (Exception e) {
                                    System.err.println(TAG + ": 토큰 전송 중 오류: " + e.getMessage());
                                }
                            }
                        });
                }, 5000); // 5초 대기
            }
        });
    }
    
    private void enableNotificationSettings() {
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Process GPT 알림",
                    NotificationManager.IMPORTANCE_HIGH); // HIGH로 설정하면 헤드업 알림 활성화
                    
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000}); // 진동 패턴 설정
            channel.setShowBadge(true);
            channel.setBypassDnd(true);
            
            // 알림음 설정
            channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, 
                    channel.getAudioAttributes());
                    
            notificationManager.createNotificationChannel(channel);
            
            // 알림 설정 강제 활성화 시도
            try {
                ComponentName componentName = new ComponentName(getPackageName(), 
                        getPackageName() + ".MainActivity");
                notificationManager.setNotificationPolicy(
                        new NotificationManager.Policy(
                                NotificationManager.Policy.PRIORITY_CATEGORY_CALLS |
                                NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES |
                                NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS |
                                NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS |
                                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
                                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                                NotificationManager.Policy.PRIORITY_SENDERS_ANY));
            } catch (Exception e) {
                System.err.println(TAG + ": 알림 정책 설정 실패: " + e.getMessage());
            }
        }
    }
    
    // Bridge 객체를 가져오는 정적 메소드 (오버라이드 아님)
    public static Bridge getCapBridge() {
        return instance != null ? instance.getBridge() : null;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                System.out.println(TAG + ": 알림 권한이 승인되었습니다.");
                enableNotificationSettings();
            } else {
                System.out.println(TAG + ": 알림 권한이 거부되었습니다.");
            }
        }
    }
}
