# WordCast AI

Android vocabulary scanner and audio lesson maker.

## Current MVP
- Select a page image from the device.
- Extract printed text using Google ML Kit Text Recognition.
- Review and edit extracted text.

## Planned
- Camera capture and document crop.
- English-to-Arabic vocabulary extraction and translation.
- Selectable male/female text-to-speech voice.
- Three pronunciation speeds, spelling, and Arabic meaning.
- Export audio and vocabulary list for offline study.

## Build
GitHub Actions builds a debug APK on every push to `main` and via manual workflow dispatch.
Artifact: `WordCastAI-debug-apk`.
