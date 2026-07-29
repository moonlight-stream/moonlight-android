# Task completion

1. Self-review ownership, normal-mode regression, thread affinity, Surface
   generation, connection cancellation and exactly-once resource release.
2. Run focused checks plus `./gradlew assembleNonRootDebug`, `./gradlew test lint`
   and `git diff --check` when relevant.
3. Inspect final Git status/diff and exclude generated APK/build/cache output.
4. Rendering, input and lifecycle work requires a separately reported device
   smoke test; a successful build is not runtime verification.
5. Reassess durable boundaries, workflow, commands and limitations. Update
   Serena memories only when that knowledge changed.
6. Final response states changes, verification, device-test status and memory
   updates.
