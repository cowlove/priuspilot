#!/bin/bash
#./scripts/configESPNOW.sh
LANEDUMPS=/media/jim/d301342c-fa0f-48c9-b34b-049471d3b681/lanedumps/
LANEDUMPS=/host/lanedumps/

./scripts/ppr \
	-minms 55 -displaymode 55 /dev/video2 -rescale 3 -displayratio 5 \
	-log $LANEDUMPS/%s.log -out $LANEDUMPS/%s.yuv \
	$@

