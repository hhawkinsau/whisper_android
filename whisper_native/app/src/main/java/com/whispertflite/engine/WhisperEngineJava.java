package com.whispertflite.engine;

import android.content.Context;
import android.util.Log;

//import com.google.android.gms.tflite.client.TfLiteInitializationOptions;
//import com.google.android.gms.tflite.gpu.support.TfLiteGpu;
//import com.google.android.gms.tflite.java.TfLite;
import com.whispertflite.utils.WaveUtil;
import com.whispertflite.utils.WhisperUtil;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
//import org.tensorflow.lite.gpu.CompatibilityList;
//import org.tensorflow.lite.gpu.GpuDelegate;
//import org.tensorflow.lite.nnapi.NnApiDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Locale;

public class WhisperEngineJava implements WhisperEngine {
    private static final int LARGE_MODEL_INTERPRETER_THREADS = 2;
    private static final int LARGE_MODEL_MEL_THREADS = 1;
    private static final int DEFAULT_MAX_INTERPRETER_THREADS = 4;
    private static final int DEFAULT_MAX_MEL_THREADS = 2;
    private static final long LARGE_MODEL_BYTES_THRESHOLD = 256L * 1024L * 1024L;
    private final String TAG = "WhisperEngineJava";
    private final WhisperUtil mWhisperUtil = new WhisperUtil();

    private final Context mContext;
    private boolean mIsInitialized = false;
    private Interpreter mInterpreter = null;
    private ProgressListener mProgressListener = null;
    private int mInterpreterThreads = 1;
    private int mMelThreads = 1;
//    private GpuDelegate gpuDelegate;

    public WhisperEngineJava(Context context) {
        mContext = context;
    }

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException {
        notifyProgress("Loading model from storage");
        // Load model
        loadModel(modelPath);
        Log.d(TAG, "Model is loaded..." + modelPath);

        notifyProgress("Loading filters and vocabulary");
        // Load filters and vocab
        boolean ret = mWhisperUtil.loadFiltersAndVocab(multilingual, vocabPath);
        if (ret) {
            mIsInitialized = true;
            Log.d(TAG, "Filters and Vocab are loaded..." + vocabPath);
        } else {
            mIsInitialized = false;
            notifyProgress("Vocabulary file failed validation");
            Log.e(TAG, "Failed to load Filters and Vocab from " + vocabPath);
        }

        return mIsInitialized;
    }

    // Unload the model by closing the interpreter
    @Override
    public void deinitialize() {
        if (mInterpreter != null) {
            mInterpreter.close();
            mInterpreter = null; // Optional: Set to null to avoid accidental reuse
        }
    }

    @Override
    public String transcribeFile(String wavePath) {
        // Calculate Mel spectrogram
        notifyProgress("Preparing mel spectrogram");
        Log.d(TAG, "Calculating Mel spectrogram...");
        float[] melSpectrogram = getMelSpectrogram(WaveUtil.getSamples(wavePath));
        Log.d(TAG, "Mel spectrogram is calculated...!");

        // Perform inference
        notifyProgress("Running Whisper decoder");
        String result = runInference(melSpectrogram);
        Log.d(TAG, "Inference is executed...!");

        return result;
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        return runInference(getMelSpectrogram(samples));
    }

    @Override
    public void setProgressListener(ProgressListener progressListener) {
        mProgressListener = progressListener;
    }

