#!/bin/sh
#set -x
host='localhost:8665'
alive=`curl http://$host/alive 2>/dev/null | tr -d $' \n'`
if test "$alive" = '{"success":true}'; then
    echo "Alive!"
else
    echo "DEAD!"
    exit 1
fi

input="Einsatzleiter fuer Gruppenfuehrer komme"
input_old="Einsatzleiter hoert."
#input="das ist so verstanden"
#input_old="das ist so verstanden"
res=`curl -X POST -d "transcript_new=$input&transcript_old=$input_old" http://$host/predict 2>/dev/null | tr '\n' ' '`
if echo $res | grep -q 'mostLikely": *"TurnAssign'; then
    echo "Works!"
else
    echo "FAILS: " + $res
    exit 1
fi
