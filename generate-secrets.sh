#!/bin/bash
set -e

mkdir -p secrets
mkdir -p opensearch_certs
mkdir -p opensearch_security

openssl rand -base64 32 > secrets/nlp_grpc_key.txt
openssl rand -base64 32 > secrets/jwt_secret.txt
openssl rand -base64 24 > secrets/service_token.txt
openssl rand -base64 16 > secrets/opensearch_password.txt

echo "test" > secrets/aws_access_key.txt
echo "test" > secrets/aws_secret_key.txt

openssl genrsa -out opensearch_certs/root-ca-key.pem 2048

cat > opensearch_certs/root-ca.cnf << EOF
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_req
prompt = no

[req_distinguished_name]
C = US
ST = New York
L = New York
O = LocalStack
CN = LocalStack Root CA

[v3_req]
basicConstraints = critical, CA:TRUE
keyUsage = critical, digitalSignature, keyCertSign
subjectKeyIdentifier = hash
EOF

openssl req -new -x509 -key opensearch_certs/root-ca-key.pem \
  -out opensearch_certs/root-ca.pem -days 3650 \
  -config opensearch_certs/root-ca.cnf

openssl genrsa -out opensearch_certs/node-key.pem 2048

cat > opensearch_certs/node.cnf << EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = US
ST = New York
L = New York
O = LocalStack
CN = opensearch.localhost

[v3_req]
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = opensearch.localhost
DNS.3 = localstack
IP.1 = 127.0.0.1
EOF

openssl req -new -key opensearch_certs/node-key.pem \
  -out opensearch_certs/node.csr -config opensearch_certs/node.cnf

openssl x509 -req -in opensearch_certs/node.csr \
  -CA opensearch_certs/root-ca.pem -CAkey opensearch_certs/root-ca-key.pem \
  -CAcreateserial -out opensearch_certs/node.pem -days 3650 \
  -extensions v3_req -extfile opensearch_certs/node.cnf

openssl genrsa -out opensearch_certs/admin-key.pem 2048

cat > opensearch_certs/admin.cnf << EOF
[req]
distinguished_name = req_distinguished_name
prompt = no

[req_distinguished_name]
C = US
ST = New York
L = New York
O = LocalStack
CN = admin
EOF

openssl req -new -key opensearch_certs/admin-key.pem \
  -out opensearch_certs/admin.csr -config opensearch_certs/admin.cnf

openssl x509 -req -in opensearch_certs/admin.csr \
  -CA opensearch_certs/root-ca.pem -CAkey opensearch_certs/root-ca-key.pem \
  -CAcreateserial -out opensearch_certs/admin.pem -days 3650

keytool -import -trustcacerts -alias root-ca -file opensearch_certs/root-ca.pem \
  -keystore opensearch_certs/truststore.jks -storepass changeit -noprompt

keytool -importkeystore -srckeystore opensearch_certs/truststore.jks \
  -srcstorepass changeit \
  -destkeystore opensearch_certs/truststore.p12 \
  -deststoretype PKCS12 \
  -deststorepass changeit \
  -noprompt

cat opensearch_certs/root-ca.pem > opensearch_certs/ca-chain.pem

