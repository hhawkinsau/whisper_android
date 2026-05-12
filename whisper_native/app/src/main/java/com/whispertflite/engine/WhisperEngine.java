package com.whispertflite.engine;

import java.io.IOException;

public interface WhisperEngine {
    interface ProgressListener {
        void onProgress(String message);
    }

    boolean isInitialized();
    boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException;
    void deinitialize();
    String transcribeFile(String wavePath);
    String transcribeBuffer(float[] samples);
    void setProgressListener(ProgressListener progressListener);
}