    // Load TFLite model
    private void loadModel(String modelPath) throws IOException {
        ByteBuffer tfliteModel;
        long declaredLength;
        try (FileInputStream fileInputStream = new FileInputStream(modelPath);
             FileChannel fileChannel = fileInputStream.getChannel()) {
            long startOffset = 0;
            declaredLength = fileChannel.size();
            tfliteModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }

        // Set the number of threads for inference
        Interpreter.Options options = new Interpreter.Options();
        boolean useConservativeLargeModelProfile = shouldUseConservativeLargeModelProfile(modelPath, declaredLength);
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        mInterpreterThreads = chooseInterpreterThreads(availableProcessors, useConservativeLargeModelProfile);
        mMelThreads = chooseMelThreads(availableProcessors, useConservativeLargeModelProfile);
        options.setNumThreads(mInterpreterThreads);
        notifyProgress(String.format(
                Locale.US,
                "Using LiteRT profile: %d inference thread%s, %d mel thread%s",
                mInterpreterThreads,
                mInterpreterThreads == 1 ? "" : "s",
                mMelThreads,
                mMelThreads == 1 ? "" : "s"));
        Log.d(TAG, String.format(
                Locale.US,
                "Model profile for %s: size=%d bytes, availableProcessors=%d, interpreterThreads=%d, melThreads=%d, conservative=%s",
                modelPath,
                declaredLength,
                availableProcessors,
                mInterpreterThreads,
                mMelThreads,
                useConservativeLargeModelProfile));
//        options.setUseXNNPACK(true);

//        boolean isNNAPI = true;
//        if (isNNAPI) {
//            // Initialize interpreter with NNAPI delegate for Android Pie or above
//            NnApiDelegate nnapiDelegate = new NnApiDelegate();
//            options.addDelegate(nnapiDelegate);
////                    options.setUseNNAPI(false);
//                    options.setAllowFp16PrecisionForFp32(true);
//                    options.setAllowBufferHandleOutput(true);
//            options.setUseNNAPI(true);
//        }

        // Check if GPU delegate is available asynchronously
//        TfLiteGpu.isGpuDelegateAvailable(mContext).addOnCompleteListener(task -> {
//            if (task.isSuccessful() && task.getResult()) {
//                // GPU is available; initialize the interpreter with GPU delegate
////                    GpuDelegate gpuDelegate = new GpuDelegate();
////                    Interpreter.Options options = new Interpreter.Options().addDelegate(gpuDelegate);
////                    tflite = new Interpreter(loadModelFile(), options);
//                TfLite.initialize(mContext, TfLiteInitializationOptions.builder().setEnableGpuDelegateSupport(true).build());
//                Log.d(TAG, "GPU is available; initialize the interpreter with GPU delegate........................");
//            } else {
//                // GPU is not available; fallback to CPU
////                    tflite = new Interpreter(loadModelFile());
////                    System.out.println("Initialized with CPU.");
//                Log.d(TAG, "GPU is not available; fallback to CPU........................");
//            }
//        });
        
//        boolean isGPU = true;
//        if (isGPU) {
//            gpuDelegate = new GpuDelegate();
//            options.setPrecisionLossAllowed(true); // It seems that the default is true
//            options.setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED);
//             .setPrecisionLossAllowed(true) // Allow FP16 precision for faster performance
//                    .setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_FAST_SINGLE_ANSWER);
//            options.addDelegate(gpuDelegate);
//        }

        mInterpreter = new Interpreter(tfliteModel, options);
    }

    private float[] getMelSpectrogram(float[] samples) {
        int fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE;
        float[] inputSamples = new float[fixedInputSize];
        int copyLength = Math.min(samples.length, fixedInputSize);
        System.arraycopy(samples, 0, inputSamples, 0, copyLength);

        return mWhisperUtil.getMelSpectrogram(inputSamples, inputSamples.length, mMelThreads);
    }

