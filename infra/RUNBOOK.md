# Infrastructure Runbook — Terraform + Ansible

This runbook covers the end-to-end steps to provision and configure the
OpenShift environment for the payment application.

**Order of operations:**
```
1. Prerequisites check
2. Fill in .env
3. terraform init + apply   ← provisions namespaces, PVCs, postgres Deployment/Service
4. ansible-playbook         ← creates postgres-credentials Secret, app-config ConfigMap,
                               waits for postgres readiness, runs pre-flight health check
5. Verify
```

---

## Prerequisites

### Tools required

| Tool | Minimum version | Install |
|------|----------------|---------|
| Terraform | 1.5.0 | `brew install terraform` |
| kubectl / oc | any | `brew install openshift-cli` |
| Python | 3.9+ | `brew install python` |
| Ansible | 2.14+ | `pip install ansible` |
| kubernetes Python SDK | latest | `pip install kubernetes openshift` |
| kubernetes.core collection | 2.4+ | `ansible-galaxy collection install kubernetes.core` |

### One-time Ansible setup

```bash
pip install ansible kubernetes openshift
ansible-galaxy collection install kubernetes.core
```

---

## Step 1 — Fill in `.env`

All credentials live in one file:

```
payment-app-java17/infra/terraform/.env
```

Open it and confirm/update these values:

| Variable | Description |
|----------|-------------|
| `TF_VAR_github_token` | GitHub PAT with `repo` scope (classic) or `Secrets: Read and write` (fine-grained) |
| `TF_VAR_github_owner` | GitHub username or org (e.g. `CatherineCao2016`) |
| `TF_VAR_github_repository` | Repo name only — **not** the full URL (e.g. `bob-demo`) |
| `TF_VAR_cluster_url` | OpenShift API URL (e.g. `https://api.cluster.example.com:6443`) |
| `TF_VAR_cluster_token` | Service account token — obtain with `oc whoami -t` |
| `TF_VAR_openshift_server` | Same as `TF_VAR_cluster_url` |
| `TF_VAR_openshift_token` | Same as `TF_VAR_cluster_token` |
| `TF_VAR_openshift_registry` | Internal registry route — see below |
| `STAGING_DB_PASSWORD` | Any password string for the staging PostgreSQL instance |
| `PROD_DB_PASSWORD` | Any password string for the production PostgreSQL instance |

**Get the registry route:**
```bash
oc get route default-route \
  -n openshift-image-registry \
  --template='{{ .spec.host }}'
```

**Get the cluster token:**
```bash
oc whoami -t
```

---

## Step 2 — Source `.env`

Do this **once** in your terminal session. It exports all variables for both
Terraform and Ansible:

```bash
cd payment-app-java17/infra/terraform
source .env
```

Verify a few variables loaded correctly:
```bash
echo $TF_VAR_cluster_url
echo $TF_VAR_github_repository
echo $STAGING_DB_PASSWORD
```

---

## Step 3 — Run Terraform

```bash
# Already in payment-app-java17/infra/terraform/

# First time only — download providers
terraform init

# Preview what will be created
terraform plan

# Apply — provisions namespaces, ResourceQuotas, NetworkPolicies,
# ServiceAccounts, PVCs, PostgreSQL Deployment + Service, GitHub Actions secrets
terraform apply -auto-approve
```

**What Terraform creates:**
- Namespaces: `bob-demo-staging`, `bob-demo-prod`
- ResourceQuota per namespace
- NetworkPolicy (default-deny + allow payment→postgres:5432)
- ServiceAccount `payment-service-sa` + RBAC
- PostgreSQL Deployment (`postgres:15`, 1 replica)
- PostgreSQL ClusterIP Service (`postgres-service:5432`)
- PersistentVolumeClaim (`postgres-pvc`, 5Gi)
- GitHub Actions secrets: `OPENSHIFT_SERVER`, `OPENSHIFT_TOKEN`, `OPENSHIFT_REGISTRY`, `NAMESPACE_STAGING`, `NAMESPACE_PROD`

> ⚠️ The postgres pod will **not** become Ready until Ansible creates the
> `postgres-credentials` Secret in Step 4. If Terraform times out waiting
> for the deployment, that is expected — proceed to Step 4.

---

## Step 4 — Run Ansible

```bash
cd ../ansible   # payment-app-java17/infra/ansible/

# Run the full playbook (configures both staging and prod namespaces)
ansible-playbook -i inventory/hosts.yml playbook.yml
```

**What Ansible creates:**
- `postgres-credentials` Secret in each namespace (`DB_USER` + `DB_PASSWORD`)
- `app-config` ConfigMap in each namespace (`SPRING_DATASOURCE_URL`, driver, JPA settings)
- Waits for the PostgreSQL pod to become Ready (polls every 10s, up to 120s)
- Runs a pre-flight health check on `payment-service` if it is already deployed

**To run only staging or only prod:**
```bash
# Staging only
ansible-playbook -i inventory/hosts.yml playbook.yml --limit staging

# Prod only
ansible-playbook -i inventory/hosts.yml playbook.yml --limit production
```

**Dry run (check mode — no changes applied):**
```bash
ansible-playbook -i inventory/hosts.yml playbook.yml --check
```

---

## Step 5 — Verify

```bash
# Check postgres pods are Running in both namespaces
kubectl get pods -n bob-demo-staging
kubectl get pods -n bob-demo-prod

# Confirm the Secret and ConfigMap were created
kubectl get secret postgres-credentials -n bob-demo-staging
kubectl get configmap app-config -n bob-demo-staging
kubectl get secret postgres-credentials -n bob-demo-prod
kubectl get configmap app-config -n bob-demo-prod

# Check Terraform-managed resources
terraform show
```

Expected postgres pod status: `Running` (1/1 Ready)

---

## Re-running after changes

| Scenario | Command |
|----------|---------|
| Terraform config changed | `source .env && terraform apply -auto-approve` |
| DB password rotated | Update `.env`, then `source .env && ansible-playbook -i inventory/hosts.yml playbook.yml` |
| Token expired | `oc login ...`, update `TF_VAR_cluster_token` in `.env`, `source .env` |
| Full teardown | `terraform destroy -auto-approve` (deletes all K8s resources and GitHub secrets) |

---

## Troubleshooting

| Error | Cause | Fix |
|-------|-------|-----|
| `terraform apply` prompts for variables | `.env` not sourced | `source .env` first |
| GitHub 404 on repo | `TF_VAR_github_repository` set to full URL | Use repo name only: `bob-demo` |
| GitHub 403 on secrets | PAT missing `repo` scope or `Secrets: write` | Regenerate PAT with correct permissions |
| Postgres deployment timeout | `postgres-credentials` Secret missing | Run Ansible (Step 4) |
| Ansible `k8s_host` not set | `TF_VAR_cluster_url` not exported | `source .env` before running Ansible |
| `STAGING_DB_PASSWORD` not set | Ansible pre-task validation fails | Set the variable in `.env` and re-source |