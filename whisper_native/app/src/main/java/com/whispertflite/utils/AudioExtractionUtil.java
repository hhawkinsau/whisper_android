package com.whispertflite.utils;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class AudioExtractionUtil {
    private static final long CODEC_TIMEOUT_US = 10_000L;
    private static final int TARGET_SAMPLE_RATE = 16_000;

    private AudioExtractionUtil() {
    }

    public static void extractToWaveFile(Context context, Uri inputUri, File outputFile) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(context, inputUri, null);
            int audioTrackIndex = selectAudioTrack(extractor);
            if (audioTrackIndex < 0) {
                throw new IOException("No audio track found in selected media");
            }

            extractor.selectTrack(audioTrackIndex);
            MediaFormat inputFormat = extractor.getTrackFormat(audioTrackIndex);
            String mimeType = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mimeType == null) {
                throw new IOException("Audio track is missing a MIME type");
            }

            decoder = MediaCodec.createDecoderByType(mimeType);
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            FloatArrayBuilder sampleBuilder = new FloatArrayBuilder(estimateInitialCapacity(inputFormat));

            int sourceSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int sourceChannelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;

            boolean inputDone = false;
            boolean outputDone = false;
            while (!outputDone) {
                if (!inputDone) {
                    int inputBufferIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer == null) {
                            throw new IOException("Decoder returned a null input buffer");
                        }
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            );
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    extractor.getSampleTime(),
                                    extractor.getSampleFlags()
                            );
                            extractor.advance();
                        }
                    }
                }

                int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US);
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue;
                }
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = decoder.getOutputFormat();
                    sourceSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    sourceChannelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                    continue;
                }
                if (outputBufferIndex < 0) {
                    continue;
                }

                if (bufferInfo.size > 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer == null) {
                        throw new IOException("Decoder returned a null output buffer");
                    }
                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    sampleBuilder.append(decodeOutputChunk(outputBuffer, sourceChannelCount, pcmEncoding));
                }

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true;
                }
                decoder.releaseOutputBuffer(outputBufferIndex, false);
            }

            float[] monoSamples = sampleBuilder.toArray();
            float[] resampled = resampleLinear(monoSamples, sourceSampleRate, TARGET_SAMPLE_RATE);
            byte[] pcm16 = floatsToPcm16(resampled);
            WaveUtil.createWaveFile(outputFile.getAbsolutePath(), pcm16, TARGET_SAMPLE_RATE, 1, 2);
        } finally {
            extractor.release();
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
        }
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++) {
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType != null && mimeType.startsWith("audio/")) {
                return trackIndex;
            }
        }
        return -1;
    }

    private static int estimateInitialCapacity(MediaFormat format) {
        if (!format.containsKey(MediaFormat.KEY_DURATION) || !format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            return TARGET_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE;
        }
        long durationUs = format.getLong(MediaFormat.KEY_DURATION);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        long sampleCount = (durationUs * sampleRate) / 1_000_000L;
        return (int) Math.min(Math.max(sampleCount, TARGET_SAMPLE_RATE), Integer.MAX_VALUE);
    }

    private static float[] decodeOutputChunk(ByteBuffer buffer, int channelCount, int pcmEncoding) throws IOException {
        if (channelCount <= 0) {
            throw new IOException("Invalid audio channel count: " + channelCount);
        }

        ByteBuffer pcmBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);
        switch (pcmEncoding) {
            case AudioFormat.ENCODING_PCM_16BIT:
                return decode16BitChunk(pcmBuffer, channelCount);
            case AudioFormat.ENCODING_PCM_FLOAT:
                return decodeFloatChunk(pcmBuffer, channelCount);
            default:
                throw new IOException("Unsupported PCM encoding: " + pcmEncoding);
        }
    }

    private static float[] decode16BitChunk(ByteBuffer pcmBuffer, int channelCount) {
        int frameCount = pcmBuffer.remaining() / (Short.BYTES * channelCount);
        float[] monoSamples = new float[frameCount];
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            float mixed = 0f;
            for (int channelIndex = 0; channelIndex < channelCount; channelIndex++) {
                mixed += pcmBuffer.getShort() / 32768f;
            }
            monoSamples[frameIndex] = mixed / channelCount;
        }
        return monoSamples;
    }

    private static float[] decodeFloatChunk(ByteBuffer pcmBuffer, int channelCount) {
        int frameCount = pcmBuffer.remaining() / (Float.BYTES * channelCount);
        float[] monoSamples = new float[frameCount];
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            float mixed = 0f;
            for (int channelIndex = 0; channelIndex < channelCount; channelIndex++) {
                mixed += pcmBuffer.getFloat();
            }
            monoSamples[frameIndex] = mixed / channelCount;
        }
        return monoSamples;
    }

    private static float[] resampleLinear(float[] samples, int sourceSampleRate, int targetSampleRate) {
        if (samples.length == 0 || sourceSampleRate <= 0 || sourceSampleRate == targetSampleRate) {
            return samples;
        }

        int targetLength = Math.max(1, (int) Math.round(samples.length * (targetSampleRate / (double) sourceSampleRate)));
        float[] resampled = new float[targetLength];
        double scale = sourceSampleRate / (double) targetSampleRate;
        for (int targetIndex = 0; targetIndex < targetLength; targetIndex++) {
            double sourceIndex = targetIndex * scale;
            int leftIndex = (int) Math.floor(sourceIndex);
            int rightIndex = Math.min(leftIndex + 1, samples.length - 1);
            double fraction = sourceIndex - leftIndex;
            float left = samples[Math.min(leftIndex, samples.length - 1)];
            float right = samples[rightIndex];
            resampled[targetIndex] = (float) (left + ((right - left) * fraction));
        }
        return resampled;
    }

    private static byte[] floatsToPcm16(float[] samples) {
        ByteBuffer pcmBuffer = ByteBuffer.allocate(samples.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float sample : samples) {
            float clamped = Math.max(-1f, Math.min(1f, sample));
            pcmBuffer.putShort((short) Math.round(clamped * 32767f));
        }
        return pcmBuffer.array();
    }

    private static final class FloatArrayBuilder {
        private float[] data;
        private int size;

        private FloatArrayBuilder(int initialCapacity) {
            data = new float[Math.max(1, initialCapacity)];
        }

        private void append(float[] chunk) {
            ensureCapacity(size + chunk.length);
            System.arraycopy(chunk, 0, data, size, chunk.length);
            size += chunk.length;
        }

        private float[] toArray() {
            float[] result = new float[size];
            System.arraycopy(data, 0, result, 0, size);
            return result;
        }

        private void ensureCapacity(int requiredCapacity) {
            if (requiredCapacity <= data.length) {
                return;
            }
            int newCapacity = data.length;
            while (newCapacity < requiredCapacity) {
                newCapacity *= 2;
            }
            float[] expanded = new float[newCapacity];
            System.arraycopy(data, 0, expanded, 0, size);
            data = expanded;
        }
    }
}
