#!/usr/bin/env bash
# Fingerprint of Gradle *dependency* inputs for mdwiki-api-build-base.
# Project `version =` / `version=` is release metadata and must not bust the cache.
# Plugin versions (e.g. `id("...") version "4.0.5"`) stay in the hash.
# Usage: source this file, then gradle_build_fingerprint [root_dir]

gradle_build_fingerprint() {
  local root="${1:-${ROOT_DIR:?ROOT_DIR or fingerprint root required}}"
  {
    cat "${root}/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null || true
    awk '/^[[:space:]]*version[[:space:]]*=/ { next } { print }' \
      "${root}/build.gradle.kts" 2>/dev/null || true
    cat "${root}/settings.gradle.kts" 2>/dev/null || true
    awk '/^[[:space:]]*version[[:space:]]*=/ { next } { print }' \
      "${root}/gradle.properties" 2>/dev/null || true
  } | shasum -a 256 | awk '{print substr($1, 1, 12)}'
}
