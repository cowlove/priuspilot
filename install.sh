#!/bin/bash -x 

sudo raspi-config nonint do_boot_behaviour B1 

sudo apt-get -y install \
    git python3-distutils \
    openjdk-17-jdk-headless openjdk-17-jdk xfce4-terminal automake \
    tightvncserver scons syslog-ng

mkdir ~/src
cd !$
git clone https://github.com/cowlove/priuspilot.git
mkdir ~/src/priuspilot/lanedumps

sudo mkdir -p /host/lanedumps
sudo chown pi:pi !$

cd ~/src
git clone https://gitlab.com/gpsd/gpsd.git
cd gpsd
scons

cd ~/src
git clone https://github.com/kernc/logkeys.git
cd logkeys
./autogen.sh
cd build
../configure
make
sudo make install

cd ~/src/priuspilot
make -C joystick/linux
cp joystick/lib/*.so pplib/

make -C src libFrameCaptureJNI.so
cp src/libFrameCaptureJNI.so pplib/

make -C src priuspilot.jar
cp src/priuspilot.jar pplib/

#test installation
#./scripts/pp 
 
