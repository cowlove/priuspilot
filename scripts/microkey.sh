#!/bin/bash

while sleep 1; do
    for f in { 1 .. 3 }; do 
        sleep 1
        stdbuf -o0 evtest /dev/input/event3 | tee -a /tmp/keys  
    done
    #echo power off | bluetoothctl
    #sleep 1
    #echo power on | bluetoothctl
    #sleep 3
    #echo agent on | bluetoothctl
    #echo scan on | bluetoothctl
    #sleep 3
    #echo pair E4:17:D8:14:F2:BE | bluetoothctl
    echo connect E4:17:D8:14:F2:BE | bluetoothctl
    sleep 1
done


