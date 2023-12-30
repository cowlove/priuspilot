#!/bin/bash -x 

mkdir ~/src
cd !$
git clone https://github.com/cowlove/priuspilot.git

cd ~/src/priuspilot
mkdir lanedumps

sudo apt-get -y install openjdk-17-jdk-headless openjdk-17-jdk xfce4-terminal automake tightvncserver syslog-ng



cd ~/src
git clone https://github.com/kernc/logkeys.git
cd logkeys
./autogen.sh
cd build
../configure
make
sudo make install


make -C joystick/linux
cp joystick/lib/*.so pplib/

make -C src libFrameCaptureJNI.so
cp src/libFrameCaptureJNI.so pplib/

make -C src priuspilot.jar
cp src/priuspilot.jar pplib/

#test installation
./scripts/pp 
 
