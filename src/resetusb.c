
#include <stdio.h>

main() { 
	system("/sbin/modprobe -r ftdi_sio usbserial");
	system("/sbin/modprobe ftdi_sio");
}

