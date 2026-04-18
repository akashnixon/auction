# Kubernetes Deployment Notes

This directory contains a cloud-ready Kubernetes layout for the auction platform.

## What is included
- `namespaces.yaml`: dedicated `auction` namespace
- `configmap.yaml`: non-secret runtime configuration
- `secret.yaml`: placeholder secret manifest that must be replaced with real values before deployment
- `database.yaml`: PostgreSQL StatefulSet, service, and schema-init job
- `redis.yaml`: Redis deployment and service
- `*-service.yaml`: deployment and service per backend microservice
- `frontend.yaml`: frontend deployment and service
- `hpa.yaml`: autoscaling policy for `bid-service`
- `ingress.yaml`: frontend root exposure plus API path routing

## Default host
- Frontend and APIs are routed through `http://auction.localtest.me`

`localtest.me` resolves to `127.0.0.1`, which makes it useful for local ingress demos.

## Image names
The manifests are currently pinned to the `deployment` branch images published in GitHub Container Registry:

- `ghcr.io/akashnixon/auction-user-service:deployment`
- `ghcr.io/akashnixon/auction-auth-service:deployment`
- `ghcr.io/akashnixon/auction-auction-service:deployment`
- `ghcr.io/akashnixon/auction-bid-service:deployment`
- `ghcr.io/akashnixon/auction-notification-service:deployment`
- `ghcr.io/akashnixon/auction-frontend:deployment`

If you later merge and publish stable `main` images, you can switch these tags to `latest` or to a commit-specific tag.

## Production notes
- Replace every placeholder in `secret.yaml` before applying these manifests.
- For real cloud deployments, prefer your platform secret manager or an operator such as External Secrets instead of committing live values.
- `AUCTION_DURATION_SECONDS` is set to `300` here to match the project specification. Local Docker demo flows can still use shorter durations.

## Scaling guidance
- Safe to scale horizontally:
  `frontend`, `user-service`, `auth-service`, `bid-service`
- Conditionally safe:
  `auction-service` only if exactly one replica performs scheduler/finalization leadership
- Not safe to scale horizontally in the current implementation:
  `notification-service`, because SSE clients and buffered event history are stored in-memory per pod

## Suggested apply order
```bash
kubectl apply -f infra/kubernetes/namespaces.yaml
kubectl apply -k infra/kubernetes
kubectl -n auction wait --for=condition=complete job/postgres-init --timeout=180s
```

## Secret bootstrap example
Before applying the bundle, either update `secret.yaml` directly for a private environment or create a runtime secret out-of-band:

```bash
kubectl -n auction create secret generic auction-secrets \
  --from-literal=DB_USER=auction \
  --from-literal=DB_PASSWORD='<strong-db-password>' \
  --from-literal=JWT_SECRET='<strong-jwt-secret>' \
  --from-literal=POSTGRES_DB=auction \
  --from-literal=POSTGRES_USER=auction \
  --from-literal=POSTGRES_PASSWORD='<strong-postgres-password>'
```
