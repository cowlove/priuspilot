#!/bin/bash -x 

sudo apt-get install openjdk-11-jdk-headless openjdk-11-jdk xfce4-terminal


make -C joystick/linux
cp joystick/lib/*.so pplib/

make -C src libFrameCaptureJNI.so
cp src/libFrameCaptureJNI.so pplib/

make -C src priuspilot.jar
cp src/priuspilot.jar pplib/

#test installation
./scripts/pp 
 
