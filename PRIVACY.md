# Cap Tunes privacy policy

Last updated: November 14, 2026

Cap Tunes is a music player for your own music. It has no accounts of its own, no analytics, no ads and no servers run by its developer. This page says what the app keeps, what it sends, and how to remove it.

## What stays on your phone

- **Sign-in details for your music server:** the server address, your username, and a sign-in token. The token is encrypted with a key held in the Android Keystore. Your password is used once to create the token and is not saved.
- **Listening history:** which songs you played, when, and whether you finished or skipped them. It powers mixes and Wrapped. This detailed history is never sent anywhere.
- **Downloads, the saved queue, cached cover art and settings.**

## What the app sends, and to whom

- **Your own music server** (server mode): requests for your library, cover art and audio, each carrying your username and sign-in token. The app also reports each song you play to that server, so its play counts and "recently played" stay current. Plain `http` is only allowed for private addresses (home network, Tailscale, this phone); anything else must use `https`.
- **LRCLIB and Deezer** (only if you turn on "Online lyrics and popularity"): the title, artist, album and length of songs you play, to fetch lyrics from lrclib.net and a popularity score from api.deezer.com. No account, device identifier or listening history is sent. These lookups are off until you turn them on, and the switch is in Account. Those services see your internet address, as any website does, and handle it under their own policies.

Nothing else is sent anywhere.

## Permissions

- **Music and audio** (only in "music on this phone" mode): to find and play songs stored on the device.
- **Internet and network state:** to reach your server and to tell Wi-Fi from mobile data.
- **Foreground service and wake lock:** to keep playing and downloading with the screen off.

## Removing your data

- **Sign out** removes the sign-in token, downloads, the saved queue and cached cover art.
- **Erase listening history** (in Account) deletes every recorded play.
- **Uninstalling** the app removes everything it stored.

## Contact

Questions or problems: open an issue at https://github.com/dcappillo/CapTunes/issues
