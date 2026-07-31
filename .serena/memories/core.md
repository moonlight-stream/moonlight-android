# Core

- This repository is `Bogdan7c/moonlight-android`, a fork of
  `moonlight-stream/moonlight-android`. `origin` is the fork; `upstream` is the
  official project. The active feature branch is `feature/experimental-sbs`.
- Product goal: duplicate one flat Moonlight stream into two side-by-side eye
  images for a OnePlus 11 in a SHINECON SC-G12. It is not stereo VR and does not
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
  screen scale, separation, common/per-eye placement and perspective controls,
  one-term radial lens correction, and independent signed horizontal/vertical
  chromatic-aberration correction.
  HDR is disabled in SBS. Absolute input is intentionally not treated as mapped
  headset input.
- Lens correction is a local `0..100%` strength in `SbsRenderer`, defaulting to
  `50%` (`k=0.20`). Signed chromatic correction is independently adjustable and
  switchable for the X/Y axes in `-100..100%`, defaults to enabled at zero, and
  shifts red/blue sampling symmetrically around green in the same OES pass. When
  both effective coefficients are zero, the original single-sample path remains.
  The legacy scalar preference migrates to both enabled axes. Common horizontal
  offset moves the already calibrated eye pair without changing the per-eye difference.
  A headset-specific optical profile, Cardboard integration and head tracking
  remain future scopes requiring a user decision.
- Start further discovery with `mem:tech_stack`, `mem:conventions`,
  `mem:suggested_commands`, and `mem:task_completion`.
