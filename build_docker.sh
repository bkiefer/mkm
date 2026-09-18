#!/bin/sh
#set -x
pom_version() {
    if test -n "$1"; then cd "$1"; fi
    # There are deprecation warnings under the hood!
    mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null
}

# TODO: use a binary jar from a github release instead
echo "$PATH" | grep -q 'vonda' || export PATH="$(pwd)/modules/vonda/bin:$PATH"
./compile
mvn install
docker build -f Dockerfile -t mkm:`pom_version` .
