#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SYNC_SCRIPT="$SCRIPT_DIR/sync-released-version.sh"
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT
REMOTE="$TEST_DIR/remote.git"
SEED="$TEST_DIR/seed"

# Stops the integration test with a readable assertion failure.
fail() {
  echo "FAIL: $*" >&2
  exit 1
}

git init --quiet --bare "$REMOTE"
git init --quiet -b master "$SEED"
git -C "$SEED" config user.name "Version Sync Test"
git -C "$SEED" config user.email "version-sync-test@example.invalid"
git -C "$SEED" remote add origin "$REMOTE"
mkdir -p "$SEED/app"
printf 'versionName "12.11.0"\nversionCode = 121100006\n' \
  > "$SEED/app/build.gradle"
git -C "$SEED" add app/build.gradle
git -C "$SEED" commit --quiet -m init
git -C "$SEED" push --quiet -u origin master

(
  cd "$SEED"
  bash "$SYNC_SCRIPT" 12.12.0 121200001 origin master app/build.gradle
)
synced_file=$(git --git-dir="$REMOTE" show master:app/build.gradle)
grep -Fq 'versionName "12.12.0"' <<< "$synced_file" ||
  fail "versionName was not pushed"
grep -Fq 'versionCode = 121200001' <<< "$synced_file" ||
  fail "versionCode was not pushed"
[ "$(git --git-dir="$REMOTE" log -1 --format=%s master)" = \
  'chore(release): sync version 12.12.0' ] ||
  fail "sync commit has an unexpected subject"

commit_count=$(git --git-dir="$REMOTE" rev-list --count master)
(
  cd "$SEED"
  bash "$SYNC_SCRIPT" 12.12.0 121200001 origin master app/build.gradle
  bash "$SYNC_SCRIPT" 12.11.0 121100006 origin master app/build.gradle
)
[ "$(git --git-dir="$REMOTE" rev-list --count master)" = "$commit_count" ] ||
  fail "no-op or older sync created an extra commit"

if (
  cd "$SEED"
  bash "$SYNC_SCRIPT" 12.12.0-other 121200001 origin master app/build.gradle
) >/dev/null 2>&1; then
  fail "same versionCode with a different versionName must fail"
fi

touch "$REMOTE/reject-next-push"
cat > "$REMOTE/hooks/pre-receive" <<'HOOK'
#!/usr/bin/env bash
set -euo pipefail

git_dir=$(git rev-parse --git-dir)
marker="$git_dir/reject-next-push"
if [ -f "$marker" ]; then
  rm -f -- "$marker"
  echo "Rejecting the first push to exercise retry handling" >&2
  exit 1
fi
HOOK
chmod +x "$REMOTE/hooks/pre-receive"

retry_output=$(
  cd "$SEED"
  bash "$SYNC_SCRIPT" 12.13.0 121300001 origin master app/build.gradle 2>&1
)
grep -Fq 'retrying (1/3)' <<< "$retry_output" ||
  fail "sync did not report retrying after the rejected push"
retried_file=$(git --git-dir="$REMOTE" show master:app/build.gradle)
grep -Fq 'versionName "12.13.0"' <<< "$retried_file" ||
  fail "retry did not push versionName"
grep -Fq 'versionCode = 121300001' <<< "$retried_file" ||
  fail "retry did not push versionCode"

echo "sync-released-version tests passed"
