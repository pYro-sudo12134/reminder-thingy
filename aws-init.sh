#!/bin/bash
set -e

echo "Starting LocalStack initialization"
echo "Waiting for LocalStack to be ready"

until curl -s http://localhost:4566/_localstack/health > /dev/null 2>&1; do
  echo "Waiting for LocalStack services to be available"
  sleep 5
done

wait_for_stack() {
    local stack_name=$1
    local max_attempts=30
    local attempt=1

    echo "Waiting for stack $stack_name to complete..."

    while [ $attempt -le $max_attempts ]; do
        STATUS=$(aws --endpoint-url=http://localhost:4566 cloudformation describe-stacks \
            --stack-name $stack_name \
            --region us-east-1 \
            --query 'Stacks[0].StackStatus' \
            --output text 2>/dev/null || echo "NOT_FOUND")

        if [[ "$STATUS" == "CREATE_COMPLETE" ]]; then
            echo "Stack $stack_name created successfully"
            return 0
        elif [[ "$STATUS" == "CREATE_FAILED" || "$STATUS" == "ROLLBACK_COMPLETE" ]]; then
            echo "Stack $stack_name creation failed with status: $STATUS"
            return 1
        fi

        echo "Attempt $attempt: Stack status = $STATUS"
        sleep 10
        attempt=$((attempt + 1))
    done

    echo "Timeout waiting for stack $stack_name"
    return 1
}

create_api_gateway() {
    echo "Creating API Gateway"

    API_ID=$(aws --endpoint-url=http://localhost:4566 apigateway create-rest-api \
      --name "voice-reminder-api" \
      --region us-east-1 \
      --query 'id' \
      --output text)

    echo "API created with ID: $API_ID"

    ROOT_RESOURCE_ID=$(aws --endpoint-url=http://localhost:4566 apigateway get-resources \
      --rest-api-id $API_ID \
      --region us-east-1 \
      --query 'items[?path==`/`].id' \
      --output text)

    HEALTH_RESOURCE_ID=$(aws --endpoint-url=http://localhost:4566 apigateway create-resource \
      --rest-api-id $API_ID \
      --parent-id $ROOT_RESOURCE_ID \
      --path-part "health" \
      --region us-east-1 \
      --query 'id' \
      --output text)

    aws --endpoint-url=http://localhost:4566 apigateway put-method \
      --rest-api-id $API_ID \
      --resource-id $HEALTH_RESOURCE_ID \
      --http-method GET \
      --authorization-type "NONE" \
      --region us-east-1

    aws --endpoint-url=http://localhost:4566 apigateway put-integration \
      --rest-api-id $API_ID \
      --resource-id $HEALTH_RESOURCE_ID \
      --http-method GET \
      --type MOCK \
      --integration-http-method POST \
      --request-templates '{"application/json":"{\"statusCode\": 200}"}' \
      --region us-east-1

    aws --endpoint-url=http://localhost:4566 apigateway put-method-response \
      --rest-api-id $API_ID \
      --resource-id $HEALTH_RESOURCE_ID \
      --http-method GET \
      --status-code 200 \
      --response-models '{"application/json":"Empty"}' \
      --region us-east-1

    aws --endpoint-url=http://localhost:4566 apigateway put-integration-response \
      --rest-api-id $API_ID \
      --resource-id $HEALTH_RESOURCE_ID \
      --http-method GET \
      --status-code 200 \
      --response-templates '{"application/json":"{\"status\": \"healthy\", \"timestamp\": \"$context.requestTime\"}"}' \
      --region us-east-1

    DEPLOYMENT_ID=$(aws --endpoint-url=http://localhost:4566 apigateway create-deployment \
      --rest-api-id $API_ID \
      --stage-name "dev" \
      --region us-east-1 \
      --query 'id' \
      --output text)

    echo "API Gateway created successfully!"
}

