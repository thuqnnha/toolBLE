package com.example.tooldriver;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

public class TextSpeaker {
    private TextToSpeech tts;

    public TextSpeaker(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("vi", "VN"));
            }
        });
    }

    public void speak(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speak_id");
    }

    // ✅ Thêm phiên bản có callback khi nói xong
    public void speak(String text, Runnable onDone) {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // Có thể log nếu cần
            }

            @Override
            public void onDone(String utteranceId) {
                // Gọi callback trên UI thread
                new Handler(Looper.getMainLooper()).post(onDone);
            }

            @Override
            public void onError(String utteranceId) {
                // Cũng gọi callback để tránh kẹt
                new Handler(Looper.getMainLooper()).post(onDone);
            }
        });

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utteranceId");
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utteranceId");
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
