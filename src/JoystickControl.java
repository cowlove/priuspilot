import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

import javax.speech.recognition.FinalResult;

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
	
	public ButtonDebounce() { 
		minTime = 100;
	}
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
	double lowGain = 1.0;
	double hiGain = 0.45;
	double gainStep = 0.001;
	public double steerAssist = 0;
	JoystickControl() { 
		Joystick j = null;
		buttonDebounce = new ButtonDebounce[18];
		for (int i = 0; i < 18; i++) 
			buttonDebounce[i] = new ButtonDebounce();
		int n = 0;
		try { 
			while((j = Joystick.createInstance(n++)) != null)
					joysticks.add(j);
		} catch (IOException e) {
		}
	    System.out.printf("%d joysticks found\n", joysticks.size());
	}
	boolean joystickPresent() { return joysticks.size() > 0; }
	int buttonBits = 0;

	
	boolean getExit() { 
		final int EXIT_CODE = L1 | L2 | R1 | R2;
		return (buttonBits & EXIT_CODE) == EXIT_CODE; 
	}
	
	static final int L2 = 0x10, R2 = 0x20, L1 = 0x40, R1 =0x80;
	static final int BUTTON_TRIANGLE = 0x8, BUTTON_EX = 0x2, BUTTON_SQUARE = 0x1, BUTTON_CIR = 0x4;
	static final int BUTTON_START=0x80, BUTTON_BACK=0x40;
	
	static final int BUTTON_REC = 0x200;

	
	
	boolean isArmed() { 
		return false;
	}
	double steer(double in) {
		double steer = in;
		for (Joystick joystick : joysticks) { 
	        if (joystick != null) {
	        	joystick.poll();
	        	buttonBits = joystick.getButtons();
	    		if (Silly.debug("PRINT_JOYSTICK_BUTTONS") && buttonBits != 0x0){ 
	        		System.out.printf("buttons 0x%x\n", buttonBits);
	    			System.out.printf("%f %f %f %f %f %f %f\n", joystick.getX(), 
	    					joystick.getY(), joystick.getZ(), 
	    					joystick.getR(), joystick.getU(), joystick.getV(),
	    					joystick.getPOV());
	    		}
				//if ((buttonBits & (L2 | R2 | L1)) == 0) // TMPonly allow steer if buttons pressed 
				//	steer = 0;
				if ((buttonBits & L1) != 0) {  // L1 - engage right joystick
	           		steerAssist = joystick.getX() * lowGain;
					steer += steerAssist;
				} else { 
					steerAssist = 0;
				}
	           	if ((buttonBits & R1) != 0)  // R1 - engage right joystick
	           		steer = joystick.getZ() * lowGain;

				/* 
	           	if (false && isArmed()) { 
	           		steer = joystick.getX() * lowGain;
	           		if (Math.abs(joystick.getX()) >= 1.0)
	           			steer += joystick.getZ() * lowGain * 0.5;
	           	}
	           	
	           	if (false && (buttonBits & (R1 | R2 | L1 | L2)) != 0) { 
	           		System.out.printf("axis x=%.2f y=%.2f z=%.2f\n", 
	           				joystick.getX(), joystick.getY(), joystick.getZ());
	           		
	           	}
	           	// triangle and X buttons increase/decrease currently-used gain
	        	if ((buttonBits & BUTTON_TRIANGLE) != 0) {
	           	   	if (isArmed() || (buttonBits & (L1 | R1)) != 0)  
	               		lowGain += gainStep;
	               	if ((buttonBits & (L2 | R2)) != 0)  
	               		hiGain += gainStep;
	       			System.out.printf("Gain %.3f/%.3f\n", lowGain, hiGain);
	                       	
	           	}           	
	           	if ((buttonBits & BUTTON_CIR) != 0) {
	           	   	if (isArmed() || (buttonBits & (L1 | R1)) != 0) 
	               		lowGain -= gainStep;
	               	if ((buttonBits & (L2 | R2)) != 0)  
	               		hiGain -= gainStep;        
	       			System.out.printf("Gain %.3f/%.3f\n", lowGain, hiGain);
	           	}
	    		*/
	        }
		}
		return steer;
	}
	
	boolean safetyButton() { 
		return (buttonBits & (L2 | R2 | L1 | R2)) != 0;
	}

	ButtonDebounce buttonDebounce[];
	public boolean getButtonPressed(int b) {
		for (Joystick joystick : joysticks) { 
	        if (joystick != null) {
	        	joystick.poll();
				if (b >= 0 && b < 10) 
					return buttonDebounce[b].pressed((buttonBits & (0x1 << b)) != 0); 
				else if (b == 10)  
					return buttonDebounce[b].pressed(joystick.getU() == -1);
				else if (b == 11)
					return buttonDebounce[b].pressed(joystick.getU() == 1);
				else if (b == 12)  
					return buttonDebounce[b].pressed(joystick.getV() == 1);
				else if (b == 13)
					return buttonDebounce[b].pressed(joystick.getV() == -1);
				else if (b == 14)  
					return buttonDebounce[b].pressed(joystick.getZ() == -1);
				else if (b == 15)
					return buttonDebounce[b].pressed(joystick.getZ() == 1);
				else if (b == 16)  
					return buttonDebounce[b].pressed(joystick.getR() == 1);
				else if (b == 17)
					return buttonDebounce[b].pressed(joystick.getR() == -1);
			}
		}
		return false;
	}

			
		
	
	public double getThrottleChange() {
		for (Joystick joystick : joysticks) { 
			if (joystick != null) { 
		      	joystick.poll();
		    	buttonBits = joystick.getButtons();
		    	//if ((buttons & (L1 | L2 | R1 | R2)) != 0)
		    	//	return -joystick.getV();
		 	}
		}
		return 0.0;
	}
}
