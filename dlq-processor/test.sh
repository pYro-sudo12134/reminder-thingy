#!/bin/bash

QUEUE_URL="http://localhost:4566/000000000000/dev-reminder-dlq"

aws --endpoint-url=http://localhost:4566 sqs send-message \
  --queue-url $QUEUE_URL \
  --message-body '{
    "source": "aws.lambda",
    "detail-type": "Lambda Function Invocation Result - Failure",
    "detail": {
      "functionName": "send-reminder-lambda",
      "errorMessage": "Connection timeout to OpenSearch",
      "requestId": "test-123"
    },
    "time": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
  }'

aws --endpoint-url=http://localhost:4566 sqs send-message \
  --queue-url $QUEUE_URL \
  --message-body '{
    "source": "aws.transcribe",
    "detail-type": "Transcribe Job State Change",
    "detail": {
      "TranscriptionJobName": "job-456",
      "TranscriptionJobStatus": "FAILED",
      "ErrorMessage": "Media file format not supported"
    },
    "time": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
  }'

echo "Done - Check your email!"