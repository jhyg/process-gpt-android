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
import android.content.SharedPreferences;
import java.util.Set;
import java.util.HashSet;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FCMService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "process_gpt_channel";
    private static final String TAG = "ProcessGPTFCM";
    private static final String PREFS_NAME = "FCMMessageHashes";
    private static final String PROCESSED_MESSAGES_KEY = "processed_message_hashes";
    private static final long MESSAGE_DUPLICATE_TIME_WINDOW = 30 * 1000; // 30초
    
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // 메시지 데이터 확인
        Map<String, String> data = remoteMessage.getData();
        
        // 알림 제목과 내용 설정
        String title = remoteMessage.getNotification() != null ? 
                remoteMessage.getNotification().getTitle() : data.get("title");
        String body = remoteMessage.getNotification() != null ? 
                remoteMessage.getNotification().getBody() : data.get("body");
        
        // 메시지 내용 해시 생성 및 중복 확인
        String messageHash = generateMessageHash(title, body, data);
        if (isDuplicateMessage(messageHash)) {
            System.out.println(TAG + ": 중복 메시지 감지, 무시합니다. Hash: " + messageHash);
            System.out.println(TAG + ": 내용 - " + title + ": " + body);
            return;
        }
        
        // 메시지 ID 정보
        String messageId = remoteMessage.getMessageId();
        
        // 디버그 로그
        System.out.println(TAG + ": 푸시 알림 수신 - " + title + ": " + body);
        System.out.println(TAG + ": MessageId: " + messageId + ", Hash: " + messageHash);
        
        // 메시지 해시 저장 (중복 방지용)
        saveProcessedMessageHash(messageHash);
        
        // 앱이 포그라운드 상태일 때도 알림을 표시
        sendNotification(title, body, data);
        
        // 앱이 포그라운드 상태일 때 Bridge에 알림 데이터 전달
        try {
            Bridge bridge = MainActivity.getCapBridge();
            if (bridge != null) {
                JSObject notificationJson = new JSObject();
                notificationJson.put("title", title);
                notificationJson.put("body", body);
                notificationJson.put("messageId", messageId);
                notificationJson.put("messageHash", messageHash);
                
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
                        System.out.println(TAG + ": 포그라운드 이벤트 전달 성공 (Hash: " + messageHash + ")");
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
    
    /**
     * 메시지 내용으로 해시값 생성
     */
    private String generateMessageHash(String title, String body, Map<String, String> data) {
        StringBuilder contentBuilder = new StringBuilder();
        
        // 기본 정보 추가
        contentBuilder.append(title != null ? title : "");
        contentBuilder.append("|");
        contentBuilder.append(body != null ? body : "");
        contentBuilder.append("|");
        
        // 중요한 데이터 필드들 추가 (보낸사람, 채팅방 등)
        String[] importantKeys = {"sender", "senderId", "senderName", "roomId", "roomName", "chatId", "from", "type"};
        for (String key : importantKeys) {
            if (data.containsKey(key)) {
                contentBuilder.append(key).append(":").append(data.get(key)).append("|");
            }
        }
        
        String content = contentBuilder.toString();
        
        try {
            // SHA-256 해시 생성
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes("UTF-8"));
            
            // 바이트 배열을 16진수 문자열로 변환
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16); // 앞 16자리만 사용
        } catch (Exception e) {
            System.err.println(TAG + ": 해시 생성 오류: " + e.getMessage());
            // 해시 생성 실패 시 문자열 해시코드 사용
            return String.valueOf(Math.abs(content.hashCode()));
        }
    }
    
    /**
     * 메시지 해시가 이미 처리되었는지 확인 (시간 기반)
     */
    private boolean isDuplicateMessage(String messageHash) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> processedHashes = prefs.getStringSet(PROCESSED_MESSAGES_KEY, new HashSet<>());
        
        // 현재 시간과 함께 저장된 해시 형식: "messageHash:timestamp"
        long currentTime = System.currentTimeMillis();
        
        // 기존 해시들 중에서 시간 윈도우 내의 것들만 확인
        Set<String> validHashes = new HashSet<>();
        for (String entry : processedHashes) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                try {
                    long timestamp = Long.parseLong(parts[1]);
                    if (currentTime - timestamp < MESSAGE_DUPLICATE_TIME_WINDOW) {
                        validHashes.add(entry);
                        // 해시가 일치하는지 확인
                        if (parts[0].equals(messageHash)) {
                            return true; // 중복 메시지
                        }
                    }
                } catch (NumberFormatException e) {
                    // 잘못된 형식은 무시
                }
            }
        }
        
        // 시간 윈도우를 벗어난 해시들 정리
        if (validHashes.size() != processedHashes.size()) {
            prefs.edit().putStringSet(PROCESSED_MESSAGES_KEY, validHashes).apply();
        }
        
        return false; // 새로운 메시지
    }
    
    /**
     * 처리된 메시지 해시 저장
     */
    private void saveProcessedMessageHash(String messageHash) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> processedHashes = new HashSet<>(prefs.getStringSet(PROCESSED_MESSAGES_KEY, new HashSet<>()));
        
        // 현재 시간과 함께 저장: "messageHash:timestamp"
        String entry = messageHash + ":" + System.currentTimeMillis();
        processedHashes.add(entry);
        
        prefs.edit().putStringSet(PROCESSED_MESSAGES_KEY, processedHashes).apply();
        System.out.println(TAG + ": 메시지 해시 저장 완료: " + messageHash);
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