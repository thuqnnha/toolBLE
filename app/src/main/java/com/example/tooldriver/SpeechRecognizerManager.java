package com.example.tooldriver;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.List;

public class SpeechRecognizerManager {
    private final SpeechRecognizer speechRecognizer;
    private final Intent recognizerIntent;
    private boolean isListening = false;
    private boolean autoRestart = true;
    private final Context context;
    private final OnSpeechResultListener listener;

    public interface OnSpeechResultListener {
        void onResult(String text);
        void onError(String message);
    }

    public SpeechRecognizerManager(Context context, OnSpeechResultListener listener) {
        this.context = context;
        this.listener = listener;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d("Speech", "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d("Speech", "Speech started");
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                Log.d("Speech", "Speech ended");
            }

            @Override
            public void onError(int error) {
                isListening = false;
                String message = getErrorText(error);
                listener.onError("Lỗi nhận diện giọng nói: " + message);
                restartListeningWithDelay();
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    listener.onResult(matches.get(0));
                }
                restartListeningWithDelay();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    public void startListening() {
        if (!isListening) {
            isListening = true;
            try {
                speechRecognizer.startListening(recognizerIntent);
            } catch (Exception e) {
                isListening = false;
                listener.onError("Không thể bắt đầu lắng nghe: " + e.getMessage());
            }
        }
    }

    public void stopListening() {
        if (isListening) {
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    private void restartListeningWithDelay() {
        if (!autoRestart) return;
        new Handler(Looper.getMainLooper()).postDelayed(this::startListening, 1500);
    }

    public void setAutoRestart(boolean enabled) {
        this.autoRestart = enabled;
    }

    public void destroy() {
        stopListening();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }

    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Lỗi âm thanh";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client lỗi";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Không có quyền";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Lỗi mạng";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Timeout mạng";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "Không nhận được kết quả";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Bận";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server lỗi";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "Không nghe thấy bạn nói";
            default:
                return "Không rõ lỗi";
        }
    }
}
