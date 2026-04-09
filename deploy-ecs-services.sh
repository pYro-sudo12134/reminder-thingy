#!/bin/bash
set -e

# ECS Services Deployment Script
# Deploys ECS services with task definitions created inside CloudFormation

ENVIRONMENT_NAME=${1:-dev}
AWS_REGION=${AWS_DEFAULT_REGION:-us-east-1}
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

echo "Deploying ECS Services for environment: $ENVIRONMENT_NAME"

echo "Getting infrastructure outputs..."
ECR_CLUSTER=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='ECSClusterName'].OutputValue" \
    --output text)

ALB_DNS=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='ALBDNSName'].OutputValue" \
    --output text)

NLB_DNS=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='NLBDNSName'].OutputValue" \
    --output text)

RDS_ENDPOINT=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='RDSEndpoint'].OutputValue" \
    --output text)

REDIS_ENDPOINT=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='RedisPrimaryEndpoint'].OutputValue" \
    --output text)

APP_SG=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='AppSecurityGroupId'].OutputValue" \
    --output text)

NLP_SG=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='NLPSecurityGroupId'].OutputValue" \
    --output text)

OLLAMA_SG=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='OllamaSecurityGroupId'].OutputValue" \
    --output text)

DLQ_SG=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='DLQSecurityGroupId'].OutputValue" \
    --output text)

OPENSEARCH_DOMAIN=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Parameters[?ParameterKey=='OpenSearchDomain'].ParameterValue" \
    --output text 2>/dev/null || echo "reminder-domain")

echo "Infrastructure outputs retrieved"

# Get Target Group ARNs
APP_TG_ARN=$(aws elbv2 describe-target-groups \
    --names "${ENVIRONMENT_NAME}-app-tg" \
    --query "TargetGroups[0].TargetGroupArn" \
    --output text)

NLP_TG_ARN=$(aws elbv2 describe-target-groups \
    --names "${ENVIRONMENT_NAME}-nlp-tg" \
    --query "TargetGroups[0].TargetGroupArn" \
    --output text)

# Get VPC and Subnet IDs
VPC_ID=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Parameters[?ParameterKey=='VpcId'].ParameterValue" \
    --output text)

SUBNET_IDS=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Parameters[?ParameterKey=='SubnetIds'].ParameterValue" \
    --output text)

# Get DLQ URL
DLQ_URL=$(aws sqs get-queue-url \
    --queue-name "${ENVIRONMENT_NAME}-reminder-dlq" \
    --query "QueueUrl" \
    --output text 2>/dev/null || echo "")

# Get Secrets ARNs
SECRETS_ARN=$(aws secretsmanager describe-secret \
    --secret-id "${ENVIRONMENT_NAME}/voice-reminder/secrets" \
    --query "ARN" \
    --output text 2>/dev/null || echo "")

RD_SECRETS_ARN=$(aws secretsmanager describe-secret \
    --secret-id "${ENVIRONMENT_NAME}/rds-credentials" \
    --query "ARN" \
    --output text 2>/dev/null || echo "")

REDIS_SECRETS_ARN=$(aws secretsmanager describe-secret \
    --secret-id "${ENVIRONMENT_NAME}/redis-credentials" \
    --query "ARN" \
    --output text 2>/dev/null || echo "")

SMTP_SECRETS_ARN=$(aws secretsmanager describe-secret \
    --secret-id "${ENVIRONMENT_NAME}/smtp-credentials" \
    --query "ARN" \
    --output text 2>/dev/null || echo "")

# Get IAM role ARNs and LogGroup names from infrastructure stack
TASK_EXECUTION_ROLE_ARN=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='TaskExecutionRoleArn'].OutputValue" \
    --output text)

APP_TASK_ROLE_ARN=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='AppTaskRoleArn'].OutputValue" \
    --output text)

NLP_TASK_ROLE_ARN=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='NLPServiceTaskRoleArn'].OutputValue" \
    --output text)

DLQ_TASK_ROLE_ARN=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='DLQProcessorTaskRoleArn'].OutputValue" \
    --output text)

OLLAMA_TASK_ROLE_ARN=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='OllamaTaskRoleArn'].OutputValue" \
    --output text)

APP_LOG_GROUP=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='AppLogGroupName'].OutputValue" \
    --output text)

NLP_LOG_GROUP=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='NLPLogGroupName'].OutputValue" \
    --output text)

DLQ_LOG_GROUP=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='DLQLogGroupName'].OutputValue" \
    --output text)

OLLAMA_LOG_GROUP=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='OllamaLogGroupName'].OutputValue" \
    --output text)

