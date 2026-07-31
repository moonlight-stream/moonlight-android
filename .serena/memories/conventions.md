# Conventions and boundaries

- Preserve upstream behavior unless the fork requirement explicitly changes it.
- Keep the direct MediaCodec-to-Surface path free of the SBS GL pass.
- Local presentation settings do not enter StreamConfiguration or the wire
  protocol.
- GL resources and calls stay on the SBS render thread. Stop MediaCodec/native
  producers before releasing decoder Surface, SurfaceTexture, texture or EGL.
- Treat Android Surface callbacks and native connection callbacks as stale-capable;
  validate generation/state before committing lifecycle transitions.
- Do not put standalone renderer/input/config logic into the already large
  `Game` activity. Put logic with its resource/state owner.
- Follow existing Java formatting and English production comments. Harness docs
  and Serena memories are written in Russian or concise technical English.
- No cosmetic refactor mixed with feature or bug-fix diffs.
