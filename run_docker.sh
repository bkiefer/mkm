#!/bin/sh

cd "`dirname $0`"

docker run --rm -d -v ./config.yml:/app/config.yml -v ./logs/:/app/logs --network=host mkm
