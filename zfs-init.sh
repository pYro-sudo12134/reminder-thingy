#!/bin/bash

set -e

POOL_FILE="/mnt/d/zfs-pool.img"
POOL_NAME="data-pool"
NFS_EXPORTS="/exports"

mkdir -p ./nfs-config
mkdir -p $NFS_EXPORTS

dd if=/dev/zero of=$POOL_FILE bs=1M count=0 seek=51200

sudo zpool create -f \
    -o ashift=12 \
    -O atime=off \
    -O compression=lzjb \
    -O xattr=on \
    $POOL_NAME $POOL_FILE

sudo zfs set primarycache=all $POOL_NAME
sudo zfs set secondarycache=all $POOL_NAME

create_dataset() {
    local name=$1
    local mount=$2
    local compress=$3
    local recordsize=$4
    local extra_opts=$5

    sudo zfs create -o mountpoint=$mount \
        -o compression=$compress \
        -o recordsize=$recordsize \
        -o atime=off \
        -o logbias=throughput \
        $extra_opts \
        $POOL_NAME/$name
}

create_dataset "nlp-models" "$NFS_EXPORTS/nlp_models" "lzjb" "128K" ""
create_dataset "nlp-data" "$NFS_EXPORTS/nlp_data" "gzip-3" "128K" "-o primarycache=metadata"
create_dataset "localstack" "$NFS_EXPORTS/localstack_data" "lzjb" "16K"
create_dataset "postgres-backup" "$NFS_EXPORTS/postgres_backup" "gzip-3" "64K" ""
create_dataset "app-static" "$NFS_EXPORTS/app_static" "lzjb" "128K" ""

cat > ./nfs-config/exports << EOF
$NFS_EXPORTS/nlp_models *(rw,async,no_subtree_check,no_root_squash,nohide,fsid=100)
$NFS_EXPORTS/nlp_data *(rw,async,no_subtree_check,no_root_squash,nohide,fsid=101)
$NFS_EXPORTS/localstack_data *(rw,async,no_subtree_check,no_root_squash,nohide,fsid=102,sec=sys)
$NFS_EXPORTS/postgres_backup *(rw,async,no_subtree_check,no_root_squash,nohide,fsid=103,wsize=1048576)
$NFS_EXPORTS/app_static *(rw,async,no_subtree_check,no_root_squash,nohide,fsid=104)
EOF

sudo groupadd -f nfs-users
sudo usermod -a -G nfs-users $USER

for dir in $NFS_EXPORTS/*; do
    sudo chown -R root:nfs-users "$dir"
    sudo chmod -R 775 "$dir"
done

sudo zpool status $POOL_NAME
sudo zfs list -r $POOL_NAME
sudo zpool iostat -v $POOL_NAME 1 1
#sudo zpool get fragmentation $POOL_NAME

sudo zpool set cachefile=/etc/zfs/zpool.cache $POOL_NAME
sudo systemctl enable zfs-fuse.service

#sudo systemctl enable zfs.target
#sudo systemctl enable zfs-zed.service