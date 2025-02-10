rm mkm.log vonda.log persistent.nt evaluation1*
./run_mkm | tee -a vonda.log &
sleep 1
testfile=test.csv
if test -n "$1"; then
    testfile="$1"
fi
./testcsvclient.py $testfile
