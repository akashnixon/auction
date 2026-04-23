#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="${NAMESPACE:-auction}"
KUSTOMIZE_DIR="${KUSTOMIZE_DIR:-$ROOT_DIR/infra/kubernetes}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-300s}"

DEPLOYMENTS=(
  user-service
  auth-service
  auction-service
  bid-service
  notification-service
  frontend
)

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "FAIL | Missing required command: $1"
    exit 1
  fi
}

log_step() {
  echo
  echo "== $1 =="
}

require_command kubectl

log_step "Kubernetes Context"
kubectl config current-context

log_step "Validate Kubernetes Manifests"
kubectl kustomize "$KUSTOMIZE_DIR" >/dev/null
"$ROOT_DIR/scripts/check-k8s-db-schema.sh"

log_step "Ensure Namespace"
kubectl apply -f "$KUSTOMIZE_DIR/namespaces.yaml"

log_step "Check Runtime Secret"
if ! kubectl -n "$NAMESPACE" get secret auction-secrets >/dev/null 2>&1; then
  echo "FAIL | Missing secret auction-secrets in namespace $NAMESPACE."
  echo "Create it once with real values before deploying:"
  echo "kubectl -n $NAMESPACE create secret generic auction-secrets \\"
  echo "  --from-literal=DB_USER=auction \\"
  echo "  --from-literal=DB_PASSWORD='<db-password>' \\"
  echo "  --from-literal=JWT_SECRET='<jwt-secret>' \\"
  echo "  --from-literal=POSTGRES_DB=auction \\"
  echo "  --from-literal=POSTGRES_USER=auction \\"
  echo "  --from-literal=POSTGRES_PASSWORD='<postgres-password>'"
  exit 1
fi

log_step "Recreate Database Migration Job"
kubectl -n "$NAMESPACE" delete job postgres-init --ignore-not-found

log_step "Apply Application Manifests"
kubectl apply -k "$KUSTOMIZE_DIR"

log_step "Wait For Database Migration"
kubectl -n "$NAMESPACE" wait --for=condition=complete job/postgres-init --timeout="$WAIT_TIMEOUT"

log_step "Restart Deployments To Pull Latest Images"
for deployment in "${DEPLOYMENTS[@]}"; do
  kubectl -n "$NAMESPACE" rollout restart "deployment/$deployment"
done

log_step "Wait For Rollouts"
for deployment in "${DEPLOYMENTS[@]}"; do
  kubectl -n "$NAMESPACE" rollout status "deployment/$deployment" --timeout="$WAIT_TIMEOUT"
done

log_step "Current AKS Status"
kubectl -n "$NAMESPACE" get pods,svc,ingress

echo
echo "PASS | AKS deployment completed."
