# ============================================================
# main.tf — OpenShift infrastructure for the payment application
# ============================================================
#
# Provisions the following resources in BOTH namespaces
# (bob-demo-staging and bob-demo-prod) on a single OpenShift
# cluster (5 worker nodes × 16 vCPU / 64 GB RAM each):
#
#   • Namespace
#   • ResourceQuota
#   • NetworkPolicy  (default-deny + allow payment→postgres:5432)
#   • ServiceAccount (payment-service-sa) + least-privilege RBAC
#   • PostgreSQL Deployment  (postgres:15, 1 replica)
#   • PostgreSQL Service     (ClusterIP, port 5432)
#   • PersistentVolumeClaim  (5 Gi)
#
# Ansible is responsible for Secrets, ConfigMaps, and the
# payment-service Deployment (see IaCArchitecture.md).
# ============================================================

locals {
  # Convenience map — iterate over both environments with for_each
  namespaces = {
    staging = var.staging_namespace
    prod    = var.prod_namespace
  }
}

# ===========================================================
# 1. NAMESPACES
# ===========================================================

resource "kubernetes_namespace" "payment" {
  for_each = local.namespaces

  metadata {
    name = each.value

    labels = {
      "app.kubernetes.io/managed-by" = "terraform"
      "environment"                  = each.key
      "app"                          = "payment-app"
    }

    annotations = {
      "openshift.io/description" = "Payment application — ${each.key} environment"
    }
  }
}

# ===========================================================
# 2. RESOURCE QUOTAS
# ===========================================================

resource "kubernetes_resource_quota" "payment" {
  for_each = local.namespaces

  metadata {
    name      = "payment-quota"
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name
  }

  spec {
    hard = {
      "requests.cpu"    = var.quota_requests_cpu
      "limits.cpu"      = var.quota_limits_cpu
      "requests.memory" = var.quota_requests_memory
      "limits.memory"   = var.quota_limits_memory
    }
  }
}

# ===========================================================
# 3. NETWORK POLICIES
# ===========================================================

# 3a. Default-deny ALL ingress and egress
resource "kubernetes_network_policy" "default_deny" {
  for_each = local.namespaces

  metadata {
    name      = "default-deny-all"
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name
  }

  spec {
    # Empty pod_selector matches every pod in the namespace
    pod_selector {}

    policy_types = ["Ingress", "Egress"]
    # No ingress/egress rules → deny all by default
  }
}

# 3b. Allow ingress to payment-service from within the same namespace only
resource "kubernetes_network_policy" "allow_intra_namespace_to_payment" {
  for_each = local.namespaces

  metadata {
    name      = "allow-intra-namespace-to-payment"
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name
  }

  spec {
    pod_selector {
      match_labels = {
        app = "payment-service"
      }
    }

    ingress {
      # Allow from any pod in the same namespace (no namespaceSelector
      # restriction means only pods in the same namespace match)
      from {
        pod_selector {}
      }
    }

    policy_types = ["Ingress"]
  }
}

# 3c. Allow payment-service to reach postgres-service on port 5432
resource "kubernetes_network_policy" "allow_payment_to_postgres" {
  for_each = local.namespaces

  metadata {
    name      = "allow-payment-to-postgres"
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name
  }

  spec {
    # This policy applies to the payment-service pods (egress side)
    pod_selector {
      match_labels = {
        app = "payment-service"
      }
    }

    egress {
      to {
        pod_selector {
          match_labels = {
            app = "postgres"
          }
        }
      }

      ports {
        protocol = "TCP"
        port     = "5432"
      }
    }

    policy_types = ["Egress"]
  }
}

# 3d. Allow postgres-service to receive connections from payment-service on port 5432
resource "kubernetes_network_policy" "allow_ingress_to_postgres" {
  for_each = local.namespaces

  metadata {
    name      = "allow-ingress-to-postgres"
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name
  }

  spec {
    pod_selector {
      match_labels = {
        app = "postgres"
      }
    }

    ingress {
      from {
        pod_selector {
          match_labels = {
            app = "payment-service"
          }
        }
      }

      ports {
        protocol = "TCP"
        port     = "5432"
      }
    }

    policy_types = ["Ingress"]
  }
}

# ===========================================================
# 4. SERVICE ACCOUNT — payment-service-sa (least privilege)
# ===========================================================

resource "kubernetes_service_account" "payment_sa" {
  for_each = local.namespaces

  metadata {
    name      = "payment-service-sa"
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name

    labels = {
      "app.kubernetes.io/managed-by" = "terraform"
      "app"                          = "payment-service"
    }
  }

  automount_service_account_token = false
}

