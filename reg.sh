#!/bin/bash
./scripts/pp lanedumps/20231109.152233.yuv -fakeGps -skip 2300 -exit 4500  -displayratio 0 -log regression/1.log | grep RMS | tee regression/rms.out
./scripts/pp lanedumps/20231109.152651.yuv -displayratio 0 -fakeGps -skip 1200 -log regression/2.log | grep RMS | tee -a regression/rms.out 

