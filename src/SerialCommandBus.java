import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.StringTokenizer;



class SerialCommandBus { 
    FileWriter tty = null;
    String devName; 
    FrameProcessor fp; // TODO- make a clean callback interface rather than a fp member
    SerialCommandBus(String d, FrameProcessor f) { devName = d; fp = f;  }

    byte crc4itu(byte[] bytes) { 
        byte crc = (byte)0xff; // initial value
        // loop, calculating CRC for each byte of the string
        for (int byteIndex = 0; byteIndex < bytes.length; byteIndex++) {
            byte bit = (byte)0x80; // initialize bit currently being tested
            for (int bitIndex = 0; bitIndex < 8; bitIndex++)
            {
                boolean xorFlag = ((crc & 0x8) == 0x8);
                // shift bit into register
                crc <<= 1;
                if (((bytes[byteIndex] & bit) != 0)) 
                    crc++;
                // if last bit out of the register was set, xor register
                if (xorFlag)    {
                    crc ^= (byte)0x3;
                }
                bit >>= 1;
            }
        }
        return crc;
    }
    
    private void open() {
    	boolean complained = false;
    	if (tty != null) {
    		try {
				tty.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
    	}
    	tty = null;

    	while(tty == null) { 
	    	try {
	    	    Process p = Runtime.getRuntime().exec("stty -F " + devName + " 9600 -echo raw");
	    	    p.waitFor();
	        	tty = new FileWriter(devName);
				System.out.println("Opened " + devName + " for writing at 9600bps");
				complained = false;
	        } catch(Exception e) { 
	        	if (!complained) {
	        		e.printStackTrace();
	        		System.out.println("Could not open serial device " + devName + ", retrying...");
	        	}
	        	complained = true;
				tty = null;
				sleep();
	        }   	
    	}
    }

    
    void start() { 
    	if (devName != null) { 
	    	reader.start();
	    	timeout.start();
	    	open();
    	}
    }
    
    void sleep() { 
    	try {
			Thread.sleep(50);
		} catch (InterruptedException e) {}
    }
    
    Thread timeout = new Thread (new Runnable() {
        public void run() {
        	boolean complained = false;
        	while(true) { 
        		sleep();
        		int s = ack > seq ? seq + 1024 : seq;
        		if (s - ack > 5) {
        			// TODO- interrupt current read and write, then
        			// reset tUhe USB bus and/or the usbserial module
        			if (!complained) 
        				System.out.println("Overdue ACK from " + devName);
        			complained = true;
    				//reader.interrupt();
        		} else {
        			if (complained) 
        				System.out.println("Got fresh ACK from " + devName);
        			complained = false;
        		}
        	}
        }
    });
    
    
    Thread reader = new Thread (new Runnable() {
        public void run() {
        	while(true) { 
        		BufferedReader fin;
				try {
					fin = new BufferedReader(new FileReader(devName));
				} catch (FileNotFoundException e) {
					//e.printStackTrace();
					sleep();
					continue;
				}
        		System.out.println("Opened " + devName + " for reading");
                while(true) { 
                	String s = null;
					try {
						s = fin.readLine();
					} catch (IOException e) {
						e.printStackTrace();

					}	
					//System.out.println("done w read");
					if (s == null) {
						try {
							fin.close();
						} catch (IOException e) {}
						break;
					}
					if (Silly.debug("DEBUG_SERIAL"))
						System.out.println("Serial read: " + s);	
					
					StringTokenizer st = new StringTokenizer(s);
					try { 
						if (st.hasMoreTokens()) {
							String c = st.nextToken();
							if (c.equals("ack") && st.hasMoreTokens()) {
								ack = Integer.parseInt(st.nextToken());
								System.out.println("ACK " + ack);
							}
							if (c.equals("j") && st.hasMoreTokens()) {
								int a = Integer.parseInt(st.nextToken());
								fp.onCruiseJoystick(a);
							}
							if (c.equals("x") && st.hasMoreTokens()) {
								int a = Integer.parseInt(st.nextToken());
								//fp.onArduinoArmed(a);
							}
							if (c.equals("a") && st.hasMoreTokens()) {
								int a = Integer.parseInt(st.nextToken());
								if (a == 0)  
									ignitionOffCount++;
								else
									ignitionOffCount = 0;
							}
						}
					} catch(Exception e) {}
                }
        	}
        }
    });
    String lastDebugString = "";
    int ignitionOffCount = 0;
    void writeCmd(String s) {
        try {
        	if (tty != null) {
		    	//System.out.print("to serial: " + s + "\n");
	        	tty.write(s);
	        	tty.flush();
        	}
        } catch (IOException e) {
			e.printStackTrace();
        	do { 
        		open();
        	} while(tty == null);
    	}
    }    	
    
    void writeCmd(char cmd, int arg) {
    	writeCmd(String.format("%c%d %d\n", cmd, arg, arg));
    }

    void writeCmd(char cmd, int arg1, int arg2) {
    	writeCmd(String.format("%c%d %d %d %d\n", cmd, arg1, arg1, arg2, arg2));
    }

    int seq = 0, ack = 0;
	public void requestAck() {
		writeCmd('e', seq);
		seq = (seq + 1) % 1024;
	}	

}
