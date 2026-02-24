cd /mnt/d
sudo truncate -s 10G zfs-pool.img

sudo zpool create -f data-pool /mnt/d/zfs-pool.img

sudo zfs create -o mountpoint=/mnt/d/zfs/postgres \
  -o compression=lz4 \
  -o recordsize=8k \
  data-pool/postgres

sudo zfs create -o mountpoint=/mnt/d/zfs/redis \
  -o compression=lz4 \
  -o recordsize=8k \
  data-pool/redis

sudo zfs create -o mountpoint=/mnt/d/zfs/opensearch \
  -o compression=lz4 \
  -o recordsize=16k \
  data-pool/opensearch

sudo zfs create -o mountpoint=/mnt/d/zfs/nlp-models \
  -o compression=lz4 \
  data-pool/nlp-models

sudo zfs list
sudo zpool status

sudo chown -R user:user /mnt/d/zfs/
sudo chmod -R 755 /mnt/d/zfs/

# sudo zfs snapshot data-pool/postgres@before-migration
# sudo zfs get compression,compressratio data-pool/postgres