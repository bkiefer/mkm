#!/bin/bash
#set -x
logfile="`pwd`/BUILD`date -Iseconds|sed 's/[: ]/_/g'`.log"
exec &> >(tee "$logfile")

. $(dirname $0)/utils.sh

GREEN='\e[42m\e[1;30m'
YELLOW='\e[93m'
RED='\e[31m'
NC='\033[0m' # No Color

function _exitOnError {
    printf "${RED}ERROR during build or model download $1 ${NC}\n";
    exit 1;
}

function _reportSuccess {
    printf "${GREEN}$1 successfully built${NC}\n";
}

create_env_file() {
    (
    echo "ASR_VERSION='`toml_version modules/asrident`'"
    echo "INTENTSLOT_VERSION='`toml_version modules/drz_intentslot`'"
    echo "MKM_VERSION='`pom_version`'"
    echo "MKMCONNECTOR_VERSION='`pom_version modules/mkmconnector`'"
    ) > .env
}


build_asr() {
    # ASR and speaker identification
    cd "$script_dir"/modules/asrident
    ./build_docker.sh || _exitOnError "asr"
    # download silero, speaker identification and whisper models
    ./model_download.sh "$@" || _exitOnError "asr"
    mkdir ../../models/asr
    mv models/* ../../models/asr
    cd "$script_dir"
    _reportSuccess "asr"
}

build_intentslot() {
    # Build docker for intent and slot recognition, NEEDS git-lfs!!
    cd "$script_dir"/modules/drz_intentslot
    ./model_download.sh || _exitOnError "intentslot"
    mkdir ../../models/intentslot
    mv bert-base-german-cased adapters ../../models/intentslot
    ./build_docker.sh || _exitOnError "intentslot"
    cd "$script_dir"
    _reportSuccess "intentslot"
}

build_vonda() {
    # Make sure VOnDA compiler is available, needs installed JDK, not only JRE!
    cd "$script_dir"/modules/vonda
    #git submodule init; git pull --recurse-submodules # do we need that?
    mvn install || _exitOnError "vonda"
    export PATH="$(pwd)/bin:$PATH"
    cd "$script_dir"
    _reportSuccess "vonda"
}

build_mkmconnector() {
    cd "$script_dir"/modules/mkmconnector
    # build the MKM Connector docker, not doing tests (no credentials)
    ./build_docker.sh || _exitOnError "mkmconnector"
    _reportSuccess "mkmconnector"
}

build_mkm() {
    cd "$script_dir"
    # Download rasa ML model, compile the MKM and build the MKM docker
    ./model_download.sh || _exitOnError "mkm"
    ./build_docker.sh || _exitOnError "mkm"
    _reportSuccess "mkm"
}


while getopts anb: c
do
    case $c in
        a)  all="true";;
        n)  no_update="true" ;;
        b)  build="$OPTARG" ;;
        *)  echo "Usage: $0 [-<a>ll] [-<n>oupdate] [module1, module2 ...]

no update will skip updating the git submodules.
module must be one of 'asr', 'intentslot', 'vonda' or 'mkm'
"
    esac
done
shift `expr $OPTIND - 1`

if test -z "$no_update" ; then # check out and update all modules
   ./update_repo.sh
fi

create_env_file

if test "$all" = "true"; then
    build_asr
    build_intentslot
    build_vonda
    build_mkm
    build_mkmconnector
else
    for mod; do
        build_$mod
    done
fi