echo "Deploying ECS services..."
aws cloudformation deploy \
    --stack-name "${ENVIRONMENT_NAME}-ecs-services" \
    --template-file ecs-services.yaml \
    --parameter-overrides \
        EnvironmentName="$ENVIRONMENT_NAME" \
        ECSClusterName="$ECR_CLUSTER" \
        AppTargetGroupArn="$APP_TG_ARN" \
        NLPTargetGroupArn="$NLP_TG_ARN" \
        AppSecurityGroupId="$APP_SG" \
        NLPSecurityGroupId="$NLP_SG" \
        OllamaSecurityGroupId="$OLLAMA_SG" \
        DLQSecurityGroupId="$DLQ_SG" \
        VpcId="$VPC_ID" \
        SubnetIds="$SUBNET_IDS" \
        RDSEndpoint="$RDS_ENDPOINT" \
        RedisEndpoint="$REDIS_ENDPOINT" \
        OpenSearchDomain="$OPENSEARCH_DOMAIN" \
        NLBDNSName="$NLB_DNS" \
        AppBaseURL="http://$ALB_DNS" \
        DLQUrl="$DLQ_URL" \
        NotificationEmail="${NOTIFICATION_EMAIL}" \
        SecretsArn="$SECRETS_ARN" \
        RDSecretsArn="$RD_SECRETS_ARN" \
        RedisSecretsArn="$REDIS_SECRETS_ARN" \
        SMTPSecretsArn="$SMTP_SECRETS_ARN" \
        TaskExecutionRoleArn="$TASK_EXECUTION_ROLE_ARN" \
        AppTaskRoleArn="$APP_TASK_ROLE_ARN" \
        NLPTaskRoleArn="$NLP_TASK_ROLE_ARN" \
        DLQTaskRoleArn="$DLQ_TASK_ROLE_ARN" \
        OllamaTaskRoleArn="$OLLAMA_TASK_ROLE_ARN" \
        AppLogGroupName="$APP_LOG_GROUP" \
        NLPLogGroupName="$NLP_LOG_GROUP" \
        DLQLogGroupName="$DLQ_LOG_GROUP" \
        OllamaLogGroupName="$OLLAMA_LOG_GROUP" \
    --capabilities CAPABILITY_NAMED_IAM \
    --region "$AWS_REGION" \
    --no-fail-on-empty-changeset

echo "ECS Services deployed!"

# Get additional infrastructure outputs
ALB_FULL_NAME=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='ALBFullName'].OutputValue" \
    --output text)

NLB_FULL_NAME=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='NLBFullName'].OutputValue" \
    --output text)

APP_TG_FULL_NAME=$(aws cloudformation describe-stacks \
    --stack-name "${ENVIRONMENT_NAME}-ecs-infrastructure" \
    --query "Stacks[0].Outputs[?OutputKey=='AppTargetGroupFullName'].OutputValue" \
    --output text)

# Deploy monitoring
echo "Deploying CloudWatch dashboards..."
aws cloudformation deploy \
    --stack-name "${ENVIRONMENT_NAME}-ecs-monitoring" \
    --template-file ecs-monitoring.yaml \
    --parameter-overrides \
        EnvironmentName="$ENVIRONMENT_NAME" \
        ECSClusterName="$ECR_CLUSTER" \
        AppServiceName="${ENVIRONMENT_NAME}-reminder-app" \
        NLPServiceName="${ENVIRONMENT_NAME}-nlp-service" \
        OllamaServiceName="${ENVIRONMENT_NAME}-ollama" \
        DLQServiceName="${ENVIRONMENT_NAME}-dlq-processor" \
        ALBDNSName="$ALB_DNS" \
        ALBFullName="$ALB_FULL_NAME" \
        NLBDNSName="$NLB_DNS" \
        NLBFullName="$NLB_FULL_NAME" \
        AppTargetGroupFullName="$APP_TG_FULL_NAME" \
        RDSEndpoint="$RDS_ENDPOINT" \
        RedisEndpoint="$REDIS_ENDPOINT" \
        NotificationEmail="${NOTIFICATION_EMAIL}" \
    --capabilities CAPABILITY_NAMED_IAM \
    --region "$AWS_REGION" \
    --no-fail-on-empty-changeset

echo "Monitoring deployed!"

echo ""
echo "Deployment complete!"
echo "ALB DNS: $ALB_DNS"
echo "NLB DNS: $NLB_DNS"
echo "RDS: $RDS_ENDPOINT"
echo "Redis: $REDIS_ENDPOINT"
