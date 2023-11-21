import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

class HistoryArray { 
	double []values;
	HistoryArray(int size) {
		values = new double[size];
	}
	void add(double v) { 
		for (int i = 0; i < values.length - 1; i++)
			values[i] = values[i + 1];
		values[values.length - 1] = v;
	}
	void addExtrapolate(double v, int count) { 
		double last = values[values.length - 1];
		double step = (v - last) / count;
		for (int n = 0; n < count; n++)
			add(last + step * (n + 1));
	}
}
class InputZeroPointTrim { 
	class Entry { 
		int vanX = 0;
		int vanY = 0;
		int lLane = 0;
		int rLane = 0;
	}
	Entry results = new Entry();
	Entry zeroPoint = new Entry();
	
	
	final static int historySize = 15;
	RunningAverage vanXAvg = new RunningAverage(historySize);
	RunningAverage vanYAvg = new RunningAverage(historySize);
	RunningAverage lLaneAvg = new RunningAverage(historySize);
	RunningAverage rLaneAvg = new RunningAverage(historySize);
	
	Entry add(int vx, int vy, int ll, int rl) {
		vanXAvg.add(vx);
		vanYAvg.add(vy);
		lLaneAvg.add(ll);
		rLaneAvg.add(rl);
		
		results.vanX = vx - zeroPoint.vanX;
		results.vanY = vy - zeroPoint.vanY;
		results.rLane = vx - zeroPoint.rLane;
		results.lLane = vy - zeroPoint.lLane;
		return results;
	}
	void reset() { 
		vanXAvg.clear();
		vanYAvg.clear();
		lLaneAvg.clear();
		rLaneAvg.clear();
	}
	void setAutoZero() { 
		System.out.printf("Setting autoZero\n");
		if (vanXAvg.count != historySize)
			return;
		zeroPoint.vanX = (int)vanXAvg.calculate();
		zeroPoint.vanY = (int)vanYAvg.calculate();
		zeroPoint.rLane = (int)rLaneAvg.calculate();
		zeroPoint.lLane = (int)lLaneAvg.calculate();
		System.out.printf("new zero points vx=%d, vy=%d, ll=%d, rl=%d\n", zeroPoint.vanX,
				zeroPoint.vanY, zeroPoint.lLane, zeroPoint.rLane);
	
	}
}

class FrameProcessor {
    long count = 0;
    int skipFrames = 0;
    int interval = 10;
    double servoTrim = 0.0;
    double zoom = 1.0; // zoom, normalized to 320x240
    int displayMode = 63; 
    //LaneAnalyzer la = null;
    TemplateDetect td = null;
    
    BufferedImageDisplayWithInputs display = null;
    public PidControl ccPid = null; //new PidControl("Cruise Control PID");
    //public ControlLagLogic ccLag = new ControlLagLogic();
    public PidControl pidLL = new PidControl("Left Line PID");         
    public PidControl pidRL = new PidControl("Right Line PID");
    public PidControl pidPV = new PidControl("Perspective PID");
    public PidControl pidLV = null; //new PidControl("Line X PID");
    public PidControl pidCA = null; //new PidControl("Curvature PID");
    //public PidControl pid = new PidControl("Main PID");
    //public PidControl pidCSR = new PidControl("Right Lane Color Segment PID");
    //public PidControl pidCSL = new PidControl("Right Lane Color Segment PID");
    //LabJackJNI labjack = new LabJackJNI();
    public PidControl selectedPid = pidLL;
    public ArrayList<PidControl> pids = new ArrayList<PidControl>();
	GPSTrimCheat trimCheat = null;
   
	final int laneWidthPeriod = 20;
	RunningQuadraticLeastSquares laneWidthAvg = 
		new RunningQuadraticLeastSquares(1, (int)(laneWidthPeriod * PidControl.EXPECTED_FPS),
		laneWidthPeriod);



    SteeringLogicSimpleLimits steering = new SteeringLogicSimpleLimits();
    int width, height, displayRatio;
    FrameProcessorTunableParameters tp = new FrameProcessorTunableParameters(this);
    TargetFinder tf = null;
       
    TargetFinderLines tfl, tfr, tfro, tflo;
    TargetFinderRoadColor tfrc;
	TargetFinderExperimental tfex; 
    Rectangle tfrcRect;
    FinderParameters tfparam;
    ArrayList<FinderParameters> tfparams = new ArrayList<FinderParameters>();
    int tfparamIndex;
    int lineIntensityDelta = 40;
    
    InputZeroPointTrim inputZeroPoint = new InputZeroPointTrim();
    PriusVoice pv = new PriusVoice();
	HistoryArray corrHist = null, predHist = null;
    double []ch = null;
	public double manualLanePosTrim = 0.005;
	GnuplotWrapper gp = new GnuplotWrapper();

	SerialCommandBus gps = new SerialCommandBus("/dev/ttyACM0", this);

    public FrameProcessor(int w, int  h, String outFile, String dumpFile, int rescale, 
    		int displayRatio, String serialDevice, String swCam) throws IOException {
        if (displayRatio > 0) {
        	display = new BufferedImageDisplayWithInputs(this, w * rescale, h * rescale);    
			display.rescale = rescale;
			Map<TextAttribute, Object> attributes = new HashMap<>();
			Font currentFont = display.g2.getFont();
			attributes.put(TextAttribute.FAMILY, currentFont.getFamily());
			attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD);
			attributes.put(TextAttribute.SIZE, (int) (currentFont.getSize() * rescale));
			Font myFont = Font.getFont(attributes);
			display.g2.setFont(myFont);

			for(TunableParameter f : tp.ps) { 
				display.panel.cb.addItem(f.desc.substring(0, Math.min(15, f.desc.length())));
			}
		}
    	if (outFile != null) 
    		writer = new ImageFileWriter(outFile, Silly.fc);
        if (dumpFile != null) 
        	logfile = new Logfile(dumpFile);
        
        width = w;
        height = h;
        this.rescale = rescale;
        this.displayRatio = displayRatio;
        
		restartOutputFiles();
        //td = new TemplateDetectCannyCorrelation(w, h);
        td = new TemplateDetectRGB(w, h);
        
        int minSz = Silly.debugInt("minSz", 33); // min/max radius
        int maxSz = 130;
		int minAng = Silly.debugInt("minAng", 6);
		int maxAng = Silly.debugInt("maxAng", 55);
        int houghSize = Silly.debugInt("HOUGH_SIZE", 80);
		double vertPct = Silly.debugInt("SA_VERT_PERCENT",80) / 100.0;
        tfl = new TargetFinderLines(w, h, null, true, Silly.debugInt("defLAng", 55), houghSize, minSz, maxSz, minAng, maxAng, vertPct);
        tfr = new TargetFinderLines(w, h, null, false, Silly.debugInt("defLAng", 55), houghSize, minSz, maxSz, minAng, maxAng, vertPct);
        tflo = new TargetFinderLines(w, h, null, true, 77, 60, minSz, maxSz, 12, 35, .85);
        tfro = new TargetFinderLines(w, h, null, false, 77, 60, minSz, maxSz, 12, 35, .85);
		tfex = new TargetFinderExperimental(w, h, null, 100);


		tfl.toeIn = tfr.toeIn = 0;

    	caR = new CurvatureAnalyzer(false, w, h); 
    	caL = new CurvatureAnalyzer(true, w, h); 

        tfparams.add(tfl.param);
        tfparams.add(tfr.param);	
        
        tfl.useLuminanceCheck = tfr.useLuminanceCheck = true;  
        tflo.useLuminanceCheck = tfro.useLuminanceCheck = false;
        tflo.focus.minWeight = tfro.focus.minWeight = 1000;

        if (Silly.debug("GK_RAD"))
        	tfl.param.gaussianKernelRadius = tfr.param.gaussianKernelRadius =
        	(float)Silly.debugDouble("GK_RAD");
        
       	tf = new TargetFinderRed(w, h);
        tfparams.add(tf.param);
        tfparamIndex = 0;
        tfparam = tfparams.get(0);

        tfrc = new TargetFinderRoadColor(w, h);
        tfrcRect = new Rectangle((int)(w * 0.51), (int)(h * 0.55), (int)(w * .12), 
        		(int)(h * 0.16));
        //new Rectangle((int)(w * 0.44), (int)(h * 0.35), 
        //		(int)(w * .12), (int)(h * .30));
        pids.add(pidLL);
        pids.add(pidRL);
        pids.add(pidPV);
        if (pidLV != null) pids.add(pidLV);
        if (pidCA != null) pids.add(pidCA);
        if (ccPid != null) pids.add(ccPid);
        
        steeringTestPulse.testType = SteeringTestPulseGenerator.TEST_TYPE_SQUARE;
        steeringTestPulse.magnitude = 0.30;
        steeringTestPulse.duration = 0.80;
        steeringTestPulse.count = 0;
        steeringTestPulse.offset = -0.00;
       
        steeringDitherPulse.magnitude = 0.20;
        
		double lx = Silly.debugDouble("LX", 36)/ 20.0;

