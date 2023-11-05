#include <WConstants.h>
#include <wiring.h>
#include <avr/pgmspace.h>
#include <avr/wdt.h>
#include <avr/interrupt.h>

#include "timerlib.h"
#include "seriallib.h"
#include "sparkFunLCD.h"
#include "misc.h"

#include "ServoTimer2.h"

//DigitalInputPin<12> button(HIGH);
DigitalOutputPin<13> led;
 
//AnalogOutputPin<11> out1;
//ServoTimer2 servo; 

#define TIMER0_MESSED 64

template <typename T> void printLabelInt(T& s, const char *p, int v) { 
    s.print_P(p);
    s.print(v);
}

extern "C" void __cxa_pure_virtual() {}

void printStatus();
void parseCommand();

struct PersistentData {
#define PERSISTENT_DATA_OFFSET 0 // offset in EEPROM space
#define PERSISTENT_DATA_MAGIC 0xaefa
    int magic;
    void check();
    void write() { writeEeprom(this, PERSISTENT_DATA_OFFSET, sizeof(*this));} 
    void read() { readEeprom(this, PERSISTENT_DATA_OFFSET, sizeof(*this)); } 
} conf, defaults = { PERSISTENT_DATA_MAGIC};

void PersistentData::check() { 
    if (magic != PERSISTENT_DATA_MAGIC) {
	*this = defaults; 
	write();
    }
}

//EggTimer ledTimer(3000), minute(60000);

int lastButton = 1;
long buttonTime = 0;
int minPush = 5; 
int divisor = 12;
int protocolErrors = 0;

int code[5] = {3,1,3,2,1};
static const int codeSz = (sizeof(code)/sizeof(code[0]));
int history[codeSz] = {0};
int histIndex = 0;


static int trqInHiThresh = 120;
static int steer = 0;
static int authority = 0;
static int tristated = 0;
static int maxChange = 5;
static int currentSteer = 0;


void initCounters();

void setup() { 
    Serial.begin(9600);
    conf.read();
    conf.check();
	initCounters();
   //servo.attach(11);
} 
static int gotValidCmd = 0;

class TransitionWatcher {
public: 
	int last; 
	int threshold;
	TransitionWatcher(int t) : threshold(t), last(0) {}

	int changed(int current) {
		int diff = last > current ? last - current : current - last;
		last = current;
		return (diff > threshold); 
	}
};

// cruise control voltages - normal: 878, up: 238, down: 432, cancel : 618 

class PatternWatcher { 
public:
	PatternWatcher(const int *p, int t, int miT, int tmo) : 
		pattern(p), threshold(t), minTime(miT), 
		timeout(tmo) {
		for(patlen = 0; p[patlen] != -1; patlen++) {
		}
		reset();
	}
	const int *pattern;
	int patlen, threshold, minTime, timeout, index, pushTime, waitTime;
	void reset() { index = pushTime = waitTime = 0; } 
	bool check(int val) { 
		if (val < pattern[index] - threshold || val > pattern[index] + threshold) {
			if (++waitTime > timeout)  
				index = pushTime = 0;
			return false;
		} else { 
			waitTime = 0;
			if (++pushTime > minTime) {
				pushTime = 0;
				if (++index == patlen) {
					//Serial.print("match\n");
					index = pushTime = 0; 
					return true;
				}
			}
		}
		return false;
	}
				

};

long jiffies = 0;


class AnalogInputTransitionWatcher { 
public:
	TransitionWatcher tr;
	long ticks;
	int pin;
	AnalogInputTransitionWatcher(int p, int delta = 50) : pin(p), tr(delta), ticks(0) {}
	void check() {
		ticks++;
		if (tr.changed(analogRead(pin)) && pin == 5) { 
			Serial.print("A");
			Serial.print(pin);
			Serial.print(" = ");
			Serial.print(tr.last);
			Serial.print(" at ");
			Serial.print(jiffies);
			Serial.print("\n");
		}
	}
	int value() { return tr.last; } 
};

AnalogInputTransitionWatcher a2(2), a3(3);
EggTimer oneSecTimer(1000L * TIMER0_MESSED);
EggTimer jiffyTimer(10L * TIMER0_MESSED); // hundred per sec

