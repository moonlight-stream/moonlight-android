#!/usr/bin/env bash

readonly ANDROID_VERSION_CODE_MAX=2100000000

# Validates a version pair before it is read from or written to Gradle.
validate_gradle_version() {
  local version_name=${1-}
  local version_code=${2-}

  if [[ ! "$version_name" =~ ^[0-9A-Za-z][0-9A-Za-z._+-]*$ ]]; then
    echo "ERROR: invalid versionName '$version_name'" >&2
    return 1
  fi
  if [[ ! "$version_code" =~ ^[0-9]+$ ]]; then
    echo "ERROR: versionCode must be numeric, got '$version_code'" >&2
    return 1
  fi

  version_code=$((10#$version_code))
  if [ "$version_code" -lt 1 ] ||
    [ "$version_code" -gt "$ANDROID_VERSION_CODE_MAX" ]; then
    echo "ERROR: versionCode $version_code is outside Android's supported range" >&2
    return 1
  fi
}

# Prints the single canonical versionName/versionCode pair from a Gradle file.
read_gradle_version() {
  local gradle_file=${1-}
  local -a version_names version_codes

  if [ ! -f "$gradle_file" ]; then
    echo "ERROR: Gradle file '$gradle_file' does not exist" >&2
    return 1
  fi

  mapfile -t version_names < <(sed -nE \
    's/^[[:space:]]*versionName[[:space:]]+"([^"]+)"[[:space:]]*$/\1/p' \
    "$gradle_file")
  mapfile -t version_codes < <(sed -nE \
    's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' \
    "$gradle_file")

  if [ "${#version_names[@]}" -ne 1 ] ||
    [ "${#version_codes[@]}" -ne 1 ]; then
    echo "ERROR: expected exactly one versionName and versionCode in '$gradle_file'" >&2
    return 1
  fi

  validate_gradle_version "${version_names[0]}" "${version_codes[0]}" || return
  printf '%s\t%s\n' "${version_names[0]}" "$((10#${version_codes[0]}))"
}

# Atomically replaces the canonical version pair while preserving other lines.
write_gradle_version() {
  local gradle_file=${1-}
  local version_name=${2-}
  local version_code=${3-}
  local current_version
  local temp_file

  validate_gradle_version "$version_name" "$version_code" || return
  current_version=$(read_gradle_version "$gradle_file") || return
  version_code=$((10#$version_code))

  temp_file=$(mktemp "${gradle_file}.tmp.XXXXXX")
  if ! awk \
    -v version_name="$version_name" \
    -v version_code="$version_code" '
      /^[[:space:]]*versionName[[:space:]]+"[^"]*"[[:space:]]*$/ {
        match($0, /^[[:space:]]*/)
        print substr($0, RSTART, RLENGTH) "versionName \"" version_name "\""
        next
      }
      /^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*[0-9]+[[:space:]]*$/ {
        match($0, /^[[:space:]]*/)
        print substr($0, RSTART, RLENGTH) "versionCode = " version_code
        next
      }
      { print }
    ' "$gradle_file" > "$temp_file"; then
    rm -f -- "$temp_file"
    return 1
  fi

  mv -- "$temp_file" "$gradle_file"
  printf 'Updated %s: %s -> %s\t%s\n' \
    "$gradle_file" "$current_version" "$version_name" "$version_code"
}

# Dispatches the read/write command-line interface.
main() {
  local command=${1-}
  shift || true

  case "$command" in
    read)
      [ "$#" -eq 1 ] || {
        echo "Usage: $0 read <build.gradle>" >&2
        return 2
      }
      read_gradle_version "$1"
      ;;
    write)
      [ "$#" -eq 3 ] || {
        echo "Usage: $0 write <build.gradle> <version-name> <version-code>" >&2
        return 2
      }
      write_gradle_version "$1" "$2" "$3"
      ;;
    *)
      echo "Usage: $0 <read|write> ..." >&2
      return 2
      ;;
  esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  main "$@"
fi
