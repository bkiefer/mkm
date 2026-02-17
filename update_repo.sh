#!/bin/bash
#set -x
if test -z "$scrdir"; then
    # So we can source this if necessary
    scrdir=`dirname $0`
    cd $scrdir
fi
# check out and update all modules
git pull --recurse-submodules
git submodule update --init --recursive --remote
