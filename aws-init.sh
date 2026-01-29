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
    echo "=== CloudFormation Outputs ==="
    aws --endpoint-url=http://localhost:4566 cloudformation describe-stacks \
      --stack-name voice-reminder-stack \
      --region us-east-1 \
      --query 'Stacks[0].Outputs'

else
    echo "WARNING: CloudFormation stack not in CREATE_COMPLETE state"
    echo "Checking for errors..."
    aws --endpoint-url=http://localhost:4566 cloudformation describe-stack-events \
      --stack-name voice-reminder-stack \
      --region us-east-1 \
      --query 'StackEvents[?ResourceStatus==`CREATE_FAILED`]' \
      --output table || true
fi

echo "LocalStack initialization complete!"