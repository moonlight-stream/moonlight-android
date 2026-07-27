#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=gradle-version.sh
source "$SCRIPT_DIR/gradle-version.sh"

RETRY_LIMIT=3
SYNC_ROOT=
SYNC_WORKTREE=

# Removes the temporary linked worktree created by the sync attempt.
cleanup_sync_worktree() {
  local repo_root

  repo_root=$(git rev-parse --show-toplevel 2>/dev/null || true)
  if [ -n "$repo_root" ] &&
    [ -n "$SYNC_WORKTREE" ] &&
    [ -d "$SYNC_WORKTREE" ]; then
    git -C "$repo_root" worktree remove --force "$SYNC_WORKTREE" \
      >/dev/null 2>&1 || true
  fi
  if [ -n "$SYNC_ROOT" ]; then
    rmdir "$SYNC_ROOT" >/dev/null 2>&1 || true
  fi
}

# Commits a released version to the target branch without allowing regression.
sync_released_version() {
  local version_name=$1
  local version_code=$2
  local remote=${3:-origin}
  local branch=${4:-master}
  local gradle_path=${5:-app/build.gradle}
  local repo_root
  local current_info current_name current_code
  local attempt

  validate_gradle_version "$version_name" "$version_code"
  version_code=$((10#$version_code))
  repo_root=$(git rev-parse --show-toplevel)
  SYNC_ROOT=$(mktemp -d \
    "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/release-version-sync.XXXXXX")
  trap cleanup_sync_worktree EXIT

  for ((attempt = 1; attempt <= RETRY_LIMIT; attempt++)); do
    SYNC_WORKTREE="$SYNC_ROOT/attempt-$attempt"
    git -C "$repo_root" fetch "$remote" "$branch"
    git -C "$repo_root" worktree add \
      --detach \
      "$SYNC_WORKTREE" \
      "$remote/$branch"

    current_info=$(read_gradle_version "$SYNC_WORKTREE/$gradle_path")
    IFS=$'\t' read -r current_name current_code <<< "$current_info"

    if [ "$current_code" -gt "$version_code" ]; then
      echo "Version sync skipped: $branch already has newer versionCode $current_code"
      return 0
    fi
    if [ "$current_code" -eq "$version_code" ]; then
      if [ "$current_name" != "$version_name" ]; then
        echo "ERROR: versionCode $version_code is already paired with '$current_name'" >&2
        return 1
      fi
      echo "Version sync already complete: $version_name ($version_code)"
      return 0
    fi

    write_gradle_version \
      "$SYNC_WORKTREE/$gradle_path" \
      "$version_name" \
      "$version_code"
    git -C "$SYNC_WORKTREE" config user.name "github-actions[bot]"
    git -C "$SYNC_WORKTREE" config \
      user.email "41898282+github-actions[bot]@users.noreply.github.com"
    git -C "$SYNC_WORKTREE" add -- "$gradle_path"
    git -C "$SYNC_WORKTREE" commit \
      -m "chore(release): sync version $version_name"

    if git -C "$SYNC_WORKTREE" push "$remote" "HEAD:$branch"; then
      echo "Synced released version $version_name ($version_code) to $branch"
      return 0
    fi

    echo "Push raced with another $branch update; retrying ($attempt/$RETRY_LIMIT)" >&2
    git -C "$repo_root" worktree remove --force "$SYNC_WORKTREE"
    SYNC_WORKTREE=
  done

  echo "ERROR: failed to sync released version after $RETRY_LIMIT attempts" >&2
  return 1
}

# Validates CLI arguments and starts the release synchronization.
main() {
  if [ "$#" -lt 2 ] || [ "$#" -gt 5 ]; then
    echo "Usage: $0 <version-name> <version-code> [remote] [branch] [build.gradle]" >&2
    return 2
  fi
  sync_released_version "$@"
}

main "$@"
