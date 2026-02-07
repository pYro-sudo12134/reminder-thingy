mkdir -p nfs-config
echo "/exports 10.10.0.0/24(rw,sync,no_subtree_check,root_squash,fsid=0)" > nfs-config/exports