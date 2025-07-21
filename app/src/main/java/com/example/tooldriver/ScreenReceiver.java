package com.example.tooldriver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            // Tắt app: ví dụ với Activity
            if (MainActivity.instance != null) {
                MainActivity.instance.finishAffinity(); // thoát toàn bộ
//                System.exit(0); // kết thúc hẳn app
                MainActivity.instance.finishAffinity();
            }
        }
    }
}