init_metrics() {
    echo "Initializing CloudWatch metrics..."

    mkdir -p /tmp/localstack-scripts

    cat > /tmp/localstack-scripts/metrics-cron.sh << 'EOF'
#!/bin/bash
LOG_FILE="/var/log/metrics-cron.log"

log() {
    echo "[$(date)] $1" >> $LOG_FILE
}

log "Starting fixed metrics generator"

while true; do
    QUEUE_SIZE=$(aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
        --queue-url http://localhost:4566/000000000000/dev-reminder-queue \
        --attribute-names ApproximateNumberOfMessages \
        --region us-east-1 \
        --query 'Attributes.ApproximateNumberOfMessages' \
        --output text 2>/dev/null || echo "0")

    DLQ_SIZE=$(aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
        --queue-url http://localhost:4566/000000000000/dev-reminder-dlq \
        --attribute-names ApproximateNumberOfMessages \
        --region us-east-1 \
        --query 'Attributes.ApproximateNumberOfMessages' \
        --output text 2>/dev/null || echo "0")

    aws --endpoint-url=http://localhost:4566 cloudwatch put-metric-data \
        --namespace "AWS/SQS" \
        --metric-name "ApproximateNumberOfMessagesVisible" \
        --value $QUEUE_SIZE \
        --unit Count \
        --dimensions "QueueName=dev-reminder-queue" \
        --region us-east-1 || true

    aws --endpoint-url=http://localhost:4566 cloudwatch put-metric-data \
        --namespace "AWS/SQS" \
        --metric-name "ApproximateNumberOfMessagesVisible" \
        --value $DLQ_SIZE \
        --unit Count \
        --dimensions "QueueName=dev-reminder-dlq" \
        --region us-east-1 || true

    REMINDERS=$(od -An -N2 -i /dev/urandom | awk '{print $1 % 10 + 1}')
    EMAILS=$(od -An -N2 -i /dev/urandom | awk '{print $1 % 5}')
    UPLOADS=$(od -An -N2 -i /dev/urandom | awk '{print $1 % 3}')

    log "Generated: Reminders=$REMINDERS, Emails=$EMAILS, Uploads=$UPLOADS"

    aws --endpoint-url=http://localhost:4566 cloudwatch put-metric-data \
        --namespace "VoiceReminderApp" \
        --metric-name "reminders.created" \
        --value $REMINDERS \
        --unit Count \
        --dimensions "service=reminder-app" \
        --region us-east-1

    if [ $? -eq 0 ]; then
        log "Sent reminders.created=$REMINDERS"
    else
        log "Failed to send reminders.created"
    fi

    aws --endpoint-url=http://localhost:4566 cloudwatch put-metric-data \
        --namespace "VoiceReminderApp" \
        --metric-name "emails.sent" \
        --value $EMAILS \
        --unit Count \
        --dimensions "service=reminder-app,type=notification" \
        --region us-east-1

    if [ $? -eq 0 ]; then
        log "Sent emails.sent=$EMAILS"
    else
        log "Failed to send emails.sent"
    fi

    aws --endpoint-url=http://localhost:4566 cloudwatch put-metric-data \
        --namespace "VoiceReminderApp" \
        --metric-name "audio.uploaded" \
        --value $UPLOADS \
        --unit Count \
        --dimensions "bucket=voice-reminder-audio-bucket" \
        --region us-east-1

    if [ $? -eq 0 ]; then
        log "Sent audio.uploaded=$UPLOADS"
    else
        log "Failed to send audio.uploaded"
    fi

    CPU=$(od -An -N2 -i /dev/urandom | awk '{print $1 % 50 + 10}')
    MEMORY=$(od -An -N2 -i /dev/urandom | awk '{print $1 % 40 + 30}')

    aws --endpoint-url=http://localhost:4566 cloudwatch put-metric-data \
        --namespace "AWS/ES" \
        --metric-name "CPUUtilization" \
        --value $CPU \
        --unit Percent \
        --dimensions "DomainName=reminder-domain,ClientId=000000000000" \
        --region us-east-1 || true

    aws --endpoint-url=http://localhost:4566 cloudwatch put-metric-data \
        --namespace "AWS/ES" \
        --metric-name "JVMMemoryPressure" \
        --value $MEMORY \
        --unit Percent \
        --dimensions "DomainName=reminder-domain,ClientId=000000000000" \
        --region us-east-1 || true

    log "Sleeping 60 seconds..."
    sleep 60
done
EOF

    chmod +x /tmp/localstack-scripts/metrics-cron.sh

    nohup /tmp/localstack-scripts/metrics-cron.sh > /dev/null 2>&1 &

    echo "Metrics generator started with PID: $!"

    aws --endpoint-url=http://localhost:4566 cloudwatch put-metric-data \
        --namespace "VoiceReminderApp" \
        --metric-name "reminders.created" \
        --value 5 \
        --unit Count \
        --dimensions "service=reminder-app" \
        --region us-east-1 || true

    aws --endpoint-url=http://localhost:4566 cloudwatch put-metric-data \
        --namespace "VoiceReminderApp" \
        --metric-name "emails.sent" \
        --value 3 \
        --unit Count \
        --dimensions "service=reminder-app,type=notification" \
        --region us-east-1 || true

    aws --endpoint-url=http://localhost:4566 cloudwatch put-metric-data \
        --namespace "VoiceReminderApp" \
        --metric-name "audio.uploaded" \
        --value 2 \
        --unit Count \
        --dimensions "bucket=voice-reminder-audio-bucket" \
        --region us-east-1 || true

    echo "Initial test metrics created"
}

