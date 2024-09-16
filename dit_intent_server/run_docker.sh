#!/bin/sh
if test -z "$MODELDIR"; then
    MODELDIR=/opt/models
fi
docker run -p 8665:8665 \
       -v $MODELDIR/dit_dia_server:/app/data/models/dit_dia_server \
       --name dit_da_server -d dit_dia_server:latest
