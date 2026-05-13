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

Source repository:

- `https://huggingface.co/cik009/whisper/tree/main`

Important note:

- The upstream OpenAI Whisper project itself is MIT-licensed:
  `https://github.com/openai/whisper/blob/main/LICENSE`
- The files checked into this repository are not copied directly from the
  OpenAI GitHub repo; they appear to be downloaded from the `cik009/whisper`
  Hugging Face repository, so the most direct confirmed license attached to the
  checked-in artifacts is that repository's declared `apache-2.0`.

## Build Tooling

The repository also contains build-tool artifacts such as:

- `whisper_native/gradle/wrapper/gradle-wrapper.jar`

I did not perform a full tooling-license audit in this pass. This file focuses
on app/runtime code and bundled data checked into the repository.

## Audit Basis

This inventory was based on:

- checked-in license files in the repository
- local Gradle POM metadata for direct dependencies
- local file inventory of models and native libraries
- upstream project/model pages for the bundled Whisper model artifacts
