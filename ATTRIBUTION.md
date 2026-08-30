# ELARA — Attribution & License Notes

## YouTube Music (InnerTube) source

ELARA's YouTube Music support (`app/src/main/kotlin/com/aesthetic/spotify/data/network/YouTubeMusicApi.kt`)
was written **independently** for ELARA against the publicly observable
InnerTube protocol.

The protocol approach (client identities, endpoint shapes, fallback strategy)
was studied with reference to:

- **Echo Music** — https://github.com/EchoMusicApp/Echo-Music
  License: **GPL-3.0** (c) Echo Music contributors.
  Specifically its `innertube` module (client definitions: WEB_REMIX,
  ANDROID "no SDK", ANDROID_VR gating notes, PoToken requirements).

No Echo source code was copied into ELARA. ELARA uses its own networking
stack (OkHttp), models, and parsing. Where Echo's implementation informed
protocol behavior (e.g., that the ANDROID no-SDK client returns muxed
direct-URL streams without a PoToken), that behavior is independently
observable network protocol, not copyrightable expression.

If ELARA is ever distributed, this file must be kept alongside the binary,
and — because the design references a GPL-3.0 work — the distributor should
review GPL-3.0 obligations for derivative-work status before distribution.

Not ported from Echo (deliberately): PoToken/BotGuard generation, account
auth, playlists, library, lyrics, transcripts, uploads, UI, database.

## Other backends

- iTunes Search API / Apple RSS (catalog & charts) — Apple, public interfaces.
- JioSaavn web API — unofficial use of JioSaavn's public web endpoints.
- LRCLIB (lrclib.net) — open lyrics API.
- Musixmatch ws/1.1 — protocol reference: Strvm/musicxmatch-api (MIT-style
  license per that repository's README).
- SoundCloud public web API — unofficial use.
