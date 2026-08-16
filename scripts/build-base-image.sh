#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=gradle-build-fingerprint.sh
source "${ROOT_DIR}/scripts/gradle-build-fingerprint.sh"

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
