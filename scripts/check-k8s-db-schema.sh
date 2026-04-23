#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
K8S_DB_FILE="$ROOT_DIR/infra/kubernetes/database.yaml"

required_patterns=(
  "starting_price NUMERIC(18, 2) NOT NULL DEFAULT 0"
  "ADD COLUMN IF NOT EXISTS starting_price NUMERIC(18, 2) NOT NULL DEFAULT 0"
)

for pattern in "${required_patterns[@]}"; do
  if ! grep -Fq "$pattern" "$K8S_DB_FILE"; then
    echo "FAIL | Kubernetes database schema is missing: $pattern"
    echo "Fix $K8S_DB_FILE before deploying to AKS."
    exit 1
  fi
done

echo "PASS | Kubernetes database schema includes required auction columns."
