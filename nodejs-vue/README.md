# nodejs vue-cli webpack

### vue-cli

- build

```shell
bash build.sh
```

- running

```shell
docker run -itd --name vue --network host -v /<path>/<vue_dir>/:/vue nodejs-vue:latest ash

docker exec -it vue ash
```

