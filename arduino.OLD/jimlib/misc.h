#ifndef _misc_h_
#define _misc_h_
#include <WConstants.h>
#include <wiring.h>
#include "seriallib.h"

#ifdef CMDLINE_MAKE
/**/#include <EEPROM/EEPROM.h>
#else
#include <EEPROM.h>
#endif

void readEeprom(void *p, int ee, int size);    
void writeEeprom(const void *p, int ee, int size);

template <int PIN> class DigitalIOPin {
 protected:
    uint8_t state;
 public:
    inline DigitalIOPin(uint8_t m, uint8_t s = LOW) { mode(m); set(s); }
    inline void toggle() { set(state == LOW ? HIGH : LOW); } 
    inline void set(int i) { digitalWrite(PIN, (state = i)); }
    inline uint8_t read() { return digitalRead(PIN); } 
    inline void mode(int m) { pinMode(PIN, m); }  
};

template <int PIN> class DigitalInputPin : public DigitalIOPin<PIN> { 
    void mode(int m) {}
 public:
    DigitalInputPin(uint8_t s = LOW) : DigitalIOPin<PIN>(INPUT, s) {}
};

template <int PIN> class DigitalOutputPin : public DigitalIOPin<PIN> { 
    void mode(int m) {}
 public:
    DigitalOutputPin(uint8_t s = LOW) : DigitalIOPin<PIN>(OUTPUT, s) {}
};

template <int PIN> class AnalogInputPin {
public:
    inline int read() { return analogRead(PIN); }
}; 

/*
 * Average class.  Maintains a circular queue of historical data
 * for calculating statistical info
 */ 
template <typename T, typename S, int SZ> class Average { 
    T array[SZ];
    int index;
public:
    int valid;
    int total;
    inline void add(T x) { 
	array[index++] = x; 
	if (index >= SZ) index = 0;
	if (valid < SZ) valid++; 
	total++;
    } 
    int offset(int i) { 
	i += index;
	if (i < 0) 
	    i = SZ - i;
	else if (i >= SZ)
	    i -= SZ;
	return i % SZ;
    }
	    
    T getItem(int o) {
	return array[offset(o)];
    }
    T avg() {
	if (valid == 0)
	    return 0;
	S sum = 0; 
	
	for(int n = 0; n < valid; n++)
	    sum += array[n];
	if (valid == SZ) 
	    return sum / SZ;
	else 
	    return sum / valid;
    }
    T leastSquaresGuess(int skip, int o) {
	if (valid < SZ)
	    return avg();

	int n;
	S X = 0, Y = 0, XX = 0, XY = 0;
	
	for(n = 0; n < valid / skip ; n++) {
	    int x = -valid / skip + n;
	    T y = array[(index + n * skip + o) % SZ];
	    X += x;
	    Y += y;
	    XX += x * x;
	    XY += x * y;
	}
	S D = n * XX - X * X;
	T yint = (XX * Y - X * XY) / D;

	return yint;
    }
    S var() { 
	S v = 0; 
	S a = avg();
	for(int n = 0; n < valid; n++)
	    v += (array[n] - a) * (array[n] - a);
	return v;
    }
    T maximum() { 
	T m = 0; 
	for(int n = 0; n < valid; n++)
	    m = max(m, array[n]);
	return m;
    }
    T minimum() {
	T m = array[0]; 
	for(int n = 0; n < valid; n++) 
	    m = min(m, array[n]);
	return m;
    }
    void reset() { 
	total = index = valid = 0; 
	for(int n = 0; n < SZ; n++)
	    array[n] = 0;
    }
    template <typename Ser> void print(Ser &s) {
	 s.print(minimum());s.print_P(PSTR(","));s.print(maximum());s.print_P(PSTR(","));
	 s.print(avg()); 
	 s.print_P(PSTR(",")); s.print(var());	
	 s.print_P(PSTR(",")); s.print(valid);	
	 s.print_P(PSTR(",")); s.print(total);	
    }
    template <typename Ser> void dump(Ser &s) {
	for(int n = 0; n < valid; n++) {
	    s.print(array[n]);
	    if (n != valid - 1) 
		s.print_P(PSTR(","));
	}
    }
    Average() { reset(); }  
};

class Profiler {
public:
    Average<unsigned int, long, 10> times;
    void print() { times.print(Serial); } 
};

class AutoProfile { 
    Profiler *pr; 
    UsecTimer t;
public:
    AutoProfile(Profiler *p) : pr(p) {}
    ~AutoProfile() { pr->times.add(t.elapsed()); } 
};

template <int N> class Dumper {
    unsigned int array[N]; 
    int head, tail; 
public:
    Dumper() : head(0), tail(0) {}
    void add(int x) {
	array[head++] = x; 
	if (head >= N) head = 0;
    } 
    void print() { 
	#define NUM_PER_LINE 4
	for(; tail != head; tail = (tail + 1) % N) {
	    Serial.print(array[tail]);
	    if ((tail % NUM_PER_LINE) == NUM_PER_LINE - 1)
		Serial.print_P(PSTR("\n"));
	    else 
		Serial.print_P(PSTR(","));
	}
    }
};
	    
extern Profiler funcProf;
#define PROFILE() AutoProfile __ap(&funcProf)

int checkFreeRam();
int lowFreeRam();

#endif
