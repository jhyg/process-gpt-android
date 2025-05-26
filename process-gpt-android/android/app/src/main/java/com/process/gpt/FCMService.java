package com.process.gpt;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.Bridge;

public class FCMService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "process_gpt_channel";
    private static final String TAG = "ProcessGPTFCM";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // 메시지 데이터 확인
        Map<String, String> data = remoteMessage.getData();
        
        // 알림 제목과 내용 설정
        String title = remoteMessage.getNotification() != null ? 
                remoteMessage.getNotification().getTitle() : data.get("title");
        String body = remoteMessage.getNotification() != null ? 
                remoteMessage.getNotification().getBody() : data.get("body");
        
        // 디버그 로그
        System.out.println(TAG + ": 푸시 알림 수신 - " + title + ": " + body);
        
        // 앱이 포그라운드 상태일 때도 알림을 표시
        sendNotification(title, body, data);
        
        // 앱이 포그라운드 상태일 때 Bridge에 알림 데이터 전달
        try {
            Bridge bridge = MainActivity.getCapBridge();
            if (bridge != null) {
                JSObject notificationJson = new JSObject();
                notificationJson.put("title", title);
                notificationJson.put("body", body);
                
                // 데이터 필드 추가
                JSObject dataJson = new JSObject();
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    dataJson.put(entry.getKey(), entry.getValue());
                }
                notificationJson.put("data", dataJson);
                
                // Capacitor 이벤트 발생 (runOnUiThread 사용)
                MainActivity activity = (MainActivity) bridge.getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        bridge.triggerWindowJSEvent("pushNotificationReceived", notificationJson.toString());
                        System.out.println(TAG + ": 포그라운드 이벤트 전달 성공");
                    });
                }
            } else {
                System.err.println(TAG + ": Bridge가 null입니다.");
            }
        } catch (Exception e) {
            System.err.println(TAG + ": Capacitor 이벤트 전달 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onNewToken(String token) {
        System.out.println(TAG + ": 새 FCM 토큰: " + token);
        
        // FCM 토큰이 갱신되면 브릿지를 통해 웹앱에 알림
        try {
            Bridge bridge = MainActivity.getCapBridge();
            if (bridge != null) {
                JSObject tokenJson = new JSObject();
                tokenJson.put("value", token);
                
                // Capacitor 이벤트 발생
                bridge.triggerWindowJSEvent("pushNotificationToken", tokenJson.toString());
            }
        } catch (Exception e) {
            System.err.println(TAG + ": Capacitor 토큰 이벤트 전달 중 오류: " + e.getMessage());
        }
    }

    private void sendNotification(String title, String messageBody, Map<String, String> data) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        // 데이터 페이로드를 인텐트에 추가
        for (Map.Entry<String, String> entry : data.entrySet()) {
            intent.putExtra(entry.getKey(), entry.getValue());
        }
        
        // 알림을 클릭했을 때 실행될 PendingIntent 생성
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 알림 사운드 설정
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
        // 알림 생성
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Android Oreo 이상에서는 알림 채널 생성 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Process GPT 알림",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Process GPT 앱의 중요 알림");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            channel.setShowBadge(true);
            channel.setBypassDnd(true);
            notificationManager.createNotificationChannel(channel);
        }

        // 알림 표시 (고유한 ID 생성)
        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, notificationBuilder.build());
    }
} 