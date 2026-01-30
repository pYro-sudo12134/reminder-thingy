#!/bin/bash
set -e

echo "Starting LocalStack initialization"
echo "Waiting for LocalStack to be ready"

sleep 15

until curl -s http://localhost:4566/_localstack/health > /dev/null 2>&1; do
  echo "Waiting for LocalStack services to be available"
  sleep 5
done

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
      --query 'items[?path=='\''/'\''].id' \
      --output text)

    echo "Root resource ID: $ROOT_RESOURCE_ID"

    HEALTH_RESOURCE_ID=$(aws --endpoint-url=http://localhost:4566 apigateway create-resource \
      --rest-api-id $API_ID \
      --parent-id $ROOT_RESOURCE_ID \
      --path-part "health" \
      --region us-east-1 \
      --query 'id' \
      --output text)

    echo "Health resource ID: $HEALTH_RESOURCE_ID"

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

    echo "Deployment created with ID: $DEPLOYMENT_ID"

    echo -e "\nTesting API Gateway health endpoint"
    API_RESPONSE=$(curl -s -X GET "http://localhost:4566/restapis/$API_ID/dev/_user_request_/health")
    echo "Response: $API_RESPONSE"

    echo "API Gateway created successfully!"
    echo "Test URL: http://localhost:4566/restapis/$API_ID/dev/_user_request_/health"

    return 0
}

echo "Deploying CloudFormation stack"
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
  --region us-east-1

echo "Waiting for CloudFormation stack creation (60 seconds)"
sleep 60

echo "Checking CloudFormation stack status"
STACK_STATUS=$(aws --endpoint-url=http://localhost:4566 cloudformation describe-stacks \
  --stack-name voice-reminder-stack \
  --region us-east-1 \
  --query 'Stacks[0].StackStatus' \
  --output text)

echo "Stack status: $STACK_STATUS"

if [[ "$STACK_STATUS" == "CREATE_COMPLETE" ]]; then
    echo "CloudFormation stack created successfully"

    echo "Creating S3 folders"
    aws --endpoint-url=http://localhost:4566 s3api put-object \
      --bucket voice-reminder-audio-bucket \
      --key audio/ \
      --region us-east-1 2>/dev/null && echo "Created audio folder" || echo "Audio folder already exists"

    aws --endpoint-url=http://localhost:4566 s3api put-object \
      --bucket voice-reminder-audio-bucket \
      --key transcriptions/ \
      --region us-east-1 2>/dev/null && echo "Created transcriptions folder" || echo "Transcriptions folder already exists"

    echo "Checking SNS topic"
    SNS_TOPICS=$(aws --endpoint-url=http://localhost:4566 sns list-topics --region us-east-1 --query 'Topics[].TopicArn' --output text)

    if [[ "$SNS_TOPICS" == *"dev-reminder-notifications"* ]]; then
        echo "SNS topic already exists"
    else
        echo "Creating SNS topic for notifications"
        SNS_TOPIC_ARN=$(aws --endpoint-url=http://localhost:4566 sns create-topic \
          --name dev-reminder-notifications \
          --region us-east-1 \
          --query 'TopicArn' \
          --output text)

        aws --endpoint-url=http://localhost:4566 sns subscribe \
          --topic-arn $SNS_TOPIC_ARN \
          --protocol email \
          --notification-endpoint notifications@example.com \
          --region us-east-1
    fi

    create_api_gateway

    echo ""
    echo "=== Resource Summary ==="
    echo ""
    echo "S3 Buckets:"
    aws --endpoint-url=http://localhost:4566 s3api list-buckets --region us-east-1 --query 'Buckets[*].Name'

    echo ""
    echo "OpenSearch Domains:"
    aws --endpoint-url=http://localhost:4566 opensearch list-domain-names --region us-east-1 --query 'DomainNames[*].DomainName'

    echo ""
    echo "Secrets in Secrets Manager:"
    aws --endpoint-url=http://localhost:4566 secretsmanager list-secrets --region us-east-1 --query 'SecretList[*].Name'

    echo ""
    echo "SNS Topics:"
    aws --endpoint-url=http://localhost:4566 sns list-topics --region us-east-1 --query 'Topics[*].TopicArn'

    echo ""
    echo "CloudWatch Log Groups:"
    aws --endpoint-url=http://localhost:4566 logs describe-log-groups --region us-east-1 --query 'logGroups[*].logGroupName'

    echo ""
    echo "IAM Roles:"
    aws --endpoint-url=http://localhost:4566 iam list-roles --region us-east-1 --query 'Roles[*].RoleName'

    echo ""
    echo "API Gateway REST APIs:"
    aws --endpoint-url=http://localhost:4566 apigateway get-rest-apis --region us-east-1 --query 'items[*].[id,name]' --output table

    echo ""
    echo "=== CloudFormation Outputs ==="
    aws --endpoint-url=http://localhost:4566 cloudformation describe-stacks \
      --stack-name voice-reminder-stack \
      --region us-east-1 \
      --query 'Stacks[0].Outputs'

    echo ""
    echo "=== API Gateway Test URLs ==="
    API_ID=$(aws --endpoint-url=http://localhost:4566 apigateway get-rest-apis \
      --region us-east-1 \
      --query "items[?name=='voice-reminder-api'].id" \
      --output text)

    if [ ! -z "$API_ID" ] && [ "$API_ID" != "None" ]; then
        echo "Health endpoint: http://localhost:4566/restapis/$API_ID/dev/_user_request_/health"
        echo "Direct test: curl -X GET 'http://localhost:4566/restapis/$API_ID/dev/_user_request_/health'"
    else
        echo "Could not find API Gateway ID"
        echo "Available APIs:"
        aws --endpoint-url=http://localhost:4566 apigateway get-rest-apis --region us-east-1 --query 'items[*].[id,name]' --output table
    fi

else
    echo "WARNING: CloudFormation stack not in CREATE_COMPLETE state"
    echo "Checking for errors"
    aws --endpoint-url=http://localhost:4566 cloudformation describe-stack-events \
      --stack-name voice-reminder-stack \
      --region us-east-1 \
      --query 'StackEvents[?ResourceStatus==`CREATE_FAILED`]' \
      --output table || true
fi

echo "LocalStack initialization complete!"