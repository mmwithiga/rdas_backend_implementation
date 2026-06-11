# Kubernetes Troubleshooting Guide — RDAS

## Quick Diagnostics

```bash
# Overall health at a glance
kubectl get all -n rdas

# Pod status
kubectl get pods -n rdas -o wide

# Events (first place to look for errors)
kubectl get events -n rdas --sort-by='.lastTimestamp'
```

---

## Problem: Pod Stuck in `CrashLoopBackOff`

**Symptoms:** Pod keeps restarting, STATUS shows `CrashLoopBackOff`

**Steps:**

```bash
# 1. Get the pod name
kubectl get pods -n rdas

# 2. Read the logs
kubectl logs <pod-name> -n rdas

# 3. Read logs from previous (crashed) container
kubectl logs <pod-name> -n rdas --previous

# 4. Describe the pod for event details
kubectl describe pod <pod-name> -n rdas
```

**Common causes:**

| Log message | Cause | Fix |
|-------------|-------|-----|
| `Connection refused` to Redis | Redis service not running or wrong host | Check `redis-service` is up: `kubectl get svc -n rdas` |
| `Unable to connect to SOAP service` | Network policy blocking egress | Ensure egress to external SOAP URL is allowed |
| `OOMKilled` | Memory limit too low | Increase `resources.limits.memory` in `deployment.yaml` |
| `application.yml not found` | ConfigMap not mounted | Re-apply configmap: `kubectl apply -f k8s/configmap.yaml` |

---

## Problem: Pod Stuck in `Pending`

**Symptoms:** Pod never starts, STATUS shows `Pending`

```bash
kubectl describe pod <pod-name> -n rdas
# Look at Events section at the bottom
```

**Common causes:**

- `Insufficient cpu` / `Insufficient memory` — cluster nodes don't have enough resources. Scale node pool or reduce resource requests.
- `PersistentVolumeClaim not bound` — no StorageClass available. Check: `kubectl get pvc -n rdas`
- `ImagePullBackOff` — wrong image name or missing registry credentials. Verify image name in `deployment.yaml`.

---

## Problem: `ImagePullBackOff`

```bash
kubectl describe pod <pod-name> -n rdas | grep -A5 "Events"
```

**Fix:**
```bash
# Verify image exists
docker pull your-dockerhub-username/rdas:1.0.0

# If private registry, create imagePullSecret
kubectl create secret docker-registry regcred \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=YOUR_USERNAME \
  --docker-password=YOUR_PASSWORD \
  -n rdas
```

Then add to `deployment.yaml` under `spec.template.spec`:
```yaml
imagePullSecrets:
  - name: regcred
```

---

## Problem: Redis Connection Refused

```bash
# Check Redis pod is running
kubectl get pods -n rdas -l app=redis

# Check Redis service
kubectl get svc redis-service -n rdas

# Test Redis connectivity from within RDAS pod
kubectl exec -it <rdas-pod-name> -n rdas -- sh
# Inside pod:
wget -qO- redis-service:6379
```

**Verify ConfigMap has correct Redis host:**
```bash
kubectl get configmap rdas-config -n rdas -o yaml
# REDIS_HOST should be "redis-service"
```

---

## Problem: SOAP Circuit Breaker Open

**Symptoms:** All country endpoints return `503 Service Temporarily Unavailable`

```bash
# Check circuit breaker state via actuator
kubectl port-forward svc/rdas-service 8080:80 -n rdas
curl http://localhost:8080/actuator/health | jq '.components.circuitBreakers'
```

**Expected output when open:**
```json
{
  "soapService": {
    "status": "CIRCUIT_OPEN",
    "details": { "failureRate": "60.0%" }
  }
}
```

**Fix steps:**
1. Verify SOAP service is reachable: `curl http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL`
2. Once SOAP recovers, circuit will transition to HALF_OPEN automatically after `waitDurationInOpenState` (60s)
3. To force cache refresh after SOAP recovers: `curl -X POST http://localhost:8080/api/v1/admin/cache/refresh`

---

## Problem: Stale / Missing Data After Deployment

```bash
# Force cache refresh
kubectl port-forward svc/rdas-service 8080:80 -n rdas
curl -X POST http://localhost:8080/api/v1/admin/cache/refresh
```

Or delete Redis data to force full re-fetch:
```bash
kubectl exec -it <redis-pod-name> -n rdas -- redis-cli FLUSHALL
```

RDAS will re-warm on next request.

---

## Problem: High Latency / Slow Responses

```bash
# Check HPA — is it scaling?
kubectl get hpa rdas-hpa -n rdas

# Check resource usage
kubectl top pods -n rdas
kubectl top nodes

# Check Redis memory
kubectl exec -it <redis-pod-name> -n rdas -- redis-cli INFO memory | grep used_memory_human
```

**If Redis memory is high:** increase the PVC size or reduce TTL for less-critical caches.

**If CPU is maxed:** HPA should auto-scale. If not, check:
```bash
kubectl describe hpa rdas-hpa -n rdas
# Look for "ScalingLimited" or metric errors
```

---

## Problem: Deployment Rollout Stuck

```bash
kubectl rollout status deployment/rdas -n rdas
# If stuck, check pod events
kubectl describe pod <new-pod-name> -n rdas

# Rollback
kubectl rollout undo deployment/rdas -n rdas
kubectl rollout status deployment/rdas -n rdas
```

---

## Useful Commands Reference

```bash
# Live log streaming
kubectl logs -f deployment/rdas -n rdas

# Tail logs from all RDAS pods
kubectl logs -l app=rdas -n rdas --tail=100

# Exec into a running pod
kubectl exec -it <pod-name> -n rdas -- sh

# Check all resource usage
kubectl top pods -n rdas

# Force pod restart (by deleting — deployment recreates it)
kubectl delete pod <pod-name> -n rdas

# Check ConfigMap values
kubectl get configmap rdas-config -n rdas -o yaml

# Check Secret (values are base64 encoded)
kubectl get secret rdas-secrets -n rdas -o yaml

# Full health check
curl http://localhost:8080/actuator/health | jq
curl http://localhost:8080/actuator/metrics | jq
curl http://localhost:8080/actuator/caches | jq
```

---

## Monitoring Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Overall health (Redis, circuit breaker) |
| `/actuator/health/liveness` | K8s liveness probe |
| `/actuator/health/readiness` | K8s readiness probe |
| `/actuator/metrics` | All Micrometer metrics |
| `/actuator/prometheus` | Prometheus scrape endpoint |
| `/actuator/caches` | Current cache state |
| `/actuator/circuitbreakers` | Circuit breaker state |
