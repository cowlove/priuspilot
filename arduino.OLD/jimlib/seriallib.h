#ifndef _seriallib_h
#define _seriallib_h
#include "timerlib.h" 

#include <HardwareSerial.h>
#ifdef CMDLINE_MAKE
/**/#include <SoftwareSerial/SoftwareSerial.h>
#else
#include <SoftwareSerial.h>
#endif
#include <avr/pgmspace.h>

void sprintd(char *buf, int bufsize, long n, int decplace, 
             bool left, bool zeros);

/*
 * BufferedSoftwareSerial extends SoftwareSerial such that print(),etc. 
 * add data to a small circular buffer, which can then be sent a few 
 * msec at a time with calls to send()
 */ 
template <int Size> class BufferedSoftwareSerial : public SoftwareSerial {
    typedef SoftwareSerial Parent;
    char buf[Size];
    int head, tail;
public:
    BufferedSoftwareSerial(int txPin, int rxPin) : 
	Parent(txPin, rxPin), head(0), tail(0) {}
    void printbyte(uint8_t c) {
	buf[head++] = c;  
	if (head == Size) head = 0;
    }
    void print(const char *s) { 
	while(*s) printbyte(*s++);
    }
    void print_P(const char *p) { 
	char c;
	while ((c = pgm_read_byte(p++)))
	    printbyte(c);
    } 
    void send(int ms = 0);
    bool empty() { return head == tail; } 
};


template <int Size> void BufferedSoftwareSerial<Size>::send(int ms) {
    int us = ms * 1000;
    const int bytedelay = 1000000UL / 2600 * 10;
    UsecTimer t;
    while(tail != head && (us == 0 || t.elapsed() < us)) { 
	if (us == 0 || t.elapsed() + bytedelay < us)
	    Parent::print(buf[tail++]);      
	if (tail == Size) tail = 0;
    }
}

template <typename T> int serialReadInt(T &s, long us) { 
    UsecTimer ut; 
    int rval = 0;
    int i, base = 10;
    bool neg = false;
    while(us == 0 || ut.elapsed() < us) { 
	if ((i = s.read()) >= 0) { 
	    if (i == '-')
		neg = true;
	    else if(i == 'x')
		base = 16;
	    else if (i >= '0' && i <= '9')
		rval = rval * base + (i - '0');
	    else if (i >= 'a' && i <= 'f')
		rval = rval * base + (i - 'a' + 10);
	    else
		break;
	}
    }
    return neg ? -rval : rval;
}

#endif


