package com.whispertflite;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

import com.google.android.gms.tasks.Tasks;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.whispertflite.asr.Player;
import com.whispertflite.asr.Recorder;
import com.whispertflite.asr.Whisper;
import com.whispertflite.utils.AudioExtractionUtil;
import com.whispertflite.utils.WaveUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final String HUGGING_FACE_MODEL_BASE_URL =
            "https://huggingface.co/cik009/whisper/resolve/main/";
    private static final String HUGGING_FACE_DOWNLOAD_QUERY = "?download=true";
    private static final String DEFAULT_MODEL_TO_USE = "whisper-tiny.tflite";
    private static final String LARGE_V3_MULTILINGUAL_VOCAB_FILE = "filters_vocab_multilingual-v3.bin";
    private static final int DOWNLOAD_BUFFER_SIZE = 1024 * 1024;
    // English only model ends with extension ".en.tflite"
    private static final String ENGLISH_ONLY_MODEL_EXTENSION = ".en.tflite";
    private static final String ENGLISH_ONLY_VOCAB_FILE = "filters_vocab_en.bin";
    private static final String MULTILINGUAL_VOCAB_FILE = "filters_vocab_multilingual.bin";
    private static final String[] EXTENSIONS_TO_COPY = {"bin", "wav", "pcm"};
    private static final String EXTRACTED_VIDEO_WAV = "selected_video_audio.wav";
    private static final String[] SUPPORTED_VIDEO_MIME_TYPES = {
            "video/*",
            "video/mp4",
            "video/webm",
            "video/3gpp",
            "video/mpeg",
            "video/x-msvideo",
            "video/quicktime",
            "video/x-matroska",
            "video/mp2t",
            "video/x-flv",
            "application/ogg"
    };
    private static final String[] SUPPORTED_VIDEO_EXTENSIONS = {
            ".mp4", ".webm", ".3gp", ".mpg", ".mpeg", ".avi", ".mov", ".mkv", ".ogv", ".ts", ".flv"
    };

    private TextView tvStatus;
    private TextView tvResult;
    private TextView tvLog;
    private FloatingActionButton fabCopy;
    private Button btnRecord;
    private Button btnPlay;
    private Button btnTranscribe;
    private Button btnPickVideo;
    private Button btnDownloadModel;
    private Spinner spinnerTflite;
    private Spinner spinnerWave;
    private Spinner spinnerOutputLanguage;
    private ScrollView scrollLog;

    private Player mPlayer = null;
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;

    private File sdcardDataFolder = null;
    private File selectedWaveFile = null;
    private File selectedTfliteFile = null;
    private final ArrayList<File> waveFiles = new ArrayList<>();
    private final ArrayList<ModelOption> modelOptions = new ArrayList<>(Arrays.asList(
            new ModelOption("Tiny (multilingual, recommended)", "whisper-tiny.tflite", MULTILINGUAL_VOCAB_FILE),
            new ModelOption("Tiny English", "whisper-tiny.en.tflite", ENGLISH_ONLY_VOCAB_FILE),
            new ModelOption("Base (multilingual)", "whisper-base.tflite", MULTILINGUAL_VOCAB_FILE),
            new ModelOption("Base English", "whisper-base.en.tflite", ENGLISH_ONLY_VOCAB_FILE),
            new ModelOption("Small (multilingual)", "whisper-small.tflite", MULTILINGUAL_VOCAB_FILE),
            new ModelOption("Small English", "whisper-small.en.tflite", ENGLISH_ONLY_VOCAB_FILE),
            new ModelOption("Medium (multilingual)", "whisper-medium.tflite", MULTILINGUAL_VOCAB_FILE),
            new ModelOption("Medium English", "whisper-medium.en.tflite", ENGLISH_ONLY_VOCAB_FILE),
            new ModelOption("Large v3 Turbo", "whisper-large-v3-turbo.tflite", LARGE_V3_MULTILINGUAL_VOCAB_FILE),
            new ModelOption("Large v3", "whisper-large-v3.tflite", LARGE_V3_MULTILINGUAL_VOCAB_FILE)
    ));
    private final ArrayList<SubtitleOutputOption> subtitleOutputOptions = new ArrayList<>(Arrays.asList(
            new SubtitleOutputOption("English", "en"),
            new SubtitleOutputOption("Arabic", "ar"),
            new SubtitleOutputOption("Spanish", "es"),
            new SubtitleOutputOption("French", "fr"),
            new SubtitleOutputOption("Hindi", "hi"),
            new SubtitleOutputOption("Portuguese", "pt"),
            new SubtitleOutputOption("Russian", "ru"),
            new SubtitleOutputOption("Ukrainian", "uk"),
            new SubtitleOutputOption("Mandarin Chinese", "zh-CN")
    ));
    private boolean isModelDownloadInProgress = false;
    private ModelOption selectedModelOption = null;
    private SubtitleOutputOption selectedSubtitleOutputOption = subtitleOutputOptions.get(0);
    private String latestTranscriptText = "";

    private long startTime = 0;
    private final boolean loopTesting = false;
    private final SharedResource transcriptionSync = new SharedResource();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<Intent> videoPickerLauncher;
    private ActivityResultLauncher<Intent> subtitleFolderLauncher;
    private VideoSubtitleJob pendingVideoSubtitleJob;
    private String pendingSubtitleText;
    private String pendingSubtitleFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Call the method to copy specific file types from assets to data folder
        File externalDataFolder = this.getExternalFilesDir(null);
        sdcardDataFolder = externalDataFolder != null ? externalDataFolder : getFilesDir();
        copyAssetsToSdcard(this, sdcardDataFolder, EXTENSIONS_TO_COPY);

        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);
        tvLog = findViewById(R.id.tvLog);
        btnRecord = findViewById(R.id.btnRecord);
        btnPlay = findViewById(R.id.btnPlay);
        btnTranscribe = findViewById(R.id.btnTranscb);
        btnPickVideo = findViewById(R.id.btnPickVideo);
        btnDownloadModel = findViewById(R.id.btnDownloadModel);
        fabCopy = findViewById(R.id.fabCopy);
        spinnerTflite = findViewById(R.id.spnrTfliteFiles);
        spinnerWave = findViewById(R.id.spnrWaveFiles);
        spinnerOutputLanguage = findViewById(R.id.spnrOutputLanguage);
        scrollLog = findViewById(R.id.scrollLog);

        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri videoUri = result.getData().getData();
                        if (videoUri != null) {
                            persistVideoUriPermission(result.getData(), videoUri);
                            openVideoForSubtitle(videoUri);
                        }
                    }
                });
        subtitleFolderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            persistTreeUriPermission(result.getData(), treeUri);
                            writePendingSubtitleToTree(treeUri);
                        }
                    } else {
                        clearPendingSubtitleWrite();
                        resetVideoSubtitleJob();
                        tvStatus.setText(R.string.folder_permission_cancelled);
                    }
                });

        refreshModelFiles();
        refreshWaveFiles();
        spinnerOutputLanguage.setAdapter(getSubtitleOutputAdapter());
        spinnerOutputLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SubtitleOutputOption option = (SubtitleOutputOption) parent.getItemAtPosition(position);
                if (option != null) {
                    selectedSubtitleOutputOption = option;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedSubtitleOutputOption = subtitleOutputOptions.get(0);
            }
        });

        spinnerTflite.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ModelOption candidateModel = (ModelOption) parent.getItemAtPosition(position);
                if (candidateModel == null) {
                    selectedModelOption = null;
                    selectedTfliteFile = null;
                    updateModelAvailabilityUi();
                    return;
                }
                deinitModel();
                selectedModelOption = candidateModel;
                selectedTfliteFile = candidateModel.getModelFile(sdcardDataFolder);
                updateModelAvailabilityUi();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedModelOption = null;
                selectedTfliteFile = null;
                updateModelAvailabilityUi();
            }
        });

        spinnerWave.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Cast item to File and get the file name
                selectedWaveFile = (File) parent.getItemAtPosition(position);

                // Check if the selected file is the recording file
                if (selectedWaveFile.getName().equals(WaveUtil.RECORDING_FILE)) {
                    btnRecord.setVisibility(View.VISIBLE);
                } else {
                    btnRecord.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Handle case when nothing is selected, if needed
            }
        });
        btnDownloadModel.setOnClickListener(v -> downloadSelectedModelArtifacts());

        // Implementation of record button functionality
        btnRecord.setOnClickListener(v -> {
            if (mRecorder != null && mRecorder.isInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            } else {
                Log.d(TAG, "Start recording...");
                startRecording();
            }
        });

        // Implementation of Play button functionality
        btnPlay.setOnClickListener(v -> {
            if (selectedWaveFile == null) return;
            if(!mPlayer.isPlaying()) {
                mPlayer.initializePlayer(selectedWaveFile.getAbsolutePath());
                mPlayer.startPlayback();
            } else {
                mPlayer.stopPlayback();
            }
        });

        // Implementation of transcribe button functionality
        btnTranscribe.setOnClickListener(v -> {
            if (selectedWaveFile == null) return;
            if (!ensureModelReady()) return;
            clearProgressLog();
            appendProgressLog("Starting manual transcript for " + selectedWaveFile.getName());
            if (mRecorder != null && mRecorder.isInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            }

            if (mWhisper == null && !initModel(selectedTfliteFile)) {
                return;
            }

            if (!mWhisper.isInProgress()) {
                Log.d(TAG, "Start transcription...");
                pendingVideoSubtitleJob = null;
                latestTranscriptText = "";
                startTranscription(selectedWaveFile.getAbsolutePath());

                // only for loop testing
                if (loopTesting) {
                    new Thread(() -> {
                        for (int i = 0; i < 1000; i++) {
                            if (!mWhisper.isInProgress())
                                startTranscription(selectedWaveFile.getAbsolutePath());
                            else
                                Log.d(TAG, "Whisper is already in progress...!");

                            boolean wasNotified = transcriptionSync.waitForSignalWithTimeout(15000);
                            Log.d(TAG, wasNotified ? "Transcription Notified...!" : "Transcription Timeout...!");
                        }
                    }).start();
                }
            } else {
                Log.d(TAG, "Whisper is already in progress...!");
                stopTranscription();
            }
        });

        btnPickVideo.setOnClickListener(v -> openVideoPicker());

        fabCopy.setOnClickListener(v -> {
            // Get the text from tvResult
            String textToCopy = tvResult.getText().toString();

            // Copy the text to the clipboard
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied Text", textToCopy);
            clipboard.setPrimaryClip(clip);
        });

        // Audio recording functionality
        mRecorder = new Recorder(this);
        mRecorder.setListener(new Recorder.RecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "Update is received, Message: " + message);
                handler.post(() -> tvStatus.setText(message));

                if (message.equals(Recorder.MSG_RECORDING)) {
                    handler.post(() -> tvResult.setText(""));
                    handler.post(() -> btnRecord.setText(R.string.stop));
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    handler.post(() -> btnRecord.setText(R.string.record));
                }
            }

            @Override
            public void onDataReceived(float[] samples) {
//                mWhisper.writeBuffer(samples);
            }
        });

        // Audio playback functionality
        mPlayer = new Player(this);
        mPlayer.setListener(new Player.PlaybackListener() {
            @Override
            public void onPlaybackStarted() {
                handler.post(() -> btnPlay.setText(R.string.stop));
            }

            @Override
            public void onPlaybackStopped() {
                handler.post(() -> btnPlay.setText(R.string.play));
            }
        });

        // Assume this Activity is the current activity, check record permission
        checkRecordPermission();

        handleIncomingVideoIntent(getIntent());
        updateModelAvailabilityUi();
        appendProgressLog("App ready");

        // for debugging
