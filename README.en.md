<p align="center">
  <img src="artwork/icon.svg" width="112" alt="Sirin Music icon">
</p>

# Sirin Music

[Русский](README.md)

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=ru.rainedev.sirinmusic"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" width="180" alt="Get it on Google Play"></a>
</p>

A native Android client for the [musik](https://github.com/torwin-job/musik) music server. Your music library, personal mixes and radio that learns your taste — with a Material 3 interface and system playback controls.

Sirin Music is a client: the library, profiles and listening history live on your musik server — the app connects to it by server address and account. No ads, subscriptions or tracking — the source code is open ([GPL-2.0](LICENSE)).

**No server of your own? Try the demo:** [demo-music.rainedev.ru](https://demo-music.rainedev.ru) — login `demo`, password `musik_demo` 

## Google Play vs GitHub — what's the difference

| | Google Play | GitHub (APK from releases) |
| --- | --- | --- |
| Updates | automatically via Google Play | a built-in update checker and installer for signed APKs from GitHub Releases (checks off by default) |
| `REQUEST_INSTALL_PACKAGES` | no | yes — required to install the APK |
| Features | identical | identical |

Both builds are signed with the same certificate — you can freely switch between the channels without reinstalling or losing data.

## Screenshots

<p align="center">
  <img src="docs/screenshots/home.jpg" width="220" alt="Home screen: radio and personal mixes">
  <img src="docs/screenshots/player.jpg" width="220" alt="Player and track ratings">
  <img src="docs/screenshots/settings.jpg" width="220" alt="Theme and palette settings">
</p>

<details>
<summary>Library, playlists and profile</summary>

<p align="center">
  <img src="docs/screenshots/library.jpg" width="220" alt="Library with search and favorites">
  <img src="docs/screenshots/playlists.jpg" width="220" alt="User playlists">
  <img src="docs/screenshots/profile.jpg" width="220" alt="Profile and listening statistics">
</p>

<p align="center">
  <img src="docs/screenshots/profile-discovery.jpg" width="220" alt="Radio novelty bounds">
  <img src="docs/screenshots/profile-statistics.jpg" width="220" alt="Weekly radio statistics">
  <img src="docs/screenshots/profile-contexts.jpg" width="220" alt="Moods and hidden entries">
</p>
<p align="center">
  <img src="docs/screenshots/profile-share.jpg" width="220" alt="Radio links">
</p>

</details>

<details>
<summary>Connection and QR</summary>

<p align="center">
  <img src="docs/screenshots/login.jpg" width="220" alt="Sign in with username and password or token">
  <img src="docs/screenshots/qr-permission.jpg" width="220" alt="Camera permission for QR scanning">
  <img src="docs/screenshots/qr-scanner.jpg" width="220" alt="QR scanning and image selection">
  <img src="docs/screenshots/connection.jpg" width="220" alt="Connection settings">
</p>

</details>

<details>
<summary>Account, profiles and users</summary>

<p align="center">
  <img src="docs/screenshots/account.jpg" width="220" alt="Account and profile selection">
  <img src="docs/screenshots/devices.jpg" width="220" alt="Devices and tokens">
  <img src="docs/screenshots/users.jpg" width="220" alt="User management and invitations">
</p>
<p align="center">
  <img src="docs/screenshots/account-login.jpg" width="220" alt="Account management sign-in">
  <img src="docs/screenshots/account-profiles.jpg" width="220" alt="Creating and renaming a profile">
  <img src="docs/screenshots/invitations.jpg" width="220" alt="Creating an invitation">
</p>

</details>

<details>
<summary>Server settings</summary>

<p align="center">
  <img src="docs/screenshots/server-settings.jpg" width="220" alt="Server address and network access">
  <img src="docs/screenshots/server-library.jpg" width="220" alt="Music library folder">
  <img src="docs/screenshots/server-connection.jpg" width="220" alt="Connecting the phone to the server">
</p>
<p align="center">
  <img src="docs/screenshots/server-qr.jpg" width="220" alt="QR code for connecting the app">
</p>

</details>

<details>
<summary>Storage, updates and about</summary>

<p align="center">
  <img src="docs/screenshots/storage.jpg" width="220" alt="Storage and cache cleanup">
  <img src="docs/screenshots/updates.jpg" width="220" alt="App update check">
  <img src="docs/screenshots/about.jpg" width="220" alt="About Sirin Music">
</p>

</details>

## Features

- **Library:** search for tracks, artists and albums, a dedicated favorites screen.
- **Radio and mixes:** server recommendations, personal mixes, playback by artist, album or radio from a track.
- **Playlists:** create, rename, add and remove tracks, reorder. Duplicate tracks keep their own entries in a playlist.
- **Favorites and taste learning:** hearts save tracks, artists and albums; like/dislike ratings train the recommendation profile. A dislike does not auto-switch the track.
- **Track repeat:** a player button loops the current composition.
- **Music cache:** upcoming queue tracks are preloaded to disk so network pauses never interrupt playback; the size limit is configurable (100 MB–1 GB), clearing lives in Storage.
- **System player:** background playback, shade and lock screen controls, pause, previous and next track, a like button. Going back uses the server session history.
- **Lyrics:** plain text and synced LRC, line highlighting, following playback, tap-to-seek a line.
- **Theming:** system, light and dark themes, wallpaper colors on Android 12+, ten built-in palettes, bottom navigation or a side rail on wide screens.
- **Connection:** QR from camera or gallery, sign-in with username and password or API token, server verification before saving. The API token is stored encrypted and excluded from Android backups.
- **Radio profile:** novelty bounds, weekly statistics, moods, hidden entries and radio links.
- **Accounts:** password change, profile creation and selection, device and token management; invitations and user rights for administrators. Account management requires HTTPS and a separate sign-in.
- **Server:** address, port, music folder and phone pairing. Saving and restart availability depends on server rights and capabilities.
- **Sync:** restores the active session while paused, keeps the position and hands control between devices when the server supports it.
- **Artist photos:** photos from the server with cover fallback and a shared image cache.
- **Updates (GitHub build):** a dedicated screen for checking, downloading and installing signed stable APKs. Startup and background checks are off by default. In the Play build, updates are delivered by Google Play.

The queue and recommendations are chosen by the server. The end of a fixed playlist stops playback. API, audio and covers share one network client; authentication is limited to the configured server. On an invalid token the app returns to the sign-in screen.

## Connect

1. Install the app: from [Google Play](https://play.google.com/store/apps/details?id=ru.rainedev.sirinmusic), [GitHub Releases](https://github.com/fenixvd/SirinMusic/releases) or [F-Droid](https://f-droid.org) (inclusion requested).
2. **No server of your own?** Use the demo server `demo-music.rainedev.ru`, login `demo`, password `musik_demo`.
3. Have your own server: tap "Scan QR code" and point the camera at the code from the musik web UI (*Profile → Settings → "Show QR"*) — the address and token are filled in automatically. The code can also be scanned with the system camera: the `musik://connect` link opens the app. Or enter the server address and sign in with username and password, or an API token.
4. Once the library loads, pick radio, a mix or a track.

QR codes are recognized on the device (ZXing), without Google Play services or network requests.

The client uses musik API 1.0.0 contracts. The server must be reachable from the phone: a local setup needs a home network or VPN. Use HTTPS when connecting over an untrusted network; HTTP is supported for local installations.

The app contains no built-in server address or token. Lyrics must be provided by the server. Offline library downloads, a smart playlist rule editor and playlist import/export are not implemented yet.

## Privacy

Sirin sends nothing to analytics: your listening history lives on your server, the artwork and music caches are local. Details — in the [privacy policy](https://rainedev.ru/en/sirin/privacy/).

## Build

Requires JDK 21 for Gradle, JDK 17 for compilation, Android SDK Platform 37 and Build Tools 37.0.0. The Gradle configuration can provision the required JDKs. Target SDK — Android 16 / API 36, minimum — API 27.

Point the SDK path in `ANDROID_HOME` or in a local, Git-excluded `local.properties` file via `sdk.dir`.

```sh
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The tests use a local HTTP server and verify favorites, playlist operations, duplicate entries, queue navigation and going back, ratings, lyrics, authorization errors and token transmission restricted to your own server. LRC and fixed playlist completion are verified separately.

The Google Play build (no GitHub updater, AAB):

```sh
./gradlew -PplayStore=true :app:bundleRelease
```

## CI and releases

`Android CI` runs unit tests, Android Lint and a debug APK build on push and pull request to `master` and `dev`. APKs and reports are available in GitHub Actions artifacts.

`Android Release` builds a signed APK on a `vX.Y.Z` tag or manually for an existing tag. The tagged commit must be part of the `master` history. A separate publishing job uploads the verified APK and SHA-256 to GitHub Releases. Publishing requires signing secrets; the private key is not in the repository.

The Google Play AAB is built locally: `./gradlew -PplayStore=true :app:bundleRelease`. Signing and publishing are described in [docs/RELEASING.md](docs/RELEASING.md).

## Contributing

Pre-pull-request checks and change requirements are described in [CONTRIBUTING.md](CONTRIBUTING.md). For vulnerability reports see [SECURITY.md](SECURITY.md).

## License

[GNU GPL v2](LICENSE)
