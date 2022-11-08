#!/bin/bash
TMP=/tmp/test.out
rm $TMP
FLAGS="-key 30,127 -nosteer -debug SHOW_FPS -displayratio 0"

time(
./scripts/pp $FLAGS SIM -exit 1200  -key 200,39 | tee -a $TMP
./scripts/pp $FLAGS lanedumps/20221010.151339.yuv -skip 1900 -exit 500   | tee -a $TMP 
./scripts/pp $FLAGS lanedumps/20221010.145903.yuv -skip 2400 -exit 800 | tee -a $TMP

for f in lanedumps/*.yuv; do ./scripts/pp $FLAGS $f ; done | tee -a $TMP
)

grep RMS $TMP | sort > test.out
cat test.out
