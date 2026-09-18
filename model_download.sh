#!/bin/sh
#set -x
logfile="`pwd`/MODELS`date -Iseconds|sed 's/[: ]/_/g'`.log"
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

models_mkm() {
    link='https://cloud.dfki.de/owncloud/index.php/s/CPiKNWG62wJPFqs/download/nlu-20251103-142306-weighted-comptroller.tar.gz'
    #link='https://cloud.dfki.de/owncloud/index.php/s/SEyqYJXCaeHfdbJ/download/nlu-20250310-114133-several-kicker.tar.gz'

    if \! test -f models/rasa/"${link##*/}"; then
        mkdir models/rasa 2>/dev/null
        cd models/rasa
        # Download NLU model
        wget "$link" || _exitOnError "mkm"
    fi
    _reportSuccess "mkm"
}

models_asr() {
    cd "$script_dir"/modules/asrident
    mkdir ../../models/asr 2>/dev/null
    mv models models0
    ln -s ../../models/asr models
    ./model_download.sh "$@" || _exitOnError "asr"
    rm models
    mv models0 models
    cd "$script_dir"
    _reportSuccess "asr"
}

models_intentslot() {
    cd "$script_dir"
    mkdir models/intentslot 2>/dev/null
    cd models/intentslot
    ../../modules/drz_intentslot/model_download.sh || _exitOnError "intentslot"
    cd "$script_dir"
    _reportSuccess "intentslot"
}

while getopts a c
do
    case $c in
        a)  all="true";;
        *)  echo "Usage: $0 [-<a>ll] [module1, module2 ...]

Download ML models for the specified modules (asr, intentslot or mkm) or all
"
    esac
done
shift `expr $OPTIND - 1`

if test "$all" = "true"; then
    models_asr
    models_intentslot
    models_mkm
else
    for mod; do
        models_$mod
    done
fi