echo "Deploying main CloudFormation stack"
aws --endpoint-url=http://localhost:4566 cloudformation create-stack \
  --stack-name voice-reminder-stack \
  --template-body file:///etc/localstack/init/ready.d/template.yaml \
  --parameters \
    ParameterKey=EnvironmentName,ParameterValue=dev \
    ParameterKey=EmailAddress,ParameterValue=notifications@example.com \
    ParameterKey=OpenSearchDomainName,ParameterValue=reminder-domain \
    ParameterKey=S3BucketName,ParameterValue=voice-reminder-audio-bucket \
    ParameterKey=LambdaFunctionName,ParameterValue=send-reminder-lambda \
  --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
  --region us-east-1 || true

wait_for_stack "voice-reminder-stack"

if [ -f "/etc/localstack/init/ready.d/metrics-config.yaml" ]; then
    echo "Deploying metrics CloudFormation stack"
    aws --endpoint-url=http://localhost:4566 cloudformation create-stack \
      --stack-name voice-reminder-metrics \
      --template-body file:///etc/localstack/init/ready.d/metrics-config.yaml \
      --parameters \
        ParameterKey=EnvironmentName,ParameterValue=dev \
        ParameterKey=ProcessingQueueName,ParameterValue=dev-reminder-queue \
        ParameterKey=DLQName,ParameterValue=dev-reminder-dlq \
        ParameterKey=LambdaFunctionName,ParameterValue=send-reminder-lambda \
        ParameterKey=OpenSearchDomainName,ParameterValue=reminder-domain \
        ParameterKey=S3BucketName,ParameterValue=voice-reminder-audio-bucket \
      --capabilities CAPABILITY_IAM \
      --region us-east-1 || true

    wait_for_stack "voice-reminder-metrics" || echo "Metrics stack warnings are expected"
else
    echo "metrics-config.yaml not found, skipping metrics stack"
fi

init_metrics

echo "Creating S3 folders"
aws --endpoint-url=http://localhost:4566 s3api put-object \
  --bucket voice-reminder-audio-bucket \
  --key audio/ \
  --region us-east-1 2>/dev/null || true

aws --endpoint-url=http://localhost:4566 s3api put-object \
  --bucket voice-reminder-audio-bucket \
  --key transcriptions/ \
  --region us-east-1 2>/dev/null || true

SNS_TOPIC_ARN=$(aws --endpoint-url=http://localhost:4566 sns create-topic \
  --name dev-reminder-notifications \
  --region us-east-1 \
  --query 'TopicArn' \
  --output text)

aws --endpoint-url=http://localhost:4566 sns subscribe \
  --topic-arn $SNS_TOPIC_ARN \
  --protocol email \
  --notification-endpoint notifications@example.com \
  --region us-east-1 2>/dev/null || true

create_api_gateway

echo ""
echo "=== CloudWatch Metrics Summary ==="
echo ""

sleep 5

echo "Available metrics:"
aws --endpoint-url=http://localhost:4566 cloudwatch list-metrics \
  --namespace VoiceReminderApp \
  --region us-east-1 \
  --query 'Metrics[*].MetricName' \
  --output table || echo "No metrics yet"

echo ""
echo "Recent metrics values:"
for metric in reminders.created emails.sent audio.uploaded; do
    VALUE=$(aws --endpoint-url=http://localhost:4566 cloudwatch get-metric-statistics \
      --namespace VoiceReminderApp \
      --metric-name $metric \
      --start-time $(date -u -d '5 minutes ago' +%Y-%m-%dT%H:%M:%SZ) \
      --end-time $(date -u +%Y-%m-%dT%H:%M:%SZ) \
      --period 300 \
      --statistics Sum \
      --region us-east-1 \
      --query 'Datapoints[0].Sum' \
      --output text 2>/dev/null || echo "0")

    echo "$metric: $VALUE"
done

if pgrep -f "metrics-cron.sh" > /dev/null; then
    echo "Metrics generator is running"
else
    echo "Warning: Metrics generator is not running"
