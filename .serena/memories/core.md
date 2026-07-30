# Core

- This repository is `Bogdan7c/moonlight-android`, a fork of
  `moonlight-stream/moonlight-android`. `origin` is the fork; `upstream` is the
  official project. The active feature branch is `feature/experimental-sbs`.
- Product goal: duplicate one flat Moonlight stream into two side-by-side eye
  images for a OnePlus 11 in a SHINECON SC-H12. It is not stereo VR and does not
  require Sunshine/protocol changes, OpenXR, a virtual room, or head tracking.
- The intended path is one network stream, one hardware MediaCodec decoder,
  `SurfaceTexture`/external OES, and one GLES composition pass with no CPU frame
  copies. The ordinary direct-to-Surface upstream path remains the default.
- Ownership: `Game` orchestrates Activity/Surface/connection lifecycle;
  `MediaCodecDecoderRenderer` owns codec/pacing/recovery; `SbsRenderer` owns
  EGL/OES/SurfaceTexture and decoder Surface; `PreferenceConfiguration` owns
  persisted local SBS settings; `NvConnection` owns connection cancellation and
  native permit lifecycle.
- Current SBS MVP provides duplicated eyes, aspect-fit black surround, virtual
  screen scale, horizontal separation, vertical position, and configurable
  one-term radial lens correction. HDR is disabled in SBS. Absolute input is
  intentionally not treated as mapped headset input.
- Lens correction is a local `0..100%` strength in `SbsRenderer`, defaulting to
  `50%` (`k=0.20`); it adds no render pass and `0%` bypasses fragment distortion.
  A headset-specific optical profile, chromatic correction, Cardboard integration
  and head tracking remain future scopes requiring a user decision.
- Start further discovery with `mem:tech_stack`, `mem:conventions`,
  `mem:suggested_commands`, and `mem:task_completion`.
