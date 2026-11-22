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

CRT/LCD do not inspect audio. Kaleidoscope uses audio only when its configurable audioGain is nonzero. Water uses lowEnergy/onset only when audioGain is nonzero. Heat-ripple/fire scale source injection from lowEnergy and accept each new onset ID at most once per runtime. Heat onset injection also requires an event timestamp within 250 ms of presentation time. Rotozoom and kaleidoscope advance independent phases from monotonic presentation timestamps, with a 100 ms step cap. They smooth BPM-derived speed over 0.9 s and audio modulation over 0.18 s, clamp angular speed to ±4 rad/s, and integrate the rate into continuous phases. Onset event IDs are deduplicated per node and the response decays over time. Reusing the same snapshot does not create repeated bursts. No effect interprets a continuously high onset field as a new event.

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

## Infinimedia adapter
The current Infinimedia host adapter uses libinfinisound's existing PCM
analysis and does not add a decoder or FFT analyzer. It maps the existing 16
normalized spectrum bands directly, averages indices 0–3 for low, 4–9 for mid,
and 10–15 for high energy, computes RMS as the root-mean-square of the existing
normalized left/right VU RMS values, and takes peak as the maximum of the
existing left/right peak holders. The adapter timestamps the snapshot with
monotonic System.nanoTime() when the host receives the analyzer update.
libinfinisound additionally estimates BPM and low-band transient events from
those existing bands. The host maps BPM to the optional field and maps each
fresh, sequence-numbered transient to one FxAudioEvent. Spectral flux and beat
phase remain unavailable; the estimate is a visual cue, not a downbeat detector.

A host with an authored or externally analyzed stable beat/transient stream can
also provide its own monotonically increasing IDs and monotonic timestamps as
FxAudioEvent. Effects such as rotozoom accept each ID once; this library never
subscribes to a player or performs audio I/O.


### Optional artwork palette channels

Hosts may supply an ordered palette to `generator:lightning-spectrum` through the existing bounded `FxMetadataChannels` map: `fx.palette.count` (1–8) and `fx.palette.color.<index>.r/g/b` (sRGB components normalized to 0–1). Palette extraction is host-owned and is computed when the source image changes, never on the audio or GL render thread. The library uses the supplied entries in order for lightning outlines and falls back to cyan if the channel group is absent. Other effects ignore these channels unless their descriptors explicitly declare an interest.
