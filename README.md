# Cap Tunes

A Spotify-style music player for Android that plays **your own music**, either the songs stored on your phone or your library on a self-hosted [Navidrome](https://www.navidrome.org/) (or any Subsonic-compatible) server.

Built with Kotlin, Jetpack Compose and Media3.

## Features

- **Two ways to listen.** Tap *Use music on this phone* to play the songs already on your device (no account needed), or sign in to your own Navidrome/Subsonic server.
- **Home** with quick picks, recently played, new additions, Rediscover, and playlists by decade.
- **Smart mixes** that learn from what you finish, skip and dislike. Daily Mixes keep adding songs as you near the end, and an optional world-popularity signal from Deezer's public charts helps balance a library that leans heavily toward one era.
- **Synced lyrics** from your server or [LRCLIB](https://lrclib.net), shown line by line on the album art or full screen.
- **Wrapped:** a yearly recap built from your on-device play history.
- **Player:** background playback with lock-screen and notification controls, a queue with drag-to-reorder, resume after restart, and offline downloads (server mode).
- **Mobile-data quality:** on a metered connection the server converts songs to a smaller MP3 (320, 192 or 128 kbps, your choice) so lossless files don't buffer on the road; Wi-Fi and downloads stay original.
- **Format fallback:** in server mode, files Android can't decode (WMA, APE…) are converted to MP3 by the server on the fly.

## Build it

1. Install **Android Studio** (current stable) with JDK 21.
2. **File > Open** and pick this folder. Let Gradle sync.
3. Build with the Gradle task `app > Tasks > other > assembleDebug`, or press **Run ▶** with a phone connected.
   The APK lands in `app/build/outputs/apk/debug/`.

## Using a server

Any Subsonic-compatible server works. Want to try it without your own? Use Navidrome's public demo: `https://demo.navidrome.org`, user `demo`, password `demo`.

For safety, plain `http` is only accepted for private addresses (home network, [Tailscale](https://tailscale.com), localhost). Anything on the open internet must use `https`. Your password is never stored; the app keeps only the salted token the Subsonic API uses.

## Privacy

Cap Tunes has no accounts, analytics or ads. Your play history stays on your device. Two optional lookups send a song's **title, artist and length** to public services: [LRCLIB](https://lrclib.net) for lyrics and [Deezer](https://developers.deezer.com/api)'s public search for world popularity.

## Project layout

```
code/data/       music sources (server + on-device), play log, taste engine, mixes, lyrics, popularity
code/playback/   background player service, queue, downloads, play logging
code/ui/         Compose screens: home, search, library, album/playlist/artist, player, Wrapped
app/             Android manifest, resources, Gradle build
```

## Credits

- The **Bungee** font by David Jonathan Ross, used under the SIL Open Font License (see `BUNGEE-OFL.txt`).
