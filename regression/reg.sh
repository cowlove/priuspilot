#!/bin/bash
ARGS="-headless -nodither"

cat /dev/null > ./regression/rms.out
for f in \
	./lanedumps/20231114.181221.yuv \
	./lanedumps/20231114.181623.yuv \
	./lanedumps/20231116.140744.yuv \
	./lanedumps/20231116.141353.yuv \
	./lanedumps/20231117.174827.yuv \
	./lanedumps/20231117.175518.yuv \
	./lanedumps/20231119.101920.yuv \
;
do ./scripts/pp $f $ARGS -log ./regression/`basename -s .yuv $f`.log  | grep RMS | tee -a ./regression/rms.out
done
