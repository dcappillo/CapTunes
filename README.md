# Cap Tunes

A Spotify-style music player for Android that plays **your own music**, either the songs stored on your phone or your library on a self-hosted [Navidrome](https://www.navidrome.org/) (or any Subsonic-compatible) server.

Built with Kotlin, Jetpack Compose and Media3.

## Features

- **Two ways to listen.** Tap *Use music on this phone* to play the songs already on your device (no account needed), or sign in to your own Navidrome/Subsonic server.
- **Home** with quick picks, recently played, new additions, Rediscover, and playlists by decade.
- **Smart mixes** that learn from what you finish, skip and dislike. Daily Mixes keep adding songs as you near the end, and an optional world-popularity signal from Deezer's public charts helps balance a library that leans heavily toward one era.
- **Playlists:** create, rename and delete playlists, and add or remove songs, all from the app.
- **Synced lyrics** from your server or, if you turn online lookups on, [LRCLIB](https://lrclib.net), shown line by line on the album art or full screen.
- **Wrapped:** a yearly recap built from your on-device play history.
- **Player:** background playback with lock-screen and notification controls, a queue with drag-to-reorder, resume after restart, and offline downloads (server mode).
- **Streaming quality:** pick the quality separately for mobile data and Wi-Fi (Original, 320, 192 or 128 kbps). Below Original, the server converts songs to a smaller MP3 so large lossless files don't buffer. Downloads keep the original file, except formats Android can't play, which are saved as MP3.
- **Format fallback:** in server mode, files Android can't decode (WMA, APE…) are converted to MP3 by the server on the fly.

## Install it

There's no public download yet. To try it, build it yourself with the steps below.

## Build it

1. Install **Android Studio** (current stable) with JDK 21.
2. **File > Open** and pick this folder. Let Gradle sync.
3. Build with the Gradle task `app > Tasks > other > assembleDebug`, or press **Run ▶** with a phone connected.
   The APK lands in `app/build/outputs/apk/debug/`.

## Using a server

Any Subsonic-compatible server works. Want to try it without your own? Use Navidrome's public demo: `https://demo.navidrome.org`, user `demo`, password `demo`.

For safety, plain `http` is only accepted for private addresses (home network, [Tailscale](https://tailscale.com), localhost). Anything on the open internet must use `https`. Your password isn't saved. The app keeps the sign-in token the Subsonic API uses, encrypted with a key in the phone's Android Keystore. That token works like a password for your server, so signing out removes it along with downloads and cached cover art.

## Privacy

Cap Tunes has no accounts, analytics or ads. The detailed play history behind mixes and Wrapped stays on your device, and you can erase it from Account. In server mode the app also tells your own music server which songs you play, so its play counts stay current.

Two online lookups are **off until you turn them on**: synced lyrics from [LRCLIB](https://lrclib.net) and world popularity from [Deezer](https://developers.deezer.com/api)'s public search. When they're on, the app sends those services the **title, artist, album and length** of songs you play. The app asks once after you sign in, and the switch is in Account.

The full policy is in [PRIVACY.md](PRIVACY.md).

## Project layout

```
code/data/       music sources (server + on-device), play log, taste engine, mixes, lyrics, popularity
code/playback/   background player service, queue, downloads, play logging
code/ui/         Compose screens: home, search, library, album/playlist/artist, player, Wrapped
app/             Android manifest, resources, Gradle build
```

## Credits

- The **Bungee** font by David Jonathan Ross, used under the SIL Open Font License (see `BUNGEE-OFL.txt`).
