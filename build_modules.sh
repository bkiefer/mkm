#!/bin/bash
#set -x
scrdir=`dirname $0`
cd $scrdir
# check out and update all modules
./update_repo.sh

GREEN='\e[42m\e[1;30m'
YELLOW='\e[93m'
RED='\e[31m'
NC='\033[0m' # No Color

function _exitOnError {
    printf "${RED}ERROR during build or model download $1 ${NC}\n";
    exit -1;
}

function _reportSuccess {
    printf "${GREEN}$1 successfully built${NC}\n";
}

logfile="`pwd`/build`date -Iseconds|sed 's/[: ]/_/g'`.log"

# ASR and speaker identification
pushd modules/asrident
(./build_docker.sh &&
# download silero, speaker identification and whisper models
./model_download.sh) > "$logfile" || _exitOnError "asrident"
popd
_reportSuccess "asrident"

# Build docker for intent and slot recognition, NEEDS git-lfs!!
pushd modules/drz_intentslot
(./model_download.sh &&
./build_docker.sh ) >> "$logfile" || _exitOnError "drz_intentslot"
popd
_reportSuccess "drz_intentslot"

# Make sure VOnDA compiler is available, needs installed JDK, not only JRE!
pushd modules/vonda
#git submodule init; git pull --recurse-submodules # do we need that?
mvn install >> "$logfile" || _exitOnError "vonda_compiler"
popd
_reportSuccess "vonda_compiler"

# Download rasa ML model, compile the MKM and build the MKM docker
(./model_download.sh &&
mvn clean &&
./modules/vonda/bin/vondac -c "config.yml" &&
mvn install
./build_docker.sh) >> "$logfile" || _exitOnError "mkm"
_reportSuccess "mkm"
