#!/bin/bash
mkdir -p nfs-config

cat > nfs-config/exports << EOF
/exports/nlp_models *(rw,sync,no_subtree_check,no_root_squash,nohide)
/exports/nlp_data *(rw,sync,no_subtree_check,no_root_squash,nohide)
/exports/localstack_data *(rw,sync,no_subtree_check,no_root_squash,nohide)
/exports/postgres_backup *(rw,sync,no_subtree_check,no_root_squash,nohide)
/exports/app_static *(rw,sync,no_subtree_check,no_root_squash,nohide)
EOF

mkdir -p /exports
mkdir -p /exports/nlp_models
mkdir -p /exports/nlp_data
mkdir -p /exports/localstack_data
mkdir -p /exports/postgres_backup
mkdir -p /exports/app_static

chown -R nobody:nogroup /exports
chmod -R 777 /exports