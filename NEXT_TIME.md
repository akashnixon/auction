# Next Time Runbook

This file is a practical step-by-step checklist for running the Auction project again locally or on AKS.

Use this when you want to:
- run the app locally with Docker
- push fresh changes to GitHub
- let GitHub Actions build images
- deploy to AKS
- verify the deployment
- clean up Azure resources after testing

## Important Capacity Note

The deployment did **not** run correctly on a 1-node AKS cluster with app routing enabled.

What happened:
- AKS system pods plus app-routing consumed most of the available CPU
- `postgres-0` stayed pending or unstable at first
- `postgres-init` could not finish
- some application pods stayed in `Pending` or `Init` states

What fixed it:
- scaling the AKS node pool from `1` node to `2` nodes

So for this project, the known-good AKS capacity is:
- `Standard_B2s_v2`
- `2` nodes

If you want the smoothest repeat deployment, use 2 nodes from the start or scale to 2 immediately after cluster creation.

## 1. Local Run With Docker Compose

Open PowerShell in the project root:

```powershell
cd C:\Users\ARVIND\OneDrive\Desktop\691\auction
docker compose up --build -d
```

Check status:

```powershell
docker compose ps
```

Open:
- `http://localhost:8080`

Health endpoints:
- `http://localhost:3001/health`
- `http://localhost:3002/health`
- `http://localhost:3003/health`
- `http://localhost:3004/health`
- `http://localhost:3005/health`

Stop the local stack:

```powershell
docker compose down
```

Full reset including volumes:

```powershell
docker compose down -v
```

## 2. Push Changes To GitHub

From the repo root:

```powershell
git status
git add .
git commit -m "Describe your changes"
git push origin deployment
```

If the GitHub Action should run from the `deployment` branch, confirm that `.github/workflows/ci.yml` still includes `deployment` under `push.branches`.

## 3. Check GitHub Actions

Go to the GitHub repo:
- open the `Actions` tab
- open the `CI` workflow

Confirm these jobs pass:
- `backend-tests`
- `frontend-build`
- `docker-images`

Confirm container packages exist in GHCR:
- `auction-user-service`
- `auction-auth-service`
- `auction-auction-service`
- `auction-bid-service`
- `auction-notification-service`
- `auction-frontend`

Current manifests are set to use the `deployment` tag from GHCR.

## 4. Azure Login And Subscription

Open PowerShell and sign in:

```powershell
az login
az account set --subscription "Azure subscription 1"
az account show --output table
```

Use `Azure subscription 1`, not the university production-looking subscription.

## 5. Create Azure Resource Group

```powershell
az group create --name auction-rg --location canadacentral
```

## 6. Register AKS Providers If Needed

These are only needed if Azure says a resource provider is not registered.

```powershell
az provider register --namespace Microsoft.ContainerService
az provider register --namespace Microsoft.Network
az provider register --namespace Microsoft.Compute
az provider register --namespace Microsoft.ManagedIdentity
```

Check one provider:

```powershell
az provider show --namespace Microsoft.ContainerService --query registrationState --output tsv
```

Expected value:
- `Registered`

## 7. Create AKS Cluster

Use the VM size and node count that worked with the student subscription:

```powershell
az aks create `
  --resource-group auction-rg `
  --name auction-aks `
  --location canadacentral `
  --node-count 2 `
  --node-vm-size Standard_B2s_v2 `
  --enable-managed-identity `
  --enable-app-routing `
  --generate-ssh-keys
```

This is the recommended default because 1 node was not enough capacity in practice for:
- AKS system pods
- app routing pods
- postgres
- redis
- all application services

## 8. What `az aks nodepool scale` Is For

This command is used to change the number of worker nodes in an AKS node pool **after the cluster already exists**.

It is a scaling command, not a cluster-creation command.

Use it when:
- the cluster was created with too few nodes
- pods stay `Pending`
- the node does not have enough CPU or memory
- you want to increase or decrease cluster capacity later

Example:

```powershell
az aks nodepool scale `
  --resource-group auction-rg `
  --cluster-name auction-aks `
  --name nodepool1 `
  --node-count 2
```

Meaning:
- `--cluster-name auction-aks` selects the AKS cluster
- `--name nodepool1` selects the node pool inside that cluster
- `--node-count 2` sets the node pool size to 2 nodes

If you create the cluster with `--node-count 2` from the start, you usually do **not** need this scaling command.

## 9. Connect kubectl To AKS