		pidRL.setGains(2.50, 0.04, 2.00, 0, 1.8);
		pidRL.period.l = 0.15;
		pidRL.delays.l.delay = 1.75;
        pidRL.gain.p.hiGain = 1.52;
        pidRL.gain.i.max = 0.50; // I control has minor oscillating problems 
        pidRL.finalGain = 0.54;
        pidRL.qualityFadeThreshold = .022;
        pidRL.qualityFadeGain = 2;
        pidRL.gain.p.loTrans = -0.05;  // "bumper" points of increased gain for lane proximity
        pidRL.gain.p.hiTrans = +0.05; 
 		pidRL.reset();
        
        pidLL.copySettings(pidRL);
		pidLL.reset();
		        
        if (pidLV != null) {
			pidLV.setGains(0,.00,0,0,0);
			pidLV.finalGain = 0;//.90;
			pidLV.gain.i.max = 0.0;
			pidLV.qualityFadeThreshold = .084;
			pidLV.qualityFadeGain = 2;
		}
		
        //pidPV.copySettings(pidLV);
        pidPV.setGains(2.0, 0, 0.60, 0, 0.40);
		pidPV.finalGain = 1.80;
    	pidPV.period.l = 0.2;
		pidPV.delays.l.delay = 1.55;
	    pidPV.qualityFadeThreshold = 0.015;
        pidPV.qualityFadeGain = 2;
		//pidPV.fadeCountMin = pidPV.fadeCountMax = 0;
		pidPV.reset();
        //pidLV.setGains(0,0,0,0,0);
		
        if (pidCA != null) { 
			pidCA.setGains(10.0, 0, 0, 0, 0);
			pidCA.finalGain = 0; //:1.55;
			pidCA.period = pidCA.new PID(1.2, 1, 1, 1, 1);
			pidCA.qualityFadeThreshold = 0.003;
			pidCA.qualityFadeGain = 5;
			pidCA.finalGain = 0; // disable
		}
        
		if (ccPid != null) {
			ccPid.setGains(.06, 0, .34, 0, 1);
			ccPid.finalGain = 0.9;
			ccPid.period = ccPid.new PID(3, 1, 4.5, 1, 1);     
			ccPid.qualityFadeGain = 0;
			ccPid.reset();
		}
		//	ccLag.actuationTime = 0;
		//	ccLag.deadTime = 1000;
		//	ccLag.lagTime = 2000;
	
        
        tdStartX = (int)(w * 0.42);
        tdStartY = (int)(h * 0.33);
        tfSearchArea = new Rectangle(tdStartX, tdStartY, w/5, h/4);
        
        inputZeroPoint.zeroPoint.vanX = Silly.debugInt("VANX", 219); 
        inputZeroPoint.zeroPoint.vanY = Silly.debugInt("VANY", 32);
        inputZeroPoint.zeroPoint.rLane = 490;
        inputZeroPoint.zeroPoint.lLane = 1;

