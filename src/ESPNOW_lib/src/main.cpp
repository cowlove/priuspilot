/*
Etienne Arlaud
*/

#include <stdint.h>
#include <stdio.h>

#include <assert.h>
#include <unistd.h>
#include <sys/time.h>

#include <thread>

#include "ESPNOW_manager.h"
#include "ESPNOW_types.h"

using namespace std;

//static uint8_t my_mac[6] = {0xF8, 0x1A, 0x67, 0xb7, 0xEB, 0x0B};
static uint8_t dest_mac[6] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
//static uint8_t ESP_mac[6] = {0xB4,0xE6,0x2D,0xB5,0x9F,0x85};
//static uint8_t ESP_mac[6] =  {0xAC,0x67,0xB2,0x36,0x8D,0xFC};

static uint8_t my_mac[6] = {0x00,0x13,0xef,0x80,0x26,0x22};
//00:13:ef:80:26:22
ESPNOW_manager *handler;

uint8_t payload[127];


long usec()
{
	struct timeval tv;
	gettimeofday (&tv, NULL);
	return tv.tv_sec * 10000000 + tv.tv_usec;
}

void callback(uint8_t src_mac[6], uint8_t *data, int len) {
	printf("%ld received\n", usec());
	printf("callback() got %d bytes: ", len);
	for(int n = 0; n < len; n++)  
		printf("%02x", data[n]);
	printf("\n");
}

int main(int argc, char **argv) {
	assert(argc > 1);

	//nice(-20);

	handler = new ESPNOW_manager(argv[1], DATARATE_24Mbps, CHANNEL_freq_6, my_mac, dest_mac, false);
	//handler->set_filter(ESP_mac, dest_mac);

	handler->set_recv_callback(&callback);
	handler->start();

	struct {
		int32_t goo = 1234567;
		int32_t goo2 = 1234567;
	} bufx;
        char buf[256];
	while(1) {
		sleep(1);
		usleep(10000);
		snprintf(buf, sizeof(buf), "%d\n", bufx.goo2++);
		int r = handler->send((uint8_t*)&buf, strlen(buf));
		printf("%ld send\n", usec());
		std::this_thread::yield();
	}

	handler->end();
}
