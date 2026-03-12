#!/bin/bash
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Генерация секретов для Kubernetes${NC}"
echo -e "${GREEN}========================================${NC}"

mkdir -p overlays/dev
mkdir -p overlays/prod
mkdir -p overlays/staging
mkdir -p components/secrets-from-env
mkdir -p base/secrets

generate_secret() {
    openssl rand -base64 32 | tr -d '\n' | tr -d '=' | tr '/+' '_-'
}

create_env_file() {
    local env=$1
    local file="overlays/${env}/.env.${env}"

    cat > "$file" << EOF
# AWS/LocalStack
AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-test}
AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-test}
AWS_REGION=us-east-1
AWS_ENDPOINT_URL=http://localstack.cloud.svc.cluster.local:4566

# OpenSearch
OPENSEARCH_ADMIN_PASSWORD=${OPENSEARCH_ADMIN_PASSWORD:-$(generate_secret)}
OPENSEARCH_USER=admin

OPENSEARCH_ROOT_CA=$(cat ../opensearch_certs/root-ca.pem | base64 -w 0)
OPENSEARCH_NODE_CERT=$(cat ../opensearch_certs/node.pem | base64 -w 0)
OPENSEARCH_NODE_KEY=$(cat ../opensearch_certs/node-key.pem | base64 -w 0)
OPENSEARCH_ADMIN_CERT=$(cat ../opensearch_certs/admin.pem | base64 -w 0)
OPENSEARCH_ADMIN_KEY=$(cat ../opensearch_certs/admin-key.pem | base64 -w 0)

# NLP Service
NLP_GRPC_API_KEY=${NLP_GRPC_API_KEY:-$(generate_secret)}
NLP_MODE=ollama-rag
OLLAMA_MODEL=${OLLAMA_MODEL:-llama3.2:latest}

# PostgreSQL
POSTGRES_USER=postgres
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-$(generate_secret)}
POSTGRES_DB=voice_reminder
DB_SCHEMA=voice_schema

# Redis
REDIS_PASSWORD=${REDIS_PASSWORD:-$(generate_secret)}
REDIS_USE_SSL=true

# JWT
JWT_SECRET=${JWT_SECRET:-$(generate_secret)}
EOF

    if [ "$env" = "prod" ]; then
        cat >> "$file" << EOF
# SMTP Production
SMTP_HOST=${SMTP_HOST:-smtp.gmail.com}
SMTP_PORT=${SMTP_PORT:-587}
SMTP_USERNAME=${SMTP_USERNAME:-}
SMTP_PASSWORD=${SMTP_PASSWORD:-}
FROM_EMAIL=${FROM_EMAIL:-}
NOTIFICATION_EMAIL=${NOTIFICATION_EMAIL:-}
EOF
    else
        cat >> "$file" << EOF
# SMTP Development/Staging
SMTP_HOST=${SMTP_HOST:-mailhog}
SMTP_PORT=${SMTP_PORT:-1025}
SMTP_USERNAME=${SMTP_USERNAME:-test}
SMTP_PASSWORD=${SMTP_PASSWORD:-test}
FROM_EMAIL=${FROM_EMAIL:-noreply@example.com}
NOTIFICATION_EMAIL=${NOTIFICATION_EMAIL:-admin@example.com}
EOF
    fi

    cat >> "$file" << EOF

# Grafana
GRAFANA_USER=admin
GRAFANA_PASSWORD=${GRAFANA_PASSWORD:-admin}

# LocalStack
LOCALSTACK_API_KEY=${LOCALSTACK_API_KEY:-}
EOF

    echo -e "${GREEN}Created ${file}${NC}"
}

create_env_file "dev"
create_env_file "staging"
create_env_file "prod"

echo -e "${YELLOW}Копирование сертификатов...${NC}"

