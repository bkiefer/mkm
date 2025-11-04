#!/bin/sh
mvn clean
./compile
mvn install
docker build -f Dockerfile -t mkm .
