#!/bin/bash 
./scripts/pp -displayratio 0  lanedumps/20221010.151339.yuv -skip 1900 -exit 500 | grep RMS
./scripts/pp -displayratio 0  lanedumps/20221010.145903.yuv -skip 2400 -exit 800 | grep RMS


