# ============================================================
# outputs.tf — Useful values exposed after terraform apply
# ============================================================
#
# Outputs are consumed by downstream automation (e.g. Ansible,
# CI/CD pipelines) to discover resource names and endpoints
# without hard-coding them.
# ============================================================

# ---------------------------------------------------------------------------
# Namespace names
# ---------------------------------------------------------------------------

output "staging_namespace" {
  description = "Name of the staging namespace provisioned on the OpenShift cluster."
  value       = kubernetes_namespace.payment["staging"].metadata[0].name
}

output "prod_namespace" {
  description = "Name of the production namespace provisioned on the OpenShift cluster."
  value       = kubernetes_namespace.payment["prod"].metadata[0].name
}

# ---------------------------------------------------------------------------
# Cluster endpoint
# ---------------------------------------------------------------------------

output "cluster_endpoint" {
  description = "OpenShift API server URL used by this Terraform configuration."
  value       = var.cluster_url
}

# ---------------------------------------------------------------------------
# PostgreSQL service endpoints (ClusterIP DNS names, internal only)
# ---------------------------------------------------------------------------

output "postgres_service_endpoint_staging" {
  description = "Internal DNS endpoint for the PostgreSQL ClusterIP service in the staging namespace. Reachable only from within the cluster."
  value       = "postgres-service.${kubernetes_namespace.payment["staging"].metadata[0].name}.svc.cluster.local:5432"
}

output "postgres_service_endpoint_prod" {
  description = "Internal DNS endpoint for the PostgreSQL ClusterIP service in the production namespace. Reachable only from within the cluster."
  value       = "postgres-service.${kubernetes_namespace.payment["prod"].metadata[0].name}.svc.cluster.local:5432"
}

# ---------------------------------------------------------------------------
# PostgreSQL service cluster IPs (resolved at apply time)
# ---------------------------------------------------------------------------

output "postgres_cluster_ip_staging" {
  description = "ClusterIP assigned to postgres-service in the staging namespace."
  value       = kubernetes_service.postgres_service["staging"].spec[0].cluster_ip
}

output "postgres_cluster_ip_prod" {
  description = "ClusterIP assigned to postgres-service in the production namespace."
  value       = kubernetes_service.postgres_service["prod"].spec[0].cluster_ip
}

# ---------------------------------------------------------------------------
# ServiceAccount names
# ---------------------------------------------------------------------------

output "payment_service_account_staging" {
  description = "Name of the least-privilege ServiceAccount in the staging namespace."
  value       = kubernetes_service_account.payment_sa["staging"].metadata[0].name
}

output "payment_service_account_prod" {
  description = "Name of the least-privilege ServiceAccount in the production namespace."
  value       = kubernetes_service_account.payment_sa["prod"].metadata[0].name
}

# ---------------------------------------------------------------------------
# PersistentVolumeClaim names
# ---------------------------------------------------------------------------

output "postgres_pvc_staging" {
  description = "Name of the PostgreSQL PersistentVolumeClaim in the staging namespace."
  value       = kubernetes_persistent_volume_claim.postgres_pvc["staging"].metadata[0].name
}

output "postgres_pvc_prod" {
  description = "Name of the PostgreSQL PersistentVolumeClaim in the production namespace."
  value       = kubernetes_persistent_volume_claim.postgres_pvc["prod"].metadata[0].name
}