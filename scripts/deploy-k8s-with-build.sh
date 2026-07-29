#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./scripts/deploy-k8s-with-build.sh [options]

Options:
  --recreate-db   Delete Postgres StatefulSet + PVC before deploy.
                  Use when you need a clean DB and fresh Liquibase checksums.
  --no-wait-for-postgres-ready
                  Skip waiting for Postgres readiness before API restart.
  --embedding-provider <provider>
                  Set app.embeddingProvider (openai | ollama | lmstudio).
  --openai-api-key <key>
                  Set app.openaiApiKey in Helm values for this deploy.
  -h, --help      Show this help.

Environment:
  BUILD_BASE_IMAGE=auto|false|true
                  auto (default): use mdwiki-api-build-base:<fingerprint> when present,
                  otherwise build Dockerfile.build-base once per Gradle fingerprint.
  DOCKER_BUILD_BASE_IMAGE
                  Override base image tag (default: mdwiki-api-build-base:<fingerprint>).
  BUILD_BASE_IMAGE=true
                  Force rebuild of the Gradle base image before app build.
EOF
}

resolve_embedding_provider_from_values_file() {
  local file_path="$1"
  if [[ ! -f "${file_path}" ]]; then
    return 0
  fi

  awk '
    BEGIN { in_app = 0 }
    /^[[:space:]]*#/ { next }
    /^[^[:space:]]/ {
      if ($0 ~ /^app:[[:space:]]*$/) {
        in_app = 1
        next
      }
      in_app = 0
    }
    in_app == 1 && $0 ~ /^[[:space:]]+embeddingProvider:[[:space:]]*/ {
      line = $0
      sub(/^[[:space:]]+embeddingProvider:[[:space:]]*/, "", line)
      sub(/[[:space:]]*#.*/, "", line)
      gsub(/^["'\'']|["'\'']$/, "", line)
      if (length(line) > 0) {
        print line
        exit
      }
    }
  ' "${file_path}"
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART_DIR="${CHART_DIR:-${ROOT_DIR}/deploy/helm/mdwiki-api}"

RELEASE_NAME="${RELEASE_NAME:-mdwiki-api}"
NAMESPACE="${NAMESPACE:-mdwiki}"
VALUES_FILE="${VALUES_FILE:-}"
TIMEOUT="${TIMEOUT:-5m}"
RECREATE_DB="${RECREATE_DB:-false}"
WAIT_FOR_POSTGRES_READY="${WAIT_FOR_POSTGRES_READY:-true}"
EMBEDDING_PROVIDER_VALUE="${EMBEDDING_PROVIDER_VALUE:-${EMBEDDING_PROVIDER:-}}"
OPENAI_API_KEY_VALUE="${OPENAI_API_KEY_VALUE:-${OPENAI_API_KEY:-}}"

IMAGE_REPOSITORY="${IMAGE_REPOSITORY:-mdwiki-api}"
GIT_SHA="$(git -C "${ROOT_DIR}" rev-parse --short HEAD)"
VERSION_TAG="$(git -C "${ROOT_DIR}" describe --tags --always)"
EXACT_VERSION_TAG="$(git -C "${ROOT_DIR}" describe --tags --exact-match HEAD 2>/dev/null || true)"
IMAGE_TAG="${IMAGE_TAG:-${GIT_SHA}}"
IMAGE_PULL_POLICY="${IMAGE_PULL_POLICY:-IfNotPresent}"
FULL_IMAGE="${IMAGE_REPOSITORY}:${IMAGE_TAG}"

# auto | docker | bootbuildimage
BUILD_METHOD="${BUILD_METHOD:-auto}"
DOCKER_BUILD_BASE_IMAGE="${DOCKER_BUILD_BASE_IMAGE:-mdwiki-api-build-base:latest}"
BUILD_BASE_IMAGE="${BUILD_BASE_IMAGE:-auto}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --recreate-db)
      RECREATE_DB="true"
      shift
      ;;
    --no-wait-for-postgres-ready)
      WAIT_FOR_POSTGRES_READY="false"
      shift
      ;;
    --embedding-provider)
      if [[ $# -lt 2 ]]; then
        echo "--embedding-provider requires a value" >&2
        exit 1
      fi
      EMBEDDING_PROVIDER_VALUE="$2"
      shift 2
      ;;
    --openai-api-key)
      if [[ $# -lt 2 ]]; then
        echo "--openai-api-key requires a value" >&2
        exit 1
      fi
      OPENAI_API_KEY_VALUE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -d "${CHART_DIR}" ]]; then
  echo "Chart directory not found: ${CHART_DIR}" >&2
  exit 1
fi

if [[ "${BUILD_METHOD}" == "auto" ]]; then
  if [[ -f "${ROOT_DIR}/Dockerfile" ]]; then
    BUILD_METHOD="docker"
  else
    BUILD_METHOD="bootbuildimage"
  fi
fi

gradle_build_fingerprint() {
  local files=(
    "${ROOT_DIR}/gradle/wrapper/gradle-wrapper.properties"
    "${ROOT_DIR}/build.gradle.kts"
    "${ROOT_DIR}/settings.gradle.kts"
    "${ROOT_DIR}/gradle.properties"
  )
  cat "${files[@]}" 2>/dev/null | shasum -a 256 | awk '{print substr($1, 1, 12)}'
}

ensure_build_base_image() {
  if [[ "${BUILD_BASE_IMAGE}" == "false" ]]; then
    return 0
  fi

  local fp tagged_image
  fp="$(gradle_build_fingerprint)"
  if [[ "${DOCKER_BUILD_BASE_IMAGE}" == "mdwiki-api-build-base:latest" ]]; then
    tagged_image="mdwiki-api-build-base:${fp}"
    DOCKER_BUILD_BASE_IMAGE="${tagged_image}"
    export DOCKER_BUILD_BASE_IMAGE
  else
    tagged_image="${DOCKER_BUILD_BASE_IMAGE}"
  fi

  if [[ "${BUILD_BASE_IMAGE}" == "auto" ]] && docker image inspect "${tagged_image}" >/dev/null 2>&1; then
    echo "Using existing build base image ${tagged_image}"
    return 0
  fi

  echo "Building Gradle base image ${tagged_image} (fingerprint ${fp})"
  DOCKER_BUILDKIT=1 docker build -f "${ROOT_DIR}/Dockerfile.build-base" -t "${tagged_image}" "${ROOT_DIR}"
  if [[ "${tagged_image}" != "mdwiki-api-build-base:latest" ]]; then
    docker tag "${tagged_image}" mdwiki-api-build-base:latest
  fi
}

echo "Building image ${FULL_IMAGE} using method=${BUILD_METHOD}"
if [[ "${BUILD_METHOD}" == "docker" ]]; then
  ensure_build_base_image
  echo "Building with APP_VERSION_TAG=${VERSION_TAG}"
  DOCKER_BUILDKIT=1 docker build \
    --build-arg "BUILD_BASE_IMAGE=${DOCKER_BUILD_BASE_IMAGE}" \
    --build-arg "APP_GIT_SHA=${GIT_SHA}" \
    --build-arg "APP_VERSION_TAG=${VERSION_TAG}" \
    -t "${FULL_IMAGE}" \
    "${ROOT_DIR}"
  if [[ -n "${EXACT_VERSION_TAG}" ]]; then
    echo "Also tagging image as ${IMAGE_REPOSITORY}:${EXACT_VERSION_TAG}"
    docker tag "${FULL_IMAGE}" "${IMAGE_REPOSITORY}:${EXACT_VERSION_TAG}"
  fi
elif [[ "${BUILD_METHOD}" == "bootbuildimage" ]]; then
  APP_GIT_SHA="${GIT_SHA}" APP_VERSION_TAG="${VERSION_TAG}" \
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" bootBuildImage --imageName="${FULL_IMAGE}"
else
  echo "Unsupported BUILD_METHOD: ${BUILD_METHOD}" >&2
  echo "Supported values: auto, docker, bootbuildimage" >&2
  exit 1
fi

if [[ "${RECREATE_DB}" == "true" ]]; then
  POSTGRES_STATEFULSET_NAME="${POSTGRES_STATEFULSET_NAME:-${RELEASE_NAME}-mdwiki-api-postgres}"
  POSTGRES_PVC_NAME="${POSTGRES_PVC_NAME:-pgdata-${POSTGRES_STATEFULSET_NAME}-0}"

  echo "RECREATE_DB=true: deleting statefulset/${POSTGRES_STATEFULSET_NAME} and pvc/${POSTGRES_PVC_NAME}"
  kubectl -n "${NAMESPACE}" delete statefulset "${POSTGRES_STATEFULSET_NAME}" --ignore-not-found=true --wait=true
  kubectl -n "${NAMESPACE}" delete pvc "${POSTGRES_PVC_NAME}" --ignore-not-found=true
fi

HELM_ARGS=(
  upgrade
  --install
  "${RELEASE_NAME}"
  "${CHART_DIR}"
  --namespace "${NAMESPACE}"
  --create-namespace
  --wait
  --timeout "${TIMEOUT}"
  --set "image.repository=${IMAGE_REPOSITORY}"
  --set "image.tag=${IMAGE_TAG}"
  --set "image.pullPolicy=${IMAGE_PULL_POLICY}"
)

if [[ -n "${VALUES_FILE}" ]]; then
  HELM_ARGS+=(--values "${VALUES_FILE}")
fi

if [[ -z "${EMBEDDING_PROVIDER_VALUE}" && -n "${VALUES_FILE}" ]]; then
  EMBEDDING_PROVIDER_VALUE="$(resolve_embedding_provider_from_values_file "${VALUES_FILE}")"
fi

if [[ -n "${EMBEDDING_PROVIDER_VALUE}" ]]; then
  HELM_ARGS+=(--set-string "app.embeddingProvider=${EMBEDDING_PROVIDER_VALUE}")
fi

if [[ -n "${OPENAI_API_KEY_VALUE}" ]]; then
  HELM_ARGS+=(--set-string "app.openaiApiKey=${OPENAI_API_KEY_VALUE}")
else
  EFFECTIVE_EMBEDDING_PROVIDER="${EMBEDDING_PROVIDER_VALUE:-openai}"
  if [[ "${EFFECTIVE_EMBEDDING_PROVIDER}" == "openai" ]]; then
    echo "Warning: OPENAI API key is empty. embeddingProvider=openai will fail with 401." >&2
  fi
fi

echo "Deploying ${RELEASE_NAME} to namespace ${NAMESPACE}"
helm "${HELM_ARGS[@]}"

DEPLOYMENT_NAME="${DEPLOYMENT_NAME:-${RELEASE_NAME}-mdwiki-api}"
if [[ "${RECREATE_DB}" == "true" ]]; then
  POSTGRES_STATEFULSET_NAME="${POSTGRES_STATEFULSET_NAME:-${RELEASE_NAME}-mdwiki-api-postgres}"
  POSTGRES_POD_NAME="${POSTGRES_POD_NAME:-${POSTGRES_STATEFULSET_NAME}-0}"
  POSTGRES_DB_NAME="${POSTGRES_DB_NAME:-mdwiki}"
  POSTGRES_USER_NAME="${POSTGRES_USER_NAME:-mdwiki}"

  if [[ "${WAIT_FOR_POSTGRES_READY}" == "true" ]]; then
    echo "RECREATE_DB=true: waiting for statefulset/${POSTGRES_STATEFULSET_NAME} rollout"
    kubectl -n "${NAMESPACE}" rollout status "statefulset/${POSTGRES_STATEFULSET_NAME}" --timeout="${TIMEOUT}"

    echo "RECREATE_DB=true: waiting for pod/${POSTGRES_POD_NAME} readiness"
    kubectl -n "${NAMESPACE}" wait --for=condition=Ready "pod/${POSTGRES_POD_NAME}" --timeout="${TIMEOUT}"

    echo "RECREATE_DB=true: waiting for Postgres to accept connections"
    POSTGRES_READY=false
    for attempt in {1..30}; do
      if kubectl -n "${NAMESPACE}" exec "${POSTGRES_POD_NAME}" -- \
        pg_isready -U "${POSTGRES_USER_NAME}" -d "${POSTGRES_DB_NAME}" >/dev/null 2>&1; then
        POSTGRES_READY=true
        break
      fi
      sleep 2
    done

    if [[ "${POSTGRES_READY}" != "true" ]]; then
      echo "Postgres did not become ready in time." >&2
      exit 1
    fi
  fi

  echo "RECREATE_DB=true: forcing deployment/${DEPLOYMENT_NAME} restart to rerun Liquibase on fresh DB"
  kubectl -n "${NAMESPACE}" rollout restart "deployment/${DEPLOYMENT_NAME}"
fi

echo "Waiting for deployment/${DEPLOYMENT_NAME} rollout"
kubectl rollout status "deployment/${DEPLOYMENT_NAME}" -n "${NAMESPACE}" --timeout="${TIMEOUT}"

echo "Deployment image:"
kubectl -n "${NAMESPACE}" get deployment "${DEPLOYMENT_NAME}" \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'

echo "Pod image IDs:"
kubectl -n "${NAMESPACE}" get pods -l app.kubernetes.io/name=mdwiki-api \
  -o jsonpath='{range .items[*]}{.metadata.name}{" => "}{.status.containerStatuses[0].imageID}{"\n"}{end}'

echo "Deployment finished successfully"
