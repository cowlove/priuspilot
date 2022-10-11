#!/bin/bash
rm /tmp/test.out
FLAGS="-debug SHOW_FPS -displayratio 0"

time(
./scripts/pp $FLAGS lanedumps/20221010.151339.yuv -skip 1900 -exit 500   | tee -a /tmp/test.out 
./scripts/pp $FLAGS lanedumps/20221010.145903.yuv -skip 2400 -exit 800 | tee -a /tmp/test/out

for f in lanedumps/*.yuv; do ./scripts/pp $FLAGS $f ; done | tee -a /tmp/test.out
)

grep RMS /tmp/test.out | sort > test.out
cat test.out
