# ============================================================
# provider.tf — OpenShift / Kubernetes provider configuration
# ============================================================
#
# The OpenShift provider is a thin wrapper around the Kubernetes
# provider that adds OpenShift-specific resources (Routes, etc.).
# Both providers share the same cluster credentials supplied via
# variables (or TF_VAR_* environment variables at runtime).
# ============================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    # Kubernetes provider — used for core resources (Namespace,
    # ResourceQuota, NetworkPolicy, ServiceAccount, Deployment,
    # Service, PersistentVolumeClaim, ClusterRole, etc.)
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.27"
    }

    # GitHub provider — used to manage GitHub Actions secrets
    github = {
      source  = "integrations/github"
      version = "~> 6.0"
    }

    # Null provider — used for local-exec provisioners
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
  }
}

# ---------------------------------------------------------------------------
# Kubernetes provider — authenticates to the OpenShift API server
# ---------------------------------------------------------------------------
provider "kubernetes" {
  host  = var.cluster_url
  token = var.cluster_token

  # TLS verification is enabled by default.
  # Set insecure = true only for self-signed dev clusters.
  insecure = false
}