    private String runInference(float[] inputData) {
        if (mInterpreter == null) {
            throw new IllegalStateException("Interpreter is not initialized");
        }

        // Create input tensor
        Tensor inputTensor = mInterpreter.getInputTensor(0);
        if (inputTensor.dataType() != DataType.FLOAT32) {
            throw new IllegalStateException("Unexpected Whisper input type: " + inputTensor.dataType());
        }

        // Create output tensor
        Tensor outputTensor = mInterpreter.getOutputTensor(0);
        if (outputTensor.dataType() != DataType.INT32) {
            throw new IllegalStateException("Unexpected Whisper output type: " + outputTensor.dataType());
        }
        ByteBuffer outputBuf = ByteBuffer.allocateDirect(outputTensor.numBytes());
        outputBuf.order(ByteOrder.nativeOrder());

        // Load input data
        int inputSize = inputTensor.numElements() * Float.BYTES;
        ByteBuffer inputBuf = ByteBuffer.allocateDirect(inputSize);
        inputBuf.order(ByteOrder.nativeOrder());
        for (float input : inputData) {
            inputBuf.putFloat(input);
        }
        inputBuf.rewind();

        // To test mel data as a input directly
//        try {
//            byte[] bytes = Files.readAllBytes(Paths.get("/data/user/0/com.example.tfliteaudio/files/mel_spectrogram.bin"));
//            inputBuf = ByteBuffer.wrap(bytes);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

//        Log.d(TAG, "Before inference...");
        // Run inference
        outputBuf.rewind();
        mInterpreter.run(inputBuf, outputBuf);
//        Log.d(TAG, "After inference...");
        outputBuf.rewind();
        int[] outputTokens = new int[outputTensor.numElements()];
        for (int i = 0; i < outputTokens.length; i++) {
            outputTokens[i] = outputBuf.getInt();
        }
        int outputLen = outputTokens.length;
        Log.d(TAG, "output_len: " + outputLen);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < outputLen; i++) {
            int token = outputTokens[i];
            if (token == mWhisperUtil.getTokenEOT())
                break;

            // Get word for token and Skip additional token
            if (token < mWhisperUtil.getTokenEOT()) {
                String word = mWhisperUtil.getWordFromToken(token);
                //Log.d(TAG, "Adding token: " + token + ", word: " + word);
                result.append(word);
            } else {
                if (token == mWhisperUtil.getTokenTranscribe())
                    Log.d(TAG, "It is Transcription...");

                if (token == mWhisperUtil.getTokenTranslate())
                    Log.d(TAG, "It is Translation...");

                String word = mWhisperUtil.getWordFromToken(token);
                Log.d(TAG, "Skipping token: " + token + ", word: " + word);
            }
        }

        return result.toString();
    }

    private void printTensorDump(String message, Tensor tensor) {
        Log.d(TAG,"Output Tensor Dump ===>");
        Log.d(TAG, "  shape.length: " + tensor.shape().length);
        for (int i = 0; i < tensor.shape().length; i++)
            Log.d(TAG, "    shape[" + i + "]: " + tensor.shape()[i]);
        Log.d(TAG, "  dataType: " + tensor.dataType());
        Log.d(TAG, "  name: " + tensor.name());
        Log.d(TAG, "  numBytes: " + tensor.numBytes());
        Log.d(TAG, "  index: " + tensor.index());
        Log.d(TAG, "  numDimensions: " + tensor.numDimensions());
        Log.d(TAG, "  numElements: " + tensor.numElements());
        Log.d(TAG, "  shapeSignature.length: " + tensor.shapeSignature().length);
        Log.d(TAG, "  quantizationParams.getScale: " + tensor.quantizationParams().getScale());
        Log.d(TAG, "  quantizationParams.getZeroPoint: " + tensor.quantizationParams().getZeroPoint());
        Log.d(TAG, "==================================================================");
    }

    private void notifyProgress(String message) {
        if (mProgressListener != null) {
            mProgressListener.onProgress(message);
        }
    }

    private boolean shouldUseConservativeLargeModelProfile(String modelPath, long declaredLength) {
        return declaredLength >= LARGE_MODEL_BYTES_THRESHOLD
                || modelPath.toLowerCase(Locale.US).contains("large-v3");
    }

    private int chooseInterpreterThreads(int availableProcessors, boolean conservativeLargeModelProfile) {
        if (conservativeLargeModelProfile) {
            return Math.min(LARGE_MODEL_INTERPRETER_THREADS, Math.max(1, availableProcessors));
        }
        return Math.min(DEFAULT_MAX_INTERPRETER_THREADS, Math.max(1, availableProcessors / 2));
    }

    private int chooseMelThreads(int availableProcessors, boolean conservativeLargeModelProfile) {
        if (conservativeLargeModelProfile) {
            return Math.min(LARGE_MODEL_MEL_THREADS, Math.max(1, availableProcessors));
        }
        return Math.min(DEFAULT_MAX_MEL_THREADS, Math.max(1, availableProcessors / 2));
    }
}
