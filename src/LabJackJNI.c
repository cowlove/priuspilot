#include "LabJackJNI.h"
#include "u3.h"
#include <unistd.h>

#include "u3.c"

/*
gcc LabJackJNI.c -I /usr/lib/jvm/java-6-openjdk/include/ -o  liblabjackpwm.so -lm -llabjackusb -shared
*/

int initLabJack(void);
HANDLE hDevice = NULL;
u3CalibrationInfo caliInfo;
long firstCall = 1;

JNIEXPORT void JNICALL Java_LabJackJNI_setPWM
  (JNIEnv *a, jobject b, jint c, jdouble v1, jdouble v2) {
	static int init = 0;
	long error = 0;
//	printf("setPWM(%d)\n", (int)c);

	if (init == 0) { 
		init = 1;
		initLabJack();
	}


        if (hDevice == NULL)
            return;
    //Read and reset the input timer (Timer1), read and reset Counter0, and update the
    //value (duty-cycle) of the output timer (Timer0)
  //  printf("\nCalling eTCValues to read and reset the input Timer1 and Counter0, and update the value (duty-cycle) of the output Timer0\n");
    long alngReadTimers[2] = {0, 1};  //Read Timer1
    long alngUpdateResetTimers[2] = {1, 0};  //Update timer0
    long alngReadCounters[2] = {1, 0};  //Read Counter0
    long alngResetCounters[2] = {0, 0};  //Reset no Counters
    double adblCounterValues[2] = {0, 0};
    double adblTimerValues[2] = {(double)65535 * c / 1000, 0};  //Change Timer0 duty-cycle%
    if((error = eTCValues(hDevice, alngReadTimers, alngUpdateResetTimers, alngReadCounters, alngResetCounters, adblTimerValues, adblCounterValues, 0, 0)) != 0)
       goto close;
    //printf("Timer0 value = %.0f\n", adblTimerValues[0]);
    //printf("Counter0 value = %.0f\n", adblCounterValues[0]);

   // Set DAC0
   if((error = eDAC(hDevice, &caliInfo, firstCall, 0, v1, 0, 0, 0)) != 0)
        goto close;


   // Set DAC1
   if((error = eDAC(hDevice, &caliInfo, firstCall, 1, v2, 0, 0, 0)) != 0)
        goto close;


   firstCall = 0;
   return;
close:
    printf("Ouch!\n");
    hDevice = NULL;
    return;


}

JNIEXPORT jdouble JNICALL Java_LabJackJNI_getAIN
  (JNIEnv *a, jobject b, jint pin) {
   long DAC1Enable =1;
   double dblVoltage;
   int error;

   // special values for negative pin- 31 single ended, 32 special 2vref range
   if((error = eAIN(hDevice, &caliInfo, 1, &DAC1Enable, pin, 31, &dblVoltage, 0, 0, 0, 0, 0, 0)) != 0)
        goto close;
    //printf("AIN%d value = %.3f\n", pin, dblVoltage);

    return dblVoltage;
close:
    printf("AIN Ouch!\n");
    hDevice = NULL;
    return;


}


int initLabJack(void)
{
    int localID;
    long DAC1Enable, error;

    system("/usr/local/bin/chmodusb");

    //Open first found U3 over USB
    localID = -1;
    if( (hDevice = openUSBConnection(localID)) == NULL)
        goto done;

    //Get calibration information from UE9
    if(getCalibrationInfo(hDevice, &caliInfo) < 0)
        goto close;
  
    //Enable and configure 1 output timer and 1 input timer, and enable counter0
    printf("\nCalling eTCConfig to enable and configure 1 output timer (Timer0) and 1 input timer (Timer1), and enable counter0\n");
    long alngEnableTimers[2] = {1, 1};  //Enable Timer0-Timer1
    long alngTimerModes[2] = {LJ_tmPWM16, LJ_tmRISINGEDGES32};  //Set timer modes
    double adblTimerValues[2] = {32768, 0};  //Set PWM8 duty-cycles to 75%
    long alngEnableCounters[2] = {1, 0};  //Enable Counter0
    if((error = eTCConfig(hDevice, alngEnableTimers, alngEnableCounters, 4, LJ_tc4MHZ, 0, alngTimerModes, adblTimerValues, 0, 0)) != 0)
        goto close;



    printf("OK!\n");
    return;

done:
close:

    printf("Ouch!\n");
    hDevice = NULL;
    return;

}

