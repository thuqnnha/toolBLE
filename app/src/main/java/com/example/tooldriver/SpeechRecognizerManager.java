package com.example.tooldriver;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.List;

public class SpeechRecognizerManager {
    private final SpeechRecognizer speechRecognizer;
    private final Intent recognizerIntent;
    private final Context context;
    private final OnSpeechResultListener listener;
    private boolean isListening = false;
    private boolean hasResult = false;
    public interface OnSpeechResultListener {
        void onStart();
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
            @Override public void onReadyForSpeech(Bundle params) {
                listener.onStart();
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                isListening = false;
                if (!hasResult) {
                    String message = getErrorText(error);
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        listener.onError("timeout_empty");
                    } else {
                        listener.onError(message);
                    }
                }
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    hasResult = true;
                    listener.onResult(matches.get(0));
                } else {
                    listener.onError("timeout_empty");
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    public void startListening() {
        if (!isListening) {
            hasResult = false;
            isListening = true;
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    public void stopListening() {
        if (isListening) {
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    public void destroy() {
        stopListening();
        speechRecognizer.destroy();
    }

    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: return "Lỗi âm thanh";
            case SpeechRecognizer.ERROR_CLIENT: return "Lỗi client";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Không có quyền";
            case SpeechRecognizer.ERROR_NETWORK: return "Lỗi mạng";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Timeout mạng";
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "timeout_empty";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Bận";
            case SpeechRecognizer.ERROR_SERVER: return "Lỗi server";
            default: return "Lỗi không xác định";
        }
    }
}
