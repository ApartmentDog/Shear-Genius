# Shear Genius

A pixel-art Minecraft skin editor for Android, built with Kotlin and Jetpack Compose.

## Features

- Tools: pencil, eraser, line, fill, eyedropper, shade, select, move
- Shade modes: lighten, darken, shine, noise, noisy pen, dither
- Up to 12 paint layers with per-layer visibility, and undo/redo
- Mirror painting, classic/slim arm models, and overlay (second layer) toggle
- Gestures: pinch to zoom, two-finger tap to undo, three-finger tap to redo, long-press to pick a color
- Rectangular selection with copy/paste
- Reference image screen with color sampling
- 3D preview with spin, walk animation, and optional painting directly on the model
- Multiple projects stored on-device, with zip backup and restore
- Skin lookup from Mojang
- Optional watermark on exports

## Requirements

- Android 10 (API 29) or newer
- Portrait orientation only, for now

## Building

You need JDK 17 and the Android SDK (compileSdk 36).

```
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`.

### Signing

The release build is signed from environment variables, or from a local `keystore.properties` file that is git-ignored. If neither is present, it falls back to the debug key. That APK installs fine but is not a distributable signature.

Create your own key (keep the file and passwords out of git):

```
keytool -genkeypair -v -keystore release.keystore -alias sheargenius \
  -keyalg RSA -keysize 4096 -validity 10000
```

For a local build, put this in `keystore.properties`:

```
storeFile=release.keystore
storePassword=...
keyAlias=sheargenius
keyPassword=...
```

For GitHub Actions, add these repository secrets (Settings > Secrets and variables > Actions):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 release.keystore` |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | the key alias |
| `KEY_PASSWORD` | the key password |

## CI

Every push to `main` builds a release APK and publishes it as a GitHub release named after the run number.

## Layout

- `app/src/main/java/com/apartmentdog/sheargenius/AppState.kt` - editor state, tools, layers, undo, project storage
- `model/SkinLayout.kt` - 64x64 skin texture layout and region maps
- `ui/` - Compose screens (editor, preview, reference, files) and the skin renderer

## License

No license has been chosen yet, so all rights are reserved by default. The bundled Pixelify Sans font is under the SIL Open Font License (see `app/src/main/assets/fonts/OFL.txt`).

