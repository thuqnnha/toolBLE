package com.example.tooldriver;

import android.content.Context;
import android.speech.tts.TextToSpeech;

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

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
