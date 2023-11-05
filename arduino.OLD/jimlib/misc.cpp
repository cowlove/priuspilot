#include "misc.h"

void readEeprom(void *p, int ee, int size) { 
    uint8_t *b = (uint8_t *)p;
    for(int n = 0; n < size; n++) 
	b[n] = EEPROM.read(n + ee);
}
    
void writeEeprom(const void *p, int ee, int size) { 
    const uint8_t *b = (uint8_t *)p;
    for(int n = 0; n < size; n++) {
	if (b[n] != EEPROM.read(n + ee))
	    EEPROM.write(n + ee, b[n]);
    }
}

Profiler funcProf;

extern int __bss_end;
unsigned int ramLowPoint = ~0;
int checkFreeRam() { 
    volatile int rval = (int)&rval - (int)&__bss_end;
    if (rval < ramLowPoint) ramLowPoint = rval;
}
int lowFreeRam() { 
    return ramLowPoint;
}


