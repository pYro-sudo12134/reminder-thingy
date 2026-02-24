#!/bin/bash

set -e

cd /mnt/d
sudo truncate -s 50G zfs-pool.img

sudo zpool create -f data-pool /mnt/d/zfs-pool.img

sudo zfs create -o mountpoint=/exports \
  -o compression=lzjb \
  data-pool/nfs-root

sudo zfs create -o mountpoint=/exports/nlp_models \
  -o compression=lzjb \
  -o atime=off \
  data-pool/nlp-models

sudo zfs create -o mountpoint=/exports/nlp_data \
  -o compression=lzjb \
  -o atime=off \
  data-pool/nlp-data

sudo zfs create -o mountpoint=/exports/localstack_data \
  -o compression=lzjb \
  -o atime=off \
  data-pool/localstack

sudo zfs create -o mountpoint=/exports/postgres_backup \
  -o compression=lzjb \
  -o atime=off \
  data-pool/postgres-backup

sudo zfs create -o mountpoint=/exports/app_static \
  -o compression=lzjb \
  -o atime=off \
  data-pool/app-static

mkdir -p ./nfs-config

cat > ./nfs-config/exports << 'EOF'
/exports/nlp_models *(rw,sync,no_subtree_check,no_root_squash,nohide,fsid=100)
/exports/nlp_data *(rw,sync,no_subtree_check,no_root_squash,nohide,fsid=101)
/exports/localstack_data *(rw,sync,no_subtree_check,no_root_squash,nohide,fsid=102)
/exports/postgres_backup *(rw,sync,no_subtree_check,no_root_squash,nohide,fsid=103)
/exports/app_static *(rw,sync,no_subtree_check,no_root_squash,nohide,fsid=104)
EOF

sudo chown -R nobody:nogroup /exports
sudo chmod -R 777 /exports

echo "=== ZFS Pool Status ==="
sudo zpool status data-pool

echo -e "\n=== ZFS Datasets ==="
sudo zfs list -r data-pool

echo -e "\n=== Exports Directory Structure ==="
ls -la /exports/

echo -e "\n=== NFS Exports Config ==="
cat ./nfs-config/exports

echo -e "\n=== Ownership ==="
ls -la /exports/ | awk '{print $3, $4, $9}'

sudo zpool set cachefile=/etc/zfs/zpool.cache data-pool
sudo systemctl enable zfs-fuse.service