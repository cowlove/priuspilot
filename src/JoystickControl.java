import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;


import com.centralnexus.input.Joystick;

class JoystickX { 
	static public Joystick createInstance(int n) throws IOException { return null; } 
	void poll() {}
	int getButtons() { return 0; } 
	int getV() { return 0; } 
	int getU() { return 0; } 
	int getX() { return 0; } 
	int getY() { return 0; } 
	int getZ() { return 0; } 
}	

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
	ArrayList<Joystick>  joysticks = new ArrayList<Joystick>();
	double lowGain = 0.40;
	double hiGain = 0.45;
	double gainStep = 0.001;
	double trim = 0.0;
	JoystickControl() { 
		Joystick j = null;
		int n = 0;
		try { 
			while((j = Joystick.createInstance(n++)) != null)
					joysticks.add(j);
		} catch (IOException e) {
		}
	    System.out.printf("%d joysticks found\n", joysticks.size());
	}
	boolean joystickPresent() { return joysticks.size() > 0; }
	int buttons = 0;
	boolean getExit() { 
		final int EXIT_CODE = L1 | L2 | R1 | R2;
		return (buttons & EXIT_CODE) == EXIT_CODE; 
	}
	static final int L2 = 0x10, R2 = 0x20, L1 = 0x0, R1 =0x0;
	static final int BUTTON_TRIANGLE = 0x8, BUTTON_EX = 0x2, BUTTON_SQUARE = 0x1, BUTTON_CIR = 0x4;
	static final int BUTTON_START=0x80, BUTTON_BACK=0x40;
	
	
	boolean armed = false;
	boolean isArmed() { 
		if ((buttons & BUTTON_SQUARE) != 0)
			armed = true;
		else if ((buttons & BUTTON_EX) != 0)
			armed = false;
		return armed;
	}
	double steer(double in) {
		double steer = in;
		for (Joystick joystick : joysticks) { 
	        if (joystick != null) {
	        	joystick.poll();
	        	buttons = joystick.getButtons();
	    		if (Silly.debug("PRINT_JOYSTICK_BUTTONS") && buttons != 0x0){ 
	        		System.out.printf("buttons 0x%x\n", buttons);
	    			System.out.printf("%f %f %f %f %f %f %f\n", joystick.getX(), 
	    					joystick.getY(), joystick.getZ(), 
	    					joystick.getR(), joystick.getU(), joystick.getV(),
	    					joystick.getPOV());
	    		}
	           	if ((buttons & 0x10) != 0)  // L1 - engage right joystick
	           		steer = joystick.getX() * lowGain;
	           	if ((buttons & R1) != 0)  // R1 - engage right joystick
	           		steer = joystick.getR() * lowGain;
	           	if ((buttons & 0x20) != 0)  // L1 - engage right joystick
	           		steer = joystick.getX() * hiGain;
	           	if ((buttons & R2) != 0)  // R1 - engage right joystick
	           		steer = joystick.getR() * hiGain;
	           	
	           	if (false && isArmed()) { 
	           		steer = joystick.getX() * lowGain;
	           		if (Math.abs(joystick.getX()) >= 1.0)
	           			steer += joystick.getZ() * lowGain * 0.5;
	           	}
	           	
	           	if (false && (buttons & (R1 | R2 | L1 | L2)) != 0) { 
	           		System.out.printf("axis x=%.2f y=%.2f z=%.2f\n", 
	           				joystick.getX(), joystick.getY(), joystick.getZ());
	           		
	           	}
	           	// triangle and X buttons increase/decrease currently-used gain
	           	if ((buttons & BUTTON_TRIANGLE) != 0) {
	           	   	if (isArmed() || (buttons & (L1 | R1)) != 0)  
	               		lowGain += gainStep;
	               	if ((buttons & (L2 | R2)) != 0)  
	               		hiGain += gainStep;
	       			System.out.printf("Gain %.3f/%.3f\n", lowGain, hiGain);
	                       	
	           	}           	
	           	if ((buttons & BUTTON_CIR) != 0) {
	           	   	if (isArmed() || (buttons & (L1 | R1)) != 0) 
	               		lowGain -= gainStep;
	               	if ((buttons & (L2 | R2)) != 0)  
	               		hiGain -= gainStep;        
	       			System.out.printf("Gain %.3f/%.3f\n", lowGain, hiGain);
	           	}
	    
		    	if ((buttons & (L1 | L2 | R1 | R2)) != 0) { 
		           	double trimStep = 0.001;
		           	if (joystick.getY() < -0.1) {
		           		trim -= trimStep;
		           		//System.out.print("LEFT BLINKER");
		           		System.out.printf("trim %.3f\n", trim);
		           	}
		           	if (joystick.getY() > 0.1) { 
		           		//System.out.print("RIGHT BLINKER");
		           		trim += trimStep;
		           		System.out.printf("trim %.3f\n", trim);
		           	}
	
		    	}
	        }
		}
		return steer;
	}
	
	
	ButtonDebounce recButton = new ButtonDebounce(500);
	public boolean getRecordButtonPressed() {
		return recButton.pressed((buttons & BUTTON_START) != 0); 
	}
	
	public double getThrottleChange() {
		for (Joystick joystick : joysticks) { 
			if (joystick != null) { 
		      	joystick.poll();
		    	buttons = joystick.getButtons();
		    	//if ((buttons & (L1 | L2 | R1 | R2)) != 0)
		    	//	return -joystick.getV();
		 	}
		}
		return 0.0;
	}
}
