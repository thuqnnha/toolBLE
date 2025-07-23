package com.example.tooldriver;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.util.Log;
import android.view.*;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    ExecutorService settingExecutor = Executors.newSingleThreadExecutor();
    private BleManager bleManager;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "ToolDriverPrefs";
    private static final String KEY_LAST_MESSAGE = "lastMessage";
    private String lastMessageToSend = "Hello"; // mặc định
    @Override
    public void onCreate() {
        super.onCreate();
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

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        //giữ màn hình sáng
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK  | PowerManager.ACQUIRE_CAUSES_WAKEUP, "ToolDriver::BubbleWakeLock");
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        //
        // Đăng ký receiver trong Service để theo dõi khi màn hình tắt
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

        //exit
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
        //

        bubbleView = LayoutInflater.from(this).inflate(R.layout.main_layout, null);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;

        windowManager.addView(bubbleView, params);

        txtSpeed = bubbleView.findViewById(R.id.txt_speed);

        setupDrag(bubbleView, params);
        setupButtons();
        requestLocationUpdates();

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        lastMessageToSend = sharedPreferences.getString(KEY_LAST_MESSAGE, "Hello");
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        bleManager = new BleManager(this);
        bleManager.setConnectionListener(new BleManager.BleConnectionListener() {
            @Override
            public void onConnected() {
                new Handler(Looper.getMainLooper()).post(() ->
                        showCustomToast("Đã kết nối Bluetooth")
                );
            }

            @Override
            public void onDisconnected() {
                new Handler(Looper.getMainLooper()).post(() ->
                        showCustomToast("Bluetooth đã ngắt kết nối")
                );
            }

            @Override
            public void onConnectionFailed() {
                new Handler(Looper.getMainLooper()).post(() ->
                        showCustomToast("Không thể kết nối Bluetooth")
                );
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
    private void fetchSpeedLimitFromOverpass(double lat, double lon) {
        new Thread(() -> {
            try {
                String urlStr = "https://overpass-api.de/api/interpreter?data=[out:json];"
                        + "way(around:100," + lat + "," + lon + ")[\"highway\"][\"maxspeed\"];"
                        + "out;";

                // Log URL
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
                String speedLimit = "NoData";

                if (json.has("elements") && json.getJSONArray("elements").length() > 0) {
                    JSONObject firstElement = json.getJSONArray("elements").getJSONObject(0);
                    if (firstElement.has("tags")) {
                        JSONObject tags = firstElement.getJSONObject("tags");
                        if (tags.has("maxspeed")) {
                            speedLimit = tags.getString("maxspeed") + " km/h";
                        }
                    }
                }

                // Log kết quả
                android.util.Log.d("SpeedLimit", "Speed limit found: " + speedLimit);

                String finalSpeed = (speedLimit != null && !"NoData".equals(speedLimit)) ? speedLimit : "NoData";
                new Handler(Looper.getMainLooper()).post(() -> {
                    txtSpeed.setText(finalSpeed);  // Chỉ hiển thị "50 km/h" hoặc "NoData"
                });

            } catch (Exception e) {
                android.util.Log.e("SpeedLimit", "Lỗi lấy tốc độ giới hạn", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    txtSpeed.setText("NoData");
                });
            }
        }).start();
    }


    private void setupButtons() {
        ImageButton btn_bluetooth = bubbleView.findViewById(R.id.btn_bluetooth);
        ImageButton btn_settings = bubbleView.findViewById(R.id.btn_settings);

        btn_bluetooth.setOnClickListener(v -> {
            bluetoothExecutor.execute(() -> {
                if (bleManager != null && bleManager.isConnected()) {
                    boolean ok = bleManager.sendData(lastMessageToSend);
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast(ok ? "Đã gửi: " + lastMessageToSend : "Lỗi khi gửi")
                    );
                } else {
                    new Handler(Looper.getMainLooper()).post(() ->
                            showCustomToast("Chưa kết nối Bluetooth")
                    );
                }
            });
        });

        btn_settings.setOnClickListener(v -> {
            settingExecutor.execute(() -> {
//                TODO: Xử lí nặng
//                android.util.Log.d("Setting", "Thực hiện hành động Settings");

                new Handler(Looper.getMainLooper()).post(() ->
                        showCustomToast("Settings action")
                );
            });
        });
        btn_bluetooth.setOnLongClickListener(v -> {
            showInputDialog();  // gọi hàm nhập tin nhắn
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

                        isInsideExitZone = distance < 100; // khoảng cách nhỏ thì coi là nằm trong vùng thoát

                        return true;

                    case MotionEvent.ACTION_UP:
                        exitView.setVisibility(View.GONE); // ẩn dấu X sau khi nhả tay
                        if (isInsideExitZone) {
                            stopSelf(); // dừng Service -> thoát bong bóng
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
    private void showInputDialog() {
        new Handler(Looper.getMainLooper()).post(() -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getApplicationContext());
            builder.setTitle("Nhập nội dung gửi");

            final android.widget.EditText input = new android.widget.EditText(getApplicationContext());
            input.setText(lastMessageToSend);
            input.setTextColor(android.graphics.Color.BLACK); // tránh chữ trắng

            builder.setView(input);

            builder.setPositiveButton("OK", (dialog, which) -> {
                lastMessageToSend = input.getText().toString();
                // Lưu vào SharedPreferences
                sharedPreferences.edit().putString(KEY_LAST_MESSAGE, lastMessageToSend).apply();
                showCustomToast("Đã lưu: " + lastMessageToSend);
            });

            builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());

            android.app.AlertDialog dialog = builder.create();
            dialog.getWindow().setType((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
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
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

