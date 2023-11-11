#!/bin/bash -x
ARGS="-fakeGps -displayratio 0"
./scripts/pp lanedumps/20231109.152233.yuv $ARGS -skip 2300 -exit 4500 -log regression/1.log | grep RMS | tee regression/rms.out
./scripts/pp lanedumps/20231109.152651.yuv $ARGS -skip 1200 -log regression/2.log |  grep RMS | tee -a regression/rms.out
./scripts/pp lanedumps/20231111.123744.yuv $ARGS -log regression/3.log |  grep RMS | tee -a regression/rms.out 
./scripts/pp lanedumps/20231111.124126.yuv $ARGS -log regression/4.log |  grep RMS | tee -a regression/rms.out

