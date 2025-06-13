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
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.content.SharedPreferences;
import android.util.Log;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.content.Intent;
import android.net.Uri;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "ProcessGPTMain";
    private static MainActivity instance;
    private static final int NOTIFICATION_PERMISSION_CODE = 123;
    private static final int AUDIO_PERMISSION_CODE = 124;
    private static final int CAMERA_PERMISSION_CODE = 125;
    private static final int MEDIA_PERMISSION_CODE = 126;
    private static final int STORAGE_PERMISSION_CODE = 127;
    private static final int FILE_CHOOSER_RESULT_CODE = 128;
    private static final String CHANNEL_ID = "process_gpt_channel";
    private String pendingToken = null;
    private SharedPreferences sharedPreferences;
    private long backPressedTime = 0; // 이전 백 버튼 누른 시간
    private static final int BACK_PRESS_TIMEOUT = 2000; // 2초
    private PermissionRequest pendingPermissionRequest;
    private ValueCallback<Uri[]> fileUploadCallback;
    private String fcmToken = null; // FCM 토큰 저장
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        
        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("ProcessGPT", MODE_PRIVATE);
        
        // 모든 권한 요청
        requestPermissions();
        
        // Firebase 초기화
        FirebaseApp.initializeApp(this);
        
        // WebView 설정
        WebView webView = getBridge().getWebView();
        webView.clearCache(true);
        WebSettings webSettings = webView.getSettings();
        
        // CORS 및 보안 설정 완화
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        // 미디어 관련 설정 (마이크/카메라 접근 허용)
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        
        // 쿠키 활성화
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        
        // JavaScript 인터페이스 추가
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void changeTenant(String tenantId) {
                runOnUiThread(() -> {
                    webView.loadUrl("https://" + tenantId + ".process-gpt.io/definition-map");
                });
            }
            
            @JavascriptInterface
            public void saveSessionToken(String accessToken, String refreshToken) {
                try {
                    sharedPreferences.edit()
                        .putString("access_token", accessToken)
                        .putString("refresh_token", refreshToken)
                        .apply();
                    System.out.println(TAG + ": 세션 토큰 저장 완료");
                } catch (Exception e) {
                    System.err.println(TAG + ": 세션 토큰 저장 중 오류 발생: " + e.getMessage());
                }
            }
            
            @JavascriptInterface
            public String getSessionToken() {
                try {
                    String accessToken = sharedPreferences.getString("access_token", "");
                    String refreshToken = sharedPreferences.getString("refresh_token", "");
                    return String.format("{\"access_token\":\"%s\",\"refresh_token\":\"%s\"}", accessToken, refreshToken);
                } catch (Exception e) {
                    System.err.println(TAG + ": 세션 토큰 조회 중 오류 발생: " + e.getMessage());
                    return "{}";
                }
            }
            
            @JavascriptInterface
            public void clearSession() {
                sharedPreferences.edit().clear().apply();
                CookieManager.getInstance().removeAllCookies(null);
            }
            
            @JavascriptInterface
            public String getFcmToken() {
                return fcmToken != null ? fcmToken : "";
            }
            
        }, "AndroidBridge");
        
        // WebChromeClient 설정 (마이크 권한 처리)
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // 요청된 권한들 확인
                boolean needsAudio = false;
                boolean needsVideo = false;
                
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                        needsAudio = true;
                    } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                        needsVideo = true;
                    }
                }
                
                // 필요한 안드로이드 권한들 확인
                ArrayList<String> missingPermissions = new ArrayList<>();
                
                if (needsAudio && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(Manifest.permission.RECORD_AUDIO);
                }
                
                if (needsVideo && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(Manifest.permission.CAMERA);
                }
                
                if (missingPermissions.isEmpty()) {
                    // 모든 권한이 있으면 승인
                    request.grant(request.getResources());
                    System.out.println(TAG + ": 미디어 권한 승인됨 (오디오: " + needsAudio + ", 비디오: " + needsVideo + ")");
                } else {
                    // 권한이 없으면 런타임 권한 요청
                    pendingPermissionRequest = request;
                    ActivityCompat.requestPermissions(MainActivity.this,
                            missingPermissions.toArray(new String[0]),
                                                         MEDIA_PERMISSION_CODE);
                }
            }
            
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                    WebChromeClient.FileChooserParams fileChooserParams) {
                // 이전 콜백이 있으면 취소
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                
                fileUploadCallback = filePathCallback;
                
                // 저장소 권한 확인
                if (!hasStoragePermissions()) {
                    // 권한이 없으면 요청
                    requestStoragePermissions();
                    return true;
                }
                
                // 파일 선택 인텐트 생성
                openFileChooser();
                return true;
            }
        });
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                String currentUrl = view.getUrl();
                
                // URL 정규화 (쿼리 파라미터와 프래그먼트 제거하여 비교)
                String normalizedCurrentUrl = normalizeUrl(currentUrl);
                String normalizedNewUrl = normalizeUrl(url);
                
                // 정규화된 URL이 같으면 차단
                if (normalizedCurrentUrl != null && normalizedCurrentUrl.equals(normalizedNewUrl)) {
                    System.out.println(TAG + ": 동일한 URL 로딩 차단");
                    return true; // 로딩 차단
                }
                
                System.out.println(TAG + ": 다른 URL로 로딩 허용");
                return false; // 정상 로딩
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                
                  // FCM 토큰 가져오기
                FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        System.err.println(TAG + ": FCM 토큰 획득 실패");
                        return;
                    }
                    String token = task.getResult();
                    fcmToken = token; // 토큰을 인스턴스 변수에 저장
                    System.out.println(TAG + ": FCM 토큰 저장됨: " + token);
                });
            }
        });
    }
    
    private String normalizeUrl(String url) {
        if (url == null) return null;
        
        try {
            // 쿼리 파라미터와 프래그먼트 제거
            if (url.contains("?")) {
                url = url.substring(0, url.indexOf("?"));
            }
            if (url.contains("#")) {
                url = url.substring(0, url.indexOf("#"));
            }
            
            // 마지막 슬래시 제거 (일관성을 위해)
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            
            return url.toLowerCase(); // 대소문자 통일
        } catch (Exception e) {
            System.err.println(TAG + ": URL 정규화 중 오류: " + e.getMessage());
            return url;
        }
    }
    
    private boolean hasStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 이상
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 12 이하
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void addStoragePermissionsIfNeeded(ArrayList<String> permissionsToRequest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 이상
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else {
            // Android 12 이하
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }
    
    private void requestStoragePermissions() {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        addStoragePermissionsIfNeeded(permissionsToRequest);
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    STORAGE_PERMISSION_CODE);
        }
    }
    
    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "image/*", "video/*", "audio/*", "application/pdf", "text/*"
        });
        
        try {
            startActivityForResult(Intent.createChooser(intent, "파일 선택"), FILE_CHOOSER_RESULT_CODE);
        } catch (Exception e) {
            System.err.println(TAG + ": 파일 선택기 실행 실패: " + e.getMessage());
            if (fileUploadCallback != null) {
                fileUploadCallback.onReceiveValue(null);
                fileUploadCallback = null;
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (fileUploadCallback != null) {
                Uri[] results = null;
                
                if (resultCode == RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
            }
        }
    }
    
    @Override
    public void onBackPressed() {
        WebView webView = getBridge().getWebView();

        try {

            String jsCode = 
                "function closeDialogByDOM() {" +
                "    const dialogs = document.querySelectorAll('.v-dialog.v-overlay--active');" +
                "    " +
                "    if (dialogs.length > 0) {" +
                "        dialogs.forEach(dialog => {" +
                "            dialog.remove();" +
                "        });" +
                "        " +
                "        const overlays = document.querySelectorAll('.v-overlay');" +
                "        overlays.forEach(overlay => overlay.remove());" +
                "        " +
                "        return true;" +
                "    }" +
                "    " +
                "    return false;" +
                "}" +
                "closeDialogByDOM();";

            webView.evaluateJavascript(jsCode, result -> {
                if ("true".equals(result)) {
                    System.out.println(TAG + ": 다이얼로그 닫기 완료");
                    return;
                } else {
                    if (webView.canGoBack()) {
                        System.out.println(TAG + ": WebView 뒤로 가기 실행");
                        webView.goBack();
                    } else {
                        handleAppExit();
                    }
                }
            });
        } catch (Exception e) {
            System.err.println(TAG + ": 백 버튼 처리 중 오류: " + e.getMessage());
            // 오류 발생 시 기본 동작
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                handleAppExit();
            }
        }
    }
    
    private void handleAppExit() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - backPressedTime < BACK_PRESS_TIMEOUT) {
            // 2초 이내에 다시 백 버튼을 누르면 앱 종료
            System.out.println(TAG + ": 앱 종료");
            super.onBackPressed();
        } else {
            // 첫 번째 백 버튼: 토스트 메시지 표시
            backPressedTime = currentTime;
            Toast.makeText(this, "뒤로 버튼을 한 번 더 누르면 앱이 종료됩니다.", Toast.LENGTH_SHORT).show();
            System.out.println(TAG + ": 앱 종료 경고 표시");
        }
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
    
    private void requestPermissions() {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        
        // 마이크 권한 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        }
        
        // 카메라 권한 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }
        
        // 알림 권한 확인 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        
        // 저장소 권한 확인 및 추가
        addStoragePermissionsIfNeeded(permissionsToRequest);
        
        // 권한 요청이 필요한 경우
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    MEDIA_PERMISSION_CODE);
        } else {
            // 모든 권한이 이미 있으면 알림 설정 활성화
            enableNotificationSettings();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == MEDIA_PERMISSION_CODE) {
            // 각 권한별 승인 상태 확인
            boolean hasNotificationPermission = true;
            boolean hasMediaPermissions = true;
            
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                
                if (Manifest.permission.POST_NOTIFICATIONS.equals(permission)) {
                    hasNotificationPermission = granted;
                    System.out.println(TAG + ": 알림 권한 " + (granted ? "승인" : "거부"));
                } else {
                    if (!granted) {
                        hasMediaPermissions = false;
                    }
                }
            }
            
            // 알림 설정 활성화 (알림 권한이 있거나 Android 12 이하)
            if (hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                enableNotificationSettings();
            }
            
            // 미디어 권한 처리
            if (hasMediaPermissions) {
                System.out.println(TAG + ": 미디어 권한이 승인되었습니다.");
                // 대기 중인 웹뷰 권한 요청이 있으면 승인
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                    pendingPermissionRequest = null;
                }
            } else {
                System.out.println(TAG + ": 미디어 권한이 거부되었습니다.");
                // 대기 중인 웹뷰 권한 요청이 있으면 거부
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.deny();
                    pendingPermissionRequest = null;
                }
            }
        } else if (requestCode == STORAGE_PERMISSION_CODE) {
            // 저장소 권한 요청 결과
            boolean hasAnyStoragePermission = false;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13 이상: 하나라도 승인되면 OK
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        hasAnyStoragePermission = true;
                        break;
                    }
                }
            } else {
                // Android 12 이하
                hasAnyStoragePermission = grantResults.length > 0 && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED;
            }
            
            if (hasAnyStoragePermission) {
                System.out.println(TAG + ": 저장소 권한이 승인되었습니다.");
                // 파일 선택기 열기
                openFileChooser();
            } else {
                System.out.println(TAG + ": 저장소 권한이 거부되었습니다.");
                // 파일 업로드 콜백 취소
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                    fileUploadCallback = null;
                }
            }
        }
    }
}

