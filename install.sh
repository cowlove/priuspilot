#!/bin/bash -x 

sudo apt-get install openjdk-11-jdk-headless 

make -C joystick/linux
cp joystick/lib/*.so pplib/

make -C src libFrameCaptureJNI.so
cp src/libFrameCaptureJNI.so pplib/

make -C src priuspilot.jar
cp src/priuspilot.jar pplib/

#test installation
./scripts/pp 
 
