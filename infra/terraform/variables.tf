# ============================================================
# variables.tf — Input variables for the OpenShift environment
# ============================================================
#
# Sensitive values (cluster_url, cluster_token) are intentionally
# left without defaults so they MUST be supplied at runtime via
# environment variables:
#
#   export TF_VAR_cluster_url="https://<your-api-server>:6443"
#   export TF_VAR_cluster_token="<your-service-account-token>"
#
# All other variables have sensible defaults that match the
# target architecture (5 × 16 vCPU / 64 GB RAM worker nodes).
# ============================================================

# ---------------------------------------------------------------------------
# Cluster connectivity
# ---------------------------------------------------------------------------

variable "cluster_url" {
  description = "OpenShift API server URL (e.g. https://api.cluster.example.com:6443). Supplied via TF_VAR_cluster_url at runtime."
  type        = string
  # No default — must be provided at runtime.
}

variable "cluster_token" {
  description = "Bearer token for a service account with cluster-admin or namespace-admin privileges. Supplied via TF_VAR_cluster_token at runtime."
  type        = string
  sensitive   = true
  # No default — must be provided at runtime.
}

# ---------------------------------------------------------------------------
# Environment / namespace names
# ---------------------------------------------------------------------------

variable "staging_namespace" {
  description = "Name of the staging namespace."
  type        = string
  default     = "bob-demo-staging"
}

variable "prod_namespace" {
  description = "Name of the production namespace."
  type        = string
  default     = "bob-demo-prod"
}

# ---------------------------------------------------------------------------
# ResourceQuota — applied identically to both namespaces
# ---------------------------------------------------------------------------

variable "quota_requests_cpu" {
  description = "Aggregate CPU request quota per namespace."
  type        = string
  default     = "4"
}

variable "quota_limits_cpu" {
  description = "Aggregate CPU limit quota per namespace."
  type        = string
  default     = "8"
}

variable "quota_requests_memory" {
  description = "Aggregate memory request quota per namespace."
  type        = string
  default     = "8Gi"
}

variable "quota_limits_memory" {
  description = "Aggregate memory limit quota per namespace."
  type        = string
  default     = "16Gi"
}

# ---------------------------------------------------------------------------
# PostgreSQL — applied identically to both namespaces
# ---------------------------------------------------------------------------

variable "postgres_image" {
  description = "PostgreSQL container image."
  type        = string
  default     = "postgres:15"
}

variable "postgres_pvc_size" {
  description = "Size of the PersistentVolumeClaim for PostgreSQL data."
  type        = string
  default     = "5Gi"
}

variable "postgres_cpu_limit" {
  description = "CPU limit for the PostgreSQL container."
  type        = string
  default     = "1"
}

variable "postgres_memory_limit" {
  description = "Memory limit for the PostgreSQL container."
  type        = string
  default     = "2Gi"
}

variable "postgres_cpu_request" {
  description = "CPU request for the PostgreSQL container."
  type        = string
  default     = "250m"
}

variable "postgres_memory_request" {
  description = "Memory request for the PostgreSQL container."
  type        = string
  default     = "512Mi"
}