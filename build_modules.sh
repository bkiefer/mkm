#!/bin/bash
#set -x
scrdir=`dirname $0`
cd $scrdir
scrdir=`pwd`
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

build_asr() {
    # ASR and speaker identification
    cd "$scrdir"/modules/asrident
    (./build_docker.sh &&
         # download silero, speaker identification and whisper models
         ./model_download.sh) 2>&1 | tee "$logfile" || _exitOnError "asrident"
    cd "$scrdir"
    _reportSuccess "asrident"
}

build_intentslot() {
    # Build docker for intent and slot recognition, NEEDS git-lfs!!
    cd "$scrdir"/modules/drz_intentslot
    (./model_download.sh &&
         ./build_docker.sh ) 2>&1 | tee -a "$logfile" || _exitOnError "drz_intentslot"
    cd "$scrdir"
    _reportSuccess "drz_intentslot"
}

build_vonda() {
    # Make sure VOnDA compiler is available, needs installed JDK, not only JRE!
    cd "$scrdir"/modules/vonda
    #git submodule init; git pull --recurse-submodules # do we need that?
    mvn install 2>&1 | tee -a "$logfile" || _exitOnError "vonda_compiler"
    export PATH="$(pwd)/bin:$PATH"
    cd "$scrdir"
    _reportSuccess "vonda_compiler"
}

build_mkm() {
    cd "$scrdir"
    # Download rasa ML model, compile the MKM and build the MKM docker
    (./model_download.sh &&
         ./build_docker.sh) 2>&1 | tee -a "$logfile" || _exitOnError "mkm"
    _reportSuccess "mkm"
}

while getopts anb: c
do
    case $c in
        a)  all="true";;
        n)  update="false" ;;
        b)  build="$OPTARG" ;;
        *)  echo "Usage: $0 [-<a>ll] [-<n>oupdate] [module1, module2 ...]

no update will skip updating the git submodules.
module must be one of 'asr', 'intentslot', 'vonda' or 'mkm'
"
    esac
done
shift `expr $OPTIND - 1`

if test "$all" = "true"; then
    build_asr
    build_intentslot
    build_vonda
    build_mkm
else
    for mod; do
        build_$mod
    done
fi
