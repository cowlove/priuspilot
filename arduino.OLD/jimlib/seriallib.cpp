#include "seriallib.h"

/* sprintd: print an unsinged long number in to the string
 * buf provided.  Options to place a decimal point, left
 * justify, or provide leading zeros. 
 */ 

void sprintd(char *buf, int bufsize, long n, int decplace, 
             bool left, bool zeros) {
    int orig_bufsize = bufsize;
    bool neg = n < 0;
    
    if (neg) 
	n = -n;
    buf[--bufsize] = '\0';
    while(bufsize > 0) { 
	buf[--bufsize] = (char)('0' + (n % 10));
	n /= 10;
	if (decplace > 0) {
	    if (--decplace == 0 && bufsize > 0)
		buf[--bufsize] = '.';
	} else if (n == 0) 
	    break;
    }
    if (neg && bufsize > 0) 
	buf[--bufsize] = '-';
    
    if (left && bufsize > 0) {   // left justify? 
	for(int i = 0; i < orig_bufsize - bufsize; i++) 
	    buf[i] = buf[bufsize + i];
    } else // or right? 
	while(bufsize > 0)
	    buf[--bufsize] = zeros ? '0' : ' ';
}

#if 0
void test_sprintd() { 
    char buf[7]; 
    sprintd(buf, sizeof(buf), 123, 1, false, false); Serial.println(buf);
    sprintd(buf, sizeof(buf), -123, 1, false, false); Serial.println(buf);
    sprintd(buf, sizeof(buf), -123, 1, true, false); Serial.println(buf);
    sprintd(buf, sizeof(buf), 123, 0, true, false); Serial.println(buf);
    sprintd(buf, sizeof(buf), -12345678, 1, true, false); Serial.println(buf);
    sprintd(buf, sizeof(buf), -12345678, 0, false, false); Serial.println(buf);
    sprintd(buf, sizeof(buf), 0, 2, false, false); Serial.println(buf);
    sprintd(buf, sizeof(buf), -1, 2, false, false); Serial.println(buf);
    sprintd(buf, sizeof(buf), 1, 2, false, false); Serial.println(buf);
    sprintd(buf, sizeof(buf), 0, 2, true, false); Serial.println(buf);
    sprintd(buf, sizeof(buf), -1, 2, true, false); Serial.println(buf);
    sprintd(buf, sizeof(buf), 1, 2, true, false); Serial.println(buf);
    /* Expected output: 
      X  12.3
      X -12.3
      X-12.3
      X123
      X4567.8
      X345678
      X  0.00
      X -0.01
      X  0.01
      X0.00
      X-0.01
      X0.01
    */
}
#endif

