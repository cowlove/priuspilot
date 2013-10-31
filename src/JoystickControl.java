import java.io.IOException;
import java.util.Calendar;

import com.centralnexus.input.Joystick;

class ButtonDebounce { 
	long lastPress = 0;
	boolean lastState= true;
	int minTime = 200;
	
	public ButtonDebounce(int i) {
		minTime = i;
	}

	boolean pressed(boolean raw) { 
		long now = Calendar.getInstance().getTimeInMillis();
		if (raw && lastState == false && now - lastPress > minTime) {
			lastPress = now;
			lastState = true;
			return true;
		}
		lastState = raw;
		return false;		
	}
}

class JoystickControl {
	Joystick joystick;
	double lowGain = 0.25;
	double hiGain = 0.45;
	double gainStep = 0.001;
	JoystickControl() { 
       try {
			joystick = Joystick.createInstance();
		} catch (IOException e) {
			System.out.println("No joystick found.");
			joystick = null;
		}

	}
	int buttons = 0;
	boolean getExit() { 
		final int EXIT_CODE = L1 | L2 | R1 | R2;
		return (buttons & EXIT_CODE) == EXIT_CODE; 
	}
	boolean getDisarm() { 
		return (buttons & BUTTON_EX) == BUTTON_EX;
	}
	boolean getArm() { 
		return (buttons & BUTTON_TRIANGLE) == BUTTON_TRIANGLE;
	}
	static final int L1 = 0x40, R1 = 0x80, L2 = 0x10, R2 = 0x20;
	static final int BUTTON_TRIANGLE = 0x8, BUTTON_EX = 0x2, BUTTON_SQUARE = 0x1, BUTTON_CIR = 0x2;
	
	double steer(double in) { 
		double steer = in;
        if (joystick != null) {
        	joystick.poll();
        	buttons = joystick.getButtons();
           	if ((buttons & L1) != 0)  // L1 - engage right joystick
           		steer = joystick.getX() * lowGain;
           	if ((buttons & R1) != 0)  // R1 - engage right joystick
           		steer = joystick.getZ() * lowGain;
           	if ((buttons & L2) != 0)  // L1 - engage right joystick
           		steer = joystick.getX() * hiGain;
           	if ((buttons & R2) != 0)  // R1 - engage right joystick
           		steer = joystick.getZ() * hiGain;
           	
           	// triangle and X buttons increase/decrease currently-used gain
           	if ((buttons & BUTTON_TRIANGLE) != 0) {
           	   	if ((buttons & (L1 | R1)) != 0)  
               		lowGain += gainStep;
               	if ((buttons & (L2 | R2)) != 0)  
               		hiGain += gainStep;
           	}           	
           	if ((buttons & BUTTON_EX) != 0) {
           	   	if ((buttons & (L1 | R1)) != 0) 
               		lowGain -= gainStep;
               	if ((buttons & (L2 | R2)) != 0)  
               		hiGain -= gainStep;        
           	}
    
           	
           	if (joystick.getU() < -0.1) {
           		System.out.print("LEFT BLINKER");
           	}
           	if (joystick.getU() > 0.1) { 
           		System.out.print("RIGHT BLINKER");
           	}
        }
        
        
		return steer;
	}
	
	
	ButtonDebounce recButton = new ButtonDebounce(500);
	public boolean getRecordButtonPressed() { 
		return recButton.pressed((buttons & BUTTON_SQUARE) != 0); 
	}
	
	public double getThrottleChange() {
		if (joystick != null) { 
	      	joystick.poll();
	    	buttons = joystick.getButtons();
	    	if ((buttons & (L1 | L2 | R1 | R2)) != 0)
	    		return -joystick.getV();
	    	else 
	    		return 0.0;
		} else
			return 0.0;
	}
}