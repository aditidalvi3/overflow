# Kubernetes manifests

Plain manifests (not a Helm chart) — deliberately simple for a project this size; see
[the ADR folder](../adr/) for other trade-offs made along the way. Every value in
`01-config-and-secrets.yaml` is a dev-only placeholder — replace all of it before applying
anywhere but a local `kind`/`minikube` cluster.

## Run locally with `kind`

```bash
kind create cluster --name orderflow

# Build every service's image locally, then load it into the kind cluster (no registry needed
# for local dev — the manifests use imagePullPolicy: IfNotPresent).
for svc in order-service inventory-service payment-service notification-service api-gateway; do
  docker build -t orderflow/$svc:latest ./$svc
  kind load docker-image orderflow/$svc:latest --name orderflow
done

kubectl apply -f k8s/
kubectl -n orderflow get pods -w   # wait for everything to go Running/Ready
```

Kafka and Postgres are `StatefulSet`s and take longest to become ready — the app Deployments'
`readinessProbe`s mean Kubernetes won't route traffic to them before Flyway migrations and Kafka
consumer group registration complete, but pods can still show `0/1 Ready` for the first
30-60 seconds. That's expected.

## Reach the gateway

Without an ingress controller installed:
```bash
kubectl -n orderflow port-forward svc/api-gateway 8080:8080
```
With `ingress-nginx` (`minikube addons enable ingress`, or the kind ingress-nginx quickstart) and
`orderflow.local` pointed at your cluster IP in `/etc/hosts`, the `Ingress` in
`14-api-gateway.yaml` routes `http://orderflow.local/*` to the gateway directly.

## What's real here vs. what's a placeholder

- **Real**: the resource shapes (Deployment/Service/ConfigMap/Secret/StatefulSet/HPA/Ingress),
  the `order-service` HPA scaling 2→6 replicas on 70% CPU utilization, readiness/liveness probes
  wired to each service's `/actuator/health`, database-per-service via `DB_NAME` env overrides
  into one shared Postgres StatefulSet (mirrors [ADR 0004](../adr/0004-database-per-service.md)).
- **Placeholder / your responsibility before a real deployment**: every secret value, the
  `orderflow/*:latest` image references (CI publishes real tags — see
  `.github/workflows/ci.yml` — point these at your registry and a real tag, not `latest`, for
  anything beyond local dev), single-replica Postgres/Kafka with no real persistence/backup
  strategy, no NetworkPolicies restricting which pods can reach which, no PodDisruptionBudgets,
  no resource-limit tuning beyond a reasonable-looking starting guess.
