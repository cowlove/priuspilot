#ifndef timerlib_h
#define timerlib_h


unsigned long usecs(void);

unsigned long usecs_I (void);
unsigned long usecs(void);

/* subtract a-b accounting for 32-bit overflow 
 */
inline unsigned long ulsubtract(unsigned long a, unsigned long b) { 
  return (a < b) ? ~0UL - b + a + 1 : a - b;
}

inline unsigned int usubtract(unsigned int a, unsigned int b) { 
  return (a < b) ? 0xffff - b + a + 1 : a - b;
}

class UsecTimer { 
    unsigned long last;
public:
    unsigned long elapsed();
    unsigned long elapsed_I();
    unsigned long reset();
    unsigned long reset_I();
    UsecTimer() { reset(); }
};


class EggTimer {
public:
    unsigned long last, interval; 
    void set(unsigned long);
    unsigned long check();
    EggTimer() {}
    EggTimer(unsigned long i)  { set(i); } 
};


#endif
