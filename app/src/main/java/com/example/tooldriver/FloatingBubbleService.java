package com.example.tooldriver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class FloatingBubbleService extends Service {
    private WindowManager windowManager;
    private View bubbleView;
    private TextView txtSpeed;
    private View exitView;
    private WindowManager.LayoutParams exitParams;
    private boolean isInsideExitZone = false;
    private PowerManager.WakeLock wakeLock;
    private BroadcastReceiver screenReceiver;
    private View toastView;
    ExecutorService bluetoothExecutor = Executors.newSingleThreadExecutor();
    ExecutorService btn2Executor = Executors.newSingleThreadExecutor();
    ExecutorService btn3Executor = Executors.newSingleThreadExecutor();
    ExecutorService btn4Executor = Executors.newSingleThreadExecutor();
    ExecutorService btn5Executor = Executors.newSingleThreadExecutor();
    ExecutorService btnVoiceExecutor = Executors.newSingleThreadExecutor();
    private BleManager bleManager;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "ToolDriverPrefs";
    private static final String KEY_MSG1 = "msg1";
    private static final String KEY_MSG2 = "msg2";
    private static final String KEY_MSG3 = "msg3";
    private static final String KEY_MSG4 = "msg4";
    private static final String KEY_MSG5 = "msg5";
    private String msg1 = "default1";
    private String msg2 = "default2";
    private String msg3 = "default3";
    private String msg4 = "default4";
    private String msg5 = "default5";
    private static final String KEY_LABEL1 = "label1";
    private static final String KEY_LABEL2 = "label2";
    private static final String KEY_LABEL3 = "label3";
    private static final String KEY_LABEL4 = "label4";
    private static final String KEY_LABEL5 = "label5";

    private String label1 = "Mặc định 1";
    private String label2 = "Mặc định 2";
    private String label3 = "Mặc định 3";
    private String label4 = "Mặc định 4";
    private String label5 = "Mặc định 5";
    private SpeechRecognizerManager speechRecognizerManager;
    private TextSpeaker textSpeaker;
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private final Handler tapTimeoutHandler = new Handler(Looper.getMainLooper());
    private boolean isLongPress = false;
    private Button btn_bluetooth;
    private Button btn_2;
    private Button btn_3;
    private Button btn_4;
    private Button btn_5;
    private boolean wasInsideExitZone = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationForegroundService();
        acquireWakeLock();

        registerScreenReceiver();

        setupExitView();

        setupBubbleView();

        requestLocationUpdates();
        loadPreferences();

        //init tts
        textSpeaker = new TextSpeaker(getApplicationContext());
    }
    private void createNotificationForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("tool_driver_channel",
                    "ToolDriver", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, "tool_driver_channel")
                    .setContentTitle("ToolDriver đang chạy")
                    .setSmallIcon(R.drawable.ic_bluetooth)
                    .build();

            startForeground(1, notification);
        }
    }
    private void acquireWakeLock() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        //giữ màn hình sáng
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK  | PowerManager.ACQUIRE_CAUSES_WAKEUP, "ToolDriver::BubbleWakeLock");
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }
    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    stopSelf(); // Dừng service khi màn hình tắt
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }
    private void setupExitView() {
        exitView = LayoutInflater.from(this).inflate(R.layout.exit_layout, null);

        exitParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        exitParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        exitParams.y = 100;

        windowManager.addView(exitView, exitParams);
        exitView.setVisibility(View.GONE); // ban đầu ẩn
    }
    private void setupBubbleView() {
        bubbleView = LayoutInflater.from(this).inflate(R.layout.main_layout, null);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;

        windowManager.addView(bubbleView, params);

        txtSpeed = bubbleView.findViewById(R.id.txt_speed);

        setupDrag(bubbleView, params);
        setupButtons();
    }
    private void loadPreferences() {
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        msg1 = sharedPreferences.getString(KEY_MSG1, "Hello");
        msg2 = sharedPreferences.getString(KEY_MSG2, "Button 2");
        msg3 = sharedPreferences.getString(KEY_MSG3, "Button 3");
        msg4 = sharedPreferences.getString(KEY_MSG4, "Button 4");
        msg5 = sharedPreferences.getString(KEY_MSG5, "Button 5");

        label1 = sharedPreferences.getString(KEY_LABEL1, "Mặc định 1");
        label2 = sharedPreferences.getString(KEY_LABEL2, "Mặc định 2");
        label3 = sharedPreferences.getString(KEY_LABEL3, "Mặc định 3");
        label4 = sharedPreferences.getString(KEY_LABEL4, "Mặc định 4");
        label5 = sharedPreferences.getString(KEY_LABEL5, "Mặc định 5");

        btn_bluetooth.setText(label1);
        btn_2.setText(label2);
        btn_3.setText(label3);
        btn_4.setText(label4);
        btn_5.setText(label5);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        bleManager = new BleManager(this);
        bleManager.setConnectionListener(new BleManager.BleConnectionListener() {
            @Override
            public void onConnected() {
                new Handler(Looper.getMainLooper()).post(() -> {
                    showCustomToast("Đã kết nối Bluetooth");

                    speechRecognizerManager = new SpeechRecognizerManager(FloatingBubbleService.this, new SpeechRecognizerManager.OnSpeechResultListener() {
                        @Override
                        public void onStart() {
                            Log.d("Speech", "Bắt đầu nghe...");
                        }

                        @Override
                        public void onResult(String text) {
                            Log.d("Speech", "Kết quả: " + text);
                            if (bleManager != null && bleManager.isConnected()) {
                                boolean ok = bleManager.sendData(text);
                                new Handler(Looper.getMainLooper()).post(() ->
                                        showCustomToast("Đã gửi: " + text)
                                );
                                if (!ok) {
                                    new Handler(Looper.getMainLooper()).post(() ->
                                            showCustomToast("Lỗi khi gửi")
                                    );
                                }
                            } else {
                                new Handler(Looper.getMainLooper()).post(() ->
                                        showCustomToast("Chưa kết nối Bluetooth")
                                );
                            }
                        }

                        @Override
                        public void onError(String message) {
                            Log.e("Speech", "Lỗi: " + message);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if ("timeout_empty".equals(message)) {
                                    showCustomToast("Tôi không nghe được gì hihi");
                                } else {
                                    showCustomToast("Lỗi: " + message);
                                }
                            });
                        }
                    });

                });
            }

            @Override
            public void onDisconnected() {
                new Handler(Looper.getMainLooper()).post(() -> showCustomToast("Bluetooth đã ngắt kết nối"));
            }

            @Override
            public void onConnectionFailed() {
                new Handler(Looper.getMainLooper()).post(() -> showCustomToast("Không thể kết nối Bluetooth"));
            }
        });

        bleManager.startScan();
        return START_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                double lat = location.getLatitude();
                double lon = location.getLongitude();
                fetchSpeedLimitFromOverpass(lat, lon);
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
    }

    private String lastKnownSpeedLimit = "1 km/h";

    private void fetchSpeedLimitFromOverpass(double lat, double lon) {
        new Thread(() -> {
            try {
                String urlStr = "https://overpass-api.de/api/interpreter?data=[out:json];"
                        + "way(around:200," + lat + "," + lon + ")[\"highway\"][\"maxspeed\"];"
                        + "out;";
                android.util.Log.d("SpeedLimit", "Overpass URL: " + urlStr);

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "ToolDriverApp");

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    result.append(line);
                }
                in.close();

                JSONObject json = new JSONObject(result.toString());
                android.util.Log.d("SpeedLimit", "Overpass raw result: " + json);

                String speedLimit = "1 km/h";

                if (json.has("elements") && json.getJSONArray("elements").length() > 0) {
                    JSONObject firstElement = json.getJSONArray("elements").getJSONObject(0);
                    if (firstElement.has("tags")) {
                        JSONObject tags = firstElement.getJSONObject("tags");
                        if (tags.has("maxspeed")) {
                            speedLimit = tags.getString("maxspeed") + " km/h";
                            lastKnownSpeedLimit = speedLimit;
                            android.util.Log.d("SpeedLimit", "Maxspeed found: " + speedLimit);
                        } else {
                            android.util.Log.d("SpeedLimit", "Không có tag maxspeed trong tags");
                        }
                    } else {
                        android.util.Log.d("SpeedLimit", "Không có tags trong element");
                    }
                } else {
                    android.util.Log.d("SpeedLimit", "Không có phần tử nào trong JSON elements");
                }

                if ("1 km/h".equals(speedLimit)) {
                    speedLimit = lastKnownSpeedLimit;
                    android.util.Log.d("SpeedLimit", "Dùng lại maxspeed cũ: " + speedLimit);
                }

                String finalSpeed = speedLimit;
                new Handler(Looper.getMainLooper()).post(() -> {
                    txtSpeed.setText(finalSpeed);
                });

            } catch (Exception e) {
                android.util.Log.e("SpeedLimit", "Lỗi lấy tốc độ giới hạn", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    txtSpeed.setText(lastKnownSpeedLimit);
                });
            }
        }).start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupButtons() {
        btn_bluetooth = bubbleView.findViewById(R.id.btn_bluetooth);
        btn_2 = bubbleView.findViewById(R.id.btn_2);
        btn_3 = bubbleView.findViewById(R.id.btn_3);
        btn_4 = bubbleView.findViewById(R.id.btn_4);
        btn_5 = bubbleView.findViewById(R.id.btn_5);
        ImageButton btn_voice = bubbleView.findViewById(R.id.btn_voice);

        //click
        btn_bluetooth.setOnClickListener(v -> {
            bluetoothExecutor.execute(() -> {
                if (bleManager != null && bleManager.isConnected()) {
                    boolean ok = bleManager.sendData(msg1);
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast(ok ? "Đã gửi: " + msg1 : "Lỗi khi gửi")
                    );
                } else {
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast("Chưa kết nối Bluetooth")
                    );
                }
            });
        });

        btn_2.setOnClickListener(v -> {
            btn2Executor.execute(() -> {
                if (bleManager != null && bleManager.isConnected()) {
                    boolean ok = bleManager.sendData(msg2);
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast(ok ? "Đã gửi: " + msg2 : "Lỗi khi gửi")
                    );
                } else {
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast("Chưa kết nối Bluetooth")
                    );
                }
            });
        });

        btn_3.setOnClickListener(v -> {
            btn3Executor.execute(() -> {
                if (bleManager != null && bleManager.isConnected()) {
                    boolean ok = bleManager.sendData(msg3);
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast(ok ? "Đã gửi: " + msg3 : "Lỗi khi gửi")
                    );
                } else {
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast("Chưa kết nối Bluetooth")
                    );
                }
            });
        });

        btn_4.setOnClickListener(v -> {
            btn4Executor.execute(() -> {
                if (bleManager != null && bleManager.isConnected()) {
                    boolean ok = bleManager.sendData(msg4);
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast(ok ? "Đã gửi: " + msg4 : "Lỗi khi gửi")
                    );
                } else {
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast("Chưa kết nối Bluetooth")
                    );
                }
            });
        });

        btn_5.setOnClickListener(v -> {
            btn5Executor.execute(() -> {
                if (bleManager != null && bleManager.isConnected()) {
                    boolean ok = bleManager.sendData(msg5);
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast(ok ? "Đã gửi: " + msg5 : "Lỗi khi gửi")
                    );
                } else {
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast("Chưa kết nối Bluetooth")
                    );
                }
            });
        });

        //touchclick
        btn_voice.setOnTouchListener((v, event) -> {
            if (bleManager == null || !bleManager.isConnected() || speechRecognizerManager == null){
                new Handler(Looper.getMainLooper()).post(() ->
                        showCustomToast("Chưa kết nối Bluetooth")
                );
                return false;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    isLongPress = false;

                    // Nếu giữ > 500ms → xem như long press
                    longPressHandler.postDelayed(() -> {
                        isLongPress = true;
                        speechRecognizerManager.startListening();
                        showCustomToast("Đang nghe (giữ)...");
                    }, 500);

                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    longPressHandler.removeCallbacksAndMessages(null);

                    if (isLongPress) {
                        //long press
                        speechRecognizerManager.stopListening();
                    } else {
                        speechRecognizerManager.startListening();
                        showCustomToast("Đang nghe (nhấn)...");
                        tapTimeoutHandler.postDelayed(() -> {
                            speechRecognizerManager.stopListening();
                        }, 4000);
                    }
                    return true;
            }
            return false;
        });

        //longclick
        btn_bluetooth.setOnLongClickListener(v -> {
            showInputDialog(KEY_LABEL1, label1, KEY_MSG1, msg1, btn_bluetooth);
            return true;
        });
        btn_2.setOnLongClickListener(v -> {
            showInputDialog(KEY_LABEL2, label2, KEY_MSG2, msg2, btn_2);
            return true;
        });

        btn_3.setOnLongClickListener(v -> {
            showInputDialog(KEY_LABEL3, label3, KEY_MSG3, msg3, btn_3);
            return true;
        });

        btn_4.setOnLongClickListener(v -> {
            showInputDialog(KEY_LABEL4, label4, KEY_MSG4, msg4, btn_4);
            return true;
        });

        btn_5.setOnLongClickListener(v -> {
            showInputDialog(KEY_LABEL5, label5, KEY_MSG5, msg5, btn_5);
            return true;
        });

    }

    private void setupDrag(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        exitView.setVisibility(View.VISIBLE); // hiện nút X
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(bubbleView, params);

                        // kiểm tra xem bong bóng có nằm trên vùng X không
                        int[] bubbleLocation = new int[2];
                        bubbleView.getLocationOnScreen(bubbleLocation);

                        int[] exitLocation = new int[2];
                        exitView.getLocationOnScreen(exitLocation);

                        int bubbleCenterX = bubbleLocation[0] + bubbleView.getWidth() / 2;
                        int bubbleCenterY = bubbleLocation[1] + bubbleView.getHeight() / 2;

                        int exitCenterX = exitLocation[0] + exitView.getWidth() / 2;
                        int exitCenterY = exitLocation[1] + exitView.getHeight() / 2;

                        int distance = (int) Math.sqrt(
                                Math.pow(bubbleCenterX - exitCenterX, 2)
                                        + Math.pow(bubbleCenterY - exitCenterY, 2));

                        isInsideExitZone = distance < 100;

                        //animation
                        FrameLayout exitRoot = exitView.findViewById(R.id.exit_root);

                        if (isInsideExitZone && !wasInsideExitZone) {
                            exitRoot.setBackgroundResource(R.drawable.exit_background_highlight);
                            wasInsideExitZone = true;
                        } else if (!isInsideExitZone && wasInsideExitZone) {
                            exitRoot.setBackgroundResource(R.drawable.exit_background);
                            wasInsideExitZone = false;
                        }

                        return true;

                    case MotionEvent.ACTION_UP:
                        exitView.setVisibility(View.GONE);
                        if (isInsideExitZone) {
                            stopSelf(); // Dừng Service

                            // Tắt toàn bộ app
                            android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(0);
                        }
                        return true;
                }
                return false;
            }
        });
    }
    private void showNotification(String title, String message) {
        String channelId = "tool_driver_channel";
        String channelName = "ToolDriver Channel";

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        builder.setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_bluetooth) // đổi thành icon của bạn
                .setAutoCancel(true);

        // ID khác nhau để không bị ghi đè
        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
    }
    private void showCustomToast(String message) {
        if (toastView != null) return; // tránh hiển thị nhiều lần

        LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
        toastView = inflater.inflate(R.layout.custom_toast_layout, null);
        TextView text = toastView.findViewById(R.id.toast_text);
        text.setText(message);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = 100;

        windowManager.addView(toastView, params);

        // Tự ẩn sau 2 giây
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (toastView != null) {
                windowManager.removeView(toastView);
                toastView = null;
            }
        }, 2000);
    }
    private void showInputDialog(String keyLabel, String currentLabel,
                                 String keyMsg, String currentMsg,
                                 Button targetButton) {
        new Handler(Looper.getMainLooper()).post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(FloatingBubbleService.this.getBaseContext());
            builder.setTitle("Nhập nội dung hiển thị và nội dung gửi");

            LinearLayout layout = new LinearLayout(FloatingBubbleService.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 40, 50, 10);

            EditText inputLabel = new EditText(FloatingBubbleService.this);
            inputLabel.setHint("Text hiển thị trên nút");
            inputLabel.setText(currentLabel);
            layout.addView(inputLabel);

            EditText inputMsg = new EditText(FloatingBubbleService.this);
            inputMsg.setHint("Nội dung gửi đi (msg)");
            inputMsg.setText(currentMsg);
            layout.addView(inputMsg);

            builder.setView(layout);

            builder.setPositiveButton("OK", (dialog, which) -> {
                String newLabel = inputLabel.getText().toString();
                String newMsg = inputMsg.getText().toString();

                sharedPreferences.edit()
                        .putString(keyLabel, newLabel)
                        .putString(keyMsg, newMsg)
                        .apply();

                showCustomToast("Đã lưu");

                targetButton.setText(newLabel);

                switch (keyMsg) {
                    case KEY_MSG1: msg1 = newMsg; label1 = newLabel; break;
                    case KEY_MSG2: msg2 = newMsg; label2 = newLabel; break;
                    case KEY_MSG3: msg3 = newMsg; label3 = newLabel; break;
                    case KEY_MSG4: msg4 = newMsg; label4 = newLabel; break;
                    case KEY_MSG5: msg5 = newMsg; label5 = newLabel; break;
                }
            });

            builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());

            AlertDialog dialog = builder.create();

            if (!(dialog.getWindow() == null)) {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            }

            dialog.show();
        });
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bubbleView != null) windowManager.removeView(bubbleView);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
        }
        if (bleManager != null) bleManager.disconnect();
        if (speechRecognizerManager != null) {
            speechRecognizerManager.destroy();
            speechRecognizerManager = null;
        }
        if (textSpeaker != null) {
            textSpeaker.shutdown();
            textSpeaker = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