for env in dev staging prod; do
    mkdir -p overlays/${env}/opensearch_certs
    mkdir -p overlays/${env}/redis/certs
    mkdir -p overlays/${env}/postgres-init
    mkdir -p overlays/${env}/src/main/resources/db/migration
    mkdir -p overlays/${env}/redis/config

    [ -d "../opensearch_certs" ] && cp -r ../opensearch_certs/* overlays/${env}/opensearch_certs/ 2>/dev/null || true
    [ -d "../redis/certs" ] && cp -r ../redis/certs/* overlays/${env}/redis/certs/ 2>/dev/null || true
    [ -d "../postgres-init" ] && cp -r ../postgres-init/* overlays/${env}/postgres-init/ 2>/dev/null || true
    [ -d "../src/main/resources/db/migration" ] && cp -r ../src/main/resources/db/migration/* overlays/${env}/src/main/resources/db/migration/ 2>/dev/null || true
    [ -d "../redis/config" ] && cp -r ../redis/config/* overlays/${env}/redis/config/ 2>/dev/null || true
done

echo -e "${YELLOW}Создание kustomization файлов с подстановкой переменных...${NC}"

for env in dev staging prod; do
    (
        set -a
        source overlays/${env}/.env.${env}
        set +a

        cat > overlays/${env}/kustomization.yaml << EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: app

resources:
  - ../../base

components:
  - ../../components/secrets-from-env
  - ../../components/secrets-from-env/database-secrets-component.yaml
  - ../../components/secrets-from-env/cloud-secrets-component.yaml
  - ../../components/secrets-from-env/monitoring-secrets-component.yaml

configMapGenerator:
  - name: app-env
    literals:
      - ENVIRONMENT=${env}
      - LOG_LEVEL=${LOG_LEVEL:-INFO}
      - AWS_REGION=${AWS_REGION}
      - AWS_ENDPOINT_URL=${AWS_ENDPOINT_URL}
      - OPENSEARCH_HOST=${OPENSEARCH_HOST:-localstack}
      - OPENSEARCH_USER=${OPENSEARCH_USER}
      - NLP_MODE=${NLP_MODE}
      - OLLAMA_HOST=${OLLAMA_HOST:-ollama}
      - OLLAMA_MODEL=${OLLAMA_MODEL}
      - POSTGRES_USER=${POSTGRES_USER}
      - POSTGRES_DB=${POSTGRES_DB}
      - DB_SCHEMA=${DB_SCHEMA}
      - REDIS_HOST=${REDIS_HOST:-redis}
      - REDIS_USE_SSL=${REDIS_USE_SSL}
      - GRAFANA_USER=${GRAFANA_USER}
      - SMTP_HOST=${SMTP_HOST}
      - SMTP_PORT=${SMTP_PORT}
      - SMTP_USERNAME=${SMTP_USERNAME}
      - FROM_EMAIL=${FROM_EMAIL}
      - NOTIFICATION_EMAIL=${NOTIFICATION_EMAIL}

secretGenerator:
  - name: tls-certs
    files:
      - opensearch_certs/root-ca.pem
      - opensearch_certs/node.pem
      - opensearch_certs/node-key.pem
      - redis/certs/root-ca.pem
      - redis/certs/redis.pem
      - redis/certs/redis-key.pem
  - name: postgres-init-scripts
    files:
      - postgres-init/01-create-schema.sql
      - postgres-init/02-create-extensions.sql
  - name: flyway-migrations
    files:
      - src/main/resources/db/migration/V1__create_users_table.sql
      - src/main/resources/db/migration/V2__insert_default_users.sql
  - name: redis-config
    files:
      - redis/config/redis.conf

generatorOptions:
  disableNameSuffixHash: $([ "$env" = "prod" ] && echo "false" || echo "true")
  labels:
    type: generated
    environment: ${env}
  annotations:
    note: generated-by-kustomize
    generated-at: $(date -Iseconds)
EOF
    )

    echo -e "${GREEN}Created overlays/${env}/kustomization.yaml${NC}"
done

cat > apply.sh << 'EOF'
#!/bin/bash

ENV=${1:-dev}
NAMESPACE=${2:-app}

echo "Applying configuration for environment: $ENV"

if [ -f "overlays/$ENV/.env.$ENV" ]; then
    echo "Loading environment variables from overlays/$ENV/.env.$ENV"
    set -a
    source "overlays/$ENV/.env.$ENV"
    set +a
else
    echo "ERROR: Environment file overlays/$ENV/.env.$ENV not found!"
    exit 1
fi

echo "Generating and applying kustomize configuration..."
kubectl kustomize "overlays/$ENV" | envsubst | kubectl apply -f -

if [ $? -eq 0 ]; then
    echo "Successfully applied $ENV configuration"

    echo ""
    echo "Created secrets in all namespaces:"
    echo "----------------------------------------"
    kubectl get secrets -A | grep -E 'postgres|redis|secrets' | head -20
else
    echo "Failed to apply configuration"
    exit 1
fi
EOF

chmod +x apply.sh

cat > README.md << 'EOF'
# Kustomize Configuration for Voice Reminder

## Structure

- `base/` - базовые манифесты с плейсхолдерами
- `overlays/` - настройки для разных окружений (dev/staging/prod)
- `components/` - переиспользуемые компоненты

## Usage

### Подготовка секретов
```bash
cd /vagrant/kustomize
./scripts/generate-secrets-k3s.sh  # создает kustomize файлы
kubectl apply -k overlays/dev
EOF

urlencode() {
    local string="${1}"
    local strlen=${#string}
    local encoded=""
    local pos c o

    for (( pos=0 ; pos<strlen ; pos++ )); do
        c=${string:$pos:1}
        case "$c" in
            [-_.~a-zA-Z0-9] ) o="${c}" ;;
            * )               printf -v o '%%%02x' "'$c"
        esac
        encoded+="${o}"
    done
    echo "${encoded}"
}