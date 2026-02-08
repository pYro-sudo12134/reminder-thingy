#!/bin/bash
mkdir -p nfs-config

cat > nfs-config/exports << EOF
/exports 10.10.0.0/24(rw,sync,fsid=0,no_subtree_check,no_root_squash,insecure,crossmnt)
EOF

mkdir -p /nfs-storage
mkdir -p /nfs-storage/nlp_models
mkdir -p /nfs-storage/nlp_data
mkdir -p /nfs-storage/localstack_data
mkdir -p /nfs-storage/postgres_backup
mkdir -p /nfs-storage/app_static

chown -R nobody:nogroup /nfs-storage
chmod -R 777 /nfs-storage