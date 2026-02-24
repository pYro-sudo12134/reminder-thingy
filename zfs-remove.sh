#!/bin/bash

set -e

sudo zfs unmount -a 2>/dev/null || true
sudo zpool export data-pool 2>/dev/null || true
sudo zpool destroy -f data-pool 2>/dev/null || true
sudo rm -f /mnt/d/zfs-pool.img
sudo rm -rf /exports
sudo rm -rf ./nfs-config