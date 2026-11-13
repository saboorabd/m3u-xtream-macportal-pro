# M3U Pro
Free, ad-free IPTV player + playlist manager. Original code, no ads, no billing.

Sources: M3U URL / file, Xtream Codes, MAC portal (Stalker: URL, MAC, Device ID 1/2, Serial)
Player: HLS / DASH / TS, ClearKey (multi-key) / Widevine / PlayReady, custom User-Agent & Referer,
        audio / subtitle / quality picker, PiP, sleep timer, brightness / volume / channel swipe gestures
Guide: XMLTV timeline with program images, now-line, tap to play, long-press for details
Live tab: all channels, groups, search, continue watching, favorites
Library: auto-check on launch, dead-link checker, dedupe, rename, regroup, export

## Backend sources (no ads, nothing for end users to configure)
Edit `app/src/main/assets/sources.json` (bundled) and/or host the same file online and put its raw URL in
`Config.SOURCES_URL`. On every launch the app downloads it and (re)builds one playlist per source:
`m3u`, `xtream` (server/user/pass) and `portal` (url, mac, deviceId, deviceId2, serial).
See `sources.example.json`. Set `Config.KIOSK = true` to hide the Library tab.
