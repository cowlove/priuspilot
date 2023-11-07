import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.StringTokenizer;



class SerialCommandBus { 
    FileWriter tty = null;
	BufferedReader fakeFile = null; 
    String devName; 
    FrameProcessor fp; // TODO- make a clean callback interface rather than a fp member
    SerialCommandBus(String d, FrameProcessor f) { devName = d; fp = f;  }

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
	    	    Process p = Runtime.getRuntime().exec("/home/jim/src/gpsd/gpsd-3.25.1~dev/clients/ubxtool -f " + devName + " -p CFG-RATE,100");
	    	    p.waitFor();
	    	    p = Runtime.getRuntime().exec("stty -F " + devName + " 921600 sane -echo raw");
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

	void startFake(String fn) { 
		try {
            fakeFile = new BufferedReader(new FileReader(fn));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
	}

	long startMs = 0;
	void update(long ms) { 
		if (startMs == 0)
			startMs = ms;
		while (fakeFile != null) { 
			try {
				String s = fakeFile.readLine();
				if (s == null) break;
				String[] words = s.split("\\s+");
				lat = Double.parseDouble(words[6]);
				lon = Double.parseDouble(words[7]);
				hdg = Double.parseDouble(words[8]);
				double t = Double.parseDouble(words[0]);
				updates++;
				if (t > ms - startMs) 
					break;				
			} catch(Exception e) { 
				e.printStackTrace(); 
			}
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
    
	Double lat = 0.0, lon = 0.0, hdg = 0.0, siv = 0.0, speed = 0.0; 
	int updates = 0;

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

					
					String st[] = s.split(",");
					try { 
						if (st[0].equals("$GPRMC")) {
							lat = Double.parseDouble(st[3]) / 100.0;
							lon = -Double.parseDouble(st[5]) / 100.0;
							speed = Double.parseDouble(st[7]);
							if (st[8].length() > 0) 
								hdg = Double.parseDouble(st[8]);
							else 
								hdg = 0.0;
							updates++;
							//System.out.printf("GPS %f %f %f %f\n", lat, lon, hdg, speed);
						}
						
						
					} catch(Exception e) {
						e.printStackTrace();
					}
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