```powershell
az aks get-credentials --resource-group auction-rg --name auction-aks
kubectl get nodes
```

Expected:
- at least 1 node in `Ready` state

## 10. Deploy Kubernetes Manifests

From the repo root:

```powershell
cd C:\Users\ARVIND\OneDrive\Desktop\691\auction
bash ./scripts/deploy-aks.sh
```

What this does:
- validates Kubernetes manifests
- checks that the AKS runtime secret exists
- reruns the `postgres-init` database migration job
- applies the Kubernetes manifests
- restarts the app deployments so they pull the latest GHCR images
- waits for rollouts to complete

## 11. Important Postgres Note

The Postgres manifest includes the Azure disk fix using:
- `PGDATA=/var/lib/postgresql/data/pgdata`

Do not manually rerun the old Postgres fix commands unless you are intentionally rebuilding the database. The deploy script now handles the database migration job safely.

## 12. If The Cluster Needs More Capacity

The 1-node cluster may not have enough CPU for:
- AKS system pods
- app routing
- postgres
- redis
- all application services

If you created the cluster too small or pods remain `Pending`, scale the node pool:

```powershell
az aks nodepool scale `
  --resource-group auction-rg `
  --cluster-name auction-aks `
  --name nodepool1 `
  --node-count 2
```

Then re-check:

```powershell
kubectl get nodes
kubectl get pods -n auction
```

This scale-out step was required in the successful deployment run.

## 13. Verify Deployment

Check everything:

```powershell
kubectl get pods -n auction
kubectl get all -n auction
kubectl get ingress -n auction
kubectl get hpa -n auction
```

Expected:
- all main pods `Running`
- `postgres-init` job `Complete`
- ingress has a public IP
- `bid-service` may scale above 1 replica because of HPA

## 14. Open The Deployed App

Get the ingress IP:

```powershell
kubectl get ingress -n auction
```

Example:
- `20.175.176.128`

Open:

```text
http://<INGRESS-IP>/
```

Health URLs:
- `http://<INGRESS-IP>/user-api/health`
- `http://<INGRESS-IP>/auth-api/health`
- `http://<INGRESS-IP>/auction-api/health`
- `http://<INGRESS-IP>/bid-api/health`
- `http://<INGRESS-IP>/notification-api/health`

## 15. Demo Evidence To Capture

Take screenshots of:
- GitHub Actions passing
- `docker compose ps`
- local frontend working
- `kubectl get all -n auction`
- `kubectl get ingress -n auction`
- `kubectl get hpa -n auction`
- AKS frontend running in browser
- AKS health endpoints

## 16. Cleanup After Testing

When you are done testing, delete the project resource group to stop consuming Azure credit:

```powershell
az group delete --name auction-rg --yes --no-wait
```

Check what remains:

```powershell
az group list --output table
```

It is normal for `NetworkWatcherRG` to remain. Do not delete it unless you are sure you need to.

## 17. Known Good Architecture Notes

- Local demo uses `docker-compose.yml`
- Kubernetes manifests live under `infra/kubernetes/`
- AKS ingress class uses:
  `webapprouting.kubernetes.azure.com`
- GHCR images currently use the `deployment` tag
- `bid-service` has an HPA
- `notification-service` is not a good candidate for horizontal scaling because it keeps SSE state in memory
- `auction-service` should keep a single scheduler leader

## 18. Fast Troubleshooting

If pods are stuck:

```powershell
kubectl get pods -n auction
kubectl describe pod <pod-name> -n auction
kubectl logs <pod-name> -n auction
```

If Postgres init is stuck:

```powershell
kubectl logs job/postgres-init -n auction
kubectl logs postgres-0 -n auction
```

If ingress is not ready:

```powershell
kubectl get ingress -n auction
kubectl describe ingress auction-frontend -n auction
```

If you need a full redeploy in the same cluster:

```powershell
kubectl delete namespace auction
kubectl apply -f infra/kubernetes/namespaces.yaml
kubectl -n auction create secret generic auction-secrets `
  --from-literal=DB_USER=auction `
  --from-literal=DB_PASSWORD='<db-password>' `
  --from-literal=JWT_SECRET='<jwt-secret>' `
  --from-literal=POSTGRES_DB=auction `
  --from-literal=POSTGRES_USER=auction `
  --from-literal=POSTGRES_PASSWORD='<postgres-password>'
bash ./scripts/deploy-aks.sh
```
