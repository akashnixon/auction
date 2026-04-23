# Running The Product

This guide documents the current local and Kubernetes demo flow for the Auction System.

## Prerequisites
- Docker Desktop with Compose
- Optional for Kubernetes: a local cluster such as Docker Desktop Kubernetes, Minikube, or Kind
- Optional for CI/CD: a GitHub repository with Actions enabled

## Local Demo With Docker Compose

### 1. Start everything
From the repository root:

```bash
docker compose up --build -d
```

Or use the Makefile shortcut:

```bash
make compose-up
```

This starts:
- PostgreSQL
- PostgreSQL schema init job
- Redis
- `user-service`
- `auth-service`
- `auction-service`
- `bid-service`
- `notification-service`
- production-style `frontend`

### 2. Open the app
- Frontend: [http://localhost:8080](http://localhost:8080)

### 3. Verify health endpoints
- [http://localhost:3001/health](http://localhost:3001/health)
- [http://localhost:3002/health](http://localhost:3002/health)
- [http://localhost:3003/health](http://localhost:3003/health)
- [http://localhost:3004/health](http://localhost:3004/health)
- [http://localhost:3005/health](http://localhost:3005/health)

### 4. Watch logs if needed
```bash
docker compose logs -f
```

### 5. Stop the demo
```bash
docker compose down
```

If you need a fully clean database reset:

```bash
docker compose down -v
```

## Optional Frontend Dev Mode
If you want hot reload for frontend work:

```bash
docker compose --profile dev up --build frontend-dev user-service auth-service auction-service bid-service notification-service postgres postgres-init redis
```

Then open:
- Frontend dev server: [http://localhost:5173](http://localhost:5173)

## Kubernetes Demo

### 1. Create the runtime secret once
The committed `secret.yaml` is only an example. Create the real AKS secret before deploying:

```bash
kubectl apply -f infra/kubernetes/namespaces.yaml
kubectl -n auction create secret generic auction-secrets \
  --from-literal=DB_USER=auction \
  --from-literal=DB_PASSWORD='<db-password>' \
  --from-literal=JWT_SECRET='<jwt-secret>' \
  --from-literal=POSTGRES_DB=auction \
  --from-literal=POSTGRES_USER=auction \
  --from-literal=POSTGRES_PASSWORD='<postgres-password>'
```

### 2. Deploy or update AKS
Use the deploy script for repeat deployments:

```bash
./scripts/deploy-aks.sh
```

The script validates manifests, reruns the database migration job, restarts deployments so mutable branch images are pulled again, and waits for rollouts.

### 3. Open the ingress host
Use:
- `http://auction.localtest.me`

The ingress routes:
- `/` to the frontend
- `/user-api` to `user-service`
- `/auth-api` to `auth-service`
- `/auction-api` to `auction-service`
- `/bid-api` to `bid-service`
- `/notification-api` to `notification-service`

### 4. Real-time compatibility
The ingress disables proxy buffering and increases timeouts so Server-Sent Events remain usable through `notification-service`.

## CI/CD and GitHub

The repository now includes `.github/workflows/ci.yml`.

On GitHub it will:
- run Maven tests for each backend service
- build the frontend
- build Docker images for every service
- push images to GitHub Container Registry on `main`

## Notes
- The demo configuration uses a `30-second` auction duration by default for faster presentation.
- PostgreSQL is the source of truth for persisted data.
- Redis is used for highest-bid caching in the bid service.
- `notification-service` is not horizontally safe yet because it stores SSE clients and event history in memory per pod.
