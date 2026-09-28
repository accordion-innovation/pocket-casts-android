# Accordion Audio Variants

[Accordion](https://www.accordion.live) publishes multiple cuts of the same podcast episode — a
five-minute summary, a half-hour edit, the full show. This integration surfaces those cuts in the
player's effects sheet so a listener can choose one without leaving Pocket Casts.

Everything here is gated behind the `ACCORDION_AUDIO_VARIANTS` feature flag
(`accordion_audio_variants`), which defaults on in debug and prototype builds only.

## Where the code lives

| Area | Location |
| --- | --- |
| API client | `modules/services/servers/.../servers/accordion/` |
| Playback swap | `PlaybackManager.swapToVariantUrl`, `ExoPlayerDataSourceFactory` |
| UI | `modules/features/player/.../player/view/AccordionVariantPanel.kt`, `.../viewmodel/AccordionVariantViewModel.kt` |
| Build config | `dependencies.gradle.kts`, `fastlane/env/user.env-example` |

## The API key

`AccordionConfig.API_KEY` is resolved at build time in this order:

1. the `ACCORDION_API_KEY` environment variable,
2. `accordionApiKey` in `secret.properties`,
3. the empty string.

An empty key is not a build failure. The app builds and runs normally; the variant panel just never
appears. `AccordionManager` checks for a blank key before making a request and throws a message
naming that specific problem, because otherwise the symptom is an opaque auth redirect that looks
like a parsing bug.

The key is a build-time constant compiled into the APK, so anyone holding the app can extract it.
**Treat it as a public client identifier for attribution and rate limiting, never as an
authorization secret** — the server must not grant anything on the strength of the key alone.

It is sent as an `X-Api-Key` header rather than a query parameter. Query strings are recorded
verbatim by access logs, proxies, and the debug build's OkHttp logging interceptor, which would
spread the key well beyond the request itself.

Never commit a real key to source.

## Looking up an episode

```
GET https://www.accordion.live/api_v1/content/{podcastHash}/by-title?episode_title={title}
X-Api-Key: <key>
```

Accordion's own `episode_hash` is derived from the RSS `<guid>`, which Pocket Casts does not store.
So episodes are resolved by podcast + title instead:

- **`podcastHash`** is `md5(Podcast.podcastUrl)`.
- **`episode_title`** is the raw episode title, exactly as Pocket Casts shows it.

### `podcastUrl` is the website link, not the feed URL

This trips people up. `Podcast.podcastUrl` holds the podcast's **website link** — the feed's
`<channel><link>` — which is what the app opens from the "Website" row on a podcast's page. It is
hashed verbatim, with no normalisation, because Accordion hashes the same string.

For The Changelog that means:

| | |
| --- | --- |
| `podcastUrl` (hashed) | `https://changelog.com/master` |
| md5 | `0e4adecec0bce323f97dc2bcd1745be5` |
| Feed URL (**not** used) | `https://changelog.com/master/feed` |

When a publisher uploads an episode to Accordion, the URL they enter has to be the one Pocket Casts
stored in `podcastUrl`. Entering the feed URL produces a different hash and the lookup silently
returns nothing.

### Redirects are not followed

`accordion.live` answers an unauthenticated content request with a 303 to its HTML `/direct-login`
page. If the client followed that, an auth failure would surface as a Moshi "malformed JSON" error
on the login page's markup, which says nothing about the real cause. The Accordion Retrofit client
therefore sets `followRedirects(false)` so `AccordionManager` can report the 303 as what it is.

## Validating the response

`AccordionManager` drops anything the app cannot safely play *before* it reaches the player:

- **https only.** A variant URL is handed to ExoPlayer and written onto the playing episode, and
  the player's `DefaultDataSource` resolves `file://`, `content://` and `asset://` just as readily
  as http. Rejecting everything but https stops a tampered or compromised response from aiming the
  player at local storage or at a cleartext connection.
- **Implausible durations become `0` (unknown).** Anything non-finite, non-positive, or longer than
  24 hours cannot describe an episode and would otherwise land on the player's progress bar.
- **At most 8 variants.** The panel lays every variant out across a single row.

Order is preserved: the slider positions follow whatever order the server returned.

## Swapping the player

`PlaybackManager.swapToVariantUrl(url, durationSeconds)` overrides the active stream URL and reloads
ExoPlayer at the current position. It returns `false` when the swap cannot be applied — nothing is
playing, or the episode is downloaded — so the UI can avoid showing a variant as selected when the
audio did not actually change.

How the reload preserves position:

- **Playing** → `resetPlayer()` builds a fresh `SimplePlayer`, then `seekToTimeMs(currentPositionMs)`
  restores the position.
- **Paused** → `stopPlayer()` → `handleStop()` releases ExoPlayer and clears `prepared`, so the
  subsequent `load()` re-prepares with the new URL.

### Variant metadata never reaches the database

The override is held in memory, scoped to a single episode uuid, and applied to the in-memory
episode as it is loaded into the player. It is applied *after* the standard `updateDownloadUrl`
block in `loadCurrentEpisode` so a resume does not clobber it, and dropped as soon as playback moves
to a different episode.

`onDurationAvailable` also skips persisting the duration while a variant is active. A variant's URL
and duration describe the variant, not the episode; writing them back would corrupt the episode's
real metadata and sync that corruption to the user's other devices.

### ExoPlayer cache keys

Episodes are normally cached under their uuid alone, so a refreshed signed URL for the same audio
still hits the existing entry. Accordion variants are *different* audio sharing one uuid, so when
the flag is on `ExoPlayerDataSourceFactory` adds the URL path to the cache key. The path is stable
across signed-URL refreshes but differs per variant. With the flag off the plain-uuid key is
unchanged, so enabling the feature never invalidates a user's existing cache.

## The panel

`AccordionVariantPanel` sits in the effects sheet below the volume boost section and is hidden
unless the current episode is streaming and has at least two usable variants.

**Opening the sheet does not touch the player.** The panel opens with the longest variant
pre-selected, because that is the closest match to the full RSS audio already playing, but nothing
is swapped until the user picks something. Swapping recreates ExoPlayer and reloads the stream,
which is far too disruptive to do just because someone opened the effects sheet.

A stale panel is never acted on: the ViewModel tracks the uuid its variants belong to, discards
responses that arrive after the episode changed, and hides rather than applying a variant to an
episode it was not loaded for.

Nothing is requested unless the feature flag is on, since the lookup tells `accordion.live` what the
user is listening to. Only the hash is ever logged, never `podcastUrl` itself — a podcast URL can
carry a per-user access token for a private or premium feed, and `LogBuffer` ends up in the debug
log users attach to support emails.

## Known limitations

- Downloaded episodes hide the panel silently rather than explaining why; saying so needs new
  localized copy.
- Switching variants on a downloaded episode by streaming instead of playing the local file would
  be a genuinely useful feature, but today it is a no-op.
- Duration labels (`28m`, `1h 22m`, and the `V1`/`V2` fallback when the API omits a duration) are
  not localized.
- Only the first and last variant are labelled; intermediate slider positions are unlabelled.
- The dot markers are plain clickable boxes with no accessibility semantics, so TalkBack support
  needs work.

## Server-side requirements

The deployed Accordion server must:

- expose `api_v1/content/{podcastHash}/by-title` accepting `podcast_hash` + `episode_title`, rather
  than only the `episode_hash` route, and
- read the API key from the `X-Api-Key` header, alongside the `verifyApiKey` service-role fix.

Until both are live, the API returns 401 and the panel stays hidden.
