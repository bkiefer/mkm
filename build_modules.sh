#!/bin/bash
#set -x
scrdir=`dirname $0`
cd $scrdir
# check out and update all modules
git pull --recurse-submodules
git submodule update --init --recursive --remote

# ASR and speaker identification
pushd modules/asrident
./build_docker.sh
# download silero, speaker identification and whisper models
./model_download.sh
popd

# Build docker for intent and slot recognition
pushd modules/drz_intentslot
./model_download.sh
./build_docker.sh
popd

# Make sure VOnDA compiler is available
pushd modules/vonda
#git submodule init; git pull --recurse-submodules # do we need that?
mvn install
popd

# Compile the MKM and build the MKM docker
mvn clean
./modules/vonda/bin/vondac -c "config.yml"
mvn install
./build_docker.sh
