#!/bin/sh
#set -x
scrdir=`dirname "$0"`
cd "$scrdir"
link='https://cloud.dfki.de/owncloud/index.php/s/SEyqYJXCaeHfdbJ/download/nlu-20250310-114133-several-kicker.tar.gz'
if test -f rasa/models/"${link##*/}"; then
    :
else
    mkdir rasa/models 2>/dev/null
    cd rasa/models
    # Download NLU model
    wget "$link"
fi
