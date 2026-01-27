#!/bin/bash
set -e

echo "Starting LocalStack initialization"
echo "Waiting for LocalStack to be ready"

sleep 15

until curl -s http://localhost:4566/_localstack/health; do
  echo "Waiting for LocalStack services to be available"
  sleep 5
done

echo "Deploying CloudFormation stack"
aws --endpoint-url=http://localhost:4566 cloudformation create-stack \
  --stack-name voice-reminder-stack \
  --template-body file:///etc/localstack/init/ready.d/localstack-template.yaml \
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

echo "S3 Buckets:"
aws --endpoint-url=http://localhost:4566 s3api list-buckets --region us-east-1

echo ""
echo "OpenSearch Domains:"
aws --endpoint-url=http://localhost:4566 opensearch list-domain-names --region us-east-1

echo ""
echo "Lambda Functions:"
aws --endpoint-url=http://localhost:4566 lambda list-functions --region us-east-1

echo ""
echo "EventBridge Rules:"
aws --endpoint-url=http://localhost:4566 events list-rules --region us-east-1

echo ""
echo "SES Verified Emails:"
aws --endpoint-url=http://localhost:4566 ses list-identities --region us-east-1

echo ""
echo "SNS Topics:"
aws --endpoint-url=http://localhost:4566 sns list-topics --region us-east-1

echo ""
echo "CloudWatch Log Groups:"
aws --endpoint-url=http://localhost:4566 logs describe-log-groups --region us-east-1 | grep logGroupName

echo ""
echo "IAM Roles:"
aws --endpoint-url=http://localhost:4566 iam list-roles --region us-east-1 | grep RoleName