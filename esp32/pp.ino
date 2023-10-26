#include "jimlib.h"
#include "AsyncUDP.h"
JStuff j;

struct {
	int led = getLedPin(); 
	int pwm1 = 17;
	int pwm2 = 16;
} pins;

AsyncUDP udpCmd;

PwmChannel pwm1(pins.pwm1, 1200/*hz*/, 2/*chan*/, 0/*gradual*/);
PwmChannel pwm2(pins.pwm2, 1200/*hz*/, 1/*chan*/, 0/*gradual*/);

CLI_VARIABLE_FLOAT(setTemp, 5);
CLI_VARIABLE_FLOAT(hist, 0.05);
CLI_VARIABLE_INT(useLED, 0);

string testHook("hi");
float lastTemp = 0.0, lastSetTemp = 0.0;

struct ExtrapolationTable<float>::Pair table[] = {
	{-10.00, 8500},
	{1.12, 8500},
	{1.25, 9500},
	{1.38, 10500},
	{1.45, 11000},
	{1.50, 11400},
	{1.58, 12000}, 
	{1.71, 13000}, 
	{1.80, 13700},
	{2.00, 15200},
	{2.15, 16350}, 
	{2.30, 17450},
	{2.40, 18150}, 
	{2.50, 18900},
	{2.60, 19550}, 
	{2.70, 20100}, 
	{2.80, 20460}, 
	{2.90, 20535},
	{3.00, 20565},
	{3.10, 20590}, 
	{3.20, 20615}, 
	{3.30, 20637}, 
	{3.40, 20658}, 
	{3.50, 20679},
	{3.64, 20700},
	{3.75, 20720},
	{3.86, 20730},

	{10, 20730}};

ExtrapolationTable<float> ex(table);

void setDeg(float d) { 
	int t1 = ex.extrapolate(2.50 - d);
	int t2 = ex.extrapolate(2.50 + d);
	pwm1.setMs(t1);
	pwm2.setMs(t2);
}

LineBuffer lb;

void setup() {
	setDeg(0);
	j.mqtt.active = false;
	j.begin();
	j.cli.hookVar("HOOK", &testHook);
	j.cli.on("PWM ([-0-9.]+)", [](const char *, smatch m){ 
		if (m.size() > 1) { 
			pwm1.setMs(atoi(m.str(1).c_str()));
			pwm2.setMs(atoi(m.str(1).c_str()));
		}

		return strfmt("%f", pwm1.get()); 
	});
	j.cli.on("DEG ([-0-9.]+)", [](const char *s, smatch m){ 
		float d;
		OUT("got %s", s);
		if (sscanf(s, "DEG %f", &d) == 1) {
			OUT("setSeg(%d)", d);
			setDeg(d); 
		}
		return strfmt("%f", pwm1.get()); 
	});
	j.cli.on("GRADUAL ([0-9]+)", [](const char *, smatch m) { 
		if (m.size() > 1) 
			pwm1.gradual = atoi(m.str(1).c_str());
		return strfmt("%d", pwm1.gradual); 
	});
	j.onConn = [](){ 
		udpCmd.listen(7788); 
		udpCmd.onPacket([](AsyncUDPPacket packet) {
			float f; 
			long t;
			OUT("GOT %d bytes", packet.length());
			if (sscanf((const char*)packet.data(), "PPDEG %f %ld", &f, &t) == 2) { 
				OUT("DEG %f %d", f, t);
				setDeg(-f);	
			}	
		});
	};
	delay(1000);
	WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0); //disable brownout detector   

}

void processUdp(WiFiUDP *udp, LineBuffer *lb, std::function<void(const char *)>f) { 
	while(udp->parsePacket() > 0) {
		char buf[4096]; 
		int n;
		while(n = udp->read(buf, sizeof(buf)) > 0) { 
			OUT("got %d");
			lb->add(buf, n, f);

		}
	}
}

void loop() {
	j.run();
	delay(1);
	yield();  
}
