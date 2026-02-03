### Description

That is a thigy for writing the reminders.

Processing flow:

Audio input → S3 → Transcribe → Parsing → OpenSearch → EventBridge → SES

### How to launch

```shell
./generate-secrets.sh
docker-compose up --build -d
```

But first make sure you have the required images, as I didn't publish them.

```shell
docker build -t reminder-nlp:1.0 . # when you are in `nlp` dir
docker build -t reminder-backend:1.0 # in the root of the project
```

If you want to check out whether it is working

```shell
curl http://localhost:8090/api/test
```

Or you can visit `http://localhost:8090/`

### Stack (main):

### Backend
- Java 17
- Google Guice, Jersey, EclipseLink
- Jetty 11
- gRPC (with Netty)
- Gradle
- JUnit 5, Mockito
- Flyway
- AWS SDKv2

### NLP Service
- Python 3.11
- gRPC
- spaCy, Transformers, PyTorch, Scikit-learn

### Infrastructure
- AWS CloudFormation
- Docker, Docker Compose, Docker Swarm
- PostgreSQL, OpenSearch
- SQS (as DLQ)
- Transcribe
- CloudWatch
- AWS Secrets Manager, KMS
- EventBridge
- AWS Lambda
- LocalStack (where I deployed everything)