fi

echo ""
echo "=== Deploying Route53 CloudFormation stack ==="
echo ""

if [ -f "/etc/localstack/init/ready.d/route53.yaml" ]; then
    echo "Deploying Route53 CloudFormation stack..."

    MAIN_ZONE_ID=$(aws --endpoint-url=http://localhost:4566 route53 create-hosted-zone \
      --name reminder.local \
      --caller-reference "main-$(date +%s)" \
      --query 'HostedZone.Id' \
      --output text 2>/dev/null | cut -d'/' -f3 || echo "")

    INTERNAL_ZONE_ID=$(aws --endpoint-url=http://localhost:4566 route53 create-hosted-zone \
      --name internal.reminder.local \
      --caller-reference "internal-$(date +%s)" \
      --query 'HostedZone.Id' \
      --output text 2>/dev/null | cut -d'/' -f3 || echo "")

    echo "Main Hosted Zone ID: $MAIN_ZONE_ID"
    echo "Internal Hosted Zone ID: $INTERNAL_ZONE_ID"

    export CFN_IGNORE_UNSUPPORTED_RESOURCE_TYPES=1

    aws --endpoint-url=http://localhost:4566 cloudformation create-stack \
      --stack-name voice-reminder-route53 \
      --template-body file:///etc/localstack/init/ready.d/route53.yaml \
      --parameters \
        ParameterKey=EnvironmentName,ParameterValue=dev \
        ParameterKey=DomainName,ParameterValue=reminder.local \
        ParameterKey=NLPServiceIP,ParameterValue=10.10.0.5 \
        ParameterKey=PostgresIP,ParameterValue=10.10.0.6 \
        ParameterKey=RedisIP,ParameterValue=10.10.0.7 \
        ParameterKey=LocalStackIP,ParameterValue=10.10.0.4 \
        ParameterKey=DLQProcessorIP,ParameterValue=10.10.0.12 \
        ParameterKey=TraefikIP,ParameterValue=10.10.0.2 \
        ParameterKey=AppIPs,ParameterValue=\"10.10.0.10,10.10.0.11\" \
        ParameterKey=MainHostedZoneId,ParameterValue=$MAIN_ZONE_ID \
        ParameterKey=InternalHostedZoneId,ParameterValue=$INTERNAL_ZONE_ID \
      --capabilities CAPABILITY_IAM \
      --region us-east-1 \
      --query 'StackId' \
      --output text || true

    wait_for_stack "voice-reminder-route53"

    echo ""
    echo "Route53 stack deployed successfully!"
    echo ""

    if [ ! -z "$MAIN_ZONE_ID" ]; then
        echo "DNS Records in main zone (reminder.local):"
        aws --endpoint-url=http://localhost:4566 route53 list-resource-record-sets \
          --hosted-zone-id "$MAIN_ZONE_ID" \
          --region us-east-1 \
          --query 'ResourceRecordSets[?Type!=`NS` && Type!=`SOA`].{Name:Name,Type:Type,Value:ResourceRecords[0].Value}' \
          --output table 2>/dev/null || echo "  No records found"
    fi

    if [ ! -z "$INTERNAL_ZONE_ID" ]; then
        echo ""
        echo "DNS Records in internal zone (internal.reminder.local):"
        aws --endpoint-url=http://localhost:4566 route53 list-resource-record-sets \
          --hosted-zone-id "$INTERNAL_ZONE_ID" \
          --region us-east-1 \
          --query 'ResourceRecordSets[?Type!=`NS` && Type!=`SOA`].{Name:Name,Type:Type,Value:ResourceRecords[0].Value}' \
          --output table 2>/dev/null || echo "  No records found"
    fi

    echo ""
    echo "For local resolution, add these lines to /etc/hosts:"
    echo "# Voice Reminder DNS"
    echo "10.10.0.2 api.reminder.local app.reminder.local"
    echo "10.10.0.4 localstack.reminder.local"
    echo "10.10.0.5 nlp.internal.reminder.local"
    echo "10.10.0.6 postgres.internal.reminder.local"
    echo "10.10.0.7 redis.internal.reminder.local"
    echo "10.10.0.4 localstack.internal.reminder.local"
    echo "10.10.0.12 dlq.internal.reminder.local"
    echo "10.10.0.10 app.internal.reminder.local"
    echo "10.10.0.11 app.internal.reminder.local"

else
    echo "route53.yaml not found, skipping Route53 deployment"
fi

echo ""
echo "LocalStack initialization complete!"