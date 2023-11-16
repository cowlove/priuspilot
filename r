#!/bin/bash
./scripts/configESPNOW.sh
sudo ./scripts/ppr -debug EXPECTED_FPS=15 -debug SZ_PERIOD=5 -debug minSz=33 -debug defLAng=65 -rescale 2 -debug LASTEP=-0.9 \
	-skipratio 2 -rescale 2 -displayratio 5 $@

