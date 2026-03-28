#!/bin/bash
set -e

LAMBDA_NAME="send-reminder-lambda"
LAMBDA_HANDLER="by.losik.lambda.SendReminderLambda::handleRequest"
LAMBDA_RUNTIME="java17"
LAMBDA_TIMEOUT=60
LAMBDA_MEMORY=512
AWS_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"
JAR_PATH="build/libs/send-reminder-lambda.jar"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

if [ ! -f "$JAR_PATH" ]; then
    log_error "JAR файл не найден: $JAR_PATH"
    log_info "Запускаю сборку проекта..."
    ./gradlew clean shadowJar

    if [ ! -f "$JAR_PATH" ]; then
        log_error "Сборка не удалась"
        exit 1
    fi
fi

log_info "Найден JAR файл: $JAR_PATH ($(du -h "$JAR_PATH" | cut -f1))"

log_info "Проверка доступности LocalStack..."
if ! curl -s "$AWS_ENDPOINT/_localstack/health" > /dev/null 2>&1; then
    log_error "LocalStack недоступен"
    exit 1
fi
log_info "LocalStack доступен"

log_info "Создание IAM роли для Lambda"
ROLE_ARN="arn:aws:iam::000000000000:role/lambda-execution-role"

aws --endpoint-url="$AWS_ENDPOINT" iam create-role \
    --role-name lambda-execution-role \
    --assume-role-policy-document '{
        "Version": "2012-10-17",
        "Statement": [{
            "Effect": "Allow",
            "Principal": {"Service": "lambda.amazonaws.com"},
            "Action": "sts:AssumeRole"
        }]
    }' \
    --region "$AWS_REGION" 2>/dev/null || log_warn "Роль уже существует"

log_info "Настройка политик для роли"
aws --endpoint-url="$AWS_ENDPOINT" iam attach-role-policy \
    --role-name lambda-execution-role \
    --policy-arn "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole" \
    --region "$AWS_REGION" 2>/dev/null || true

cat > lambda-policy.json << 'EOF'
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "secretsmanager:GetSecretValue",
                "secretsmanager:DescribeSecret"
            ],
            "Resource": "*"
        }
    ]
}
EOF

aws --endpoint-url="$AWS_ENDPOINT" iam put-role-policy \
    --role-name lambda-execution-role \
    --policy-name lambda-additional-policy \
    --policy-document file://lambda-policy.json \
    --region "$AWS_REGION" 2>/dev/null

rm -f lambda-policy.json

log_info "Деплой Lambda функции: $LAMBDA_NAME"

FUNCTION_EXISTS=$(aws --endpoint-url="$AWS_ENDPOINT" lambda get-function \
    --function-name "$LAMBDA_NAME" \
    --region "$AWS_REGION" 2>/dev/null || echo "")

if [ -n "$FUNCTION_EXISTS" ]; then
    log_info "Обновление существующей Lambda"
    aws --endpoint-url="$AWS_ENDPOINT" lambda update-function-code \
        --function-name "$LAMBDA_NAME" \
        --zip-file "fileb://$JAR_PATH" \
        --region "$AWS_REGION" \
        --publish > /dev/null
else
    log_info "Создание новой Lambda"
    aws --endpoint-url="$AWS_ENDPOINT" lambda create-function \
        --function-name "$LAMBDA_NAME" \
        --runtime "$LAMBDA_RUNTIME" \
        --role "$ROLE_ARN" \
        --handler "$LAMBDA_HANDLER" \
        --zip-file "fileb://$JAR_PATH" \
        --timeout "$LAMBDA_TIMEOUT" \
        --memory-size "$LAMBDA_MEMORY" \
        --region "$AWS_REGION" > /dev/null
fi

sleep 10

log_info "Обновление конфигурации Lambda"
aws --endpoint-url="$AWS_ENDPOINT" lambda update-function-configuration \
    --function-name "$LAMBDA_NAME" \
    --handler "$LAMBDA_HANDLER" \
    --timeout "$LAMBDA_TIMEOUT" \
    --memory-size "$LAMBDA_MEMORY" \
    --role "$ROLE_ARN" \
    --region "$AWS_REGION" > /dev/null

log_info "Настройка переменных окружения"

cat > lambda-env.json << EOF
{
    "Variables": {
        "SMTP_HOST": "smtp.gmail.com",
        "SMTP_PORT": "587",
        "SMTP_USERNAME": "${SMTP_MAIL}",
        "SMTP_PASSWORD": "${SMTP_PASSWORD}",
        "FROM_EMAIL": "${FROM_EMAIL}",
        "SMTP_TLS": "true",
        "SMTP_SSL": "false",
        "USE_SECRETS_MANAGER": "false",
        "USE_PARAMETER_STORE": "false",
        "AWS_REGION": "${AWS_REGION}"
    }
}
EOF

log_info "Переменные окружения:"
while IFS= read -r line; do
    echo "  $line"
done < lambda-env.json

aws --endpoint-url="$AWS_ENDPOINT" lambda update-function-configuration \
    --function-name "$LAMBDA_NAME" \
    --environment file://lambda-env.json \
    --region "$AWS_REGION" > /dev/null

rm -f lambda-env.json

log_info "Тестовый вызов Lambda"
sleep 2

aws --endpoint-url="$AWS_ENDPOINT" lambda invoke \
    --function-name "$LAMBDA_NAME" \
    --cli-binary-format raw-in-base64-out \
    --payload '{"detail":{"reminderId":"test-001","userEmail":"test@example.com","action":"Test reminder","scheduledTime":"2024-12-01 12:00:00"}}' \
    response.json \
    --region "$AWS_REGION" > /dev/null

if [ -f response.json ]; then
    log_info "Результат выполнения:"
    cat response.json
    echo ""
    rm -f response.json
fi

log_info "Последние логи Lambda:"
sleep 3

LOG_GROUP="/aws/lambda/$LAMBDA_NAME"
LOG_STREAMS=$(aws --endpoint-url="$AWS_ENDPOINT" logs describe-log-streams \
    --log-group-name "$LOG_GROUP" \
    --order-by "LastEventTime" \
    --descending \
    --limit 1 \
    --region "$AWS_REGION" \
    --query 'logStreams[0].logStreamName' \
    --output text 2>/dev/null)

if [ -n "$LOG_STREAMS" ] && [ "$LOG_STREAMS" != "None" ]; then
    aws --endpoint-url="$AWS_ENDPOINT" logs get-log-events \
        --log-group-name "$LOG_GROUP" \
        --log-stream-name "$LOG_STREAMS" \
        --limit 10 \
        --region "$AWS_REGION" \
        --query 'events[*].message' \
        --output table 2>/dev/null || echo "Нет логов"
else
    echo "Лог-группа пуста"
fi

echo ""
log_info "Lambda функция успешно развернута"
log_warn "Убедитесь, что переменные окружения установлены!!!"
echo "Для просмотра логов:"
echo "  aws --endpoint-url=http://localhost:4566 logs tail /aws/lambda/$LAMBDA_NAME --follow"