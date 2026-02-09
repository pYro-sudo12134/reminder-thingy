#!/bin/bash
mkdir -p nfs-config

cat > nfs-config/exports << EOF
/exports/nlp_models *(rw,sync,no_subtree_check,no_root_squash,nohide)
/exports/nlp_data *(rw,sync,no_subtree_check,no_root_squash,nohide)
/exports/localstack_data *(rw,sync,no_subtree_check,no_root_squash,nohide)
/exports/postgres_backup *(rw,sync,no_subtree_check,no_root_squash,nohide)
/exports/app_static *(rw,sync,no_subtree_check,no_root_squash,nohide)
EOF

mkdir -p /nfs-storage
mkdir -p /nfs-storage/nlp_models
mkdir -p /nfs-storage/nlp_data
mkdir -p /nfs-storage/localstack_data
mkdir -p /nfs-storage/postgres_backup
mkdir -p /nfs-storage/app_static

chown -R nobody:nogroup /nfs-storage
chmod -R 777 /nfs-storage