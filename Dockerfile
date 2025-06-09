# Container image that runs your code
FROM ghcr.io/manetu/ci-tools:3.1

# Copies your code file from your action repository to the filesystem path `/` of the container
COPY scripts/ /usr/local/bin/

RUN bake -h

# Code file to execute when the docker container starts up (`entrypoint.sh`)
ENTRYPOINT ["/usr/local/bin/bake"]