void initCounters() { 
	// Set counter0 to full clock rate.  TODO - use counter1 so that
	// wiring.c millis() and other routines aren't affected

	// set counter0 prescaler to 0
	(_SFR_BYTE(TCCR0B) &= ~(_BV(CS01)));
	(_SFR_BYTE(TCCR0B) &= ~(_BV(CS02)));
	(_SFR_BYTE(TCCR0B) |= (_BV(CS00)));

	// set counter1 prescaler to 0 
	(_SFR_BYTE(TCCR1B) &= ~(_BV(CS11)));
	(_SFR_BYTE(TCCR1B) &= ~(_BV(CS12)));
	(_SFR_BYTE(TCCR1B) |= (_BV(CS10)));

	// switch timer1 from 8bit phase-correct to 8bit fast pwm
	(_SFR_BYTE(TCCR1A) |= (_BV(WGM12)));

	// set timer2 prescaler to 0
	(_SFR_BYTE(TCCR1B) &= ~(_BV(CS21)));
	(_SFR_BYTE(TCCR1B) &= ~(_BV(CS22)));
	(_SFR_BYTE(TCCR1B) |= (_BV(CS20)));

	// switch timer2 from 8bit phase-correct to 8bit fast pwm
	(_SFR_BYTE(TCCR2A) |= (_BV(WGM21)));
}

static const int CC_UP = 240;
static const int CC_DOWN = 430;
static const int CC_ONOFF = 0;
static const int CC_CANCEL = 620;
static const int CC_NOTHING = 875;

// confused values observed when cancel button is pushed while arduino 
// tries to drive either an UP or DOWN signal. 
static const int CC_CANCEL_WITH_UP = 210; 
static const int CC_CANCEL_WITH_DOWN = 350;

static const int patUpDown[] = {CC_UP, CC_DOWN, -1};
static const int patDownUp[] = {CC_DOWN, CC_UP, -1};
static const int patBack[] = {CC_NOTHING, CC_CANCEL, -1};
static const int patOnOff[] = {CC_NOTHING, CC_ONOFF, -1};
static const int patDoubleBack[] = {CC_NOTHING, CC_CANCEL, CC_NOTHING, 
									CC_CANCEL, CC_NOTHING, -1};	

// a9l values to recreate cruise joystick actions
// a9l            ad in value
// --------------------------
// 25			  429
// 24			  442
// 30 			  349 or so
// 35			  297
// 40 			  240


class CruiseJoystick { 
	static const int aInThreshold = 50;
	static const int minPush = 5;
	static const int timeout = 100;
public:
	PatternWatcher patternUpDown, patternDownUp, patternBack, patternDoubleBack,
		patternLongOff, patternLongBack;
	CruiseJoystick() : patternUpDown(patUpDown, aInThreshold, minPush, timeout), 
			patternDownUp(patDownUp, aInThreshold, minPush, timeout),
			patternBack(patBack, aInThreshold, minPush, timeout),
			patternDoubleBack(patDoubleBack, aInThreshold, minPush, timeout),
			patternLongOff(patOnOff, aInThreshold, minPush * 10, timeout),
			patternLongBack(patBack, aInThreshold, minPush * 10, timeout) {
		armed = false;	
	}
	
	void reset() { 
		patternUpDown.reset();
		patternDownUp.reset();
		patternBack.reset();
		patternDoubleBack.reset();
		patternLongOff.reset();
		patternLongBack.reset();
	}
	
	bool instant(int val, int thresh = aInThreshold) { 
		a2.check();
		int diff = a2.value() - val;
		if (diff < 0) 
			diff = -diff;
		return diff < thresh;
	} 
		
	void check() {
		a2.check();
		if (false && a2.value() < aInThreshold) 
			armed = false;
		if (patternBack.check(a2.value())) {
			Serial.print("j 0\n");
			armed = false;
		}
		if (patternUpDown.check(a2.value())) {
			armed = true;
			Serial.print("j 1\n");
		}
		if (patternDownUp.check(a2.value())) {
			Serial.print("j 2\n");
			armed = true;
		}
		if (patternDoubleBack.check(a2.value())) {
			Serial.print("j 4\n");
			armed = true;
		}
		if (patternLongOff.check(a2.value())) {
			Serial.print("j 5\n");
			armed = false;
		}
		if (patternLongBack.check(a2.value())) {
			Serial.print("j 6\n");
			armed = false;
		}
	}
	bool armed;
} joystick;

