# Third-Party Notices

This file inventories third-party code, models, and sample data that are
currently checked into this repository as of 2026-05-13.

This is a practical attribution audit, not legal advice.

## Project License

- The repository's top-level license is in `LICENSE.txt`.
- That file currently declares the repository's own code as MIT-licensed:
  `Copyright (c) 2023 Vilas Ninawe`.

## Confirmed Third-Party Components

### Android dependencies used by the app

The following direct Gradle dependencies are present in
`whisper_native/app/build.gradle` and `whisper_native/gradle/libs.versions.toml`.
Their local Maven metadata in the Gradle cache declares Apache License 2.0.

- `androidx.appcompat:appcompat:1.7.1`  
  License: Apache License 2.0
- `androidx.constraintlayout:constraintlayout:2.2.1`  
  License: Apache License 2.0
- `com.google.android.material:material:1.13.0`  
  License: Apache License 2.0
- `com.google.ai.edge.litert:litert:2.1.4`  
  License: Apache License 2.0
- `com.google.ai.edge.litert:litert-support:1.4.2`  
  License: Apache License 2.0

### Vendored legacy native ML code

The repository contains a vendored legacy TensorFlow Lite / native support tree
under:

- `whisper_native/app/src/main/cpp/tf-lite-api/tensorflow_src/`
- `whisper_native/app/src/main/cpp/tf-lite-api/generated-libs/`
- `whisper_native/app/src/main/cpp/tf-lite-api/include/flatbuffers/`
- `whisper_native/app/src/main/cpp/tf-lite-api/include/abseil-cpp/`

Confirmed license status from checked-in license files and source headers:

- TensorFlow Lite / TensorFlow Lite native source tree  
  License: Apache License 2.0
- FlatBuffers  
  License: Apache License 2.0  
  Local license file:
  `whisper_native/app/src/main/cpp/tf-lite-api/include/flatbuffers/LICENSE.txt`
- Abseil C++  
  License: Apache License 2.0  
  Local license file:
  `whisper_native/app/src/main/cpp/tf-lite-api/include/abseil-cpp/LICENSE`

### Whisper TFLite model artifacts checked into the repo

The following local files appear to come from the Hugging Face repository
`cik009/whisper`, which declares `License: apache-2.0` on its model page:

- `whisper_native/app/src/main/assets/filters_vocab_en.bin`
- `whisper_native/app/src/main/assets/filters_vocab_multilingual.bin`
- `models_and_scripts/legacy_models/whisper-tiny.tflite`
- `models_and_scripts/legacy_models/whisper-tiny.en.tflite`

Source repository:

- `https://huggingface.co/cik009/whisper/tree/main`

Important note:

- The upstream OpenAI Whisper project itself is MIT-licensed:
  `https://github.com/openai/whisper/blob/main/LICENSE`
- The files checked into this repository are not copied directly from the
  OpenAI GitHub repo; they appear to be downloaded from the `cik009/whisper`
  Hugging Face repository, so the most direct confirmed license attached to the
  checked-in artifacts is that repository's declared `apache-2.0`.

### Common Voice sample data

The following sample set appears to be derived from Mozilla Common Voice 15.0:

- `models_and_scripts/common_voice_15_top10_samples/audio/`
- `models_and_scripts/common_voice_15_top10_samples/video/`
- `models_and_scripts/common_voice_15_top10_samples.tsv`

The manifest references Common Voice clip names such as
`common_voice_ar_28865270.mp3`, `common_voice_en_27710027.mp3`, etc.

Confirmed license status from upstream dataset listings and Mozilla release
materials:

- Mozilla Common Voice dataset  
  License: CC0-1.0 / public domain dedication

Useful upstream references:

- `https://huggingface.co/datasets/mozilla-foundation/common_voice_15_0/tree/main`
- `https://www.mozillafoundation.org/en/blog/common-voice-18-dataset-release/`

## Files With Unclear or Unverified Provenance

I could not confirm the original source license for the following checked-in
media files from local repository evidence alone:

- `demo_and_apk/demo_video.mp4`
- `whisper_native/app/src/main/assets/jfk.wav`
- `whisper_native/app/src/main/assets/english_test1.wav`
- `whisper_native/app/src/main/assets/english_test2.wav`
- `whisper_native/app/src/main/assets/english_test_3_bili.wav`
- `whisper_native/app/src/main/assets/MicInput.wav`
- `whisper_native/app/src/main/cpp/samples/MicInput.wav`
- `whisper_native/app/src/main/cpp/samples/english_test_3_bili.wav`
- `whisper_native/app/src/main/cpp/samples/english_test_3_bili_16000_mono_float_silence_removed.wav`

Notes:

- `MicInput.wav` may be project-authored or locally recorded, but that is not
  proven by a checked-in attribution record.
- `english_test_3_bili.wav` strongly suggests an external source, but the
  repository does not currently include attribution or license text for it.
- `jfk.wav`, `english_test1.wav`, and `english_test2.wav` are commonly used as
  demo/sample audio names in speech repos, but this repository does not
  preserve their upstream provenance.

Recommendation:

- Before redistributing those files in a public release, either:
  - add explicit attribution and source/license records for each file, or
  - remove/replace them with clearly licensed samples.

## Build Tooling

The repository also contains build-tool artifacts such as:

- `whisper_native/gradle/wrapper/gradle-wrapper.jar`

I did not perform a full tooling-license audit in this pass. This file focuses
on app/runtime code and bundled data checked into the repository.

## Audit Basis

This inventory was based on:

- checked-in license files in the repository
- local Gradle POM metadata for direct dependencies
- local file inventory of models, native libraries, audio, and video samples
- upstream project/model/dataset pages for the bundled Whisper model artifacts
  and Common Voice sample data
