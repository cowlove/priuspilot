#!/bin/bash

IF=#wlx74da38e1a8ba
IF=wlx0013ef802622
sudo ifconfig $IF down
sudo iwconfig $IF mode monitor
sudo ifconfig $IF up
sudo iwconfig $IF channel 6

#make
sudo ./bin/exec $IF
