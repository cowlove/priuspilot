#ifndef _lcd_h_
#define _lcd_h_
#include "seriallib.h"
#include <WConstants.h>
#include <wiring.h>

/*
 * Lcd extends BufferedSoftwareSerial to add commands for controlling
 * a SparkFun serial LCD module.  Hardcoded to run LCD at 2400bps
 * so software serial output is more tolerant of interrupts
 */ 
class Lcd : public BufferedSoftwareSerial<32> {
    typedef BufferedSoftwareSerial<32> Parent;
public:
    Lcd(uint8_t txPin) : Parent(txPin, txPin) { pinMode(txPin, OUTPUT); }
    void begin() {
	Parent::begin(9600);   // configure serial port to 9600BPS
	printbyte(18); send(); // reset LCD with a 9600BPS ^R char
	delay(1);
	cmd(124, 11); send();  // command LCD to 2400BPS 
	delay(1);
	Parent::begin(2400);   // configure serial port to 2400BPS
    }
    void cmd(int b1, int b2) { printbyte(b1); printbyte(b2); }
    void clear() { cmd(0xfe, 0x01); }
    void position(int x, int y) { cmd(0xfe, 128 + x + y * 64); }
    void backlight(int pcnt) { cmd(0x7c, 128 + ((pcnt * 29 / 100) % 30)); }
    void scrollRight() { cmd(0xFE, 0x1c); } 
    void scrollLeft() { cmd(0xFE, 0x18); } 
    void cursorRight() { cmd(0xFE, 0x14); } 
    void cursorLeft() { cmd(0xFE, 0x10); } 
    void cursorOff() { cmd(0xFE, 0x0C); } 
    void cursorOn(uint8_t box = 1) { cmd(0xFE, box ? 0x0D : 0x0E); } 
};


#endif
