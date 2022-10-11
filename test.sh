#!/bin/bash

( time ( for f in lanedumps/*.yuv; do ./scripts/pp $f -displayratio 0; done | grep RMS | sort ) ) >  test.out 2>&1

cat test.out
