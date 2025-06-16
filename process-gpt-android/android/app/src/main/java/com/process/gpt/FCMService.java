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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

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
        
        // 앱이 포그라운드 상태인지 확인
        if (MainActivity.isAppInForeground()) {
            System.out.println(TAG + ": 앱이 포그라운드 상태이므로 알림 표시하지 않음");
        } else {
            // 앱이 백그라운드일 때만 알림 표시
            sendNotification(title, body, data);
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
        
        // 앱 아이콘을 비트맵으로 변환하여 Large Icon으로 사용
        Bitmap largeIconBitmap = getLauncherIconBitmap();
        
        // 알림 생성
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_noti) // 전용 알림 아이콘
                        .setLargeIcon(largeIconBitmap) // 큰 아이콘 (앱 아이콘)
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
    
    /**
     * 앱 런처 아이콘을 비트맵으로 변환하여 반환
     */
    private Bitmap getLauncherIconBitmap() {
        try {
            System.out.println(TAG + ": 앱 아이콘 비트맵 생성 시작");
            
            // 앱 아이콘 가져오기 (ic_launcher 또는 ic_launcher_round 사용)
            Drawable iconDrawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher);
            System.out.println(TAG + ": ic_launcher 로드 결과: " + (iconDrawable != null ? "성공" : "실패"));
            
            if (iconDrawable == null) {
                // ic_launcher가 없으면 ic_launcher_foreground 사용
                iconDrawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher_foreground);
                System.out.println(TAG + ": ic_launcher_foreground 로드 결과: " + (iconDrawable != null ? "성공" : "실패"));
            }
            
            if (iconDrawable != null) {
                // Drawable을 Bitmap으로 변환
                Bitmap bitmap = Bitmap.createBitmap(
                    iconDrawable.getIntrinsicWidth(),
                    iconDrawable.getIntrinsicHeight(),
                    Bitmap.Config.ARGB_8888
                );
                
                Canvas canvas = new Canvas(bitmap);
                iconDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                iconDrawable.draw(canvas);
                
                System.out.println(TAG + ": 앱 아이콘 비트맵 생성 완료");
                return bitmap;
            }
        } catch (Exception e) {
            System.err.println(TAG + ": 앱 아이콘 비트맵 생성 중 오류: " + e.getMessage());
        }
        
        // 기본값으로 null 반환 (Large Icon 없이 표시됨)
        return null;
    }
} 