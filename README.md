### Description

That is a thigy for writing the reminders.

The flow:
Audio input → S3 → Transcribe → Parsing → OpenSearch → EventBridge → SES

### How to launch

```
./generate-secrets.sh
docker-compose up --build -d
```