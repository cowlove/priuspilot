
#include <WConstants.h>
#include <wiring.h>
#include <avr/pgmspace.h>
#include <avr/wdt.h>
#include <avr/interrupt.h>
#include "timerlib.h"

extern volatile unsigned long timer0_overflow_count;

/* get usecs from TCNT0.  Assumes interrupts already masked
 */
unsigned long usecs_I (void) {  //~4uSec execution time
    uint8_t tcnt = TCNT0;	
    unsigned long toc = timer0_overflow_count;
    if (TIFR0 & _BV(TOV0)) { 
       toc++;
       tcnt = TCNT0;
    }
    // TODO: consult TCCR0B bits CS10-CS12, calculate using actual
    // prescaler
    return ((toc << 8) + tcnt) << 2;
}

unsigned long usecs(void) { 
    uint8_t  oldSREG;
    oldSREG = SREG;
    cli();
    unsigned long rval = usecs_I();   
    SREG=oldSREG;
    return rval;
}

unsigned long UsecTimer::elapsed() { 
    return ulsubtract(usecs(), last);
}
unsigned long UsecTimer::elapsed_I() { 
    return ulsubtract(usecs_I(), last);
}
unsigned long UsecTimer::reset() { 
    unsigned long now = usecs();
    unsigned long rval = ulsubtract(now, last);
    last = now;
    return rval;
}     
unsigned long UsecTimer::reset_I() { 
    unsigned long now = usecs_I();
    unsigned long rval = ulsubtract(now, last);
    last = now;
    return rval;
}     

void EggTimer::set(unsigned long i) { 
    interval = i; 
    last = usecs(); 
} 

unsigned long EggTimer::check() { 
    unsigned long now = usecs();
    unsigned long elapsed = ulsubtract(now, last) / 1000;
    if (elapsed < interval)
	return 0;
    last += interval * 1000;
    return elapsed;
}