# Role — minimal permissions required by the payment service
resource "kubernetes_role" "payment_sa_role" {
  for_each = local.namespaces

  metadata {
    name      = "payment-service-role"
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name
  }

  # Allow the payment service to read its own ConfigMap and Secret
  rule {
    api_groups     = [""]
    resources      = ["configmaps", "secrets"]
    resource_names = ["app-config", "postgres-credentials"]
    verbs          = ["get", "watch", "list"]
  }

  # Allow the payment service to read its own ServiceAccount token
  rule {
    api_groups = [""]
    resources  = ["serviceaccounts"]
    verbs      = ["get"]
  }
}

# RoleBinding — bind the role to the service account
resource "kubernetes_role_binding" "payment_sa_binding" {
  for_each = local.namespaces

  metadata {
    name      = "payment-service-rolebinding"
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role.payment_sa_role[each.key].metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account.payment_sa[each.key].metadata[0].name
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name
  }
}

# ===========================================================
# 5. POSTGRESQL — PersistentVolumeClaim
# ===========================================================

resource "kubernetes_persistent_volume_claim" "postgres_pvc" {
  for_each = local.namespaces

  metadata {
    name      = "postgres-pvc"
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name

    labels = {
      "app.kubernetes.io/managed-by" = "terraform"
      "app"                          = "postgres"
      "environment"                  = each.key
    }
  }

  spec {
    access_modes = ["ReadWriteOnce"]

    resources {
      requests = {
        storage = var.postgres_pvc_size
      }
    }
  }

  # Retain the PVC if the Terraform resource is destroyed to prevent
  # accidental data loss.
  lifecycle {
    prevent_destroy = false
  }
}

# ===========================================================
# 6. POSTGRESQL — Deployment (postgres:15, single replica)
# ===========================================================

resource "kubernetes_deployment" "postgres" {
  for_each = local.namespaces

  metadata {
    name      = "postgres"
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name

    labels = {
      "app.kubernetes.io/managed-by" = "terraform"
      "app"                          = "postgres"
      "environment"                  = each.key
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "postgres"
      }
    }

    strategy {
      # Recreate ensures the old pod is terminated before the new one
      # starts — safe for a single-replica stateful workload.
      type = "Recreate"
    }

    template {
      metadata {
        labels = {
          app         = "postgres"
          environment = each.key
        }
      }

      spec {
        service_account_name            = "default"
        automount_service_account_token = false

        container {
          name  = "postgres"
          image = var.postgres_image

          port {
            container_port = 5432
            protocol       = "TCP"
          }

          # Credentials are injected by Ansible via the postgres-credentials Secret
          env {
            name = "POSTGRES_USER"
            value_from {
              secret_key_ref {
                name     = "postgres-credentials"
                key      = "DB_USER"
                optional = false
              }
            }
          }

          env {
            name = "POSTGRES_PASSWORD"
            value_from {
              secret_key_ref {
                name     = "postgres-credentials"
                key      = "DB_PASSWORD"
                optional = false
              }
            }
          }

          env {
            name  = "PGDATA"
            value = "/var/lib/postgresql/data/pgdata"
          }

          resources {
            requests = {
              cpu    = var.postgres_cpu_request
              memory = var.postgres_memory_request
            }
            limits = {
              cpu    = var.postgres_cpu_limit
              memory = var.postgres_memory_limit
            }
          }

          volume_mount {
            name       = "postgres-data"
            mount_path = "/var/lib/postgresql/data"
          }

          liveness_probe {
            exec {
              command = ["pg_isready", "-U", "$(POSTGRES_USER)"]
            }
            initial_delay_seconds = 30
            period_seconds        = 10
            failure_threshold     = 5
          }

          readiness_probe {
            exec {
              command = ["pg_isready", "-U", "$(POSTGRES_USER)"]
            }
            initial_delay_seconds = 10
            period_seconds        = 5
            failure_threshold     = 3
          }
        }

        volume {
          name = "postgres-data"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.postgres_pvc[each.key].metadata[0].name
          }
        }
      }
    }
  }

  depends_on = [
    kubernetes_persistent_volume_claim.postgres_pvc,
    kubernetes_network_policy.allow_ingress_to_postgres,
  ]
}

# ===========================================================
# 7. POSTGRESQL — ClusterIP Service (internal only, port 5432)
# ===========================================================

resource "kubernetes_service" "postgres_service" {
  for_each = local.namespaces

  metadata {
    name      = "postgres-service"
    namespace = kubernetes_namespace.payment[each.key].metadata[0].name

    labels = {
      "app.kubernetes.io/managed-by" = "terraform"
      "app"                          = "postgres"
      "environment"                  = each.key
    }
  }

  spec {
    # ClusterIP is the default; explicitly set for clarity.
    type = "ClusterIP"

    selector = {
      app = "postgres"
    }

    port {
      name        = "postgres"
      port        = 5432
      target_port = 5432
      protocol    = "TCP"
    }
  }

  depends_on = [kubernetes_deployment.postgres]
}