#include "jimlib.h"
//#include "AsyncUDP.h"
#include <esp_now.h>
#include <esp_wifi.h>
#include <esp_private/esp_wifi_private.h>

JStuff j;

int pktCount = 0;
uint8_t broadcastAddress[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

void EspNowOnDataRecv(const uint8_t * mac, const uint8_t *in, int len);

void EspNowOnDataSent(const uint8_t *mac_addr, esp_now_send_status_t status) {
}
#define TRY_ESP_ACTION(action, name) if(action == ESP_OK) {Serial.println("\t+ "+String(name));} else {Serial.println("Error: " + String(name));}
#define CHANNEL 6
#define DATARATE WIFI_PHY_RATE_24M

void EspNowInit() { 
        WiFi.mode(WIFI_STA);
        TRY_ESP_ACTION(esp_wifi_stop(), "stop WIFI");  
        TRY_ESP_ACTION(esp_wifi_deinit(), "De init");
        wifi_init_config_t my_config = WIFI_INIT_CONFIG_DEFAULT();
        my_config.ampdu_tx_enable = 0;
        TRY_ESP_ACTION(esp_wifi_init(&my_config), "Disable AMPDU");
        TRY_ESP_ACTION(esp_wifi_start(), "Restart WiFi");
        TRY_ESP_ACTION(esp_wifi_set_channel(CHANNEL, WIFI_SECOND_CHAN_NONE), "Set channel");
        TRY_ESP_ACTION(esp_wifi_config_espnow_rate(WIFI_IF_AP, DATARATE), "Fixed rate set up");
        TRY_ESP_ACTION(esp_now_init(), "ESPNow Init");
        TRY_ESP_ACTION(esp_now_register_send_cb(EspNowOnDataSent), "Attach send callback");
        TRY_ESP_ACTION(esp_now_register_recv_cb(EspNowOnDataRecv), "Attach recv callback");
        esp_now_peer_info_t peerInfo;
        bzero(&peerInfo, sizeof(peerInfo));
	memcpy(peerInfo.peer_addr, broadcastAddress, 6);
        peerInfo.channel = CHANNEL;  
        peerInfo.encrypt = false;
        TRY_ESP_ACTION(esp_now_add_peer(&peerInfo), "esp_now_add_peer()");
}

int finished = 0;
struct {
        uint8_t ack = 0xaa;
        uint8_t cmd = 0;
        uint8_t seq = 0;
        uint8_t pad[5];
        uint64_t rand = 0;
//      int x2 = 0xeeeeeeee;
} myData;



struct {
	int led = getLedPin(); 
	int pwm1 = 17;
	int pwm2 = 16;
} pins;

//AsyncUDP udpCmd;

PwmChannel pwm1(pins.pwm1, 1200/*hz*/, 2/*chan*/, 0/*gradual*/);
PwmChannel pwm2(pins.pwm2, 1200/*hz*/, 1/*chan*/, 0/*gradual*/);

CLI_VARIABLE_FLOAT(maxSteer, 0.6);

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
	float limit = maxSteer;
	d = min(limit, max(-limit, d)); 
	int t1 = ex.extrapolate(2.50 - d);
	int t2 = ex.extrapolate(2.50 + d);
	OUT("set pwm %d %d", t1, t2);
	pwm1.setMs(t1);
	pwm2.setMs(t2);
}

LineBuffer lb;
float steerCmd = 0;

float pwm = 0, pwmms = 0;
void EspNowOnDataRecv(const uint8_t * mac, const uint8_t *in, int len) {
	string s((const char *)in, len);
	OUT("ESPNOW got %s", s.c_str());
	sscanf(s.c_str(), "TPWM %f %f", &pwm, &pwmms);
}	

void setup() { 
	setDeg(0);
	j.jw.enabled = false;
	j.cliEcho = false;
	j.mqtt.active = false;
	j.onConn = []{};
	j.cli.on("NO-PPDEG ([-0-9.]+) ([-0-9.]+)", [](const char *s, smatch m){ 
		float f1, f2;
		OUT("PPDEG %s", s);
		if (m.size() > 2 && 
			sscanf(m.str(1).c_str(), "%f", &f1) == 1 &&
			sscanf(m.str(2).c_str(), "%f", &f2) == 1 && 
			f1 == f2) {
			steerCmd = -f1;
			setDeg(steerCmd);
		}
		return "";
	});
	j.cli.on("TPWM ([-0-9.]+) ([-0-9.]+)", [](const char *s, smatch m){ 
		float pwm, t;
		OUT("TPWM %s", s);
		if (m.size() > 2 && 
			sscanf(m.str(1).c_str(), "%f", &pwm) == 1 &&
			sscanf(m.str(2).c_str(), "%f", &t) == 1) {
			pwm1.set(pwm);
			pwm2.set(pwm);
			delay(t);
			pwm1.set(0);
			pwm2.set(0);
		}
		return "";
	});
	j.cli.on("PWM ([-0-9.]+)", [](const char *s, smatch m){ 
		float f = 0;
		OUT("cli got %s", s);
		if (m.size() > 1 && sscanf(m.str(1).c_str(), "%f", &f) == 1) {
			pwm1.set(f);
			pwm2.set(f);
		}
		return "";
	});
	j.cli.on("TIMING", [](const char *s, smatch m){ 
		float f = 0;
		pwm1.set(65535);
		pwm2.set(65535);
		delay(100);
		pwm1.set(0);
		pwm2.set(0);
		Serial.printf("TIMING:\n");
		for (int i = 0; i < 100; i++) { 
			Serial.printf("%03d %05d\n", i * 10, analogRead(34));
			delay(10);
		}
		return "";
	});
	j.cli.on("SAMPLE", [](const char *s, smatch m){ 
		Serial.printf("SAMPLE:\n");
		for (int i = 0; i < 100; i++) { 
			Serial.printf("%03d %05d\n", i * 10, analogRead(34));
			delay(10);
		}
		return "";
	});
	j.cli.on("TABLE", [](const char *s, smatch m){ 
		Serial.printf("TABLE:\n");
		for (int pwm = 0; pwm < 30000; pwm += 100) { 
			pwm1.set(pwm);
			pwm2.set(pwm);
			delay(250);
			Serial.printf("%03d %05d\n", pwm, analogRead(34));
			esp_task_wdt_reset();
		}
		return "";
	});
	j.cli.on("ADC", [](const char *s, smatch m){ 
			Serial.printf("ADC %05d\n", analogRead(34));
		return "";
	});
	j.begin();
	EspNowInit();
}

void EspNowSend(const String x) { 
	esp_now_send(broadcastAddress, (uint8_t *) x.c_str(), x.length());
}

void loop() {
	j.run();
	delay(1);
	yield();
	if (j.hz(5)) { 
		//steerCmd = steerCmd * 0.8;
		//setDeg(steerCmd);
	}
	if (pwm > 0) { 
		OUT("PWM %d for %d ms", (int)pwm, (int)pwmms);
		pwm1.set((int)pwm);
		pwm2.set((int)pwm);
		delay((int)pwmms);
		pwm = pwmms = 0;
		pwm1.set(0);
		pwm2.set(0);
	}  
}
