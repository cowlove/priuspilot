import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



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
	        	//tty = new FileWriter(devName);
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
	    	//timeout.start();
	    	//open();
    	}
    }

	void startFake(String fn) { 
		try {
            fakeFile = new BufferedReader(new FileReader(fn));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
	}

	long startMs = 0, curTime = 0;
	void update(long ms) { 
		if (startMs == 0)
			startMs = ms;
		while (fakeFile != null) { 
			try {
				String s = fakeFile.readLine();
				if (s == null) break;
                lat = reDouble(".*lat\\s+([-+]?[0-9.]+)", s);
                lon = reDouble(".*lon\\s+([-+]?[0-9.]+)", s);
                hdg = reDouble(".*hdg\\s+([-+]?[0-9.]+)", s);
                speed = reDouble(".*speed\\s+([-+]?[0-9.]+)", s);
				long t = (long)reDouble("t\\s+([-+]?[0-9.]+)", s);	
				updates++;
				if (t > ms - startMs) 
					break;				
			} catch(Exception e) { 
				e.printStackTrace(); 
			}
		}
	}

	String re(String pat, String s) { 
		try { 
			Pattern p = Pattern.compile(pat);
			Matcher m = p.matcher(s);
			m.find();
			return m.group(1);
		} catch(Exception e) { 
			return "";
		}
	}
    double reDouble(String p, String s) { 
        return Double.parseDouble(re(p, s));
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
    
	Double lat = 0.0, lon = 0.0, hdg = 0.0, siv = 0.0, speed = 0.0, hdop = 0.0, nsat = 0.0; 
	int updates = 0;

	double lastHdg = 0;
	long lastMs = 0;
	double maxCurve = 0.30;
	double curveGain = 0.024;
	double curve = 0.0;
	RunningQuadraticLeastSquares avgCurve = 
		new RunningQuadraticLeastSquares(1, (int)(PidControl.EXPECTED_FPS * 3),
		1.8);
    Thread reader = new Thread (new Runnable() {
        public void run() {
        	while(true) { 
        		BufferedReader fin;
				try {
					Process p = Runtime.getRuntime().exec("/home/jim/src/gpsd/gpsd-3.25.1~dev/clients/ubxtool -f " + devName + " -p CFG-RATE,100");
					p.waitFor();
					p = Runtime.getRuntime().exec("stty -F " + devName + " 921600 sane -echo raw");
					p.waitFor();
					fin = new BufferedReader(new FileReader(devName));
				} catch (Exception e) {
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
						long time = Calendar.getInstance().getTimeInMillis();

						if (st[0].equals("$GPRMC")) {
							lat = Double.parseDouble(st[3]) / 100.0;
							lon = Double.parseDouble(st[5]) / 100.0;
							lat = Math.floor(lat) + (lat - Math.floor(lat)) * 100.0 / 60.0;
							lon = Math.floor(lon) + (lon - Math.floor(lon)) * 100.0 / 60.0;
							if (st[6].equals("W")) lon = -lon;
							if (st[4].equals("S")) lat = -lat;
							speed = Double.parseDouble(st[7]);
							if (st[8].length() > 0) 
								hdg = Double.parseDouble(st[8]);
							else 
								hdg = 0.0;
							updates++;
							double timeD = time - lastMs;
							double hdgD = hdgDiff(hdg, lastHdg);
							double c = hdgD * 100000 / (timeD * speed);
							c = Math.max(-25, Math.min(25, c));
							if (Double.isNaN(c) || Double.isInfinite(c))
								c = 0;
							avgCurve.add((double)time / 1000.0, c);
							lastHdg = hdg;
							lastMs = time;
						} else if (st[0].equals("$GPGGA")) {
							nsat = Double.parseDouble(st[7]);
							hdop = Double.parseDouble(st[8]);
						} 
						//System.out.printf("GPS %+12.08f %+12.08f %05.1f %05.1f %05.1f %.0f\n", 
						//	lat, lon, hdg, speed, hdop, nsat);
					} catch(Exception e) {
						//e.printStackTrace();
					}
                }
        	}
        }
    });

	double getCurveCorrection(long ms) {
		avgCurve.removeAged((double)ms / 1000.0);
		avgCurve.validate();
		curve = avgCurve.calculate() * curveGain;
		curve = Math.max(-maxCurve, Math.min(maxCurve, curve));
		return curve;
	}
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
	double hdgDiff(double a, double b) {
		double d = a - b;
		if (d > 180) d = 360 - d;
		if (d < -180) d = d + 360;
		return d;
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
