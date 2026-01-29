#!/bin/bash
set -e

echo "Starting LocalStack initialization"
echo "Waiting for LocalStack to be ready"

sleep 15

until curl -s http://localhost:4566/_localstack/health; do
  echo "Waiting for LocalStack services to be available"
  sleep 5
done

echo "Creating secrets in Secrets Manager"

aws --endpoint-url=http://localhost:4566 secretsmanager create-secret \
  --name dev/voice-reminder/secrets \
  --secret-string '{
    "NLP_GRPC_API_KEY": "localstack-grpc-api-key-12345",
    "JWT_SECRET": "localstack-jwt-secret-key",
    "SERVICE_TOKEN": "localstack-service-token",
    "NLP_SERVICE_HOST": "nlp-service",
    "NLP_SERVICE_PORT": "50051",
    "GRPC_USE_TLS": "false",
    "WS_PORT": "8090",
    "AWS_ACCESS_KEY_ID": "test",
    "AWS_SECRET_ACCESS_KEY": "test",
    "AWS_REGION": "us-east-1"
  }' \
  --region us-east-1

echo "Creating KMS key"

KMS_KEY_ID=$(aws --endpoint-url=http://localhost:4566 kms create-key \
  --description "Voice Reminder KMS Key" \
  --region us-east-1 \
  --query 'KeyMetadata.KeyId' \
  --output text)

aws --endpoint-url=http://localhost:4566 kms create-alias \
  --alias-name alias/dev-voice-reminder-key \
  --target-key-id $KMS_KEY_ID \
  --region us-east-1

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

echo "Waiting for CloudFormation stack creation"
sleep 45

aws --endpoint-url=http://localhost:4566 cloudformation describe-stacks \
  --stack-name voice-reminder-stack \
  --region us-east-1

echo "Creating S3 folders"
aws --endpoint-url=http://localhost:4566 s3api put-object \
  --bucket voice-reminder-audio-bucket \
  --key audio/ \
  --region us-east-1

aws --endpoint-url=http://localhost:4566 s3api put-object \
  --bucket voice-reminder-audio-bucket \
  --key transcriptions/ \
  --region us-east-1

echo "Setting up SNS topic for notifications"
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

echo "Creating CloudWatch log groups"
aws --endpoint-url=http://localhost:4566 logs create-log-group \
  --log-group-name /aws/lambda/send-reminder-lambda \
  --region us-east-1

aws --endpoint-url=http://localhost:4566 logs create-log-group \
  --log-group-name /dev/reminder-app \
  --region us-east-1

echo ""
echo "S3 Buckets:"
aws --endpoint-url=http://localhost:4566 s3api list-buckets --region us-east-1

echo ""
echo "OpenSearch Domains:"
aws --endpoint-url=http://localhost:4566 opensearch list-domain-names --region us-east-1

echo ""
echo "Secrets in Secrets Manager:"
aws --endpoint-url=http://localhost:4566 secretsmanager list-secrets --region us-east-1

echo ""
echo "SNS Topics:"
aws --endpoint-url=http://localhost:4566 sns list-topics --region us-east-1

echo ""
echo "CloudWatch Log Groups:"
aws --endpoint-url=http://localhost:4566 logs describe-log-groups --region us-east-1 --query 'logGroups[*].logGroupName'