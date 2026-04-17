# Deployment Guide

This guide is meant for a first deployment pass and matches the repo changes in this branch.

## Recommended order
1. Get the Docker Compose demo working locally.
2. Push the repo to GitHub.
3. Let GitHub Actions build and publish container images to GHCR.
4. Replace the placeholder image names in `infra/kubernetes/`.
5. Deploy the Kubernetes manifests to your cluster.
6. Verify health endpoints, frontend access, database connectivity, Redis connectivity, and live notifications.

## Which is easier: push manually or use GitHub Actions?

For this project, GitHub Actions is easier.

Why:
- it builds all six images the same way every time
- it avoids manual Docker push mistakes
- it gives you a visible CI history for your course/demo
- GitHub Container Registry works well with Actions using the built-in `GITHUB_TOKEN`

Manual pushing is still possible, but it is more repetitive and easier to misconfigure when you are new.

## What GitHub Actions does not do automatically

The workflow in this repo does not perform a cloud deployment yet because that step depends on your cloud provider and credentials.

You still need to choose one of:
- AKS
- EKS
- GKE
- DigitalOcean Kubernetes
- another Kubernetes cluster used by your course/team

## Before first cloud deploy

Make sure you can answer these:
- Which cluster are we using?
- Which registry path will hold our images?
- Which DNS name or ingress host will the frontend use?
- Where will PostgreSQL and Redis run?

For this repo right now:
- Kubernetes demo uses in-cluster PostgreSQL and Redis
- frontend ingress host defaults to `auction.localtest.me`
- backend APIs are exposed through ingress path prefixes

## Horizontal scaling summary

Safe now:
- `frontend`
- `user-service`
- `auth-service`
- `bid-service`

Use caution:
- `auction-service`
  Only one replica should act as the scheduler/finalizer leader in the current implementation.

Do not horizontally scale yet:
- `notification-service`
  It keeps SSE subscribers and recent events in local memory, so multiple replicas would split clients and event history.

## Demo evidence to capture

For your final submission/demo, collect:
- screenshot of `docker compose ps`
- screenshot or terminal output of health endpoints
- screenshot of GitHub Actions passing
- screenshot of running pods and services in Kubernetes
- screenshot of ingress host serving the frontend
- proof that `bid-service` scales, such as `kubectl get hpa -n auction`
- screenshot of live notification behavior while bidding
