#!/usr/bin/env bash

# Android accepts versionCode values up to 2,100,000,000. Each semantic
# MAJOR.MINOR.PATCH base gets 999 release slots (ordinals 1-999), assigned in
# tag creation order. This keeps feature previews, hotfixes, and stable tags
# for the same base unique without trying to interpret their free-form suffixes.
readonly ANDROID_VERSION_CODE_MAX=2100000000
readonly RELEASE_SLOTS_PER_BASE=1000

parse_version_base() {
  local tag=${1-}
  local version=${tag#v}
  local major minor patch

  if [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)($|[-.].*) ]]; then
    major=$((10#${BASH_REMATCH[1]}))
    minor=$((10#${BASH_REMATCH[2]}))
    patch=$((10#${BASH_REMATCH[3]}))
  elif [[ "$version" =~ ^([0-9]+)\.([0-9]+)($|-.*) ]]; then
    major=$((10#${BASH_REMATCH[1]}))
    minor=$((10#${BASH_REMATCH[2]}))
    patch=0
  else
    return 1
  fi

  if [ "$minor" -gt 99 ] || [ "$patch" -gt 99 ]; then
    return 2
  fi

  printf '%d\n' "$((major * 10000 + minor * 100 + patch))"
}

calculate_release_version() {
  local target_tag=${1-}
  local gradle_file=${2-}
  local target_base parse_rc
  local gradle_floor_raw gradle_floor
  local tag base ordinal code
  local target_code=
  local floor floor_source
  declare -A release_ordinals=()

  parse_rc=0
  target_base=$(parse_version_base "$target_tag") || parse_rc=$?
  if [ "$parse_rc" -eq 1 ]; then
    echo "ERROR: tag '$target_tag' must start with MAJOR.MINOR[.PATCH]" >&2
    return 1
  elif [ "$parse_rc" -eq 2 ]; then
    echo "ERROR: tag '$target_tag' has a MINOR or PATCH component greater than 99" >&2
    return 1
  fi

  if ! git rev-parse --quiet --verify "refs/tags/$target_tag" >/dev/null; then
    echo "ERROR: tag '$target_tag' does not exist in the checked-out repository" >&2
    return 1
  fi

  if [ ! -f "$gradle_file" ]; then
    echo "ERROR: Gradle file '$gradle_file' does not exist" >&2
    return 1
  fi
  gradle_floor_raw=$(sed -nE \
    's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*$/\1/p' \
    "$gradle_file" | head -n 1)
  if [ -z "$gradle_floor_raw" ]; then
    echo "ERROR: no numeric versionCode assignment found in '$gradle_file'" >&2
    return 1
  fi
  gradle_floor=$((10#$gradle_floor_raw))
  if [ "$gradle_floor" -gt "$ANDROID_VERSION_CODE_MAX" ]; then
    echo "ERROR: Gradle versionCode $gradle_floor exceeds Android's maximum" >&2
    return 1
  fi

  floor=$gradle_floor
  floor_source=$gradle_file

  # Creation time is primary. refname provides a deterministic tie-breaker for
  # tags created in the same second.
  while IFS= read -r tag; do
    [[ "$tag" == v* ]] || continue
    parse_rc=0
    base=$(parse_version_base "$tag") || parse_rc=$?
    [ "$parse_rc" -eq 0 ] || continue

    ordinal=$((${release_ordinals[$base]:-0} + 1))
    release_ordinals[$base]=$ordinal
    if [ "$ordinal" -ge "$RELEASE_SLOTS_PER_BASE" ]; then
      if [ "$tag" = "$target_tag" ]; then
        echo "ERROR: version base '$target_base' has exhausted its 999 release slots" >&2
        return 1
      fi
      continue
    fi

    code=$((base * RELEASE_SLOTS_PER_BASE + ordinal))
    if [ "$code" -gt "$ANDROID_VERSION_CODE_MAX" ]; then
      if [ "$tag" = "$target_tag" ]; then
        echo "ERROR: derived versionCode $code exceeds Android's maximum" >&2
        return 1
      fi
      continue
    fi

    if [ "$code" -gt "$floor" ]; then
      floor=$code
      floor_source="tag $tag"
    fi
    if [ "$tag" = "$target_tag" ]; then
      target_code=$code
    fi
  done < <(git for-each-ref \
    --sort=refname \
    --sort=creatordate \
    --format='%(refname:short)' \
    refs/tags)

  if [ -z "$target_code" ]; then
    echo "ERROR: no versionCode could be derived for tag '$target_tag'" >&2
    return 1
  fi

  printf '%s\t%s\t%s\n' "$target_code" "$floor" "$floor_source"
}

main() {
  if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <tag> <build.gradle>" >&2
    return 2
  fi
  calculate_release_version "$1" "$2"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  main "$@"
fi
