#!/usr/bin/env bash
set -euo pipefail

RELEASE_NAME="${RELEASE_NAME:-mdwiki-api}"
NAMESPACE="${NAMESPACE:-mdwiki}"
PURGE_DATA="${PURGE_DATA:-false}"

echo "Uninstalling release ${RELEASE_NAME} from namespace ${NAMESPACE}"
helm uninstall "${RELEASE_NAME}" --namespace "${NAMESPACE}" || true

if [[ "${PURGE_DATA}" == "true" ]]; then
  echo "PURGE_DATA=true: deleting PVCs for release ${RELEASE_NAME}"
  kubectl delete pvc -n "${NAMESPACE}" \
    -l "app.kubernetes.io/instance=${RELEASE_NAME},app.kubernetes.io/name=mdwiki-api" \
    --ignore-not-found=true
fi

echo "Undeploy completed"
