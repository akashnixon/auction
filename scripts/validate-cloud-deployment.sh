#!/usr/bin/env bash
set -euo pipefail

cloud_db_url="jdbc:postgresql://auction-691.postgres.database.azure.com:5432/auction?sslmode=require"
old_k8s_db_url="jdbc:postgresql://postgres:5432/auction"

compose_config="$(docker compose config)"
kubernetes_config="$(kubectl kustomize infra/kubernetes)"

require_contains() {
  local haystack="$1"
  local needle="$2"
  local label="$3"

  if ! grep -Fq "$needle" <<<"$haystack"; then
    echo "Expected $label to contain: $needle" >&2
    exit 1
  fi
}

require_not_contains() {
  local haystack="$1"
  local needle="$2"
  local label="$3"

  if grep -Fq "$needle" <<<"$haystack"; then
    echo "Expected $label not to contain: $needle" >&2
    exit 1
  fi
}

require_contains "$compose_config" "$cloud_db_url" "Docker Compose config"
require_contains "$kubernetes_config" "$cloud_db_url" "Kubernetes config"

require_not_contains "$compose_config" "$old_k8s_db_url" "Docker Compose config"
require_not_contains "$kubernetes_config" "$old_k8s_db_url" "Kubernetes config"
require_not_contains "$kubernetes_config" "name: postgres-init" "Kubernetes config"
require_not_contains "$kubernetes_config" "name: postgres-init-sql" "Kubernetes config"

echo "Cloud deployment wiring validation passed."
