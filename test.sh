#!/bin/bash -x

ARGS="-headless -exit 10000 -key 10,10 /host/lanedumps/20231211.124404.yuv"

pp $ARGS -debug TDCANNY=0 -debug TDHSL=0 -debug TDRGB=1 | grep avgScore
pp $ARGS -debug TDCANNY=0 -debug TDHSL=1 -debug TDRGB=0 | grep avgScore
pp $ARGS -debug TDCANNY=0 -debug TDHSL=1 -debug TDRGB=1 | grep avgScore
pp $ARGS -debug TDCANNY=1 -debug TDHSL=0 -debug TDRGB=0 | grep avgScore
pp $ARGS -debug TDCANNY=1 -debug TDHSL=0 -debug TDRGB=1 | grep avgScore
pp $ARGS -debug TDCANNY=1 -debug TDHSL=1 -debug TDRGB=0 | grep avgScore
pp $ARGS -debug TDCANNY=1 -debug TDHSL=1 -debug TDRGB=1 | grep avgScore


