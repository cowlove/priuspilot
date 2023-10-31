#!/bin/bash

time for f in lanedumps/*; do ./scripts/pp -displayratio 0 $f | grep RMS; done


