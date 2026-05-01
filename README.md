# MusicFetch

An Android app that wraps [lucida.to](https://lucida.to/) in a native WebView with a built-in search bar that **automatically fills in your query, selects Amazon Music as the source, and clicks Go** — no manual interaction with the website needed.

## Download APK

Pre-built APKs are available from the [GitHub Actions](../../actions/workflows/build.yml) tab — click the latest workflow run and download the `MusicFetch-debug-apk` artifact.

## Features

- Search bar at the top — type a song name and tap the green button
- Auto-fills the lucida.to search input via JavaScript injection
- Auto-selects **Amazon Music** from the service dropdown
- Auto-clicks **Go** to start the conversion
- Downloads completed tracks to your phone's `Music/` folder via Android `DownloadManager`
- Back button navigates WebView history
- Spoofs a real Chrome Mobile user-agent so lucida.to behaves normally

## Requirements

| Item | Value |
|------|-------|
| Min Android | 8.0 (API 26) |
| Target SDK | 34 (Android 14) |
| Architecture | 64-bit (arm64-v8a, x86_64) |
| Internet | Required |

## Building from Source

### Prerequisites
- Android Studio (latest stable)
- Android SDK 34 installed

### Steps

1. Clone the repo:
   ```bash
   git clone https://github.com/saiprasath00/MusicFetch.git
   cd MusicFetch
   ```
2. Open in Android Studio: **File → Open** → select the `MusicFetch` folder
3. Let Gradle sync finish (~1–2 min)
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Install on your phone

**Via USB:** Enable Developer Options (tap Build Number 7×), enable USB Debugging, then press ▶ Run in Android Studio.

**Via file transfer:** Copy APK to phone → Settings → Security → Allow unknown sources → tap APK to install.

## How It Works

```
User types song name
        ↓
WebView navigates to lucida.to
        ↓
onPageFinished → injectSearch() fires after 800ms delay
        ↓
JS fills input, selects Amazon Music dropdown, clicks Go
        ↓
lucida.to returns download links
        ↓
User taps Download → DownloadManager saves to Music/ folder
```

The JS injection uses React/Vue-compatible value setters and polls every 300ms (up to 30 tries) to handle SPA hydration delays.

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Gradle sync fails | File → Invalidate Caches → Restart |
| SDK not found | SDK Manager → install Android 14 (API 34) |
| Nothing happens after search | lucida.to may have changed its DOM — check JS selectors in `MainActivity.java` |
| App crashes on launch | Ensure internet is available |

## License

MIT
