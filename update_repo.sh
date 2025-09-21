#!/bin/bash
#set -x
scrdir=`dirname $0`
cd $scrdir
# check out and update all modules
git pull --recurse-submodules
git submodule update --init --recursive --remote