rm -f opensearch_certs/*.csr opensearch_certs/*.srl opensearch_certs/*.cnf

cat > secrets/secrets.json << EOF
{
  "NLP_GRPC_API_KEY": "$(cat secrets/nlp_grpc_key.txt)",
  "JWT_SECRET": "$(cat secrets/jwt_secret.txt)",
  "SERVICE_TOKEN": "$(cat secrets/service_token.txt)",
  "OPENSEARCH_PASSWORD": "$(cat secrets/opensearch_password.txt)",
  "OPENSEARCH_USER": "admin",
  "OPENSEARCH_HOST": "localstack",
  "OPENSEARCH_HTTPS_PORT": "9200",
  "OPENSEARCH_USE_SSL": "true",
  "OPENSEARCH_TRUSTSTORE_PATH": "/app/certs/truststore.jks",
  "OPENSEARCH_TRUSTSTORE_PASSWORD": "changeit",
  "NLP_SERVICE_HOST": "nlp-service",
  "NLP_SERVICE_PORT": "50051",
  "GRPC_USE_TLS": "true",
  "WS_PORT": "8090",
  "AWS_ACCESS_KEY_ID": "test",
  "AWS_SECRET_ACCESS_KEY": "test"
}
EOF

cat > .env << EOF
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
AWS_REGION=us-east-1
AWS_ENDPOINT_URL=http://localstack:4566
OPENSEARCH_HOST=localstack
OPENSEARCH_PORT=4510
OPENSEARCH_HTTPS_PORT=9200
OPENSEARCH_USER=admin
OPENSEARCH_ADMIN_PASSWORD=$(cat secrets/opensearch_password.txt)
OPENSEARCH_USE_SSL=true
OPENSEARCH_DISABLE_SSL_VERIFICATION=true
OPENSEARCH_TRUSTSTORE_PATH=/app/certs/truststore.jks
OPENSEARCH_TRUSTSTORE_PASSWORD=changeit
NLP_SERVICE_HOST=nlp-service
NLP_SERVICE_PORT=50051
GRPC_USE_TLS=true
ENVIRONMENT_NAME=dev
WS_PORT=8090
USE_LOCAL_SECRETS=true
LOG_LEVEL=INFO
HEALTH_CHECK_ENABLED=true
EOF

cat > secrets/application.properties << EOF
app.name=VoiceReminder
app.version=1.0.0
app.environment=\${ENVIRONMENT_NAME:dev}
nlp.service.host=\${NLP_SERVICE_HOST:nlp-service}
nlp.service.port=\${NLP_SERVICE_PORT:50051}
nlp.grpc.use_tls=\${GRPC_USE_TLS:true}
opensearch.host=\${OPENSEARCH_HOST:localstack}
opensearch.port=\${OPENSEARCH_PORT:4510}
opensearch.https_port=\${OPENSEARCH_HTTPS_PORT:9200}
opensearch.user=\${OPENSEARCH_USER:admin}
opensearch.password=\${OPENSEARCH_ADMIN_PASSWORD}
opensearch.use_ssl=\${OPENSEARCH_USE_SSL:true}
opensearch.disable_ssl_verification=\${OPENSEARCH_DISABLE_SSL_VERIFICATION:true}
opensearch.truststore.path=\${OPENSEARCH_TRUSTSTORE_PATH:/app/certs/truststore.jks}
opensearch.truststore.password=\${OPENSEARCH_TRUSTSTORE_PASSWORD:changeit}
server.port=8080
ws.port=\${WS_PORT:8090}
aws.region=\${AWS_REGION:us-east-1}
aws.endpoint=\${AWS_ENDPOINT_URL:http://localhost:4566}
aws.use-localstack=true
security.jwt.secret=\${JWT_SECRET}
security.grpc.api-key=\${NLP_GRPC_API_KEY}
security.service.token=\${SERVICE_TOKEN}
EOF

chmod 600 secrets/*.txt
chmod 644 secrets/*.json
chmod 644 secrets/*.properties
chmod 644 .env
chmod 600 opensearch_certs/*.pem
chmod 600 opensearch_certs/*.jks
chmod 600 opensearch_certs/*.p12

echo "secrets/nlp_grpc_key.txt         - NLP gRPC API"
echo "secrets/jwt_secret.txt           - JWT"
echo "secrets/service_token.txt        - Service tokens"
echo "secrets/opensearch_password.txt  - OpenSearch admin password"
echo "opensearch_certs/truststore.jks  - SSL truststore (password: changeit)"
echo "opensearch_certs/truststore.p12  - SSL truststore (PKCS12)"
echo "opensearch_certs/ca-chain.pem    - CA chain"
echo "secrets/secrets.json             - JSON secrets"
echo "secrets/application.properties   - Application properties"
echo ".env                             - Docker-compose environment"