#!/bin/sh
#set -x
echo "$PATH" | grep -q 'vonda' || export PATH="$PATH:$(pwd)/modules/vonda/bin"
mvn clean
./compile
mvn install
docker build -f Dockerfile -t mkm .
