#!/bin/bash
set -xv
scrdir=`dirname $0`
cd $scrdir
# check out and update all modules
git pull --recurse-submodules
git submodule update --init --recursive --remote

# ASR and speaker identification
pushd modules/asrident
echo
uv sync
uv -v pip install whisper-gstreamer/
# download silero and speaker identification and whisper models
./model_download.sh -l
uv run download_models.py large-v3-turbo
popd

# Build docker for intent and slot recognition
pushd modules/drz_intentslot
uv sync
./model_download.sh
popd

# Make sure VOnDA compiler is available
pushd modules/vonda
if test -z $(find -name 'vonda*compiler.jar'); then
    #git submodule init; git pull --recurse-submodules # do we need that?
    mvn install
fi
popd

# Compile the MKM and build the MKM docker
mvn clean
./modules/vonda/bin/vondac -c "config.yml"
mvn install
