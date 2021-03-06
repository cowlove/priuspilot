import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import math.JamaLeastSquaresFit;
import math.RunningAverage;
import math.RunningLeastSquaresSine;
import math.RunningQuadraticLeastSquares;

import com.centralnexus.input.*;


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
    double servoTrim = -0.03;
    double zoom = 1.0; // zoom, normalized to 320x240
    int displayMode = 10; 
    //LaneAnalyzer la = null;
    TemplateDetect td = null;
    
    BufferedImageDisplayWithInputs display = null;
    public PidControl ccPid = new PidControl("Cruise Control PID");
    public ControlLagLogic ccLag = new ControlLagLogic();
    public PidControl pidLL = new PidControl("Left Line PID");
    public PidControl pidRL = new PidControl("Right Line PID");
    public PidControl pidPV = new PidControl("Perspective PID");
    public PidControl pidLV = new PidControl("Line X PID");
    public PidControl pidCA = new PidControl("Curvature PID");
    //public PidControl pid = new PidControl("Main PID");
    //LabJackJNI labjack = new LabJackJNI();
    public PidControl selectedPid = pidLL;
    public ArrayList<PidControl> pids = new ArrayList<PidControl>();
    
    SteeringLogic steering = new SteeringLogic();
    int width, height, displayRatio;
    FrameProcessorTunableParameters tp = new FrameProcessorTunableParameters(this);
    TargetFinder tf = null;
    
    
       
    TargetFinderLines tfl, tfr, tfro, tflo;
    TargetFinderRoadColor tfrc;
    Rectangle tfrcRect;
    FinderParameters tfparam;
    ArrayList<FinderParameters> tfparams = new ArrayList<FinderParameters>();
    int tfparamIndex;
    int lineIntensityDelta = 40;
    
    InputZeroPointTrim inputZeroPoint = new InputZeroPointTrim();
    PriusVoice pv = new PriusVoice();
    
    public FrameProcessor(int w, int  h, String outFile, String dumpFile, int rescale, 
    		int displayRatio, String serialDevice) throws IOException {
        if (displayRatio > 0) 
        	display = new BufferedImageDisplayWithInputs(this, w * rescale, h * rescale);        
    	if (outFile != null) 
    		writer = new ImageFileWriter(outFile);
        if (dumpFile != null) 
        	logfile = new Logfile(dumpFile);
        width = w;
        height = h;
        this.rescale = rescale;
        this.displayRatio = displayRatio;
        cmdBus = new SerialCommandBus(serialDevice, this);
        
 		
		restartOutputFiles();
        //td = new TemplateDetectCannyCorrelation(w, h);
        //td = new TemplateDetectRGB(w, h);
        
        int minSz = 65; // min/max radius
        int maxSz = 100;
        int houghSize = 71;
        if (Silly.debug("HOUGH_SIZE"))
        	houghSize = Silly.debugInt("HOUGH_SIZE");
        tfl = new TargetFinderLines(w, h, null, true, 45, houghSize, minSz, maxSz, 15, 45);
        tfr = new TargetFinderLines(w, h, null, false, 45, houghSize, minSz, maxSz, 15, 45);
        tflo = new TargetFinderLines(w, h, null, true, 82, 30, minSz, maxSz, 15, 45);
        tfro = new TargetFinderLines(w, h, null, false, 80, 30, minSz, maxSz, 15, 45);
    
    	caR = new CurvatureAnalyzer(false, w, h); 
    	caL = new CurvatureAnalyzer(true, w, h); 

        tfparams.add(tfl.param);
        tfparams.add(tfr.param);	   
        
        tf = new TargetFinderRed(w, h);
        tfparams.add(tf.param);
        tfparamIndex = 0;
        tfparam = tfparams.get(0);

        tfrc = new TargetFinderRoadColor(w, h);
        tfrcRect = new Rectangle((int)(w * 0.47), (int)(h * 0.26), (int)(w * .12), 
        		(int)(h * 0.45));
        //new Rectangle((int)(w * 0.44), (int)(h * 0.35), 
        //		(int)(w * .12), (int)(h * .30));
        pids.add(pidLL);
        pids.add(pidRL);
        pids.add(pidPV);
        pids.add(pidLV);
        pids.add(pidCA);
        pids.add(ccPid);
        selectedPid = pidRL;
        
        steeringTestPulse.testType = steeringTestPulse.TEST_TYPE_SQUARE;
        steeringTestPulse.magnitude = 0.2;
        steeringTestPulse.duration = 0.5;
        steeringTestPulse.count = 0;
        steeringTestPulse.offset = -0.00;
       
        pidRL.setGains(2.25, 0, 2.00, 0.20, 0);
        pidRL.gain.p.hiGain = 1.52;  
        pidRL.finalGain = 1.70;
        pidRL.manualTrim = -0.00;   
        pidRL.qualityFadeThreshold = .0046;
        pidRL.qualityFadeGain = 2;
        
        pidLL.copySettings(pidRL);
        
        pidRL.gain.p.loTrans = -0.045;  // "bumper" points of increased gain for lane proximity
        pidLL.gain.p.hiTrans = +0.037;  // TODO - change when the tfl prescale constant changes
        
        pidLV.setGains(2.0, 0, 0.40, 0.30, 0);
        pidLV.finalGain = .90;
        pidLV.manualTrim = 0;
        pidLV.qualityFadeThreshold = .084;
        pidLV.qualityFadeGain = 5;
        
        pidPV.copySettings(pidLV);
        pidPV.qualityFadeThreshold = 0.0100;
        
        pidCA.setGains(10.0, 0, 0, 0, 0);
        pidCA.finalGain = 0; //:1.55;
        pidCA.period = pidCA.new PID(1.2, 1, 1, 1, 1);
        pidCA.manualTrim = +0.00;
        pidCA.qualityFadeThreshold = 0.003;
        pidCA.qualityFadeGain = 5;
        pidCA.finalGain = 0; // disable
        
        ccPid.setGains(.06, 0, .34, 0, 1);
        ccPid.finalGain = 0.9;
        ccPid.period = ccPid.new PID(3, 1, 4.5, 1, 1);       
        ccLag.actuationTime = 0;
        ccLag.deadTime = 1000;
        ccLag.lagTime = 2000;
        ccPid.qualityFadeGain = 0;
        ccPid.reset();
        
        tfSearchArea = new Rectangle(0, 0, w, h);
        tdStartX = w / 2;
        tdStartY = h / 3;
        
        inputZeroPoint.zeroPoint.vanX = 204;
        inputZeroPoint.zeroPoint.vanY = 39;
        inputZeroPoint.zeroPoint.rLane = 464;
        inputZeroPoint.zeroPoint.lLane = -37;
        
        cmdBus.start();
    }
    
    AdvisorySounds sounds = new AdvisorySounds();
    ImageFileWriter writer = null;
    int rescale = 1;
    double fps = 0;
    int framesDropped = 0;
    boolean keepFocus = false;
    IntervalTimer intTimer = new IntervalTimer(100);
    boolean paused = false;
    double manualSteerIncrement = .1;
    Map<Object,Object> keypresses = null;
    Map<Object,Point> clicks = null;
    
    synchronized void reset() {
        for(PidControl p : pids) 
        	p.reset();
        steering.reset();
        tfl.reset();
        tfr.reset();
        tflo.reset();
        tfro.reset();
    }
    
    private int normalizePixel(int x) { 
    	return x * height / 360;
    }

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
        else if (keyCode == 10) { // [ENTER] key
        	// nothing right now
        }  else if (keyCode == 32) { // [SPACE] key
        		noProcessing = !noProcessing;  // toggle processing on/off
         		reset();
         	 	steer = 0;
         	 	tfFindTargetNow = false;
         	 	tdFindResult = null;
         	 	setSteering(0);
         	 	/*
         	 	noProcessing = disarmed;
	           	if (td != null) {
	        		td.active = !disarmed;
		        	tdFindResult = null;
		        	tdTempFrame = null;
	           	}
	           	*/
        } else if (keyCode == 8) { // backspace
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
        	steeringTestPulse.startPulse(-1);
        }
        else if (keyCode == 39) { // right arrow 
         	//tp.adjustParam(10);
         	//tp.printCurrent();
         	//logPidSettings();
         	steeringTestPulse.startPulse(1);
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
    SerialCommandBus cmdBus = null;
    
    double steeringDeadband = 0.00;
    double epsSteeringGain = 3.1;			
    double trq1 = 0, trq2 = 0;
    int steerOverrideTorque = 300;
    
    long lastCruiseSet = 0; // time of last cruise control command in ms
    synchronized void setCruise(boolean up) {
    	long now = Calendar.getInstance().getTimeInMillis();
    	if (now - lastCruiseSet > 250) {  // limit cruise control commands
    									  // to one per 1/4sec 
	    	int val = up ? 40 : 25;
	    	System.out.printf("setCruise(%s)\n", up ? "UP               " : "               DOWN");
	    	if (this.arduinoArmed) {
		    	cmdBus.writeCmd('c', val, 50);
	    	}
	    	lastCruiseSet = now;
    	}
    }

    int ardDebugInterval = 500;  // 100th seconds
    synchronized void setArduinoDebug(int level) { 
    	ardDebugInterval = level;
    	cmdBus.writeCmd('d', level);
    }
    
    synchronized void setSteering(double x) { 
    	x += servoTrim;
        x = x * epsSteeringGain;
        
        final double maxSteer = 2.2; // wasn't 2.20 a bit excessive?! (WTF)
        x = Math.max(x, -maxSteer);
        x = Math.min(x, maxSteer);
            
    	int st = (int)(-x * 128 / 2.5);
    	
    	cmdBus.writeCmd('s', st);
    	if (count % 30 == 0) { 
	    	cmdBus.writeCmd('t', steerOverrideTorque);
	     	//cmdBus.requestAck();
    	}
    }

    RunningAverage avgFrameDelay = new RunningAverage(100);
    // TODO- separate text window to display test stats
    void writeDisplayText() {
        display.writeText("FRAME: " + String.format("%d", count));
        display.writeText("FPS: " + String.format("%.1f", fps));
        display.writeText("DROPPED: " + framesDropped);
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
    long time = 0;
    boolean noProcessing = false;
    RemoteTcpCommandSocket cmdSocket = null; //new RemoteTcpCommandSocket();
    
    // HISTORICAL prescales - todo - push these constants through and adjust all the PID settings accordingly
    // Constants to adjust to new 360p frame size.  Lane width, measured at intercept 
    // of lane lines and bottom of frame and normalized to frame width, is about 1.5 times 
    // smaller in the 640x360 image than the old 320x240 frame.

    // The 0.84 is because the offset was mistakenly being computed at the bottom
    // of the tfl search area, not at the bottom of the frame, and the PIDs were
    // extensively tuned this way. 

    // the divide by 4 is historical. 
    final double lanePosPrescale = 1.5f * 0.84 / 4; 
	
	// Fixed dash feature was 224 out of 240 pixels on an 320x240 image, and 
	// 300 out of 640 pixels on a 640x360p image.  The 1.5 is historical 
	final double pixelWidthPrescale = 1.5f * (224f / 240) / (300f / 640);  
  
    TemplateDetect.FindResult tdFindResult = null;
    int badTdCount = 0;
    IntervalTimer profTimer = new IntervalTimer(100);
    int tdStartX = 180 + 10;  // TODO raw pixel use!
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
	
    long ccLastCorrectionTime = 0;
	long ccMinCorrectionInterval = 400; // milliseconds
    int ccSetPoint = 15;
	RunningAveragePoint houghVan = new RunningAveragePoint(5);
	
	GnuplotWrapper gp = new GnuplotWrapper();
    
    static Rectangle scaleRect(Rectangle r, int scale) {
    	return new Rectangle(r.x * scale, r.y * scale, r.width * scale, r.height * scale);
    }
    
	CurvatureAnalyzer caR, caL;
	int [] vpL, vpR;
	

	OriginalImage coi = null;
    synchronized void processFrameSync(long t, OriginalImage oi) throws IOException {
		double lpos = Double.NaN, rpos = Double.NaN, laneVanX = Double.NaN,
		persVanX = Double.NaN;

		time = t;
    	if (skipFrames > 0 && skipFrames-- > 0)
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
	   		
	   		if (Silly.debug("DEBUG_COLOR_SEGMENTATION")) { 
		   		BufferedImageDisplay.nextX = 640;
		   		BufferedImageDisplay.nextY = 20;
		   		tfrc.hh.draw(1);
	   		}
	   		
	   		final int vanRectW = 100;
	   		final int vanRectH = 24;
			final int vpScale = 1;
			   		
	   		tflo.vanLimits = tfro.vanLimits = tfl.vanLimits = tfr.vanLimits = new
	   			Rectangle(inputZeroPoint.zeroPoint.vanX - vanRectW / 2, 
	   					inputZeroPoint.zeroPoint.vanY - vanRectH / 2, vanRectW, vanRectH);
	   		tfrc.sa.x = inputZeroPoint.zeroPoint.vanX - tfrc.sa.width / 2;
	   		
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
	   	   		tfl.h.projectIntoRect(vpL, r, vpScale);
	   	   		r = (Rectangle)tflo.vanLimits.clone();
	   	   		r.x -= tflo.sa.x;
	   	   		r.y -= tflo.sa.y;
	   			tflo.h.projectIntoRect(vpL, r, vpScale);
	
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
			tfr.h.projectIntoRect(vpR, r, vpScale);
	   	
	   		r = (Rectangle)tfro.vanLimits.clone();
	   		r.x -= tfro.sa.x;
	   		r.y -= tfro.sa.y;
			tfro.h.projectIntoRect(vpR, r, vpScale);
	   		
	   		try { 
	   			t1.join(0);
	   		} catch(Exception e) {}
	   		
	   		int [] vp = new int[vpL.length];
	   		for(int i = 0; i < vp.length; i++)  
	   			vp[i] = vpL[i] + vpR[i];
	   		
			double gr = 5.5f / vpScale;
			GaussianKernel gk = new GaussianKernel(gr, (int)(gr * 8), r.width / vpScale, 
					r.height / vpScale);
			gk.blur(vp);
	
			houghVan.add(new Point(/*gk.bestColumn(vp)*/gk.bestX * vpScale + tfr.vanLimits.x, gk.bestY * vpScale + tfr.vanLimits.y));
			if (gk.max > 15) {	
				persVanX = (((double)houghVan.ax.calculate()  - inputZeroPoint.zeroPoint.vanX) / width) * pixelWidthPrescale;
			}
	   		
	   		if (Silly.debug("MARKUP")) { 
		   		tfl.markup(coi);
		   		tfr.markup(coi);
		   		tflo.markup(coi);
		   		tfro.markup(coi);
		   		caL.markup(coi);
		   		caR.markup(coi);
	   		}
			
	   		if (Silly.debug("DEBUG_LINES")) {
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
			
	   		// Propagate config changes from the selected lane pid to the other lane pid
	   		// Except for hi/lo gain transition points
	   		double d1 = pidLL.gain.p.loTrans;
	 		double d2 = pidLL.gain.p.hiTrans;
	 		double d3 = pidRL.gain.p.loTrans;
	 		double d4 = pidRL.gain.p.hiTrans;   		
			if (selectedPid == pidLL) 
				pidRL.copySettings(pidLL);
			else
				pidLL.copySettings(pidRL);
	   		pidLL.gain.p.loTrans = d1;
	 		pidLL.gain.p.hiTrans = d2;
	 		pidRL.gain.p.loTrans = d3;
	 		pidRL.gain.p.hiTrans = d4;	
			
	   		
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
	    		lpos = (double)(tfl.getInstantaneousX(height) - inputZeroPoint.zeroPoint.lLane) / width * lanePosPrescale;
			
			if (tfr.focus.getQuality() > laneMinQuality) 	        		
	    		rpos = (double)(tfr.getInstantaneousX(height) - (inputZeroPoint.zeroPoint.rLane)) / width * lanePosPrescale;
			
			if (tfr.focus.getQuality() > laneMinQuality && tfl.focus.getQuality() > laneMinQuality) {
	       		laneVanish = TargetFinderLines.linePairIntercept(tfl, tfr);
	      		if (tfl.insideVanRect(laneVanish))
	      			laneVanX = ((double)(laneVanish.x - inputZeroPoint.zeroPoint.vanX)) / width * 1.5 * pixelWidthPrescale; 
			}
			
			if (!Double.isNaN(lpos) && !Double.isNaN(rpos) && !Double.isNaN(laneVanX)) { 
				inputZeroPoint.add(laneVanish.x, laneVanish.y, tfl.getInstantaneousX(height), tfr.getInstantaneousX(height));
			} else { 
				inputZeroPoint.reset();
			}
			
			
			corr = -(pidLL.add(lpos, time)  + pidRL.add(rpos, time)) / 2;
			corr += -(pidLV.add(laneVanX, time) + pidPV.add(persVanX, time));
			double curve = 0;
			if (!Double.isNaN(caL.getCurve()))
				curve += caL.getCurve();
			if (!Double.isNaN(caR.getCurve()))
				curve += caR.getCurve();
		
			if (caL.getCurve() * caR.getCurve() < 0) 
				curve = Double.NaN;
			corr += -pidCA.add(curve, time);
	
			if (tfFindTargetNow) {
				// try to set tdFindResult to new template.  If fails, tdFindResult will be left null
				tdStartX = 180 + 10;
				tdStartY = 70;
				tfResult = tf.findNearest(coi, tfSearchArea, tdStartX, tdStartY);
		    	if (tfResult != null) { 
	    	    	tdStartX = tfResult.x + tfResult.width / 2 + tf.fudge / 2;
	    	    	tdStartY = tfResult.y + tfResult.height / 2 + tf.fudge / 2;
	    			//tdFindResult = td.setTemplate(picbb.array(), tdStartX, tdStartY, tfResult.width, tfResult.height);
	    			tdAvg.reset();
	    			tdAvg.add(tdFindResult);
	    			tfFindTargetNow = false;
	    			if(Silly.debug("CONT_TF")) // debugging - continiously run targetfinder  
	    				tfFindTargetNow = true;
		    	} else {
		    		tdFindResult = null;
		    	}
	    	}
	    	
	    	
	        if (td != null) {
				//td.newFrame(picbb.array());
		    	td.setSearchDist(5, 5, 2);
	        	double pos = 0;
	        	if (tdFindResult != null) {
	        		//if (tdFindResult.scale < -10)
	        		//	tdFindResult.scale = -10;
	        		
	        		// prohibit scale frome changing more than td.searchDist.scale / tdScale.size per frame 
	        		tdAvg.set(tdFindResult);
	        		//td.find(tdFindResult, picbb.array());
	        		//if (tdChartFiles != null) 
	        		//	td.makeChartFiles(tdFindResult, picbb.array(), tdChartFiles);
	            	sounds.setAlertLevel(tdFindResult.score / tdMaxErr);
	            	tdAvg.add(tdFindResult);
		        	pos = (double)(tdFindResult.x - tdStartX) / width * zoom; 
		        	if (tdFindResult.score > tdMaxErr) {
		        		if (++badTdCount > 6) {
			        		//System.out.printf("Large error %d\n", (int)tdFindResult.score);
			        		corr = 0;
			        		td.active = false;
			        		noProcessing = true;
			        		tdFindResult = null;
		        		}
		      
		        	} else {
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

	        // TODO- deadband should probably be applied last
	        // TODO- deadband and other things should be moved into steering control code
	        if (steeringDeadband > 0 || (steeringDeadband < 0 && Math.abs(corr) > Math.abs(steeringDeadband))) { 
		        if (corr < 0) corr -= steeringDeadband;
		        if (corr > 0) corr += steeringDeadband;
	        } 
        }
        
        if (cmdSocket != null) { 
	        double remoteInput = cmdSocket.getSteer();
	        if (remoteInput != 0.0) 
	        	steer = remoteInput / 10;
        }
        
        if (!noProcessing) 
        	steer = corr;    
        else
            steer = 0;
        
        //temp disabled for steering gain investigations 	
        //if (Math.abs(steer) < .25 && arduinoArmed)
        //	steer += steeringDitherPulse.currentPulse();

        steer = steering.steer(time, steer);
        steer += steeringTestPulse.currentPulse();
        
        steer = joystick.steer(steer);
        
        setSteering(steer);
        
        double tc = joystick.getThrottleChange();
        if (tc != 0.0)
        	setCruise(tc > 0/*increase vs decrease*/);
    	
        if (joystick.getExit())  
        	this.keyPressed('Q');
        if (joystick.getArm())
        	noProcessing = false;
        if (joystick.getDisarm())
        	noProcessing = true;
        
        if (joystick.getRecordButtonPressed()) {
        	restartOutputFiles();
        }
        if (false) { 
	        long delay = Calendar.getInstance().getTimeInMillis() - t;
	        avgFrameDelay.add(delay);
	        System.out.printf("delay %d ms\n", (int)avgFrameDelay.calculate());
        }
        
        if (displayRatio > 0 && (count % displayRatio) == 0) {
        	writeCompositeImage(display.image, coi, rescale, (displayMode & 0x4) != 0,
        			(displayMode & 32) != 0);
  
            if ((displayMode & 0x1) != 0) {
                writeDisplayText();                  
            }
            if ((displayMode & 0x2) != 0) {
                if (writer != null && writer.active)  
                	display.rectangle(Color.red, "REC", 0.90, 0.10, 0.1, 0.05);

                double yoff = 0.65;
	            double yspace = 0.05;
            	display.g2.setStroke(new BasicStroke(2));
	            if (tdFindResult != null) {  
	            	display.draw(arduinoArmed ? Color.red : Color.green, scaleRect(td.targetRect(tdFindResult), rescale));
	            }

	            setLineColorAndWidth(Color.green, 1);
    			TargetFinderLines.displayLinePairToOutsideVanRec(tflo, tfro, display.g2);
	            setLineColorAndWidth(Color.red, 2);
    			TargetFinderLines.displayLinePair(tfl, tfr, display.g2);
	            setLineColorAndWidth(Color.blue, 2);
       			caL.display(display.g2);
    			caR.display(display.g2);
                setLineColorAndWidth(Color.lightGray, 2);
        		tfrc.draw(display.g2);
        		display.g2.draw(tfl.vanLimits);

    			
    			int s = 7;
        		Rectangle r1 = new Rectangle(houghVan.calculate().x - s, houghVan.calculate().y - s, s * 2 + 1, s * 2 + 1);
    			display.g2.setColor(Color.orange);
    			display.g2.draw(r1);
    			display.g2.drawLine(r1.x, r1.y, r1.x + r1.width, r1.y + r1.height);
    			display.g2.drawLine(r1.x, r1.y + r1.height, r1.x + r1.width, r1.y);
    			
    			//display.g2.draw(tfl.sa);
    			//display.g2.draw(tfr.sa);
    			

    			if (tfFindTargetNow)
	            	display.draw(Color.yellow, scaleRect(tfSearchArea, rescale));

    			final double bWidth = 0.06;
	            display.rectangle(Color.blue, "FB", steering.lag.feedback + 0.5, yoff, bWidth, 0.05);
	   	        display.rectangle(Color.pink, "", corr + 0.5, yoff, bWidth, 0.05);
	            display.rectangle(Color.red, "ST", steer + 0.5, yoff, bWidth, 0.05);
	            for( PidControl pid : pids ) { 
	            	yoff += yspace;
	            	display.text(pid.description, 0, yoff + 0.05);
		            display.rectangle(Color.white, "S", -pid.corr + 0.5, yoff, bWidth, 0.05, true);
		            display.rectangle(Color.yellow, "P", pid.err.p + 0.5, yoff + 0.005, bWidth, 0.04, true);
		            display.rectangle(Color.green, "D", pid.err.d + 0.5, yoff + 0.01, bWidth, 0.03, true);	     
		            display.rectangle(Color.black, "D", pid.err.j + 0.5, yoff + 0.015, bWidth, 0.02, true);	     
		        	Color c = pid.quality < 1.0 ? Color.red : Color.yellow;
		       		display.rectangle(c, String.format("%03d", (int)(pid.drms / pid.qualityFadeThreshold * 100)), 
		       				.41 + pid.quality * 0.52, yoff, bWidth, 0.05, false);
		  //          display.text(pid.description, 0.0, yoff);
	            }
	            display.rectangle(Color.cyan, String.format("%.1f", fps), Math.min(0.95, (double)fps / 32), yoff, bWidth, 0.05);	            
            }
            if (false && (displayMode & 0x8) != 0) {
            	displayPid(pidLL, Color.yellow);
               	displayPid(pidLV, Color.green);
               	displayPid(pidPV, Color.blue);
               	displayPid(pidRL, Color.white);
            }
            	
            if ((displayMode & 0x10) != 0) {
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
	       
	        	System.out.println(String.format("%d.03%d", now / 1000, now % 1000) + ": untimely frame #" + count + " took " + ms + " ms, average is " + avgMs + " profTimer is "
	        		   + pms);
	        }
        }
        lastFrameTime = time;

        if ((Silly.debug("SHOW_FPS")) && count % intTimer.av.size == 0) {
        	System.out.printf("%.3f FPS\n", fps);
        }
        if (pendingKeyCode != 0) { 
        	int key = pendingKeyCode;
        	pendingKeyCode = 0;
        	keyPressedSync(key);
        }
        
        if (cmdBus.ignitionOffCount > 5) {
        	System.out.printf("Ignition seems off, exiting\n");
        	System.exit(1);
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
    	} while(repeatFrame > 0 && repeatFrame == count); 
    	
    	if (keypresses != null && keypresses.get((int)count) != null) { 
    		keyPressed((Integer)keypresses.get((int)count));
    	}
    	if (clicks != null && clicks.get((int)count) != null) { 
    		Point p = clicks.get((int)count);
    		this.onMouseClick(p.x, p.y, 1);
    	}
    	if (exitFrame > 0 && count == exitFrame)
        	System.exit(0);
        if (pauseFrame > 0 && count == pauseFrame) 
        	pauser.togglePaused();
        pauser.checkPaused();
        //this.notify();
    }
    
    SteeringTestPulseGenerator steeringDitherPulse = new SteeringTestPulseGenerator();
    SteeringTestPulseGenerator steeringTestPulse = new SteeringTestPulseGenerator();
        
    void displayPid(PidControl p, Color c) { 
    	double minX, maxX, minY, maxY;
    	minX = minY = maxX = maxY = 0;
    
    	if (!p.d.hist.isEmpty()) {
    		minX = maxX = p.d.hist.peekLast().x;
    		minY = maxY = p.d.hist.peekLast().y;
    	}
    		
    	for(JamaLeastSquaresFit.Entry i : p.d.hist) { 
    		if (i.x < minX) minX = i.x;
    		if (i.x > maxX) maxX = i.x;
    		if (i.y < minY) minY = i.y;
    		if (i.y > maxY) maxY = i.y;       		
    	}
    	
    	double rangeY = maxY - minY;
    	maxY += rangeY * 2 + 2	; 
    	minY -= rangeY * 1 + 1;
    	maxX = (maxX - minX) * 1.1 + minX;
    	for(JamaLeastSquaresFit.Entry i : p.d.hist) { 
    		display.rectangle(c, "", (i.x - minX) / (maxX - minX), 
    				(i.y - minY) / (maxY - minY), .02, .02);
    	}
    	double y = 0;
    	double q = p.quality;
    	if (Double.isNaN(p.quality)) { 
    		System.out.printf("hjello\n");
    	}
       	for(double x = 0.0; x < 1.0; x += .01) { 
       		y = p.d.predict(minX + x * (maxX - minX));
//	    	display.rectangle(c, "", x, (y - minY) / (maxY - minY), .01, .01);
	    	display.rectangle(c, "", x, (y - minY) / (maxY - minY) - (1 - q) / 5, .01, .01);
	    	display.rectangle(c, "", x, (y - minY) / (maxY - minY) + (1 - q) / 5, .01, .01);
	    }

        
    	c = p.quality > 1.0 ? Color.red : c;
    	y = (y - minY) / (maxY - minY);
   		display.rectangle(c, "", 0.96, y + p.quality / 30, 0.08, .04);
   		display.rectangle(c, String.format("%.1f", p.quality), 0.96, y - (1 - q) / 5, 0.08, .04);
    }
    
    Logfile logfile = null;
    long startTime = 0;
	public String logSpec = null;
	
    void logData() {
    	if (logfile != null) { 
	    	if (startTime == 0)
	    		startTime = time;
	    	String s = null;
	    	if (logSpec == null) {
	    			// TODO - replace all this coded stuff with just a default logSpec string that accomplishes
	    			// the same thing
			    	s = String.format("Frame=%d, Time=%d", count, (int)(time - startTime));
			    	s += String.format(", Corr=%.2f, Steer=%.2f, ", corr, steer);
			    	s += pidLL.toString("pidll-") + ", ";
			    	s += pidRL.toString("pidrl-") + ", ";
			    	s += pidPV.toString("pidpv-") + ", ";
			    	s += pidLV.toString("pidlv-") + ", ";
			      	s += pidCA.toString("pidca-") + ", ";
			     	s += String.format("tfl.h.bestA=%d tfl.h.bestR=%d ", tfl.h.bestA, tfl.h.bestR);
			     	s += String.format("tfr.h.bestA=%d tfr.h.bestR=%d ", tfr.h.bestA, tfr.h.bestR);
			     	s += String.format("tfl.raw=%d ", tfl.rawPeakHough); 
				 	s += String.format("tfr.raw=%d ", tfr.rawPeakHough); 
				 	s += String.format("tfl.period=%d ", tfl.pd.getPeriod()); 
			    	s += String.format("tfr.period=%d ", tfr.pd.getPeriod()); 
   					s += String.format("ccScale=%d, ccCorr=%.2f, ", 
			    			tdFindResult != null ? tdFindResult.scale : 0,
			    			ccPid.corr) + ccPid.err.toString(new String("ccpid-")) + ", ";
			       	s += String.format("trqdiff=%.3f, ", trq1 - trq2);
			       	s += String.format("caL=%.4f, caR=%.4f, caLR=%.08f, caSum=%.04f, ", caL.getCurve(), caR.getCurve(), 
			       			caL.getCurve() * caR.getCurve(), caL.getCurve() + caR.getCurve());
			       	if (tfl != null) { 
			       		Point p = TargetFinderLines.linePairIntercept(tfl, tfr);
			       		
			       		s += String.format("tflVanX=%d, tflVanY=%d, tflAng=%.1f, tflOff=%d, tfrAng=%.1f, tfrOff=%d", p.x, p.y, 
			       				tfl.getInstantaneousAngle(), tfl.getInstantaneousX(height), tfr.getInstantaneousAngle(), tfr.getInstantaneousX(height));
			       		s += String.format(", hVanX=%d, hVanY=%d", houghVan.calculate().x, houghVan.calculate().y);
			       	}
			       	s += ", serDebug = " + cmdBus.lastDebugString;
			       	cmdBus.lastDebugString = "";
	    	} else { 
	    			s = new String(logSpec);
	    			s = s.replace("%frame", String.format("%d", count));
	    			s = s.replace("%time", String.format("%d", (int)(time - startTime)));
	    			s = s.replace("%fps", String.format("%.2f", fps));
	    			s = s.replace("%tdx", String.format("%d", tdFindResult == null ? 0 : tdFindResult.x));
	    			s = s.replace("%tdy", String.format("%d", tdFindResult == null ? 0 : tdFindResult.y));
	    			s = s.replace("%tds", String.format("%d", tdFindResult == null ? 0 : tdFindResult.scale));
	    			s = s.replace("%tde", String.format("%d", tdFindResult == null ? 0 : tdFindResult.score));
	    			s = s.replace("%tdv", String.format("%d", tdFindResult == null ? 0 : tdFindResult.var));
	    			s = s.replace("%tddelta", String.format("%.1f", tdAvg.delta));

	    			s = s.replace("%tfx", String.format("%d", tfResult == null ? 0 : tfResult.x));
	    			s = s.replace("%tfy", String.format("%d", tfResult == null ? 0 : tfResult.y));
	    			s = s.replace("%tfw", String.format("%d", tfResult == null ? 0 : tfResult.width));
	    			s = s.replace("%tfh", String.format("%d", tfResult == null ? 0 : tfResult.height));

	    			s = s.replace("%steer", String.format("%.2f", steer));
	    			s = s.replace("~", " ");
	    			
	    	}
	       	logfile.write(s);
    	}
    }

    void restartOutputFiles() {
    	SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd.HHmmss");
    	String dateString = format.format(new Date());
    	if (writer != null) {
    		writer.restartFile(dateString);
        	if (writer.active)  
        		pv.speak("Recording started!");
        	else
        		pv.speak("Recording stopped!");
    	}
    	if (logfile != null) 
    		logfile.restartFile(dateString);
    	System.out.println("Outputfile datestring is now " + dateString);
    }

	synchronized public void onButtonPressed(ActionEvent ae) {
		String s = ae.getActionCommand();
		if (s.equals("RECORD")) { 
			keyPressed('A');
		} else if (s.equals("EXIT")) { 
			keyPressed('Q');				
		} else if (s.equals("FASTER")) { 
			setCruise(true);
		} else if (s.equals("SLOWER")) { 
			setCruise(false);
		} 		
	}
	
	synchronized public void onMouseClick(int x, int y, int clickCount) {
		System.out.printf("Mouse click %d,%d,%d\n", x, y, clickCount);
		
		//tdStartX  = x - 2; // TODO - figure out why these constant offsets are needed
		//tdStartY = y - 22;
		
		/*
		tdFindResult = null;
		noProcessing = false;
		tfFindTargetNow = true;
		disarmed = true;
		*/
		//tfrcRect.x = x - 2;
		//tfrcRect.y = y - 22;
		
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

}

