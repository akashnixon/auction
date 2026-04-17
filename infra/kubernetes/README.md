# Kubernetes Deployment Notes

This directory contains a demo-ready Kubernetes layout for the auction platform.

## What is included
- `namespaces.yaml`: dedicated `auction` namespace
- `configmap.yaml`: non-secret runtime configuration
- `secret.yaml`: demo secret values for PostgreSQL and JWT
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
Replace every `ghcr.io/your-github-username/...` image with your real registry path before deployment.

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
