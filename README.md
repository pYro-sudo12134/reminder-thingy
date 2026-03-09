### Description

That is a thingy for writing the reminders.

Processing flow:

Audio input → S3 → Transcribe → Parsing → OpenSearch → EventBridge → SMTP client

### How to launch

```shell
./generate-secrets.sh
./pull_embedding_model.sh
 docker-compose -f docker-compose.yaml up -d
```

Or if you want to launch Swarm version, I recommend to adjust labels and install ZFS.

```shell
# First you can apply labels by using similar command
# docker node update --label-add type=queue worker1
# After that you need to do the following

./init-zfs.sh

# However, if you don't want to use ZFS, you can just use

./nfs-export.sh

# Since it is heavily reliant on NFSv4 for file sharing across multiple devices

./generate-secrets.sh
./pull_embedding_model.sh
export $(cat .env | xargs) && docker stack deploy -c docker-stack.yml swarm
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
