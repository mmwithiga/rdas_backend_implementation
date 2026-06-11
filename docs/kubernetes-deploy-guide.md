# Kubernetes Deployment Guide — RDAS

## Prerequisites

- `kubectl` configured and pointing at your cluster (`kubectl cluster-info`)
- Docker image built and pushed to a registry
- Cluster has a StorageClass available for PVCs

---

## Step 1 — Build and Push Docker Image

```bash
# Build
docker build -t your-dockerhub-username/rdas:1.0.0 .

# Push
docker push your-dockerhub-username/rdas:1.0.0
```

Update `k8s/deployment.yaml` — replace the image field:
```yaml
image: your-dockerhub-username/rdas:1.0.0
```

---

## Step 2 — Configure Secrets

Edit `k8s/secret.yaml` and set the Redis password if required:

```yaml
stringData:
  REDIS_PASSWORD: "your-redis-password"
```

> Never commit secrets with real values to Git. Use a secrets manager (Vault, AWS Secrets Manager, K8s External Secrets) in production.

---

## Step 3 — Apply All Manifests

```bash
# From the project root
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/redis-deployment.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/hpa.yaml
```

Or apply the entire directory at once:

```bash
kubectl apply -f k8s/
```

---

## Step 4 — Verify Deployment

```bash
# Check all pods are Running
kubectl get pods -n rdas

# Expected output:
# NAME                     READY   STATUS    RESTARTS   AGE
# rdas-xxxxxxxxx-xxxxx     1/1     Running   0          2m
# rdas-xxxxxxxxx-xxxxx     1/1     Running   0          2m
# redis-xxxxxxxxx-xxxxx    1/1     Running   0          3m

# Check services
kubectl get svc -n rdas

# Watch rollout progress
kubectl rollout status deployment/rdas -n rdas
```

---

## Step 5 — Verify Application Health

```bash
# Port-forward to test without external access
kubectl port-forward svc/rdas-service 8080:80 -n rdas

# In another terminal
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

curl http://localhost:8080/api/v1/continents
```

---

## Step 6 — Get External IP (LoadBalancer)

```bash
kubectl get svc rdas-service -n rdas

# Wait for EXTERNAL-IP to be assigned (may take 1-2 min on cloud providers)
# NAME           TYPE           CLUSTER-IP     EXTERNAL-IP      PORT(S)
# rdas-service   LoadBalancer   10.100.10.50   203.0.113.5      80:32000/TCP
```

Access the service at `http://EXTERNAL-IP/swagger-ui.html`

---

## Updating the Application

```bash
# Build new image with new tag
docker build -t your-dockerhub-username/rdas:1.1.0 .
docker push your-dockerhub-username/rdas:1.1.0

# Rolling update — zero downtime
kubectl set image deployment/rdas rdas=your-dockerhub-username/rdas:1.1.0 -n rdas

# Watch the rollout
kubectl rollout status deployment/rdas -n rdas

# Rollback if needed
kubectl rollout undo deployment/rdas -n rdas
```

---

## Scaling Manually

```bash
# Scale to 5 replicas
kubectl scale deployment rdas --replicas=5 -n rdas

# HPA handles auto-scaling based on CPU/memory thresholds defined in hpa.yaml
kubectl get hpa -n rdas
```

---

## Teardown

```bash
# Remove everything in the rdas namespace
kubectl delete namespace rdas
```
