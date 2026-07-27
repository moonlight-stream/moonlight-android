#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
VERSION_SCRIPT="$SCRIPT_DIR/version-code.sh"
TEST_REPO=$(mktemp -d)
trap 'rm -rf "$TEST_REPO"' EXIT

# Stops the test with a readable assertion failure.
fail() {
  echo "FAIL: $*" >&2
  exit 1
}

# Asserts the target code, floor, and floor source returned by the script.
assert_info() {
  local tag=$1
  local expected_code=$2
  local expected_floor=$3
  local expected_source=$4
  local output code floor source

  output=$(bash "$VERSION_SCRIPT" "$tag" build.gradle)
  IFS=$'\t' read -r code floor source <<< "$output"
  [ "$code" = "$expected_code" ] ||
    fail "$tag: expected code $expected_code, got $code"
  [ "$floor" = "$expected_floor" ] ||
    fail "$tag: expected floor $expected_floor, got $floor"
  [ "$source" = "$expected_source" ] ||
    fail "$tag: expected floor source '$expected_source', got '$source'"
}

# Creates an annotated release tag at a deterministic timestamp.
tag_at() {
  local tag=$1
  local timestamp=$2
  GIT_COMMITTER_DATE="$timestamp" git tag -a "$tag" -m "$tag"
}

git init --quiet "$TEST_REPO"
cd "$TEST_REPO"
git config user.name "Version Code Test"
git config user.email "version-code-test@example.invalid"
printf 'versionName "0.0.0"\nversionCode = 393\n' > build.gradle
git add build.gradle
GIT_AUTHOR_DATE='2026-01-01T00:00:00Z' \
  GIT_COMMITTER_DATE='2026-01-01T00:00:00Z' \
  git commit --quiet -m init

tag_at v12.10.8-game-menu-beta.1 2026-01-02T00:00:00Z
tag_at v12.10.8 2026-01-03T00:00:00Z
tag_at v12.10.8-audio-haptics-0.5.14-beta.1 2026-01-04T00:00:00Z
tag_at v12.10.8-audio-haptics-0.5.14-beta.2 2026-01-05T00:00:00Z
tag_at v12.11-beta.1 2026-01-06T00:00:00Z
tag_at v12.11-beta.2 2026-01-07T00:00:00Z
tag_at v12.11.0 2026-01-08T00:00:00Z

assert_info \
  v12.10.8-game-menu-beta.1 \
  121008001 \
  121100003 \
  'tag v12.11.0'
assert_info \
  v12.10.8-audio-haptics-0.5.14-beta.1 \
  121008003 \
  121100003 \
  'tag v12.11.0'
assert_info v12.11.0 121100003 121100003 'tag v12.11.0'

# Adding a later tag does not change an existing tag's derived code.
tag_at v12.11.0-hotfix.1 2026-01-09T00:00:00Z
assert_info v12.11.0 121100003 121100004 'tag v12.11.0-hotfix.1'
assert_info v12.11.0-hotfix.1 121100004 121100004 'tag v12.11.0-hotfix.1'

# Components with leading zeroes are parsed as decimal.
tag_at v12.08.09-beta.1 2026-01-10T00:00:00Z
assert_info v12.08.09-beta.1 120809001 121100004 'tag v12.11.0-hotfix.1'

# The checked-in value remains a fallback floor when it is higher than history.
printf 'versionName "13.0.0"\nversionCode = 130000001\n' > build.gradle
assert_info v12.11.0-hotfix.1 121100004 130000001 build.gradle
printf 'versionName "0.0.0"\nversionCode = 393\n' > build.gradle

tag_at v12.100.0 2026-01-11T00:00:00Z
if bash "$VERSION_SCRIPT" v12.100.0 build.gradle >/dev/null 2>&1; then
  fail "MINOR values greater than 99 must be rejected"
fi

tag_at v210.0.0 2026-01-12T00:00:00Z
if bash "$VERSION_SCRIPT" v210.0.0 build.gradle >/dev/null 2>&1; then
  fail "versionCode values above Android's maximum must be rejected"
fi

if bash "$VERSION_SCRIPT" v12 build.gradle >/dev/null 2>&1; then
  fail "tags without a MINOR component must be rejected"
fi
if bash "$VERSION_SCRIPT" v12.12.0 build.gradle >/dev/null 2>&1; then
  fail "tags absent from the repository must be rejected"
fi

echo "version-code tests passed"
