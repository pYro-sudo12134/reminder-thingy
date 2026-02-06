#!/bin/bash
set -e

mkdir -p secrets
mkdir -p opensearch_certs
mkdir -p postgres-init
mkdir -p src/main/resources/db/migration

openssl rand -base64 32 > secrets/nlp_grpc_key.txt
openssl rand -base64 16 > secrets/opensearch_password.txt
openssl rand -base64 32 > secrets/postgres_password.txt

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

if [ -f "opensearch_certs/truststore.jks" ]; then
    keytool -delete -alias root-ca \
      -keystore opensearch_certs/truststore.jks -storepass changeit -noprompt 2>/dev/null || true
fi

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

cat > postgres-init/01-create-schema.sql << 'EOF'
-- Создание схемы
CREATE SCHEMA IF NOT EXISTS voice_schema;

-- Даем права на схему
GRANT USAGE ON SCHEMA voice_schema TO postgres;
GRANT CREATE ON SCHEMA voice_schema TO postgres;

-- Устанавливаем поисковый путь по умолчанию для базы
ALTER DATABASE voice_reminder SET search_path TO voice_schema, public;

-- Комментарии
COMMENT ON SCHEMA voice_schema IS 'Main schema for Voice Reminder Application';
EOF

cat > postgres-init/02-create-extensions.sql << 'EOF'
-- Создание полезных расширений
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";

-- Комментарии к расширениям
COMMENT ON EXTENSION "uuid-ossp" IS 'Generate UUIDs';
COMMENT ON EXTENSION "pgcrypto" IS 'Cryptographic functions';
COMMENT ON EXTENSION "citext" IS 'Case-insensitive text type';
EOF

cat > src/main/resources/db/migration/V1__create_users_table.sql << 'EOF'
-- Flyway migration: V1__create_users_table.sql
-- Создание таблицы пользователей

SET search_path TO voice_schema;

-- Таблица пользователей
CREATE TABLE users (
    id BIGSERIAL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    last_login TIMESTAMP,

    PRIMARY KEY (id, created_at),

    CONSTRAINT users_username_unique UNIQUE (username, created_at),

    -- Ограничения
    CONSTRAINT chk_username_length CHECK (LENGTH(username) >= 3)
) PARTITION BY RANGE (created_at);

CREATE TABLE users_2026q1 PARTITION OF users
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');

CREATE TABLE users_2026q2 PARTITION OF users
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');

CREATE TABLE users_2026q3 PARTITION OF users
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');

CREATE TABLE users_2026q4 PARTITION OF users
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

-- Индексы
CREATE INDEX idx_users_username ON voice_schema.users(username);
CREATE INDEX idx_users_is_active ON voice_schema.users(is_active);
CREATE INDEX idx_users_username_part ON voice_schema.users(username);
CREATE INDEX idx_users_created_at_part ON voice_schema.users(created_at DESC);
CREATE INDEX idx_users_active_login ON voice_schema.users(is_active, last_login DESC);

-- Комментарии
COMMENT ON TABLE voice_schema.users IS 'Application users table';
COMMENT ON COLUMN voice_schema.users.username IS 'Unique username for login';
COMMENT ON COLUMN voice_schema.users.password_hash IS 'BCrypt hashed password';
COMMENT ON COLUMN voice_schema.users.is_active IS 'Is user account active';

-- Триггер для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at BEFORE UPDATE
    ON voice_schema.users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
EOF

cat > src/main/resources/db/migration/V2__insert_default_users.sql << 'EOF'
-- Flyway migration: V2__insert_default_users.sql
-- Вставка тестовых пользователей

SET search_path TO voice_schema;

-- Вставка тестовых пользователей
-- Пароли: admin123 и user123
INSERT INTO voice_schema.users (username, password_hash, created_at)
VALUES
    ('admin', '$2a$12$X7h8ZrFvC8N2bQ1W6p5YCOBcBwY8J8ZJ8ZJ8ZJ8ZJ8ZJ8ZJ8ZJ8ZJ', NOW()),
    ('user', '$2a$12$Y8h9ZrFvC8N2bQ1W6p5YCOBcBwY8J8ZJ8ZJ8ZJ8ZJ8ZJ8ZJ8ZJ8ZJ', NOW())
ON CONFLICT (username, created_at) DO NOTHING;
EOF

cat > secrets/secrets.json << EOF
{
  "NLP_GRPC_API_KEY": "$(cat secrets/nlp_grpc_key.txt)",
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
  "AWS_SECRET_ACCESS_KEY": "test",
  "POSTGRES_PASSWORD": "$(cat secrets/postgres_password.txt)"
}
EOF

