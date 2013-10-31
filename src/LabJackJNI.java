
public class LabJackJNI {
	public native void setPWM(int pwm, double v1, double v2);
	public native double getAIN(int pin);
	static { System.loadLibrary("labjackpwm"); }	
}
