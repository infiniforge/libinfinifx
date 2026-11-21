# Audio metadata

Audio analysis is outside this repository. The host or libinfinisound supplies a small immutable snapshot in FxFrameInput.audio. Rendering never performs audio I/O, PCM decoding, beat detection, or callbacks to a player.

## Implemented model and units

AudioFrameMetadata(timestampNanos, rms, peak, bands, lowEnergy, midEnergy, highEnergy, spectralFlux, onsetEvents, bpm, beatPhase):

- timestampNanos: nonnegative monotonic nanoseconds for the analysis snapshot.
- rms, peak, bands, low/mid/high energy, spectralFlux: normalized [0,1] levels. Non-finite values become 0; finite out-of-range values clamp.
- bands: up to 64 normalized bands. Empty means unavailable; no missing bands are invented.
- onsetEvents: up to 32 FxAudioEvent(id, timestampNanos, strength). IDs are stable, monotonically increasing host IDs in snapshot order; strength is [0,1]. An onset is an event, not a sustained boolean. FxEventCursor provides per-runtime deduplication and resets on discontinuity.
- bpm: optional 20–400; other values become unavailable.
- beatPhase: optional cycles normalized to [0,1); finite values wrap.
- channels: optional FxMetadataChannels, max 32 name/value pairs. Keys accept letters, digits, underscore, period, and dash (recommend a reverse-DNS or app prefix). Values clamp to [-1000,1000], non-finite values become zero.

The constructors copy lists/maps they receive and limit their sizes. audio = null is the normal no-audio state. Built-ins remain usable without it.

## Effect consumption and event reuse

CRT/LCD do not inspect audio. Water uses lowEnergy/onset only when audioGain is nonzero. Heat-ripple/fire scale source injection from lowEnergy and accept each new onset ID at most once per runtime. Heat onset injection also requires an event timestamp within 250 ms of presentation time. Reusing the same snapshot does not create repeated bursts. No effect interprets a continuously high onset field as a new event.

## Example snapshot

    val audio = AudioFrameMetadata(
        timestampNanos = analysisTimestampNanos,
        rms = .24f,
        peak = .71f,
        bands = listOf(.08f, .12f, .2f, .4f, .53f, .3f),
        lowEnergy = .38f,
        midEnergy = .27f,
        highEnergy = .11f,
        spectralFlux = .62f,
        onsetEvents = listOf(FxAudioEvent(id = onsetCounter, timestampNanos = onsetTimeNanos, strength = .8f)),
        bpm = 112f,
        beatPhase = .31f,
    )

## Infinimedia adapter guidance

Infinimedia owns PCM/playback-position analysis today, including RMS/peak, 16 spectrum bands, and onset/transient features. Do not add another decoder or analyzer here. A later app-owned adapter can:

1. Use the analyzer's playback-position timestamp for audio.timestampNanos, and pass the associated spectrum snapshot with the visual frame.
2. Copy RMS/peak only after normalizing to [0,1]; if analyzer levels are dBFS with a -72 dB floor, map dBFS to linear amplitude using 10^(dBFS/20), treating the analyzer's floor/silence as zero.
3. Map the existing 16 bands to bands directly after dBFS-to-amplitude conversion. Derive low/mid/high as documented contiguous band groups; do not invent instrument labels.
4. Convert each actual onset/transient to a stable incrementing FxAudioEvent ID and timestamp. Do not emit it again as a new event on later frames.
5. Leave audio null for video/image items and pass source geometry/clock separately.

This is guidance only: no Infinimedia source or libinfinisound type is imported or modified.
