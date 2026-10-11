# ubuntu-opencode:latest 


## build

1. podman build -f Dockerfile-base -t opencode-base .

2. podman build -t opencode --build-arg https_proxy=http://10.1.3.1:10000 .

