#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

gradle_build_fingerprint() {
  local files=(
    "${ROOT_DIR}/gradle/wrapper/gradle-wrapper.properties"
    "${ROOT_DIR}/build.gradle.kts"
    "${ROOT_DIR}/settings.gradle.kts"
    "${ROOT_DIR}/gradle.properties"
  )
  cat "${files[@]}" 2>/dev/null | shasum -a 256 | awk '{print substr($1, 1, 12)}'
}

FP="$(gradle_build_fingerprint)"
IMAGE="${DOCKER_BUILD_BASE_IMAGE:-mdwiki-api-build-base:${FP}}"

echo "Building Gradle base image: ${IMAGE}"
DOCKER_BUILDKIT=1 docker build -f "${ROOT_DIR}/Dockerfile.build-base" -t "${IMAGE}" "${ROOT_DIR}"

if [[ "${TAG_LATEST:-true}" == "true" ]]; then
  docker tag "${IMAGE}" mdwiki-api-build-base:latest
  echo "Also tagged as mdwiki-api-build-base:latest"
fi

echo "Done. Build app with:"
echo "  DOCKER_BUILD_BASE_IMAGE=${IMAGE} docker build -t mdwiki-api:local ${ROOT_DIR}"