//        testParallelProcessing();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingVideoIntent(intent);
    }

    private void handleIncomingVideoIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri videoUri = null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            videoUri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            videoUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);
        }
        if (videoUri != null) {
            persistVideoUriPermission(intent, videoUri);
            openVideoForSubtitle(videoUri);
        }
    }

    // Model initialization
    private boolean initModel(File modelFile) {
        if (modelFile == null || !modelFile.exists()) {
            handler.post(() -> tvStatus.setText(selectedModelOption == null
                    ? getString(R.string.no_model_selected)
                    : getString(R.string.download_model_first, selectedModelOption.displayName)));
            return false;
        }
        boolean isMultilingualModel = !(modelFile.getName().endsWith(ENGLISH_ONLY_MODEL_EXTENSION));
        File vocabFile = getRequiredVocabFile(modelFile);
        if (!vocabFile.exists()) {
            handler.post(() -> tvStatus.setText(selectedModelOption == null
                    ? getString(R.string.no_model_selected)
                    : getString(R.string.download_model_first, selectedModelOption.displayName)));
            return false;
        }

        mWhisper = new Whisper(this);
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "Update is received, Message: " + message);
                appendProgressLog(message);

                if (message.equals(Whisper.MSG_PROCESSING)) {
                    handler.post(() -> tvStatus.setText(message));
                    handler.post(() -> tvResult.setText(""));
                    VideoSubtitleJob job = pendingVideoSubtitleJob;
                    if (job != null) {
                        job.transcript.setLength(0);
                    }
                    startTime = System.currentTimeMillis();
                }
                if (message.equals(Whisper.MSG_PROCESSING_DONE)) {
                    VideoSubtitleJob job = pendingVideoSubtitleJob;
                    if (job != null) {
                        finishVideoSubtitleJob(job);
                    } else {
                        finishManualTranscriptJob();
                    }
                    // for testing
                    if (loopTesting)
                        transcriptionSync.sendSignal();
                } else if (message.equals(Whisper.MSG_FILE_NOT_FOUND)) {
                    handler.post(() -> tvStatus.setText(message));
                    Log.d(TAG, "File not found error...!");
                    resetVideoSubtitleJob();
                }
            }

            @Override
            public void onResultReceived(String result) {
                long timeTaken = System.currentTimeMillis() - startTime;
                handler.post(() -> tvStatus.setText(getString(R.string.processing_done_in, timeTaken)));

                Log.d(TAG, "Result: " + result);
                latestTranscriptText = (latestTranscriptText == null ? "" : latestTranscriptText)
                        + (result == null ? "" : result);
                appendProgressLog("Whisper output received");
                VideoSubtitleJob job = pendingVideoSubtitleJob;
                if (job != null) {
                    job.transcript.append(result);
                }
                handler.post(() -> tvResult.append(result));
            }
        });
        appendProgressLog("Loading Whisper model " + modelFile.getName());
        boolean initialized = mWhisper.loadModel(modelFile, vocabFile, isMultilingualModel);
        if (!initialized) {
            handler.post(() -> tvStatus.setText(R.string.whisper_model_initialization_failed));
            deinitModel();
            return false;
        }
        return true;
    }

    private void deinitModel() {
        if (mWhisper != null) {
            mWhisper.unloadModel();
            mWhisper = null;
        }
    }

    private @NonNull ArrayAdapter<File> getFileArrayAdapter(ArrayList<File> waveFiles) {
        ArrayAdapter<File> adapter = new ArrayAdapter<File>(this, android.R.layout.simple_spinner_item, waveFiles) {
            @Override
            public @NonNull View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                File item = getItem(position);
                if (item != null) {
                    textView.setText(item.getName());  // Show only the file name
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                File item = getItem(position);
                if (item != null) {
                    textView.setText(item.getName());  // Show only the file name
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private ModelOption chooseDefaultModelOption() {
        for (ModelOption modelOption : modelOptions) {
            if (DEFAULT_MODEL_TO_USE.equalsIgnoreCase(modelOption.fileName)) {
                return modelOption;
            }
        }
        return modelOptions.isEmpty() ? null : modelOptions.get(0);
    }

    private void openVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_VIDEO_MIME_TYPES);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        videoPickerLauncher.launch(intent);
    }

    private void openVideoForSubtitle(Uri videoUri) {
        if (!isSupportedVideoUri(videoUri)) {
            tvStatus.setText(getString(R.string.unsupported_video_type, getDisplayName(videoUri)));
            appendProgressLog("Rejected unsupported video: " + getDisplayName(videoUri));
            return;
        }
        clearProgressLog();
        appendProgressLog("Selected video " + getDisplayName(videoUri));
        appendProgressLog("Requested output language " + selectedSubtitleOutputOption.languageCode);
        startVideoSubtitlePipeline(videoUri);
    }


    private void persistVideoUriPermission(Intent intent, Uri videoUri) {
        int takeFlags = intent.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (takeFlags == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(videoUri, takeFlags);
        } catch (SecurityException e) {
            Log.w(TAG, "Could not persist selected video URI permission", e);
        }
    }

    private void persistTreeUriPermission(Intent intent, Uri treeUri) {
        int takeFlags = intent.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (takeFlags == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
        } catch (SecurityException e) {
            Log.w(TAG, "Could not persist subtitle folder URI permission", e);
        }
    }

    private void startVideoSubtitlePipeline(Uri videoUri) {
        if (!ensureModelReady()) {
            return;
        }
        if (mWhisper != null && mWhisper.isInProgress()) {
            handler.post(() -> tvStatus.setText(R.string.whisper_already_in_progress));
            return;
        }

        btnPickVideo.setEnabled(false);
        tvResult.setText("");
        tvStatus.setText(R.string.preparing_selected_video);
        appendProgressLog("Preparing selected video");

        new Thread(() -> {
            try {
                File wavFile = new File(sdcardDataFolder, EXTRACTED_VIDEO_WAV);
                extractAudioToWav(videoUri, wavFile);
                appendProgressLog("Audio extracted to " + wavFile.getName());

                VideoSubtitleJob job = new VideoSubtitleJob();
                job.videoUri = videoUri;
                job.videoDisplayName = getDisplayName(videoUri);
                job.subtitleLanguageCode = selectedSubtitleOutputOption.languageCode;
                job.durationMs = getDurationMs(videoUri);
                job.wavFile = wavFile;
                pendingVideoSubtitleJob = job;

                handler.post(() -> tvStatus.setText(R.string.audio_extracted_starting_whisper));
                appendProgressLog("Starting Whisper transcription");
                if (mWhisper == null && !initModel(selectedTfliteFile)) {
                    resetVideoSubtitleJob();
                    return;
                }
                startTranscription(wavFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Failed to create subtitles for selected video", e);
                handler.post(() -> tvStatus.setText(getString(R.string.video_subtitle_failed, e.getMessage())));
                resetVideoSubtitleJob();
            }
        }).start();
    }

    private File copyUriToCache(Uri uri) throws IOException {
        String displayName = sanitizeFileName(getDisplayName(uri));
        File output = new File(getCacheDir(), displayName.isEmpty() ? "selected_video" : displayName);
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(output)) {
            if (inputStream == null) {
                throw new IOException("Cannot open selected video");
            }
            byte[] buffer = new byte[1024 * 64];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return output;
    }

    private void extractAudioToWav(Uri videoUri, File wavFile) throws IOException {
        handler.post(() -> tvStatus.setText("Extracting 16 kHz mono WAV with Android media APIs..."));
        appendProgressLog("Extracting audio with Android media APIs");
        AudioExtractionUtil.extractToWaveFile(this, videoUri, wavFile);
    }

    private void finishVideoSubtitleJob(VideoSubtitleJob job) {
        new Thread(() -> {
            String subtitleFileName = getSrtFileName(job.videoDisplayName, job.subtitleLanguageCode);
            String finalTranscript = translateTranscriptIfNeeded(job.transcript.toString(), job.subtitleLanguageCode);
            handler.post(() -> tvResult.setText(finalTranscript));
            String srtText = buildSrt(finalTranscript, job.durationMs);
            try {
                appendProgressLog("Saving subtitle beside source as " + subtitleFileName);
                Uri srtUri = createSiblingSubtitleUri(job.videoUri, subtitleFileName);
                writeTextToUri(srtUri, srtText);
                handler.post(() -> tvStatus.setText(getString(R.string.srt_saved_beside_video, subtitleFileName)));
                appendProgressLog("Saved subtitle beside source video");
                resetVideoSubtitleJob();
            } catch (Exception e) {
                Log.w(TAG, "Direct sibling SRT save failed; requesting folder permission", e);
                pendingSubtitleText = srtText;
                pendingSubtitleFileName = subtitleFileName;
                appendProgressLog("Direct save failed, requesting folder permission");
                handler.post(() -> requestSubtitleFolderPermission(subtitleFileName));
            }
        }).start();
    }

    private void finishManualTranscriptJob() {
        new Thread(() -> {
            String translatedText = translateTranscriptIfNeeded(latestTranscriptText, selectedSubtitleOutputOption.languageCode);
            handler.post(() -> tvResult.setText(translatedText));
        }).start();
    }

    private Uri createSiblingSubtitleUri(Uri videoUri, String subtitleName) throws IOException {
        ContentResolver resolver = getContentResolver();
        if (DocumentsContract.isDocumentUri(this, videoUri)) {
            String docId = DocumentsContract.getDocumentId(videoUri);
            int slashIndex = docId.lastIndexOf('/');
            if (slashIndex > 0) {
                String parentDocId = docId.substring(0, slashIndex);
                Uri treeUri = DocumentsContract.buildTreeDocumentUri(videoUri.getAuthority(), parentDocId);
                Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId);
                Uri subtitleUri = DocumentsContract.createDocument(resolver, parentUri, "application/x-subrip", subtitleName);
                if (subtitleUri != null) {
                    return subtitleUri;
                }
            }
        }
        throw new IOException("Cannot create subtitle next to this provider's video without folder access");
    }

    private void requestSubtitleFolderPermission(String subtitleFileName) {
        tvStatus.setText(getString(R.string.choose_video_folder, subtitleFileName));
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        subtitleFolderLauncher.launch(intent);
    }

    private void writePendingSubtitleToTree(Uri treeUri) {
        String subtitleText = pendingSubtitleText;
        String subtitleFileName = pendingSubtitleFileName;
        if (subtitleText == null || subtitleFileName == null) {
            return;
        }

        new Thread(() -> {
            try {
                String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
                Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId);
                Uri subtitleUri = DocumentsContract.createDocument(
                        getContentResolver(), parentUri, "application/x-subrip", subtitleFileName);
                if (subtitleUri == null) {
                    throw new IOException("Cannot create subtitle in selected folder");
                }
                writeTextToUri(subtitleUri, subtitleText);
                handler.post(() -> tvStatus.setText(getString(R.string.srt_saved_to_folder, subtitleFileName)));
                appendProgressLog("Saved subtitle to selected folder");
            } catch (Exception e) {
                Log.e(TAG, "Failed to write subtitle to selected folder", e);
                handler.post(() -> tvStatus.setText(getString(R.string.srt_save_failed, e.getMessage())));
                appendProgressLog("Subtitle save failed: " + e.getMessage());
            } finally {
                clearPendingSubtitleWrite();
                resetVideoSubtitleJob();
            }
        }).start();
    }

    private void writeTextToUri(Uri uri, String text) throws IOException {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri, "wt")) {
            if (outputStream == null) {
                throw new IOException("Cannot open subtitle destination");
            }
            outputStream.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void clearPendingSubtitleWrite() {
        pendingSubtitleText = null;
        pendingSubtitleFileName = null;
    }

    private String buildSrt(String transcript, long durationMs) {
        long safeDurationMs = Math.max(durationMs, 1000L);
        return "1\n" + formatSrtTimestamp(0) + " --> " + formatSrtTimestamp(safeDurationMs) + "\n"
                + transcript.trim() + "\n";
    }

    private String formatSrtTimestamp(long timeMs) {
        long hours = timeMs / 3_600_000L;
        long minutes = (timeMs % 3_600_000L) / 60_000L;
        long seconds = (timeMs % 60_000L) / 1_000L;
        long millis = timeMs % 1_000L;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }

    private long getDurationMs(Uri uri) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(this, uri);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                return Long.parseLong(duration);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read video duration", e);
        }
        return 1000L;
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String displayName = cursor.getString(nameIndex);
                    if (displayName != null) {
                        return displayName;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read display name", e);
        }
        String lastSegment = uri.getLastPathSegment();
        return lastSegment == null ? "video" : lastSegment;
    }

    private String getSrtFileName(String videoDisplayName, String languageCode) {
        int dotIndex = videoDisplayName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? videoDisplayName.substring(0, dotIndex) : videoDisplayName;
        return baseName + "." + languageCode + ".srt";
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String quotePath(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }

    private String translateTranscriptIfNeeded(String transcript, String targetLanguageCode) {
        if (TextUtils.isEmpty(transcript) || TextUtils.isEmpty(targetLanguageCode)) {
            return transcript;
        }

        try {
            appendProgressLog("Identifying transcript language");
            LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
            String sourceLanguageCode;
            try {
                sourceLanguageCode = Tasks.await(languageIdentifier.identifyLanguage(transcript));
            } finally {
                languageIdentifier.close();
            }

            if (TextUtils.isEmpty(sourceLanguageCode) || "und".equals(sourceLanguageCode)) {
                appendProgressLog("Language identification was inconclusive; keeping transcript");
                return transcript;
            }

            appendProgressLog("Detected transcript language " + sourceLanguageCode);
            if (targetLanguageCode.equalsIgnoreCase(sourceLanguageCode)) {
                appendProgressLog("Target language already matches transcript");
                return transcript;
            }

            String sourceTranslateLanguage = TranslateLanguage.fromLanguageTag(sourceLanguageCode);
            String targetTranslateLanguage = TranslateLanguage.fromLanguageTag(targetLanguageCode);
            if (sourceTranslateLanguage == null || targetTranslateLanguage == null) {
                appendProgressLog("Translation language mapping unavailable; keeping transcript");
                return transcript;
            }

            handler.post(() -> tvStatus.setText(getString(R.string.translating_subtitles, targetLanguageCode)));
            appendProgressLog("Downloading translation model for " + targetLanguageCode);
            TranslatorOptions options = new TranslatorOptions.Builder()
                    .setSourceLanguage(sourceTranslateLanguage)
                    .setTargetLanguage(targetTranslateLanguage)
                    .build();
            Translator translator = Translation.getClient(options);
            try {
                Tasks.await(translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()));
                appendProgressLog("Translation model ready; translating transcript");
                String translatedText = Tasks.await(translator.translate(transcript));
                appendProgressLog("Translation complete");
                return translatedText;
            } finally {
                translator.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to translate transcript", e);
            handler.post(() -> tvStatus.setText(getString(R.string.translation_failed, e.getMessage())));
            appendProgressLog("Translation failed; using transcript");
            return transcript;
        }
    }

    private void clearProgressLog() {
        handler.post(() -> tvLog.setText(""));
    }

    private void appendProgressLog(String message) {
        handler.post(() -> {
            String timestamp = String.format(Locale.US, "%1$tH:%1$tM:%1$tS", System.currentTimeMillis());
            String existing = tvLog.getText().toString();
            String nextLine = "[" + timestamp + "] " + message;
            tvLog.setText(existing.isEmpty() ? nextLine : existing + "\n" + nextLine);
            scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        });
    }

    private boolean isSupportedVideoUri(Uri videoUri) {
        String mimeType = getContentResolver().getType(videoUri);
        if (mimeType != null) {
            for (String supportedMimeType : SUPPORTED_VIDEO_MIME_TYPES) {
                if ("video/*".equals(supportedMimeType) && mimeType.startsWith("video/")) {
                    return true;
                }
                if (supportedMimeType.equalsIgnoreCase(mimeType)) {
                    return true;
                }
            }
        }

        String displayName = getDisplayName(videoUri).toLowerCase(Locale.US);
        for (String extension : SUPPORTED_VIDEO_EXTENSIONS) {
            if (displayName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private void refreshModelFiles() {
        spinnerTflite.setAdapter(getModelOptionAdapter());
        if (selectedModelOption == null) {
            selectedModelOption = chooseDefaultModelOption();
        }
        selectedTfliteFile = selectedModelOption == null ? null : selectedModelOption.getModelFile(sdcardDataFolder);
        int selectedIndex = selectedModelOption == null ? -1 : modelOptions.indexOf(selectedModelOption);
        if (selectedIndex >= 0) {
            spinnerTflite.setSelection(selectedIndex);
        }
    }

    private void refreshWaveFiles() {
        waveFiles.clear();
        waveFiles.addAll(getFilesWithExtension(sdcardDataFolder, ".wav"));
        spinnerWave.setAdapter(getFileArrayAdapter(waveFiles));
    }

    private @NonNull ArrayAdapter<SubtitleOutputOption> getSubtitleOutputAdapter() {
        ArrayAdapter<SubtitleOutputOption> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, subtitleOutputOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private @NonNull ArrayAdapter<ModelOption> getModelOptionAdapter() {
        ArrayAdapter<ModelOption> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modelOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void updateModelAvailabilityUi() {
        boolean modelReady = hasRequiredArtifacts(selectedTfliteFile);
        btnDownloadModel.setEnabled(selectedModelOption != null && !isModelDownloadInProgress && !modelReady);
        if (selectedModelOption != null) {
            btnDownloadModel.setText(getString(R.string.download_selected_model, selectedModelOption.displayName));
        }
        btnTranscribe.setEnabled(modelReady && !isModelDownloadInProgress);
        btnPickVideo.setEnabled(modelReady && !isModelDownloadInProgress && pendingVideoSubtitleJob == null);
        if (modelReady && selectedTfliteFile != null) {
            tvStatus.setText(getString(R.string.model_ready, selectedTfliteFile.getName()));
        } else if (isModelDownloadInProgress) {
            tvStatus.setText(R.string.model_download_in_progress);
        } else {
            if (selectedModelOption != null) {
                tvStatus.setText(getString(R.string.download_model_first, selectedModelOption.displayName));
            } else {
                tvStatus.setText(R.string.no_model_selected);
            }
        }
    }

    private boolean ensureModelReady() {
        if (isModelDownloadInProgress) {
            tvStatus.setText(R.string.model_download_in_progress);
            return false;
        }
        if (!hasRequiredArtifacts(selectedTfliteFile)) {
            if (selectedModelOption != null) {
                tvStatus.setText(getString(R.string.download_model_first, selectedModelOption.displayName));
            } else {
                tvStatus.setText(R.string.no_model_selected);
            }
            return false;
        }
        return true;
    }

    private boolean hasRequiredArtifacts(File modelFile) {
        if (modelFile == null || !modelFile.exists()) {
            return false;
        }
        return getRequiredVocabFile(modelFile).exists();
    }

    private File getRequiredVocabFile(File modelFile) {
        String modelName = modelFile.getName().toLowerCase(Locale.US);
        boolean isMultilingualModel = !modelName.endsWith(ENGLISH_ONLY_MODEL_EXTENSION);
        if (!isMultilingualModel) {
            return new File(sdcardDataFolder, ENGLISH_ONLY_VOCAB_FILE);
        }
        if (modelName.contains("large-v3") || modelName.contains("large_v3")) {
            return new File(sdcardDataFolder, LARGE_V3_MULTILINGUAL_VOCAB_FILE);
        }
        return new File(sdcardDataFolder, MULTILINGUAL_VOCAB_FILE);
    }

    private void downloadSelectedModelArtifacts() {
        if (isModelDownloadInProgress) {
            tvStatus.setText(R.string.model_download_in_progress);
            return;
        }
        if (selectedModelOption == null) {
            tvStatus.setText(R.string.no_model_selected);
            return;
        }

        isModelDownloadInProgress = true;
        deinitModel();
        clearProgressLog();
        appendProgressLog("Preparing model download for " + selectedModelOption.fileName);
        updateModelAvailabilityUi();

        new Thread(() -> {
            ModelOption modelOption = selectedModelOption;
            File modelFile = modelOption.getModelFile(sdcardDataFolder);
            File vocabFile = modelOption.getVocabFile(sdcardDataFolder);
            String failureMessage = null;
            try {
                if (!modelFile.exists()) {
                    appendProgressLog("Downloading model " + modelFile.getName());
                    handler.post(() -> tvStatus.setText(getString(R.string.downloading_model_file, modelFile.getName())));
                    downloadFile(modelOption.modelUrl, modelFile);
                }
                if (!vocabFile.exists()) {
                    appendProgressLog("Downloading vocab " + vocabFile.getName());
                    handler.post(() -> tvStatus.setText(getString(R.string.downloading_model_file, vocabFile.getName())));
                    downloadFile(modelOption.vocabUrl, vocabFile);
                }
                handler.post(() -> {
                    appendProgressLog("Model artifacts ready");
                    refreshModelFiles();
                    updateModelAvailabilityUi();
                });
            } catch (IOException e) {
                Log.e(TAG, "Failed to download default model artifacts", e);
                failureMessage = e.getMessage();
            } finally {
                isModelDownloadInProgress = false;
                String finalFailureMessage = failureMessage;
                handler.post(() -> {
                    updateModelAvailabilityUi();
                    if (finalFailureMessage != null) {
                        tvStatus.setText(getString(R.string.model_download_failed, finalFailureMessage));
                    }
                });
            }
        }).start();
    }

    private void downloadFile(String sourceUrl, File outputFile) throws IOException {
        File parentFolder = outputFile.getParentFile();
        if (parentFolder != null && !parentFolder.exists() && !parentFolder.mkdirs()) {
            throw new IOException("Cannot create " + parentFolder.getAbsolutePath());
        }

        File tempFile = new File(outputFile.getAbsolutePath() + ".part");
        if (tempFile.exists() && !tempFile.delete()) {
            throw new IOException("Cannot replace " + tempFile.getName());
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", "WhisperASR/1.0");

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode + " for " + outputFile.getName());
            }

            try (InputStream inputStream = connection.getInputStream();
                 OutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            if (!tempFile.renameTo(outputFile)) {
                throw new IOException("Cannot finalize " + outputFile.getName());
            }
        } finally {
            connection.disconnect();
            if (tempFile.exists() && !outputFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    private void resetVideoSubtitleJob() {
        pendingVideoSubtitleJob = null;
        handler.post(this::updateModelAvailabilityUi);
    }

    private void checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted");
        } else {
            Log.d(TAG, "Requesting record permission");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted");
        } else {
            Log.d(TAG, "Record permission is not granted");
        }
    }

    // Recording calls
    private void startRecording() {
        checkRecordPermission();

        File waveFile= new File(sdcardDataFolder, WaveUtil.RECORDING_FILE);
        mRecorder.setFilePath(waveFile.getAbsolutePath());
        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
    }

    // Transcription calls
    private void startTranscription(String waveFilePath) {
        mWhisper.setFilePath(waveFilePath);
        mWhisper.setAction(Whisper.ACTION_TRANSCRIBE);
        mWhisper.start();
    }

    private void stopTranscription() {
        mWhisper.stop();
    }

    // Copy assets with specified extensions to destination folder
    private static void copyAssetsToSdcard(Context context, File destFolder, String[] extensions) {
        AssetManager assetManager = context.getAssets();

        try {
            // List all files in the assets folder once
            String[] assetFiles = assetManager.list("");
            if (assetFiles == null) return;

            for (String assetFileName : assetFiles) {
                // Check if file matches any of the provided extensions
                for (String extension : extensions) {
                    if (assetFileName.endsWith("." + extension)) {
                        File outFile = new File(destFolder, assetFileName);

                        // Skip if file already exists
                        if (outFile.exists()) break;

                        // Copy the file from assets to the destination folder
                        try (InputStream inputStream = assetManager.open(assetFileName);
                             OutputStream outputStream = new FileOutputStream(outFile)) {

                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        }
                        break; // No need to check further extensions
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<File> getFilesWithExtension(File directory, String extension) {
        ArrayList<File> filteredFiles = new ArrayList<>();

        // Check if the directory is accessible
        if (directory != null && directory.exists()) {
            File[] files = directory.listFiles();

            // Filter files by the provided extension
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(extension)) {
                        filteredFiles.add(file);
                    }
                }
            }
        }

        return filteredFiles;
    }

    static class VideoSubtitleJob {
        Uri videoUri;
        String videoDisplayName;
        String subtitleLanguageCode;
        long durationMs;
        File wavFile;
        final StringBuilder transcript = new StringBuilder();
    }

    static class SubtitleOutputOption {
        final String displayName;
        final String languageCode;

        SubtitleOutputOption(String displayName, String languageCode) {
            this.displayName = displayName;
            this.languageCode = languageCode;
        }

        @NonNull
        @Override
        public String toString() {
            return displayName + " (" + languageCode + ")";
        }
    }

    static class ModelOption {
        final String displayName;
        final String fileName;
        final String modelUrl;
        final String vocabFileName;
        final String vocabUrl;

        ModelOption(String displayName, String fileName, String vocabFileName) {
            this.displayName = displayName;
            this.fileName = fileName;
            this.modelUrl = buildHuggingFaceResolveUrl(fileName);
            this.vocabFileName = vocabFileName;
            this.vocabUrl = buildHuggingFaceResolveUrl(vocabFileName);
        }

        File getModelFile(File baseDirectory) {
            return new File(baseDirectory, fileName);
        }

        File getVocabFile(File baseDirectory) {
            return new File(baseDirectory, vocabFileName);
        }

        @NonNull
        @Override
        public String toString() {
            return displayName;
        }
    }

    static class SharedResource {
        // Synchronized method for Thread 1 to wait for a signal with a timeout
        public synchronized boolean waitForSignalWithTimeout(long timeoutMillis) {
            long startTime = System.currentTimeMillis();

            try {
                wait(timeoutMillis);  // Wait for the given timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // Restore interrupt status
                return false;  // Thread interruption as timeout
            }

            long elapsedTime = System.currentTimeMillis() - startTime;

            // Check if wait returned due to notify or timeout
            if (elapsedTime < timeoutMillis) {
                return true;  // Returned due to notify
            } else {
                return false;  // Returned due to timeout
            }
        }

        // Synchronized method for Thread 2 to send a signal
        public synchronized void sendSignal() {
            notify();  // Notifies the waiting thread
        }
    }

    private static String buildHuggingFaceResolveUrl(String fileName) {
        return HUGGING_FACE_MODEL_BASE_URL + fileName + HUGGING_FACE_DOWNLOAD_QUERY;
    }
}
