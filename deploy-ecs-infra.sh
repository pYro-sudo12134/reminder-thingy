#!/bin/bash
set -e

# ECS Infrastructure Deployment Script
# Deploys ECS cluster, RDS, ElastiCache, Load Balancers

ENVIRONMENT_NAME=${1:-dev}
VPC_ID=${2:-}
SUBNET_IDS=${3:-}
KEY_NAME=${4:-}

if [ -z "$VPC_ID" ] || [ -z "$SUBNET_IDS" ] || [ -z "$KEY_NAME" ]; then
    echo "Usage: $0 <environment> <vpc-id> <subnet-ids> <key-name>"
    echo "Example: $0 dev vpc-123456 subnet-123456,subnet-789012 my-key"
    exit 1
fi

echo "Deploying ECS Infrastructure for environment: $ENVIRONMENT_NAME"

OPENSEARCH_DOMAIN=$(aws cloudformation describe-stacks \
    --stack-name voice-reminder \
    --query "Stacks[0].Outputs[?OutputKey=='OpenSearchEndpoint'].OutputValue" \
    --output text 2>/dev/null || echo "reminder-domain")

echo "Creating ECS Infrastructure stack..."
aws cloudformation deploy \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --template-file ecs-infrastructure.yaml \
    --parameter-overrides \
        EnvironmentName="$ENVIRONMENT_NAME" \
        VpcId="$VPC_ID" \
        SubnetIds="$SUBNET_IDS" \
        KeyName="$KEY_NAME" \
        OpenSearchDomain="$OPENSEARCH_DOMAIN" \
        AppBaseURL="http://localhost:8090" \
        OllamaModel="llama3.2:latest" \
        RDSMasterUsername="voiceadmin" \
        RDSMasterUserPassword="$(openssl rand -base64 16)" \
        RedisAuthToken="$(openssl rand -hex 16)" \
        SMTPUsername= "${SMTP_USERNAME:-}" \
        SMTPPassword="${SMTP_PASSWORD:-}" \
        FromEmail="${FROM_EMAIL:-}" \
        NotificationEmail="${NOTIFCATION_EMAIL:-}" \
    --capabilities CAPABILITY_NAMED_IAM \
    --region "${AWS_DEFAULT_REGION:-us-east-1}" \
    --no-fail-on-empty-changeset

echo "ECS Infrastructure deployed!"

echo "Stack outputs:"
aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[]" \
    --output table