cat > .env << EOF
# AWS/LocalStack
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
AWS_REGION=us-east-1
AWS_ENDPOINT_URL=http://localstack:4566

# OpenSearch
OPENSEARCH_HOST=localstack
OPENSEARCH_PORT=4510
OPENSEARCH_HTTPS_PORT=9200
OPENSEARCH_USER=admin
OPENSEARCH_ADMIN_PASSWORD=$(cat secrets/opensearch_password.txt)
OPENSEARCH_USE_SSL=true
OPENSEARCH_DISABLE_SSL_VERIFICATION=true
OPENSEARCH_TRUSTSTORE_PATH=/app/certs/truststore.jks
OPENSEARCH_TRUSTSTORE_PASSWORD=changeit

# NLP Service
NLP_SERVICE_HOST=nlp-service
NLP_SERVICE_PORT=50051
GRPC_USE_TLS=true

# Application
ENVIRONMENT_NAME=dev
WS_PORT=8090
USE_LOCAL_SECRETS=true
LOG_LEVEL=INFO
HEALTH_CHECK_ENABLED=true

# PostgreSQL Database
POSTGRES_USER=postgres
POSTGRES_PASSWORD=$(cat secrets/postgres_password.txt)
DB_ENABLED=true
DB_HOST=postgres
DB_PORT=5432
DB_NAME=voice_reminder
DB_SCHEMA=voice_schema
DB_POOL_SIZE=10
DB_POOL_MIN_IDLE=5
FLYWAY_ENABLED=true
FLYWAY_BASELINE_VERSION=1
JPA_DDL_GENERATION=none
JPA_SHOW_SQL=false
EOF

cat > secrets/application.properties << EOF
# Application
app.name=VoiceReminder
app.version=1.0.0
app.environment=\${ENVIRONMENT_NAME:dev}

# NLP Service
nlp.service.host=\${NLP_SERVICE_HOST:nlp-service}
nlp.service.port=\${NLP_SERVICE_PORT:50051}
nlp.grpc.use_tls=\${GRPC_USE_TLS:true}

# OpenSearch
opensearch.host=\${OPENSEARCH_HOST:localstack}
opensearch.port=\${OPENSEARCH_PORT:4510}
opensearch.https_port=\${OPENSEARCH_HTTPS_PORT:9200}
opensearch.user=\${OPENSEARCH_USER:admin}
opensearch.password=\${OPENSEARCH_ADMIN_PASSWORD}
opensearch.use_ssl=\${OPENSEARCH_USE_SSL:true}
opensearch.disable_ssl_verification=\${OPENSEARCH_DISABLE_SSL_VERIFICATION:true}
opensearch.truststore.path=\${OPENSEARCH_TRUSTSTORE_PATH:/app/certs/truststore.jks}
opensearch.truststore.password=\${OPENSEARCH_TRUSTSTORE_PASSWORD:changeit}

# Server
server.port=8090
ws.port=\${WS_PORT:8090}

# AWS
aws.region=\${AWS_REGION:us-east-1}
aws.endpoint=\${AWS_ENDPOINT_URL:http://localhost:4566}
security.grpc.api-key=\${NLP_GRPC_API_KEY}

# PostgreSQL Database
db.enabled=\${DB_ENABLED:true}
db.host=\${DB_HOST:postgres}
db.port=\${DB_PORT:5432}
db.name=\${DB_NAME:voice_reminder}
db.username=\${POSTGRES_USER:postgres}
db.password=\${POSTGRES_PASSWORD}
db.schema=\${DB_SCHEMA:voice_schema}
db.pool.size=\${DB_POOL_SIZE:10}
db.pool.minIdle=\${DB_POOL_MIN_IDLE:5}
db.connection.timeout=30000
db.pool.maxLifetime=1800000

# JPA
jpa.show_sql=\${JPA_SHOW_SQL:false}
jpa.format_sql=true
jpa.ddl.generation=\${JPA_DDL_GENERATION:none}

# Flyway
flyway.enabled=\${FLYWAY_ENABLED:true}
flyway.baseline.version=\${FLYWAY_BASELINE_VERSION:1}
flyway.locations=classpath:db/migration,filesystem:/app/db/migration
flyway.schemas=\${DB_SCHEMA:voice_schema}
flyway.table=flyway_schema_history
flyway.validate-on-migrate=true
flyway.baseline-on-migrate=true
EOF

chmod 600 secrets/*.txt
chmod 644 secrets/*.json
chmod 644 secrets/*.properties
chmod 644 .env
chmod 600 opensearch_certs/*.pem
chmod 600 opensearch_certs/*.jks
chmod 600 opensearch_certs/*.p12
chmod 644 postgres-init/*.sql
chmod 644 db/migration/*.sql