#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART_DIR="${ROOT_DIR}/deploy/helm/mdwiki-api"

RELEASE_NAME="${RELEASE_NAME:-mdwiki-api}"
NAMESPACE="${NAMESPACE:-mdwiki}"
VALUES_FILE="${VALUES_FILE:-}"
TIMEOUT="${TIMEOUT:-5m}"

if [[ ! -d "${CHART_DIR}" ]]; then
  echo "Chart directory not found: ${CHART_DIR}" >&2
  exit 1
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
)

if [[ -n "${VALUES_FILE}" ]]; then
  HELM_ARGS+=(--values "${VALUES_FILE}")
fi

echo "Deploying ${RELEASE_NAME} to namespace ${NAMESPACE}"
helm "${HELM_ARGS[@]}"

DEPLOYMENT_NAME="${DEPLOYMENT_NAME:-${RELEASE_NAME}-mdwiki-api}"
echo "Waiting for deployment/${DEPLOYMENT_NAME} rollout"
kubectl rollout status "deployment/${DEPLOYMENT_NAME}" -n "${NAMESPACE}" --timeout="${TIMEOUT}"

echo "Deployment finished successfully"