int trq1 = analogRead(0);
int trq2 = analogRead(1);

int trqInCenter = 500;
int trqInLoThresh = 85;

int trqOutCenter = 128;
int trq1Out, trq2Out;

int doprint = 0;
int trim = 0;
static int tripped = 0;
int reqSteer =0;

void setSteering(int r) {
	reqSteer = r;
	trq1 = analogRead(0);
	trq2 = analogRead(1);
	
	if (trq1 > trqInCenter + trqInHiThresh ||
	    trq1 < trqInCenter - trqInHiThresh ||
	    trq2 > trqInCenter + trqInHiThresh ||
	    trq2 < trqInCenter - trqInHiThresh || 
	    joystick.armed == false) { 
		tripped = 250;
	} else 	if (tripped && 
	    trq1 < trqInCenter + trqInLoThresh &&
	    trq1 > trqInCenter - trqInLoThresh &&
	    trq2 < trqInCenter + trqInLoThresh &&
	    trq2 > trqInCenter - trqInLoThresh) {
		tripped--;
	}

	if (tripped && authority > 0)
		authority--;
	else if (!tripped && authority < 500) 
		authority++;
	reqSteer = (long)reqSteer *  (authority) / 500;

	if (currentSteer - reqSteer <= maxChange &&
		currentSteer - reqSteer >= - maxChange)
		currentSteer = reqSteer;
	else if (reqSteer > currentSteer)
		currentSteer += maxChange;
	else if (reqSteer < currentSteer)
		currentSteer -= maxChange;

	if (tripped /* || (currentSteer < 2 && currentSteer > -2)*/) {
		tristated = 1; 
		pinMode(10, INPUT);
		digitalWrite(10, LOW);	
		pinMode(11, INPUT);
		digitalWrite(11, LOW);
	} else { 
		tristated = 0;
		trq1Out = trqOutCenter + currentSteer + trim;
		trq2Out = trqOutCenter - currentSteer + trim;
		analogWrite(9, trq1Out);
		analogWrite(10, trq2Out);
	}
}

class IgnitionMonitor {
	static const int offDelay = 15;
	EggTimer timer;
	DigitalOutputPin<8> laptopPower;
	bool lastOn;	
	bool changed; // did ignition setting change in current second
public:
	bool startPulseDone;
	int seconds;  // seconds since change
	bool pow;
	IgnitionMonitor() : timer(1000L * TIMER0_MESSED), 
		laptopPower(LOW), 
		seconds(offDelay), lastOn(0), startPulseDone(false) {}
	bool isCarOff() { 
		return lastOn == false && seconds >= offDelay;
	}
	bool loop(bool on, bool cancel) { 
		if (lastOn != on)
			changed = true;
		lastOn = on;
		if (changed)
			seconds = 0;
		if (timer.check()) {
			if (seconds < offDelay * 2) 
				seconds++;
			changed = false;
		}
		
		if (isCarOff()) 
			startPulseDone = false;
		else if (seconds > 5)
			startPulseDone = true;
		
		if (cancel || 
			(!isCarOff() &&	(!startPulseDone && seconds > 2 && seconds < 5))) {
			laptopPower.set(HIGH);
			pow = 0;
		} 	else {
			pow = 1;
			laptopPower.set(LOW);
		}	
	}
};
	
IgnitionMonitor igmon;

static int loopCount = 0;
int p9Level = 0;
int p9Duration = 0;
int debugInterval = 500; 
int cancelRequest = 0;
int seconds = 0;

