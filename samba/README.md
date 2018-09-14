### samba in docker

- build:
```shell
sh build.sh "${your samba password}"
```

- Usage:
```shell
docker run -itd --name samba -p 445:445 -v ${host_dir_rw}:/rw -v ${host_dir_ro}:/ro:ro samba:latest
```