		trimCheat = new GPSTrimCheat(50);
		//gps.start();
    }
    

    AdvisorySounds sounds = new AdvisorySounds();
    ImageFileWriter writer = null;
    int rescale = 1;
    double fps = 0;
    int framesDropped = 0;
    boolean keepFocus = false, noSteering = false;
    IntervalTimer intTimer = new IntervalTimer(100);
    boolean paused = false;
    double manualSteerIncrement = .1;
    Map<Object,Object> keypresses = null;
    Map<Object,Point> clicks = null;
    synchronized void resetPIDs() {
        for(PidControl p : pids) 
        	p.reset();
    }
   
    synchronized void reset() {
        for(PidControl p : pids) 
        	p.reset();
        steering.reset();
        tfl.reset();
        tfr.reset();
        tflo.reset();
        tfro.reset();
    }
    
    //private int normalizePixel(int x) { 
    //	return x * height / 360;
    //}
     	
    public void close() {
    	if (writer != null) 
    		writer.close();
      	if (logfile != null) 
      		logfile.close();
    }
    Pauser pauser = new Pauser();
    
    int pendingKeyCode = 0;
    void keyPressed(int keyCode) {
    	if (keyCode == 'N' || keyCode == 'Z')
    		keyPressedSync(keyCode);
    	else
    		pendingKeyCode = keyCode;
    }
    
    synchronized void keyPressedSync(int keyCode) { 
        // TODO replace the tunable keys mess down below with an array,
        // or better yet, a call to the tunable object to see if it handles
        // the key. 
    	
        if (keyCode == 'Q') {
        	close();
        	System.exit(0);
        }
        else if (keyCode == 'R')  {
        	reset();
        }
        else if (keyCode == 'T')  {
        	if (repeatFrame > 0) 
        		repeatFrame = 0;
        	else
        		repeatFrame = (int)count + 1;
        }
        else if (keyCode == 'N')  
        	pauser.step(1);
    /*    else if (keyCode == '1')  
        	la.mode = LaneAnalyzer.MODE_WATCH_LEFT;
        else if (keyCode == '2')  
        	la.mode = LaneAnalyzer.MODE_WATCH_BOTH;
        else if (keyCode == '3')  
        	la.mode = LaneAnalyzer.MODE_WATCH_RIGHT;
        	*/
        else if (keyCode == 'Z')  
        	pauser.togglePaused();
        else if (keyCode == 'A')  
        	restartOutputFiles();
		else if (keyCode == 'F') {
			td.active = false;
			tdFindResult = null;
			tfFindTargetNow = false;
		}
        else if (keyCode == 10) { // [ENTER] key
			if (td != null) {
				td.active = true;
				tdFindResult = null;
				tdTempFrame = null;
				tfFindTargetNow = true;
			}
        }  else if (keyCode == 32) { // [SPACE] key
			noSteering = !noSteering;
			//noProcessing = !noProcessing;
			reset();
			steer = 0;
			tfFindTargetNow = false;
			tdFindResult = null;
			setSteering(0);
			
			//noProcessing = disarmed;
	           	
        } else if (keyCode == 8) { // backspace
        	System.out.printf("Keyboard reset at frame %d,  time %d\n", count, time);
        	onCruiseJoystick(4);
        	setSteering(0);
        } else if (keyCode == '/') {
        	tp.printAll();
        } else if (keyCode == '.' || keyCode == 38) { // up arrow
        	tp.adjustParam(1);
        	tp.printCurrent();
        } else if (keyCode == ',' || keyCode == 40) { // down arrow  
        	tp.adjustParam(-1);
        	tp.printCurrent();
        } else if (tp.findParam(keyCode) != null) {
                tp.selectParam(keyCode);
                tp.printCurrent();
        } else if (keyCode == 37) { // left arrow
        	//tp.adjustParam(-10);
    		//tp.printCurrent();
    		//logPidSettings();	
        	steeringTestPulse.startPulse(time, -1);
        }
        else if (keyCode == 39) { // right arrow 
         	//tp.adjustParam(10);
         	//tp.printCurrent();
         	//logPidSettings();
         	steeringTestPulse.startPulse(time, 1);
        }
        else 
        	System.out.println("Unknown key pressed - " + keyCode);
        this.notifyAll();
    }

    synchronized void onCruiseJoystick(int a) { 
    	System.out.printf("joystick input %d\n", a);
    	if (false && a == 4) { 
    		// double-click back - active target finder
    		//tdFindResult = null;
    		//this.tfFindTargetNow = true;
    		noProcessing = false;
    		steer = 0;
    		reset();
    		setSteering(0);
    		
    		// double-click back - reset zero points (TMP and careful)
    		//inputZeroPoint.setAutoZero();
    	} else if (a == 4 || a == 1 || a == 2) { 
    		// arm arduino
    		noProcessing = false;
    		if (tdFindResult != null) {
    			ccSetPoint = tdFindResult.scale;
    			tdStartX = tdFindResult.x;
    		}
	    	reset();
    	} else if (a == 0) {
    		// single-tap back, disarm and clear 
       		noProcessing = true;
       		//reset();
     	 	steer = 0;
     	 	tfFindTargetNow = false;
     	 	tdFindResult = null;
    	} else if (a == 6) {
    		keyPressed('Q');
    	}
    }
    
    JoystickControl joystick = new JoystickControl();
    
    double epsSteeringGain = 1.0;	
    double trq1 = 0, trq2 = 0;
    
    long lastCruiseSet = 0; // time of last cruise control command in ms
    synchronized void setCruise(boolean up) {
    	long now = Calendar.getInstance().getTimeInMillis();
    	if (now - lastCruiseSet > 250) {  // limit cruise control commands
    									  // to one per 1/4sec 
	    	int val = up ? 40 : 25;
	    	System.out.printf("setCruise(%s)\n", up ? "UP               " : "               DOWN");
	    	lastCruiseSet = now;
    	}
    }

	String steerCmdHost = "255.255.255.255";

	FileWriter cmdLink = null;
    synchronized void setSteering(double x) { 

		if (cmdLink == null) { 
			try {
				String devName = "/dev/ttyUSB0";
				File f = new File(devName);
				if(f.exists() && !f.isDirectory()) { 
					Process p = Runtime.getRuntime().exec("stty -F " + devName + " 921600 sane -echo raw");
					p.waitFor();
					cmdLink = new FileWriter(devName);
				}
			} catch(Exception e) { 
				e.printStackTrace();
			}
		}
		if (Silly.sim != null) 
			Silly.sim.setSteer(x);

        x = x * epsSteeringGain;

		String s = String.format("PPDEG %.3f %.3f\n", x, x);
		if (Silly.fc != null) { 
			Silly.fc.espnowSend(s);	
		}
		if (cmdLink != null) {
			try {
				cmdLink.write(s);
				cmdLink.flush();
			} catch (Exception e) {
				cmdLink = null;
			}
		}
		if (false) { 
			try {
				final int lo = 300, hi = 1700;
				int pwm = (int)(x / 1.2 *  (hi - lo) / 2  + (hi + lo) / 2);
				pwm = Math.min(hi, Math.max(lo, pwm));
				DatagramSocket sendSocket = new DatagramSocket();
				sendSocket.setBroadcast(true);
				//String cmd = String.format("set pwm=%d", pwm); // old servo-based PWM
				String cmd = String.format("PPDEG %f 0", x);
				byte[] data = cmd.getBytes();
				//System.out.println(cmd);
				DatagramPacket packet = new DatagramPacket(data, data.length, 
					InetAddress.getByName(steerCmdHost), 7788);
				sendSocket.send(packet); 
				sendSocket.close();
			} catch(Exception e) {
				System.out.printf("ouch\n");
			}
		}
    }
	

    RunningAverage avgFrameDelay = new RunningAverage(100);
    // TODO- separate text window to display test stats
    void writeDisplayText() {
		display.g2.setColor(Color.blue);
        display.writeText("FRAME: " + String.format("%d", count));
        display.writeText("LTIME: " + String.format("%d",  time - logFileStartTime));
        display.writeText("FPS: " + String.format("%.1f", fps));
        display.writeText("DROPPED: " + framesDropped);
        display.writeText("GPS    : " + gps.updates);
        //display.writeText("L   : " + String.format("%.2f", pid.la.calculate()));
        //display.writeText("R   : " + String.format("%.2f", pid.ra.calculate()));
        //display.writeText("M   :" + String.format("%.2f", pid.mid));
        //			display.writeText("XERR: " + String.format("%.4f", pid.pointErr));
        //			display.writeText("CORR: " + String.format("%.4f", correction));
        //
        /*
        display.writeText("LSCAN: " + String.format("%.2f to %.2f", la.lanes.zones.lsz.m1, la.lanes.zones.lsz.m2));
        display.writeText("LLINE: " + String.format("%.2f %d", la.lanes.left.currentSlope, la.lanes.left.weight));
        display.writeText("RSCAN: " + String.format("%.2f to %.2f", la.lanes.zones.rsz.m1, la.lanes.zones.rsz.m2));
        display.writeText("RLINE: " + String.format("%.2f %d", la.lanes.right.currentSlope, la.lanes.right.weight));
        display.writeText("VANISH: " + String.format("%.2f,%.2f", la.lanes.currentVanish.x, la.lanes.currentVanish.y));
        */
        display.writeText("LLPER : " + String.format("%d", this.tfl.pd.getPeriod()));
        display.writeText("RLPER : " + String.format("%d", tfr.pd.getPeriod()));
        display.writeText("R WIDTH: " + (int)(tfl.getAngle() - tfr.getAngle()));
        

        /*display.writeText("TSCORE: " + String.format("%.1f", tdFindResult == null ? 0.0 : tdFindResult.score));
        display.writeText("TVAR  : " + String.format("%.1f", tdFindResult == null ? 0.0 : tdFindResult.var));
        display.writeText("TSCALE: " + String.format("%d", tdFindResult == null ? 0 : tdFindResult.scale));
        */
        //display.writeText("WIDTH: " + String.format("%.2f", pid.width ));

        //
        //			display.writeText("LM1: " + String.format("%.2f", la.lanes.left.scanZone.m1));
        //			display.writeText("LB1: " + String.format("%d", la.lanes.left.scanZone.b1));
        //			display.writeText("LM2: " + String.format("%.2f", la.lanes.left.scanZone.m2));
        //	 & 0xff00) >> 8		display.writeText("LB1: " + String.format("%d", la.lanes.left.scanZone.b2));
        //			display.writeText("RM1: " + String.format("%.2f", la.lanes.right.scanZone.m1));
        //			display.writeText("RB1: " + String.format("%d", la.lanes.right.scanZone.b1));
        //			display.writeText("RM2: " + String.format("%.2f", la.lanes.right.scanZone.m2));
        //			display.writeText("RB1: " + String.format("%d", la.lanes.right.scanZone.b2));
        
    }

    double corr = 0, steer = 0;
    long lastFrameTime = 0;
    double colorThresholdPercent = 0.10;
    int pauseFrame = 0;
    int exitFrame = 0;
    long time = 0, frameResponseMs = 0;
    boolean noProcessing = false;
    RemoteTcpCommandSocket cmdSocket = null; //new RemoteTcpCommandSocket();
    
    // HISTORICAL prescales - todo - push these constants through and adjust all the PID settings accordingly
    // Constants to adjust to new 360p frame size.  Lane width, measured at intercept 
    // of lane lines and bottom of frame and normalized to frame width, is about 1.5 times 
    // smaller in the 640x360 image than the old 320x240 frame.
	
	// Fixed dash feature was 224 out of 240 pixels on an 320x240 image, and 
	// 300 out of 640 pixels on a 640x360p image.  The 1.5 is historical 
	final double pixelWidthPrescale = 2.9866666666666664; //1.5f * (224f / 240) / (300f / 640);  
  
    TemplateDetect.FindResult tdFindResult = null;
    int badTdCount = 0;
    IntervalTimer profTimer = new IntervalTimer(100);
    int tdStartX = 180;  // TODO raw pixel use!
    int tdStartY = 70;
    int tdTargetSize = 40;
    ByteBuffer tdTempFrame = null;
    double tdTargetAspect = 1.0; // h to w ratio
    boolean tdTakeNewTemplateNow = false;
    int tdMaxErr = 22000;
    double tdDelta;
    Rectangle tfResult = null;
    
    
    class TdAverager {
    	final int period = 5; // TODO - make this time-based
    	TemplateDetect.FindResult last = null;
    	double delta;
    	RunningAverage scale = new RunningAverage(period), x = new RunningAverage(period), y = new RunningAverage(period);
    	void add(TemplateDetect.FindResult f) {
			if (f == null) 
				return;
    		if (last != null)
    			delta = Math.sqrt((last.x - f.x) * (last.x - f.x) + (last.y - f.y) * (last.y - f.y) + 
    					(last.scale - f.scale) * (last.scale - f.scale));
    		last = f.copy();
    		scale.add(f.scale); x.add(f.x); y.add(f.y);
    	}
    	void set(TemplateDetect.FindResult f) { 
    		f.scale = (int)Math.round(scale.calculate());
    		f.y = (int)Math.round(y.calculate());
    		f.x = (int)Math.round(x.calculate());
    	}
		public void reset() {
			scale.clear(); x.clear(); y.clear();
		}
    }
    
    Point laneVanish;
    
    TdAverager tdAvg = new TdAverager();
    String tdChartFiles = null;    

    boolean tfFindTargetNow = false; // activate TargetFinder to set a template, cleared once target is found
    Rectangle tfSearchArea = null;
	RunningAverage avgLLCorr = new RunningAverage(PidControl.EXPECTED_FPS);
	RunningAverage avgRLCorr = new RunningAverage(PidControl.EXPECTED_FPS);

    long ccLastCorrectionTime = 0;
	long ccMinCorrectionInterval = 400; // milliseconds
    int ccSetPoint = 15;
	RunningAveragePoint houghVan = new RunningAveragePoint(1);
	
	void drawLine(int x1, int y1, int x2, int y2) { 
		display.g2.drawLine(x1 * rescale, y1 * rescale, x2 * rescale, y2 * rescale);
	}
    void drawTruncatedLine(int x1, int y1, int x2, int y2, int yt1, int yt2) {
		int xt1 = x1 + (x2 - x1) * (yt1 - y1) / (y2 - y1);
		int xt2 = x1 + (x2 - x1) * (yt2 - y1) / (y2 - y1);
		drawLine(xt1, yt1, xt2, yt2);
	}
    static Rectangle scaleRect(Rectangle r, int scale) {
    	return new Rectangle(r.x * scale, r.y * scale, r.width * scale, r.height * scale);
    }
    
	CurvatureAnalyzer caR, caL;
	int [] vpL, vpR;
	

	OriginalImage coi = null;
	double lpos = Double.NaN, rpos = Double.NaN;
	double laneVanX = Double.NaN,persVanX = Double.NaN;

	synchronized void processFrameSync(long t, OriginalImage oi) throws IOException {
		lpos = Double.NaN;
		rpos = Double.NaN;
		laneVanX = Double.NaN;
		persVanX = Double.NaN;
		double dynamicLaneWidthAdj = 0.0;

		time = t;
    	if (skipFrames > 0 && count < skipFrames)
    		return;

    	profTimer.start();
    	corr = 0;
    	
    	coi = oi;
   		if (Silly.debug("COPY_IMAGE"))
   			coi = oi.deepCopy();
   		
   		if (!noProcessing) { 
	   		tfrc.findAll(coi, tfrcRect);
	   		tflo.minLineIntensity = tfro.minLineIntensity = tfl.minLineIntensity = tfr.minLineIntensity = 
	   		tfrc.roadIntensity + lineIntensityDelta;
	   		tflo.hslThresh = tfro.hslThresh = tfl.hslThresh = tfr.hslThresh =
	   				tfrc.hslThresh;
	   		tflo.tfrc = tfro.tfrc = tfl.tfrc = tfr.tfrc = tfrc;
	   		
	   		if (Silly.debug("xDEBUG_COLOR_SEGMENTATION")) { 
		   		BufferedImageDisplay.nextX = 640;
		   		BufferedImageDisplay.nextY = 20;
		   		tfrc.hh.draw(1);
	   		}
	   		
	   		final int vanRectW = 64 ;//* width / 320;
	   		final int vanRectH = 32;// * height / 240;
			final int vpScale = 1;
			   		
			avgRLCorr.validate();
			avgLLCorr.validate();
			laneWidthAvg.validate();
			
	   		tflo.vanLimits = tfro.vanLimits = tfl.vanLimits = tfr.vanLimits = new
	   			Rectangle(inputZeroPoint.zeroPoint.vanX - vanRectW / 2, 
	   					inputZeroPoint.zeroPoint.vanY - vanRectH / 2, vanRectW, vanRectH);
			
	   		tfrc.sa.x = inputZeroPoint.zeroPoint.vanX - tfrc.sa.width / 2;

			tfex.setVanRect(new Rectangle(inputZeroPoint.zeroPoint.vanX - vanRectW / 2, 
	   					inputZeroPoint.zeroPoint.vanY - vanRectH / 2, vanRectW, vanRectH));

	   		tfl.useLaneWidthFilter = tfr.useLaneWidthFilter = true;
	
	   		vpL = new int[tfl.vanLimits.width * tfl.vanLimits.height / vpScale / vpScale];
	   		vpR = new int[vpL.length];
		
	   		// do the left side in a separate thread
	   		Thread t1 = new Thread(new Runnable() { public void run() { 
	   			tfl.findNearest(coi, null, 0, 0);
	   			tflo.findNearest(coi, null, 0, 0);
				//caL.seedCurve(tfl.sa, tfl.lumPoints, tfl.getAngle(), tfl.getInstantaneousX(height));
				//caL.growCurve(tfl.sa, tfl.lumPoints);
	
	   	   		Rectangle r = (Rectangle)tfl.vanLimits.clone();
	   	   		r.x -= tfl.sa.x;
	   	   		r.y -= tfl.sa.y;
	   	   		tfl.h2.projectIntoRect(vpL, r, vpScale);
	   	   		r = (Rectangle)tflo.vanLimits.clone();
	   	   		r.x -= tflo.sa.x;
	   	   		r.y -= tflo.sa.y;
				//tflo.h2.projectIntoRect(vpL, r, vpScale);
	
	   		} });
	   		t1.start();
	   	
	   		// do right side concurrently while left side is running 
	   		tfr.findNearest(coi, null, 0, 0);
	   		tfro.findNearest(coi, null, 0, 0);
			//caR.seedCurve(tfr.sa, tfr.lumPoints, tfr.getAngle(), tfr.getInstantaneousX(height));
			//caR.growCurve(tfr.sa, tfr.lumPoints);
	
	   		Rectangle r = (Rectangle)tfr.vanLimits.clone();
	   		r.x -= tfr.sa.x;
	   		r.y -= tfr.sa.y;
			tfr.h2.projectIntoRect(vpR, r, vpScale);
	   	
	   		r = (Rectangle)tfro.vanLimits.clone();
	   		r.x -= tfro.sa.x;
	   		r.y -= tfro.sa.y;
			//tfro.h2.projectIntoRect(vpR, r, vpScale);
	   		
	   		try { 
	   			t1.join(0);
	   		} catch(Exception e) {}
	   		
	   		int [] vp = new int[vpL.length];
	   		for(int i = 0; i < vp.length; i++)  
	   			vp[i] = vpL[i] + vpR[i];
	   		
			double gr = Silly.debugDouble("VP_GR", 7) / vpScale;
			GaussianKernel gk = new GaussianKernel(gr, (int)(gr * 10), r.width / vpScale, 
					r.height / vpScale);
			gk.blur(vp);
	
			houghVan.add(new Point(/*gk.bestColumn(vp)*/gk.bestX * vpScale + tfr.vanLimits.x, gk.bestY * vpScale + tfr.vanLimits.y));
			if (gk.max > 15) {	
				persVanX = (((double)houghVan.ax.calculate()  - inputZeroPoint.zeroPoint.vanX) / width) * pixelWidthPrescale;
			}
			//System.out.printf("%f\n", persVanX);
			tfex.findNearest(coi, null, 0, 0);

			tfl.markup(coi);
			tfr.markup(coi);
			tflo.markup(coi);
			tfro.markup(coi);
			caL.markup(coi);
			caR.markup(coi);
			tfex.markup(coi, rescale);
			//setLineColorAndWidth(Color.lightGray, 2);
			//display.g2.draw(scaleRect(tfex.vanRec, rescale));

			
	   		if (Silly.debug("DEBUG_VAN")) {
				// flip chart to make it easier to read
	   			int w = tfr.vanLimits.width / vpScale;
	   			int h = tfr.vanLimits.height / vpScale;
				for(int y = 0; y < h / 2 ; y++) {
					for(int x = 0; x < w; x++) { 
						int tmp = vp[x + y * w];
						vp[x + y * w] = vp[x + (h - y - 1) * w];
						vp[x + (h - y - 1) * w] = tmp;
					}
				}
				gp.startNew();
				gp.add3DGrid(vp, w, h);
				gp.draw();
		  	}
			
			if (selectedPid == pidLL) 
				pidRL.copySettings(pidLL);
			else
				pidLL.copySettings(pidRL);
	   		
			// Two detected left lines equal, or perhaps reversed?  Reset
			if (tflo.getAngle() - tfl.getAngle() < 2) {
				if (Math.abs(tfl.getAngle() - tfl.focus.defaultAngle) > 
					Math.abs(tflo.getAngle() - tflo.focus.defaultAngle))			
					tfl.reset();
				else	
					tflo.reset();			
			}
			
			// Two detected right lines equal, or perhaps reversed?  Reset
			if (tfr.getAngle() - tfro.getAngle() < 2) {
				if (Math.abs(tfr.getAngle() - tfr.focus.defaultAngle) > 
					Math.abs(tfro.getAngle() - tfro.focus.defaultAngle))			
					tfr.reset();
				else	
					tfro.reset();			
			}
					
			final int laneMinQuality = 20;
			
			if (tfl.focus.getQuality() > laneMinQuality)	
				lpos = (double)(tfl.getInstantaneousXDouble(height) - inputZeroPoint.zeroPoint.lLane) / width;
			if (tfr.focus.getQuality() > laneMinQuality) 	        		
	    		rpos = (double)(tfr.getInstantaneousXDouble(height) - (inputZeroPoint.zeroPoint.rLane)) / width;
			if (Math.abs(rpos) > 0.6) 
				rpos = Double.NaN;
			if (Math.abs(lpos) > 0.6) 
				lpos = Double.NaN;
			
			
			// carefully maintain a quality laneWidth average
			if (!Double.isNaN(lpos) && !Double.isNaN(rpos) &&
				pidLL.quality >= 0.4 && pidRL.quality >= 0.4) { 
				laneWidthAvg.add(time / 1000.0, (double)rpos - lpos);
			} else {
				//laneWidthAvg.clear();
			}
			laneWidthAvg.removeAged(time / 1000.0);
			if (laneWidthAvg.rmsError() < .079365) { 
				dynamicLaneWidthAdj = laneWidthAvg.calculate() / 2;
			}
			//System.out.printf("%08.4f %08.4f %08.4f\n", dynamicLaneWidthAdj, laneWidthAvg.calculate(), laneWidthAvg.rmsError());

			if (tfr.focus.getQuality() > laneMinQuality && tfl.focus.getQuality() > laneMinQuality) {
	       		laneVanish = TargetFinderLines.linePairIntercept(tfl, tfr);
	      		if (tfl.insideVanRect(laneVanish))
	      			laneVanX = ((double)(laneVanish.x - inputZeroPoint.zeroPoint.vanX)) / width * 1.5 * pixelWidthPrescale; 
			}
			
			if (!Double.isNaN(lpos) && !Double.isNaN(rpos) && !Double.isNaN(laneVanish.x) && !Double.isNaN(laneVanish.y)) { 
				inputZeroPoint.add(laneVanish.x, laneVanish.y, tfl.getInstantaneousX(height), tfr.getInstantaneousX(height));
			} else { 
				inputZeroPoint.reset();
			}
			
			
			//System.out.printf("%f\n", (double)time/1000);
			//pidCSR.add((tfr.csX - inputZeroPoint.zeroPoint.rLane) /width, time);
			//pidCSL.add((tfl.csX - inputZeroPoint.zeroPoint.lLane) /width, time);
			
			corr = 0;
			// Use button 0x1 and 0x4 to temporarily avoid the LL or RL PID, use
			// the 1-second average instead 
			if (pidLL.i < 0 && pidRL.i > 0 || pidLL.i > 0 && pidRL.i < 0) 
				pidLL.i = pidRL.i = (pidLL.i + pidRL.i) / 2;
			pidLL.add(lpos + dynamicLaneWidthAdj + manualLanePosTrim, time);
			pidRL.add(rpos - dynamicLaneWidthAdj + manualLanePosTrim, time);
			if ((joystick.buttonBits & 0x1) == 0 && (trimCheat.buttons & 0x1) == 0) { 
				corr -= pidLL.corr;
				avgLLCorr.add(pidLL.corr);
			} else { 
				//System.out.printf("IGNORE L\n");
				corr -= avgLLCorr.calculate();
			}
			if ((joystick.buttonBits & 0x4) == 0 && (trimCheat.buttons & 0x4) == 0) { 
				corr -= pidRL.corr;
				avgRLCorr.add(pidRL.corr);
			} else { 
				corr -= avgRLCorr.calculate();
				//System.out.printf("IGNORE R\n");
			}
			corr = corr / 2;
 			//corr = -(pidLL.add(lpos, time)  + pidRL.add(rpos, time)) / 2;
			
			if (!Double.isNaN(persVanX)) 
				corr += -pidPV.add(persVanX, time);
			if (pidLV != null && !Double.isNaN(laneVanX))
				corr += -pidLV.add(laneVanX, time);
			double curve = 0;
			if (!Double.isNaN(caL.getCurve()))
				curve += caL.getCurve();
			if (!Double.isNaN(caR.getCurve()))
				curve += caR.getCurve();
		
			if (caL.getCurve() * caR.getCurve() < 0) 
				curve = Double.NaN;
			if (pidCA != null) 
				corr += -pidCA.add(curve, time);
	
			if (tfFindTargetNow || Silly.debug("CONT_TF")) {
				// try to set tdFindResult to new template.  If fails, tdFindResult will be left null
				tdStartX = 152;
				tdStartY = 105;
				tfResult = tf.findNearest(coi, tfSearchArea, tdStartX, tdStartY);
		    	if (tfResult != null) { 
	    	    	tdStartX = tfResult.x + tfResult.width / 2 + tf.fudge / 2;
	    	    	tdStartY = tfResult.y + tfResult.height / 2 + tf.fudge / 2;
					tdFindResult = td.setTemplate(coi, tdStartX, tdStartY, tfResult.width, tfResult.height);
				}
				tdAvg.reset();
				tdAvg.add(tdFindResult);
				tfFindTargetNow = false;
			}
	    	
	    	
	        if (td != null) {
				td.newFrame(coi);
		    	td.setSearchDist(5, 5, 2);
	        	double pos = 0;
	        	if (tdFindResult != null) {
	        		//if (tdFindResult.scale < -10)
	        		//	tdFindResult.scale = -10;	
	        		// prohibit scale frome changing more than td.searchDist.scale / tdScale.size per frame 
	        		tdAvg.set(tdFindResult);
	        		td.find(tdFindResult, coi);
	        		//if (tdChartFiles != null) 
	        		//	td.makeChartFiles(tdFindResult, picbb.array(), tdChartFiles);
	            	//sounds.setAlertLevel(tdFindResult.score / tdMaxErr);
	            	tdAvg.add(tdFindResult);
		        	pos = (double)(tdFindResult.x - tdStartX) / width * zoom; 
		        	if (tdFindResult.score > tdMaxErr) {
		        		if (++badTdCount > 600) {
			        		System.out.printf("Large error %d\n", (int)tdFindResult.score);
			        		corr = 0;
			        		td.active = false;
			        		noProcessing = true;
			        		tdFindResult = null;
		        		}
		      
		        	} else if (ccPid != null) {
		        		badTdCount = 0;
		        		double ccDist = (tdFindResult.scale - ccSetPoint) / 2;
		        		double cc = -ccPid.add(ccDist, time);		        		
		        		if (time - ccLastCorrectionTime > ccMinCorrectionInterval && Math.abs(cc) > 0.25) { 
		        			boolean up = cc < 0;
		        			// TODO - ccPid.correctionFeedback(time, up ? -0.25 : 0.25);
		        			setCruise(up);
		        			ccLastCorrectionTime = time;
		        		}
		        	}
	        	}
	        } 

        }
        
        if (cmdSocket != null) { 
	        double remoteInput = cmdSocket.getSteer();
	        if (remoteInput != 0.0) 
	        	steer = remoteInput / 10;
        }
        
		if (false) { 
			if (corrHist == null) {
				ch = new double[256];
				corrHist = new HistoryArray(64);
				predHist = new HistoryArray(corrHist.values.length);
			}
			System.arraycopy(corrHist.values, 0, ch, 0, corrHist.values.length);
			double pred = 0;
			int extrapolate = 1;
				   int filterCent = (int)Math.round(((double)ch.length) * 18/ 512 / extrapolate);
			int filterW = (int)Math.round(((double)ch.length) * 4/ 512 / extrapolate);
			double filterMag = 0.0;
			
			corrHist.addExtrapolate(corr, extrapolate);
			Complex [] ar = Complex.complexArray(ch);
			ar = FFT.fft(ar);
	   
			pred = ar[filterCent].re() * Math.cos((ch.length - 1) * 2 * Math.PI) + 
					ar[filterCent].im() * Math.sin((ch.length - 1) * 2* Math.PI);
			//if (pred < -corr) pred = -corr;
				// apply filter around oscillation frequency 
			for (int i = filterCent - filterW; i <= filterCent + filterW ; i++) { 
				if (i >= 0 && i < ar.length){  
					ar[i] = ar[i].scale(filterMag);
					ar[ar.length - i - 1] = ar[ar.length -i -1].scale(filterMag);
				}
			}	        	
			Complex [] iar = FFT.ifft(ar);
			
			pred = iar[corrHist.values.length - 1].re();
			//pred = ar[filterCent].abs();
			
			predHist.addExtrapolate(pred, extrapolate);
				
			if (Silly.debug("FFT")) {
				double [][]x = new double[5][];
				x[0] = corrHist.values;
				x[1] = predHist.values;
				x[2] = Complex.magArray(ar);
				x[3] = Complex.arrayOfRe(ar);
				x[4] = Complex.arrayOfIm(ar);
			
				gp.startNew();
				gp.addArrays(x, x[0].length);
				gp.addOptions(new String[]{"ax x1y2", "ax x1y2", null, null, null});
				gp.draw2D(5);
			}
			corr = pred;
		}

        if (!noSteering) 
        	steer = corr;    
        else
            steer = 0;
        
        steer += steeringTestPulse.currentPulse(time);
		gps.update(time);
		steer += trimCheat.get(gps.lat, gps.lon, gps.hdg);
        steer = joystick.steer(steer);
        steer += steeringDitherPulse.currentPulse(time);
		steer = steering.steer(time, steer);
	    setSteering(steer);
	    
	    frameResponseMs = Calendar.getInstance().getTimeInMillis() - t;
	    avgFrameDelay.add(frameResponseMs);

	    if (Silly.debug("RESET_PIDS") && (count % Silly.debugInt("RESET_PIDS")) == 0) 
	    	resetPIDs();
        
        double tc = joystick.getThrottleChange();
        if (tc != 0.0)
        	setCruise(tc > 0/*increase vs decrease*/);
    	
        if (joystick.getExit())  
        	this.keyPressed('Q');
         
        if (joystick.getButtonPressed(9)) 
        	restartOutputFiles();
		//if (joystick.getButtonPressed(0))  
		//	steeringTestPulse.startPulse(-1);
		//if (joystick.getButtonPressed(2))  
		//	steeringTestPulse.startPulse(1);
		if (joystick.getButtonPressed(8))  
			inputZeroPoint.setAutoZero(); 
		if (joystick.getButtonPressed(1))  {
			tp.adjustParam('M', +1);
			tp.printParam('M');
		}
		if (joystick.getButtonPressed(3)) {
			tp.adjustParam('M', -1);
			tp.printParam('M');
		} 
		if (joystick.getButtonPressed(10)) { 
			tp.adjustParam(-1);
			tp.printCurrent();
		}
		if (joystick.getButtonPressed(11)) { 
			tp.adjustParam(1);
			tp.printCurrent();
		}
		if (joystick.getButtonPressed(12)) { 
			tp.selectNext(1);
			tp.printCurrent();
		}
		if (joystick.getButtonPressed(13)) { 
			tp.selectNext(-1);
			tp.printCurrent();
		}
		

		/*
		for (int bn = 0; bn < 19; bn++) {
			if (joystick.getButtonPressed(bn)) { 
				System.out.printf("Button %02d pressed\n", bn);
			}
		}
		*/
		

        if (displayRatio > 0 && (count % displayRatio) == 0) {
			if (tdFindResult != null) {  
	            	//display.draw(arduinoArmed ? Color.red : Color.green, scaleRect(td.targetRect(tdFindResult), rescale));
				td.draw(coi, rescale);
			}
        	writeCompositeImage(display.image, coi, rescale, (displayMode & 0x4) != 0,
        			(displayMode & 32) != 0);
  
            if ((displayMode & 0x1) != 0) {
                writeDisplayText();                  
            }
            if ((displayMode & 0x2) != 0) {
                if (writer != null && writer.active)  
                	display.rectangle(Color.red, "REC", 0.90, 0.10, 0.1, 0.05);

            	display.g2.setStroke(new BasicStroke(2));
	            if (tdFindResult != null) {  
	            	display.draw(arduinoArmed ? Color.red : Color.green, scaleRect(td.targetRect(tdFindResult), rescale));
					//td.draw(oi);
	            }
 
				// draw blue target lines adjusted for dynamicLandWidth
				tfrc.rescaleDisplay = tflo.rescaleDisplay = tfro.rescaleDisplay = tfr.rescaleDisplay = tfl.rescaleDisplay = rescale;
	            setLineColorAndWidth(dynamicLaneWidthAdj == 0 ? Color.white : Color.blue, 4 * rescale);
				final int lx = (int)Math.round(inputZeroPoint.zeroPoint.lLane - (dynamicLaneWidthAdj * width));
				final int rx = (int)Math.round(inputZeroPoint.zeroPoint.rLane + (dynamicLaneWidthAdj * width));
				drawTruncatedLine(lx - 10, height, inputZeroPoint.zeroPoint.vanX, inputZeroPoint.zeroPoint.vanY, height, height * 2 / 3);
				drawTruncatedLine(lx + 10, height, inputZeroPoint.zeroPoint.vanX, inputZeroPoint.zeroPoint.vanY, height, height * 2 / 3);
				drawTruncatedLine(rx - 10, height, inputZeroPoint.zeroPoint.vanX, inputZeroPoint.zeroPoint.vanY, height, height * 2 / 3);
				drawTruncatedLine(rx + 10, height, inputZeroPoint.zeroPoint.vanX, inputZeroPoint.zeroPoint.vanY, height, height * 2 / 3);
				
	            setLineColorAndWidth(Color.green, 1 * rescale);
    			TargetFinderLines.displayLinePairToOutsideVanRec(tflo, tfro, display.g2);
	            setLineColorAndWidth(Color.red, 2 * rescale);
    			TargetFinderLines.displayLinePair(tfl, tfr, display.g2);
	            setLineColorAndWidth(Color.blue, 2);
       			caL.display(display.g2);
    			caR.display(display.g2);
                setLineColorAndWidth(Color.lightGray, 2);
        		tfrc.draw(display.g2);
        		display.g2.draw(tfl.scaleRect(tfl.vanLimits, rescale));

    			int s = 7;
        		Rectangle r1 = scaleRect(new Rectangle(houghVan.calculate().x - s, 
					houghVan.calculate().y - s, s * 2 + 1, s * 2 + 1), rescale);
    			display.g2.setColor(Color.orange);
    			display.g2.draw(r1);
    			display.g2.drawLine(r1.x, r1.y, r1.x + r1.width, r1.y + r1.height);
    			display.g2.drawLine(r1.x, r1.y + r1.height, r1.x + r1.width, r1.y);
    			
    			//display.g2.draw(tfl.sa);
    			//display.g2.draw(tfr.sa);

    			if (tfFindTargetNow)
	            	display.draw(Color.yellow, scaleRect(tfSearchArea, rescale));
            }
            if ((displayMode & 0x8) != 0) {
				//displayPid(pidPV, Color.cyan);
            	displayPid(pidLL, Color.yellow);
               	//displayPid(pidLV, Color.green);
               	//displayPid(pidPV, Color.blue);
               	//displayPid(pidRL, Color.white);
            }
            	
			final double pds = 0.315;
            if ((displayMode & 0x10) != 0) {
                double yoff = 0.80;
	            double yspace = 0.05;
    			final double bWidth = 0.06;
	   	        display.rectangle((trimCheat.buttons & 0x5) == 0 ? Color.blue : Color.cyan, String.format("%d", (int)trimCheat.count), trimCheat.trim + trimCheat.curve + 0.5, yoff, bWidth, 0.05);
	   	        display.rectangle(Color.pink, "", corr + 0.5, yoff, bWidth, 0.05);
	            display.rectangle(arduinoArmed ? Color.red : Color.magenta, "ST", steer + 0.5, yoff, bWidth, 0.05);
	            for( PidControl pid : pids ) { 
	            	yoff += yspace;
	            	display.text(pid.description, 0, yoff + 0.05);
		            display.rectangle(Color.red, "S", -pid.corr + 0.5, yoff, bWidth, 0.05, true);
		            display.rectangle(Color.yellow, "P", pid.err.p * pds + 0.5, yoff + 0.005, bWidth, 0.04, true);
		            display.rectangle(Color.white, "I", pid.err.i * pds + 0.5, yoff, bWidth, 0.03, true);
		            display.rectangle(Color.green, "D", pid.err.d * pds + 0.5, yoff + 0.01, bWidth, 0.02, true);	     
		            display.rectangle(Color.black, "L", pid.err.l * pds + 0.5, yoff + 0.015, bWidth, 0.01, true);	     
		        	Color c = pid.quality < 1.0 ? Color.red : Color.yellow;
		       		display.rectangle(c, String.format("%03d", (int)(pid.drms / pid.qualityFadeThreshold * 100)), 
		       				.41 + pid.quality * 0.52, yoff, bWidth, 0.05, false);
		  //          display.text(pid.description, 0.0, yoff);
	            }
	            display.rectangle(Color.cyan, String.format("%.1f", fps), Math.min(0.95, (double)fps / 32), yoff, bWidth, 0.05);	            

 //           	TemplateDetect.Tile ti = td.scaledTiles.getTileByScale(tdFindResult.scale);
 //           	display.image.getWritableTile(0,0).setDataElements(0, 0, ti.loc.width, ti.loc.height, 
 //               		ti.data);
        
            	/*
            	if (continuousTf && tf.data != null) { 
	    			int [] cdata = tf.data;
	            	for(int x = 0; x < tfSearchArea.width; x++) {
	                	for(int y = 0; y < tfSearchArea.height; y++) { 
	                		int i1 = (x + y * tfSearchArea.width);
	                		if ((cdata[i1] & 0xffffff) != 0) {
	                			display.image.setRGB(x + tfSearchArea.x, y + tfSearchArea.y, cdata[i1] & 0xffffff);
	                		}
	                	}
	            	}   
            	}
            	*/
            	//display.image.getWritableTile(0,0).setDataElements(tfTarget.x, tfTarget.y, tf.cimage.getData());
            	//display.image.getWritableTile(0,0).setDataElements(tfTarget.x, tfTarget.y, tfTarget.width, tfTarget.height, 
            	//	pic);
            	display.g2.setColor(Color.red);  
            	//display.g2.draw(tfResult);
            }
            display.redraw(keepFocus);
        }

        // TODO fix so can write even if !doDisplay
	    if (display != null && writer != null) 
    	   writer.write(time, display.image, coi);   
        
        logData();    
        
    	long ms = intTimer.tick();
    	long pms = profTimer.tick();
        if (ms != 0) {
        	// TODO - intTimer is real time, argument "time" is passed in, possibly simlulated
        	double avgMs = intTimer.average();
	        fps = (avgMs > 0) ? 1000.0 / avgMs : 0;
	        
	        if ((ms - avgMs) > avgMs * 5) {
	        	long now = Calendar.getInstance().getTimeInMillis();
	        	System.out.printf("%d.03%d: untimely frame #%d took %d ms, average is %.2f, profTimer is %d\n",
				 now / 1000, now % 1000, count, ms, avgMs, pms);
	        }
        }
        lastFrameTime = time;

        if ((Silly.debug("SHOW_FPS")) && count % intTimer.av.size == 0) {
        	System.out.printf("Frame %05d FPS %.1f\n", count, fps);
        }
        if (pendingKeyCode != 0) { 
        	int key = pendingKeyCode;
        	pendingKeyCode = 0;
        	keyPressedSync(key);
        }
        this.notify();
    }

    private void setLineColorAndWidth(Color c, int w) { 
    	display.g2.setStroke(new BasicStroke(w));
		display.g2.setColor(c);    	
    }
    int repeatFrame = 0;
    
    void processFrame(long t, OriginalImage orig) throws IOException {
	    count++;
    	do {
    		/*
    		if (repeatFrame > 0 && repeatFrame == count)
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				*/
    		processFrameSync(t, orig);
    		if (repeatFrame == count) {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    	} while(repeatFrame > 0 && repeatFrame == count); 
    	
    	if (keypresses != null && keypresses.get((int)count - skipFrames ) != null) { 
    		keyPressed((Integer)keypresses.get((int)count - skipFrames));
    	}
    	if (clicks != null && clicks.get((int)count - skipFrames) != null) { 
    		Point p = clicks.get((int)count - skipFrames);
    		this.onMouseClick(p.x, p.y, 1);
    	}
    	//if (exitFrame > 0 && count == exitFrame)
        	//System.exit(0);
        if (pauseFrame > 0 && count == pauseFrame) 
        	pauser.togglePaused();
        pauser.checkPaused();
        //this.notify();
    }
    
    SteeringTestPulseGenerator steeringDitherPulse = new SteeringTestPulseGenerator();
    SteeringTestPulseGenerator steeringTestPulse = new SteeringTestPulseGenerator();
      

    void displayLs(PidControl p, JamaLeastSquaresFit d, Color c) { 
    	double minX, maxX, minY, maxY;
    	minX = minY = maxX = maxY = 0;
    
    	if (!d.hist.isEmpty()) {
    		minX = maxX = d.hist.peekLast().x;
    		minY = maxY = d.hist.peekLast().y;
    	}
    	for(JamaLeastSquaresFit.Entry i : d.hist) { 
    		if (i.x < minX) minX = i.x;
    		if (i.x > maxX) maxX = i.x;
    		if (i.y < minY) minY = i.y;
    		if (i.y > maxY) maxY = i.y;       		
    	}
    	
    	double rangeY = maxY - minY;
    	maxY += rangeY * 2 + 2	; 
    	minY -= rangeY * 1 + 1;
    	maxX = (maxX - minX) * 1.1 + minX;
    	for(JamaLeastSquaresFit.Entry i : d.hist) { 
    		display.rectangle(c, "", (i.x - minX) / (maxX - minX), 
    				(i.y - minY) / (maxY - minY), .02, .02);
    	}
     	double y = 0;
    	double q = p.quality;
    	if (Double.isNaN(p.quality)) { 
    		System.out.printf("hjello\n");
    	}
       	for(double x = 0.0; x < 1.0; x += .01) { 
       		y = p.d.calculate(minX + x * (maxX - minX));
//	    	display.rectangle(c, "", x, (y - minY) / (maxY - minY), .01, .01);
	    	display.rectangle(c, "", x, (y - minY) / (maxY - minY) - (1 - q) / 5, .01, .01);
	    	display.rectangle(c, "", x, (y - minY) / (maxY - minY) + (1 - q) / 5, .01, .01);
	    }

        
    	c = p.quality > 1.0 ? Color.red : c;
    	y = (y - minY) / (maxY - minY);
   		display.rectangle(c, "", 0.96, y + p.quality / 30, 0.08, .04);
   		display.rectangle(c, String.format("%.1f", p.quality), 0.96, y - (1 - q) / 5, 0.08, .04);
    }
    	
   
    void displayLs(PidControl p, RunningQuadraticLeastSquares d, Color c) { 
    	double minX, maxX, minY, maxY;
    	minX = minY = maxX = maxY = 0;
    
    	if (!d.hist.isEmpty()) {
    		minX = maxX = d.hist.get(d.hist.size() - 1).x;
    		minY = maxY = d.hist.get(d.hist.size() - 1).y;
    	}
    	for(RunningQuadraticLeastSquares.Entry i : d.hist) { 
    		if (i.x < minX) minX = i.x;
    		if (i.x > maxX) maxX = i.x;
    		if (i.y < minY) minY = i.y;
    		if (i.y > maxY) maxY = i.y;       		
    	}
    	
    	double rangeY = maxY - minY;
    	maxY += rangeY * 2 + 2	; 
    	minY -= rangeY * 1 + 1;
    	maxX = (maxX - minX) * 1.1 + minX;
    	for(RunningQuadraticLeastSquares.Entry i : d.hist) { 
    		display.rectangle(c, "", (i.x - minX) / (maxX - minX), 
    				(i.y - minY) / (maxY - minY), .02, .02);
    	}
     	double y = 0;
    	double q = p.quality;
    	if (Double.isNaN(p.quality)) { 
    		System.out.printf("hjello\n");
    	}
       	for(double x = 0.0; x < 1.0; x += .01) { 
       		y = p.d.calculate(minX + x * (maxX - minX));
//	    	display.rectangle(c, "", x, (y - minY) / (maxY - minY), .01, .01);
	    	display.rectangle(c, "", x, (y - minY) / (maxY - minY) - (1 - q) / 5, .01, .01);
	    	display.rectangle(c, "", x, (y - minY) / (maxY - minY) + (1 - q) / 5, .01, .01);
	    }        
    	c = p.quality > 1.0 ? Color.red : c;
    	y = (y - minY) / (maxY - minY);
   		display.rectangle(c, "", 0.96, y + p.quality / 30, 0.08, .04);
   		display.rectangle(c, String.format("%.03f", p.quality), 0.96, y - (1 - q) / 5, 0.08, .04);    	
    }
    
    void displayPid(PidControl p, Color c) { 
    	displayLs(p, p.q, c);
    }

    void printFinalDebugStats() { 
        double avgMs = intTimer.average();
 	  	System.out.printf("FPS=%06.2f RMS errs: LL=%.7f %.7f %.7f, RL=%.7f %.7f %.7f, VP=%.7f %.7f %.7f, avgAction=%.7f\n",
			avgMs != 0 ? 1000.0 / avgMs : 0,  
			pidLL.getAvgRmsErr(), (double)pidLL.lowQualityCount/count, pidLL.avgQuality.calculate(),
			pidRL.getAvgRmsErr(), (double)pidRL.lowQualityCount/count, pidRL.avgQuality.calculate(),
			pidPV.getAvgRmsErr(), (double)pidPV.lowQualityCount/count, pidPV.avgQuality.calculate(),
			steering.totalAction / count);
    }
     

    Logfile logfile = null;
    long logFileStartTime = 0;
	public String logSpec = null;
	
    void logData() {
    	if (logFileStartTime == 0)
    		logFileStartTime = time;
    	if (logfile != null) { 
	    	String s = null;
	    	if (logSpec == null) 
	    		logSpec = "%TEST1";
	    	
	    	if (logSpec == null) {
	    			// TODO - replace all this coded stuff with just a default logSpec string that accomplishes
	    			// the same thing
			    	s = String.format("Frame=%d, Time=%d", count, (int)(time - logFileStartTime));
			    	s += String.format(", Corr=%.2f, Steer=%.2f, ", corr, steer);
			    	s += pidLL.toString("pidll-") + ", ";
			    	s += pidRL.toString("pidrl-") + ", ";
			    	s += pidPV.toString("pidpv-") + ", ";
			    	if (pidLV != null) s += pidLV.toString("pidlv-") + ", ";
			      	if (pidCA != null) s += pidCA.toString("pidca-") + ", ";
			     	s += String.format("tfl.h.bestA=%d tfl.h.bestR=%d ", tfl.h.bestA, tfl.h.bestR);
			     	s += String.format("tfr.h.bestA=%d tfr.h.bestR=%d ", tfr.h.bestA, tfr.h.bestR);
			     	s += String.format("tfl.raw=%d ", tfl.rawPeakHough); 
				 	s += String.format("tfr.raw=%d ", tfr.rawPeakHough); 
				 	s += String.format("tfl.period=%d ", tfl.pd.getPeriod()); 
			    	s += String.format("tfr.period=%d ", tfr.pd.getPeriod()); 
   					if (ccPid != null) { 
						s += String.format("ccScale=%d, ccCorr=%.2f, ", 
			    			tdFindResult != null ? tdFindResult.scale : 0,
			    			ccPid.corr) + ccPid.err.toString(new String("ccpid-")) + ", ";
					}
					s += String.format("trqdiff=%.3f, ", trq1 - trq2);
			       	s += String.format("caL=%.4f, caR=%.4f, caLR=%.08f, caSum=%.04f, ", caL.getCurve(), caR.getCurve(), 
			       			caL.getCurve() * caR.getCurve(), caL.getCurve() + caR.getCurve());
			       	if (tfl != null) { 
			       		Point p = TargetFinderLines.linePairIntercept(tfl, tfr);
			       		
			       		s += String.format("tflVanX=%d, tflVanY=%d, tflAng=%.1f, tflOff=%d, tfrAng=%.1f, tfrOff=%d", p.x, p.y, 
			       				tfl.getInstantaneousAngle(), tfl.getInstantaneousX(height), tfr.getInstantaneousAngle(), tfr.getInstantaneousX(height));
			       		s += String.format(", hVanX=%d, hVanY=%d", houghVan.calculate().x, houghVan.calculate().y);
			       	}
	    	} else { 
	    			s = new String(logSpec);
	    			s = s.replace("%LS1", "t=%time~cor=%corr~st=%steer~del=%delay");
	    			s = s.replace("%TEST1", 
		"t %time st %steer corr %corr tfl %tfl tfr %tfr pvx %pvx " +
		"lat %lat lon %lon hdg %hdg speed %speed gpstrim %gpstrim curve %curve " +
		"strim %strim but %buttons stass %stass %pidrl %pidll %pidpv " +
		"tfl-ang %tfl-ang tfl-x %tfl-x tfr-ang %tfr-ang tfr-x %tfr-x ");
					s = s.replace("%pidrl", pidRL.toString("pidrl-"));
					s = s.replace("%pidll", pidLL.toString("pidll-"));
					s = s.replace("%pidpv", pidPV.toString("pidvp-"));
	    			s = s.replace("%frame", String.format("%d", count));
	    			s = s.replace("%time", String.format("%d", (int)(time - logFileStartTime)));
	    			s = s.replace("%ts", String.format("%d", time));
	    			s = s.replace("%delay", String.format("%d", (int)frameResponseMs));
	    			s = s.replace("%fps", String.format("%.2f", fps));
	    			s = s.replace("%tdx", String.format("%d", tdFindResult == null ? 0 : tdFindResult.x));
	    			s = s.replace("%tdy", String.format("%d", tdFindResult == null ? 0 : tdFindResult.y));
	    			s = s.replace("%tds", String.format("%d", tdFindResult == null ? 0 : tdFindResult.scale));
	    			s = s.replace("%tde", String.format("%d", tdFindResult == null ? 0 : tdFindResult.score));
	    			s = s.replace("%tdv", String.format("%d", tdFindResult == null ? 0 : tdFindResult.var));
	    			s = s.replace("%tddelta", String.format("%.1f", tdAvg.delta));
	    			s = s.replace("%curve", String.format("%f", trimCheat.curve));

	    			s = s.replace("%tfx", String.format("%d", tfResult == null ? 0 : tfResult.x));
	    			s = s.replace("%tfy", String.format("%d", tfResult == null ? 0 : tfResult.y));
	    			s = s.replace("%tfw", String.format("%d", tfResult == null ? 0 : tfResult.width));
	    			s = s.replace("%tfh", String.format("%d", tfResult == null ? 0 : tfResult.height));

	    			s = s.replace("%tfl-ang", String.format("%.3f", tfl.getAngle()));
	    			s = s.replace("%tfl-x", String.format("%.3f", (double)tfl.getOffsetX()));
	    			s = s.replace("%tfr-ang", String.format("%.3f", tfr.getAngle()));
	    			s = s.replace("%tfr-x", String.format("%.3f", (double)tfr.getOffsetX()));

	    			s = s.replace("%tfl-cs", String.format("%.2f", tfl.csX));
	    			s = s.replace("%tfr-cs", String.format("%.2f", tfr.csX));
	    			s = s.replace("%tfl", String.format("%.4f", lpos));
	    			s = s.replace("%tfr", String.format("%.4f", rpos));
	    			s = s.replace("%pvx", String.format("%.4f", persVanX));
	    			s = s.replace("%lvx", String.format("%.4f", laneVanX));
	    	    			
	    			s = s.replace("%stass", String.format("%.4f", joystick.steerAssist));
	    			s = s.replace("%steer", String.format("%.4f", steer));
	    			s = s.replace("%corr", String.format("%.4f", corr));
	    			s = s.replace("%lat", String.format("%+13.8f", gps.lat));
	    			s = s.replace("%lon", String.format("%+13.8f", gps.lon));
	    			s = s.replace("%hdg", String.format("%5.1f", gps.hdg));
	    			s = s.replace("%speed", String.format("%5.1f", gps.speed));
	    			s = s.replace("%strim", String.format("%.5f", steering.trim));
	    			s = s.replace("%lptrim", String.format("%.5f", manualLanePosTrim));
	    			s = s.replace("%gpstrim", String.format("%.5f", trimCheat.trim));
	    			s = s.replace("%gpstcount", String.format("%d", (int)trimCheat.count));
					s = s.replace("%buttons", String.format("%d", joystick.buttonBits));

					s = s.replace("~", " ");
	    			
	    	}
	       	logfile.write(s);
    	}
    }

    void restartOutputFiles() {
    	SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd.HHmmss");
    	String dateString = format.format(new Date());
		ImageFileWriter.RestartAllFiles(dateString);
    	if (writer != null) {
        	if (writer.active)  
        		pv.speak("Recording started!");
        	else
        		pv.speak("Recording stopped!");
    	}
    	if (logfile != null) 
    		logfile.restartFile(dateString);
    	logFileStartTime = 0;
    	System.out.println("Outputfile datestring is now " + dateString);
    }

	synchronized public void actionPerformed(ActionEvent ae) {
		int current = tp.current;
		int i = display.panel.cb.getSelectedIndex();
		tp.selectIndex(i);
		if (tp.current != current) { 
			tp.printCurrent();
		}


		String s = ae.getActionCommand();
		if (s.equals("RECORD")) { 
			keyPressed('A');
		} else if (s.equals("ARM")) { 
			onCruiseJoystick(4);
		} else if (s.equals("EXIT")) { 
			keyPressed('Q');				
		} else if (s.equals("FASTER")) { 
			setCruise(true);
		} else if (s.equals("SLOWER")) { 
			setCruise(false);
		} else if (s.equals("INCREASE")) { 
			tp.adjustParam(+1);
			tp.printCurrent();
		} else if (s.equals("DECREASE")) { 
			tp.adjustParam(-1);
			tp.printCurrent();
		} 		
	}
	
	synchronized public void onMouseClick(int x, int y, int clickCount) {
		System.out.printf("Mouse click %d,%d,%d\n", x, y, clickCount);
		
		if (Silly.debug("DEBUG_ORIGIN")) { 
			this.tfr.hOriginOverride = new Point(x,y);
		}
		//tdStartX  = x - 2; // TODO - figure out why these constant offsets are needed
		//tdStartY = y - 22;
	}
	
	boolean arduinoArmed = false;
	public void onArduinoArmed(int a) {
		arduinoArmed = a > 0;
	}
	
    void writeCompositeImage(BufferedImage image, OriginalImage oi, int rescale, boolean showOriginal, boolean color) {
        int picsize = width * height;
        //int[] data = detector.getData();
        byte[] rescaledata = new byte[picsize * rescale * rescale * 3];
        
        WritableRaster r = image.getWritableTile(0, 0);
        
        //DataBufferByte db = new DataBufferByte(orig.pixels.array(), width * height * rescale * rescale * 3, 0);
        //r.setDataElements(0, 0, width * rescale, height * rescale, orig.pixels.array());
        
        for (int x = 0; x < width; x++) {
        	for (int y = 0; y < height; y++) {
        		for (int x1 = 0; x1 < rescale; x1++) {
        			for (int y1 = 0; y1 < rescale; y1++) {
        				int ri = x * rescale + x1 + (y * rescale + y1) * width * rescale;
        				//if (pixelHasGoodColor(rgb[x + y * width], 170)) {
        				//	rescaledata[ri] = 0xff000000;
        				//} else
        				int pixel = 0;
        				if (color) 
        					pixel = showOriginal ? oi.getPixelABGR(x, y) : 0;
        				else 
        					pixel = showOriginal ? oi.getGreyPixelABGR(x, y) : 0;
        				setPixel(rescaledata, ri, pixel);
        			}
        		}
        	}
        }
        r.setDataElements(0, 0, width * rescale, height * rescale, rescaledata);
        
    }

    void setPixel(byte [] b, int index,  int c) { 
    	int i = index * 3;
    	b[i] = (byte)((c & 0xff0000) >> 16);
        b[i+1]  = (byte)((c & 0xff00) >> 8);
        b[i+2] = (byte)(c & 0xff);
    }
    int getPixel(ByteBuffer b, int x, int y) { 
    	int i = (x + y * width) * 3;
    	return b.get(i) + (b.get(i + 1) << 8) + (b.get(i + 2) << 16);
    }
    int dimPixel(int pixel) {
        int r = (pixel & 0xff0000) >> 16;
        int g = (pixel & 0xff00) >> 8;
        int b = (pixel & 0xff);
        double dim = 0.75;

        return 0xff000000 | ((int) (r * dim) << 16) | ((int) (b * dim) << 8) | (int) (b * dim);
    }

	HslHist2D hsl2d = new HslHist2D();
	HslChronograph hh = new HslChronograph();
	
	public void onMouseDragged(int x, int y) {
	//	int []hsl = this.coi.getHsl(x, y);
	//	System.out.printf("Mouse Dragged %d, %d = (%d,%d,%d)\n", x, y, hsl[0], hsl[1], hsl[2]); 
	//	hsl2d.add(hsl2d.hists[0].maxX + 1, hsl);
	//	hh.add(hsl);
	}

	public void onMouseReleased() {
		// TODO Auto-generated method stub
		//hsl2d.draw();
		//hh.draw();
		//hh.clear();
		//hsl2d.clear();
		
	}

}