void loop() {
	parseCommand();
	setSteering(steer);

	if (gotValidCmd && --gotValidCmd == 0)
		steer = 0;

	if (oneSecTimer.check()) {
		seconds++;
		if (gotValidCmd == 0 && (seconds % 3) == 0)  
			led.toggle();
	}
	loopCount++;

	igmon.loop(!joystick.instant(CC_ONOFF), joystick.instant(CC_CANCEL));

	a2.check();
	if (false) { 
		int cancelThresh = 10;
		if (joystick.instant(CC_ONOFF, cancelThresh) || 
			joystick.instant(CC_CANCEL, cancelThresh) ||
			joystick.instant(CC_CANCEL_WITH_UP, cancelThresh) || 
			joystick.instant(CC_CANCEL_WITH_DOWN, cancelThresh)) {
				if (++cancelRequest > 10) 
				joystick.armed = false;
		} else 
			cancelRequest = 0;
	}
		
	if (jiffyTimer.check()) {
		jiffies++;
		if (debugInterval > 0 && jiffies % debugInterval == 0) { 
			Serial.print("a ");
			Serial.print( a2.value());
			Serial.print(" p9l ");
			Serial.print(p9Level);
			Serial.print(" p9d ");
			Serial.print(p9Duration);
			Serial.print(" armed:");
			Serial.print(joystick.armed);
			Serial.print(" req:");	
			Serial.print(reqSteer);
			Serial.print(" auth:");	
			Serial.print(authority);
			Serial.print(" trip:");	
			Serial.print(tripped);
			Serial.print(" trq1i:");	
			Serial.print(trq1);
			Serial.print(" trq2i:");	
			Serial.print(trq2);
			Serial.print(" trq1o:");	
			Serial.print(trq1Out);
			Serial.print(" trq2o:");	
			Serial.print(trq2Out);
			//Serial.print(" ");
			//Serial.print(steer);
			Serial.print(" perrs:");
			Serial.print(protocolErrors);
			Serial.print("\n");
		} 
		static bool lastArmed = false;
		if ((jiffies % 100) == 0 || joystick.armed != lastArmed) { 
			// todo- add ignition information 
			Serial.print("x ");
			Serial.print((int)joystick.armed);
			Serial.print(" i ");
			Serial.print((int)!igmon.isCarOff());
			Serial.print(" s ");
			Serial.print(igmon.seconds);
			Serial.print(" p ");
			Serial.print((int)igmon.pow);
			Serial.print(" s ");
			Serial.print((int)igmon.startPulseDone);
			Serial.print("\n");
			lastArmed = joystick.armed;
		}  


		// if joystick was beeing overriden, clear patter watcher
		if (p9Duration == 1)
			joystick.reset();
		// set pin 9 at p9Level for p9Duration jiffies, then clear
 		if ((p9Duration > 0 && --p9Duration == 0) || joystick.armed == false) 
			p9Level = 0;
		//analogWrite(9, p9Level);

		if (p9Level == 0) // only check joystick if we're not setting it!
			joystick.check();
		//ptnk.check();

	}
}

static int cmdCount = 0;
int readIntWithEcho(int *v) {	
	long intTimeout = 50000UL * TIMER0_MESSED;
	int v1 = serialReadInt(Serial, intTimeout);
	int v2 = serialReadInt(Serial, intTimeout);
	if (v1 == v2) {
		if (cmdCount++ % 1 == 0)
			led.toggle();
		gotValidCmd = 1000;
		*v = v1;
		return 0;
	} else {
		protocolErrors++;
		Serial.print(v1);
		Serial.print("!=");
		Serial.print(v2);
		Serial.print("\n");
	}
	return -1;
}	


void parseCommand() {
	// Please remember there is no space between the command char
	// and the args.  This is the second time this has caused
	// a boondoggle. 
	int v1, v2, a;
    int cmd = Serial.read();
	if (0 && cmd != -1) { 
		Serial.print("cmd ");
		Serial.print(cmd);
		Serial.print("\n");
    }
	if (cmd == 's') {
		a = -500;
		readIntWithEcho(&a);
		if (a != -500)
			steer = a;
	} else if (cmd == 't') { 
		a = -500;
		readIntWithEcho(&a);
		if (a != -500)
			trqInHiThresh = a;
	} else if (cmd == 'e') {
		readIntWithEcho(&a);
		Serial.print("ack ");
		Serial.print(a);
		Serial.print("\n");
	} else if (cmd == 'c') { 
		readIntWithEcho(&p9Level);
		readIntWithEcho(&p9Duration);
	} else if (cmd == 'd') { 
		readIntWithEcho(&debugInterval);
	} else if (cmd == 'a') { 
		a = 0;
		readIntWithEcho(&a);
		joystick.armed = a;
		Serial.print("ARMED\n");
	} else if (cmd != -1) { 
		Serial.print(cmd);
		protocolErrors++;
		Serial.print(" garb?\n");
	}
}

#ifdef CMDLINE_MAKE 
int main(void)
{
        init();
        setup();
        for (;;)
                loop();
        return 0;
}
#endif


