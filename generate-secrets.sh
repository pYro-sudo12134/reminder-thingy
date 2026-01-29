#!/bin/bash
set -e

mkdir -p secrets

openssl rand -base64 32 > secrets/nlp_grpc_key.txt
openssl rand -base64 32 > secrets/jwt_secret.txt
openssl rand -base64 24 > secrets/service_token.txt

echo "test" > secrets/aws_access_key.txt
echo "test" > secrets/aws_secret_key.txt

cat > secrets/secrets.json << EOF
{
  "NLP_GRPC_API_KEY": "$(cat secrets/nlp_grpc_key.txt)",
  "JWT_SECRET": "$(cat secrets/jwt_secret.txt)",
  "SERVICE_TOKEN": "$(cat secrets/service_token.txt)",
  "NLP_SERVICE_HOST": "nlp-service",
  "NLP_SERVICE_PORT": "50051",
  "GRPC_USE_TLS": "false",
  "WS_PORT": "8090"
}
EOF

cat > .env << EOF
# ==============================================
# Voice Reminder - Environment Variables
# ==============================================

# AWS Configuration
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
AWS_REGION=us-east-1
AWS_ENDPOINT_URL=http://localstack:4566

# NLP Service Configuration
NLP_SERVICE_HOST=nlp-service
NLP_SERVICE_PORT=50051
GRPC_USE_TLS=false

# Application Configuration
ENVIRONMENT_NAME=dev
WS_PORT=8090
USE_LOCAL_SECRETS=true

# Secrets
NLP_GRPC_API_KEY=$(cat secrets/nlp_grpc_key.txt)
JWT_SECRET=$(cat secrets/jwt_secret.txt)
SERVICE_TOKEN=$(cat secrets/service_token.txt)

# Logging
LOG_LEVEL=INFO

# Health Checks
HEALTH_CHECK_ENABLED=true
EOF

cat > secrets/application.properties << EOF
# Application Properties
app.name=VoiceReminder
app.version=1.0.0
app.environment=\${ENVIRONMENT_NAME:dev}

# NLP Service
nlp.service.host=\${NLP_SERVICE_HOST:nlp-service}
nlp.service.port=\${NLP_SERVICE_PORT:50051}
nlp.grpc.use_tls=\${GRPC_USE_TLS:false}

# Web Server
server.port=8080
ws.port=\${WS_PORT:8090}

# AWS Configuration
aws.region=\${AWS_REGION:us-east-1}
aws.endpoint=\${AWS_ENDPOINT_URL:http://localhost:4566}
aws.use-localstack=true

# Security
security.jwt.secret=\${JWT_SECRET}
security.grpc.api-key=\${NLP_GRPC_API_KEY}
security.service.token=\${SERVICE_TOKEN}
EOF

chmod 600 secrets/*.txt
chmod 644 secrets/*.json
chmod 644 secrets/*.properties
chmod 644 .env

echo "Созданы файлы:"
echo "  secrets/nlp_grpc_key.txt         - NLP gRPC API"
echo "  secrets/jwt_secret.txt           - JWT"
echo "  secrets/service_token.txt        - tokens"
echo "  secrets/secrets.json             - JSON secrets"
echo "  secrets/application.properties   - app properties"
echo "  .env                             - for docker-compose"
echo ""