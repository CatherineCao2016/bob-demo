# ============================================================
# github-secrets.tf — GitHub Actions secrets for CI/CD pipeline
# ============================================================
#
# Provisions the five GitHub Actions secrets required by the
# payment-app CI/CD workflow (.github/workflows/cicd.yml).
#
# Prerequisites
# -------------
# 1. The GitHub Terraform provider must be authenticated.
#    Supply a Personal Access Token (PAT) with `repo` scope via:
#
#      export TF_VAR_github_token="ghp_..."
#
# 2. All secret values must be exported before running
#    `terraform apply`.  See .env.example for the full list.
#
# Usage
# -----
#   source .env.example          # fill in real values first
#   terraform init
#   terraform apply
#
# IMPORTANT: Never commit real secret values to source control.
# ============================================================

# ---------------------------------------------------------------------------
# GitHub provider — added alongside the existing kubernetes/openshift providers
# ---------------------------------------------------------------------------
provider "github" {
  token = var.github_token
  owner = var.github_owner
}

# ---------------------------------------------------------------------------
# Variables — all sensitive values come from TF_VAR_* env vars at runtime
# ---------------------------------------------------------------------------

variable "github_token" {
  description = "GitHub Personal Access Token with 'repo' scope. Supplied via TF_VAR_github_token at runtime."
  type        = string
  sensitive   = true
  # No default — must be provided at runtime.
}

variable "github_owner" {
  description = "GitHub organisation or user that owns the repository (e.g. 'my-org'). Supplied via TF_VAR_github_owner at runtime."
  type        = string
  # No default — must be provided at runtime.
}

variable "github_repository" {
  description = "Name of the GitHub repository where secrets will be created (e.g. 'payment-app'). Supplied via TF_VAR_github_repository at runtime."
  type        = string
  # No default — must be provided at runtime.
}

variable "openshift_server" {
  description = "OpenShift cluster API URL (e.g. https://api.cluster.example.com:6443). Supplied via TF_VAR_openshift_server at runtime."
  type        = string
  # No default — must be provided at runtime.
}

variable "openshift_token" {
  description = "Service account bearer token for OpenShift authentication. Supplied via TF_VAR_openshift_token at runtime."
  type        = string
  sensitive   = true
  # No default — must be provided at runtime.
}

variable "openshift_registry" {
  description = "Internal OpenShift image registry URL. Obtain with: oc get route default-route -n openshift-image-registry --template='{{ .spec.host }}'. Supplied via TF_VAR_openshift_registry at runtime."
  type        = string
  # No default — must be provided at runtime.
}

# namespace_staging and namespace_prod reuse the defaults already defined in
# variables.tf, so they are NOT redeclared here to avoid duplicate variable
# errors.  Their values flow in from var.staging_namespace / var.prod_namespace.

# ---------------------------------------------------------------------------
# GitHub Actions Secrets
# ---------------------------------------------------------------------------

# 1. OPENSHIFT_SERVER — OpenShift cluster API URL
resource "github_actions_secret" "openshift_server" {
  repository      = var.github_repository
  secret_name     = "OPENSHIFT_SERVER"
  plaintext_value = var.openshift_server
}

# 2. OPENSHIFT_TOKEN — Service account token for authentication
resource "github_actions_secret" "openshift_token" {
  repository      = var.github_repository
  secret_name     = "OPENSHIFT_TOKEN"
  plaintext_value = var.openshift_token
}

# 3. OPENSHIFT_REGISTRY — Internal registry URL
#    Retrieve with:
#      oc get route default-route -n openshift-image-registry \
#        --template='{{ .spec.host }}'
resource "github_actions_secret" "openshift_registry" {
  repository      = var.github_repository
  secret_name     = "OPENSHIFT_REGISTRY"
  plaintext_value = var.openshift_registry
}

# 4. NAMESPACE_STAGING — staging namespace name
resource "github_actions_secret" "namespace_staging" {
  repository      = var.github_repository
  secret_name     = "NAMESPACE_STAGING"
  plaintext_value = var.staging_namespace
}

# 5. NAMESPACE_PROD — production namespace name
resource "github_actions_secret" "namespace_prod" {
  repository      = var.github_repository
  secret_name     = "NAMESPACE_PROD"
  plaintext_value = var.prod_namespace
}