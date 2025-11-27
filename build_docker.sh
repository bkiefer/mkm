#!/bin/sh
#set -x
echo "$PATH" | grep -q 'vonda' || export PATH="$(pwd)/modules/vonda/bin:$PATH"
mvn clean
./compile
mvn install
docker build -f Dockerfile -t mkm .
