#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
VERSION_SCRIPT="$SCRIPT_DIR/gradle-version.sh"
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT
GRADLE_FILE="$TEST_DIR/build.gradle"

# Stops the test with a readable assertion failure.
fail() {
  echo "FAIL: $*" >&2
  exit 1
}

cat > "$GRADLE_FILE" <<'GRADLE'
android {
    defaultConfig {
        // versionName "comment-must-not-change"
        versionName "12.11.0"
        versionCode = 121100006
    }
}
GRADLE

[ "$(bash "$VERSION_SCRIPT" read "$GRADLE_FILE")" = $'12.11.0\t121100006' ] ||
  fail "read returned unexpected version data"

bash "$VERSION_SCRIPT" write \
  "$GRADLE_FILE" \
  "12.12.0-audio-haptics-beta.1" \
  "121200001" >/dev/null
[ "$(bash "$VERSION_SCRIPT" read "$GRADLE_FILE")" = \
  $'12.12.0-audio-haptics-beta.1\t121200001' ] ||
  fail "write did not persist both version fields"
grep -Fq '// versionName "comment-must-not-change"' "$GRADLE_FILE" ||
  fail "write changed a commented versionName"

cat >> "$GRADLE_FILE" <<'GRADLE'
        versionCode = 7 // malformed duplicate must remain untouched
GRADLE
bash "$VERSION_SCRIPT" write "$GRADLE_FILE" 12.12.1 121201001 >/dev/null
grep -Fq 'versionCode = 7 // malformed duplicate must remain untouched' \
  "$GRADLE_FILE" ||
  fail "write changed a non-canonical versionCode line"

if bash "$VERSION_SCRIPT" write "$GRADLE_FILE" 'bad"name' 123 >/dev/null 2>&1; then
  fail "unsafe versionName must be rejected"
fi
if bash "$VERSION_SCRIPT" write "$GRADLE_FILE" 12.12.0 2100000001 >/dev/null 2>&1; then
  fail "versionCode above Android's maximum must be rejected"
fi

printf 'versionName "one"\nversionName "two"\nversionCode = 1\n' > "$GRADLE_FILE"
if bash "$VERSION_SCRIPT" read "$GRADLE_FILE" >/dev/null 2>&1; then
  fail "duplicate versionName assignments must be rejected"
fi

echo "gradle-version tests passed"
