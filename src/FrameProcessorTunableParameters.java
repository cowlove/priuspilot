
class FrameProcessorTunableParameters extends TunableParameterList { 
	void add(String s, char k, double inc, TunableParameter.Adjust a) { 
		add(s,k,inc,a, null);		
	}
	void add(String s, char k, double inc, TunableParameter.Adjust a, 
			TunableParameter.Print p) { 
		if (findParam((int)k) !=  null) 
			System.out.printf("Warning: key '%c'/%d already bound\n", k, (int)k);
		super.add(new TunableParameter(s, k, inc, a, p));
	}
	public FrameProcessorTunableParameters(FrameProcessor f) { 
		fp = f;
		
		add("GPS curve gain", '1', 0.001, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.gps.curveGain += i; }} );
		add("GPS curve period", '2', 0.1, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.gps.avgCurve.maxAge += i; }} );
		add("GPS curve max", '3', 0.01, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.gps.maxCurve += i; }} );

		add("steer.maxSteer", (char)59, .01,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.steering.maxSteer += i; }});
		add("steer.maxChange", '[', .0001,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.steering.maxChange += i; }});
		add("fp.debugMode", 'X', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.debugMode += i; }});

		add("fp.epsSteeringGain", 'C', .01, 
				new TunableParameter.Adjust() { public double adjust(double i) { 
					return fp.epsSteeringGain += i; }} );
					
		add("PID P gain", 'P', 0.01, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.selectedPid.gain.p.loGain += i; }} );
		add("PID I gain", 'I', 0.001, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.selectedPid.gain.i.loGain += i; }} );
		add("PID D gain", 'D', 0.01, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.selectedPid.gain.d.loGain += i; }} );
/*
		add("PID P period", '4', 1, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.pid.period.p += i; }} );
		add("PID I period", '5', 1, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.pid.period.i += i; }} );
		
		add("PID D period", '6', 1, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.pid.period.d += i; }} );
		add("PID L period", '7', 1, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.pid.period.l += i; }} );
				*/

		/*
		add("Debug Lines", 'F', 1, 
				new TunableParameter.Adjust() { public double adjust(double i) { 
					if (i != 0) 
						Silly.debugFlags ^= Silly.DEBUG_LINES; 
					return Silly.debugFlags; }} );
*/
		/*
		add("PID P period", '7', 1, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.selectedPid.period.p += i; }} );
		add("PID I period", '8', 1, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.selectedPid.period.i += i; }} );
		
		add("PID D period", '9', 0.1, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.selectedPid.period.d += i; }} );
		*/
		
		
		add("PID finalGain", 'G', 0.01, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.selectedPid.finalGain += i; }} );
		//add("PID ierrThreshold", 'Y', .01, 
		//	new TunableParameter.Adjust() { public double adjust(double i) { return fp.pid.ierrorThreshold += i; }} );
		//add("PID ierrLimit", 'O', .01, 
		//		new TunableParameter.Adjust() { public double adjust(double i) { return fp.pid.ierrorLimit += i; }} );
		//add("PID manualLaneTrim", 'M', .01, 
		//		new TunableParameter.Adjust() { public double adjust(double i) { return fp.selectedPid.manualTrim += i; }} );
		add("FP manualLanePosTrim", 'M', .01, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.manualLanePosTrim += i; }} );
		//add("FP select finder param group", 'X', 1, 
		//			new TunableParameter.Adjust() { public double adjust(double i) { 
		//				fp.tfparamIndex += i;
		//				if (fp.tfparamIndex >= fp.tfparams.size())
		//					fp.tfparamIndex = 0;
		//				if (fp.tfparamIndex < 0)
		//					fp.tfparamIndex = fp.tfparams.size() - 1;
		//				fp.tfparam = fp.tfparams.get(fp.tfparamIndex);
		//				if (i != 0) 
		//					System.out.print("tfparam " + fp.tfparam.name + " selected\n");
		//				return fp.tfparamIndex;
		//			}} );

		add("fp.vsenseErrMax", 'Y', .01, 
				new TunableParameter.Adjust() { public double adjust(double i) { 
					return fp.vsenseErrMax += i; }} );
//		add("detector.threshold1", 'Y', 1, 
//				new TunableParameter.Adjust() { public double adjust(double i) { 
//					return fp.tfparam.threshold1 = (fp.tfparam.threshold1 += i); }} );
		//add("PID gain.p.loTrans", 'L', .001, 
		//		new TunableParameter.Adjust() { public double adjust(double i) { 
		//			return fp.selectedPid.gain.p.loTrans += i; }} );
		add("PID gain.p.hiGain", 'O', .02, 
				new TunableParameter.Adjust() { public double adjust(double i) { 
					return (fp.selectedPid.gain.p.hiGain += i); }} );
		//add("ca.growSegmentSize", '7', .01, 
		//		new TunableParameter.Adjust() { public double adjust(double i) { 
		//			return fp.caR.growSegmentSize = (fp.caL.growSegmentSize += i); }} );
		//add("ca.maxGrowGap", '8', 1, 
		//		new TunableParameter.Adjust() { public double adjust(double i) { 
		//			return fp.caR.maxGrowGap = (fp.caL.maxGrowGap += i); }} );
		//add("ca.maxGrowError", '9', 0.1, 
		//		new TunableParameter.Adjust() { public double adjust(double i) { 
		//			return fp.caR.maxGrowError = (fp.caL.maxGrowError += i); }} );
		//add("detector.gaussianRadius", '0', 0.1, 
		//		new TunableParameter.Adjust() { public double adjust(double i) { 
		//			return fp.tfparam.gaussianKernelRadius = (fp.tfparam.gaussianKernelRadius += i); }} );
		//add("tfl.h.blurRadius", '-', .005, 
		//		new TunableParameter.Adjust() { public double adjust(double i) { 
		//			return fp.tfl.h.blurRadius = fp.tflo.h.blurRadius = fp.tfro.h.blurRadius = (fp.tfr.h.blurRadius += i); }} );

		// Available  - shift (16) ctrl (17) alt (18) windows (524) 
		
		add("FP select PID", 'E', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					if (i != 0) {
						int idx = fp.pids.indexOf(fp.selectedPid) + (int)i;
						if (idx >= fp.pids.size()) 
							idx = 0;
						if (idx < 0)
							idx = fp.pids.size() - 1;
						fp.selectedPid = fp.pids.get(idx);
					}
					return 0; 
				}},
				new TunableParameter.Print() { public String print() {
					return fp.selectedPid.description;
				}}
				);
		
		//add("PID derrDegree", 'T', 1,
		//		new TunableParameter.Adjust() { public double adjust(double i) {
		//			return fp.pid.derrDegree += i; }} );
		add("dither.magnitude", 'K', 0.01, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.steeringDitherPulse.magnitude += i; }} );
		add("dither.period", 'L', 0.01, 
				new TunableParameter.Adjust() { public double adjust(double i) { return fp.steeringDitherPulse.duration += i; }} );

		add("displayMode (bitwise 1-text, 2-PID, 4-background, 8-PID-D graph)", 'B', 1,
			new TunableParameter.Adjust() { public double adjust(double i) {
				return fp.displayMode = (fp.displayMode + (int)i) % 64 ; }} );
		add("steer.deadband", ']', .01,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.steering.deadband += i; }});

		

/*		add("TF H.low", '1', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.tf.param.H.low += i; }});		
		add("TF H.high", '2', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.tf.param.H.high += i; }});
		add("TF S.low", '3', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.tf.param.S.low += i; }});
		add("TF S.high", '4', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.tf.param.S.high += i; }});
		add("TF L.low", '5', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.tf.param.L.low += i; }});
		add("TF L.high", '6', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.tf.param.L.high += i; }});
	*/
		add("tdStartX", '4', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.tdStartX += i; }});
		add("tdStartY", '5', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.tdStartY += i; }});
		add("tdStartScale", '6', 0.01,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.tdStartScale += i; }});
		add("ccSetPoint", '7', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.ccSetPoint += i; }});

					
		add("testPulse magnitude", '8', .05,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.steeringTestPulse.magnitude += i; }});
		add("testPulse duration", '9', .05,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.steeringTestPulse.duration += i; }});
		add("testPulse type", '0', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.steeringTestPulse.changeTestType((int) i); }},
				new TunableParameter.Print() { public String print() { 
					return fp.steeringTestPulse.testTypeNames[fp.steeringTestPulse.testType]; }});
				
	
/*(add("FP selectedPid.gain.i", '8', .01,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.selectedPid.gain.i.loGain += i;  }});
*/
		add("PID gain.i.max", 'U', .005,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.selectedPid.gain.i.max += i;  }});
	/*	add("FP steering.lag.basisFactor", 'C', .01, 
				new TunableParameter.Adjust() { public double adjust(double i) { 
					return  fp.steering.lag.basisFactor += i; }} );		
*/
		add("PID quality fade threshold", 'W', .0002,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.selectedPid.qualityFadeThreshold += i; }});
		add("PID quality fade gain", 'S', .1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.selectedPid.qualityFadeGain += i; }});
		add("PID L delay", 'J', .05,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.selectedPid.delays.l.delay += i; }});
		add("FP displayRatio", 'H', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.displayRatio += i; }});
	//	add("TF nonmaxThreshold", 'U', .01,
	//			new TunableParameter.Adjust() { public double adjust(double i) {
	//				return fp.tf.param.nonmaxThreshold += i; }});
		add("TF fp.lineIntensityDelta", 'V', 1,
				new TunableParameter.Adjust() { public double adjust(double i) {
					return fp.lineIntensityDelta += i; }});

		add("fp.keepFocus", 'F', 0, 
				new TunableParameter.Adjust() { public double adjust(double i) { 
					fp.keepFocus = !fp.keepFocus; return fp.keepFocus ? 1.0 : 0.0;
				}});

				// Placeholders for hardcoded keys in FP class 
		add("QUIT", 'Q', 0, new TunableParameter.Adjust() { public double adjust(double i) { return 0; }});
		add("RESET", 'R', 0, new TunableParameter.Adjust() { public double adjust(double i) { return 0; }});
		add("LOOP", 'T', 0, new TunableParameter.Adjust() { public double adjust(double i) { return 0; }});
		add("NEXT", 'N', 0, new TunableParameter.Adjust() { public double adjust(double i) { return 0; }});
		add("PAUSE", 'Z', 0, new TunableParameter.Adjust() { public double adjust(double i) { return 0; }});
		add("CAPTURE", 'A', 0, new TunableParameter.Adjust() { public double adjust(double i) { return 0; }});
		add("DECREASE", ',', 0, new TunableParameter.Adjust() { public double adjust(double i) { return 0; }});
		add("INCREASE", '.', 0, new TunableParameter.Adjust() { public double adjust(double i) { return 0; }});
		add("RESETVP", (char)127, 0, new TunableParameter.Adjust() { public double adjust(double i) { return 0; }});

		selectParam('X');		
	}
	void printUnusedKeys() { 
		System.out.printf("Unused hot keys: ");
		for(int k = 'A'; k <= 'Z'; k++) { 
			if (findParam(k) == null) 
				System.out.printf("%c", k);
		}
		for(int k = '0'; k <= '9'; k++) { 
			if (findParam(k) == null) 
				System.out.printf("%c", k);
		}
		char []otherKeys = {'=', '-', '`', '[', ']', '\\', ',', '.', '?'};
		for(int k : otherKeys) { 
			if (findParam(k) == null) 
				System.out.printf("%c", k);			
		}
		int []otherWeirdKeys = {32,1222};
		for(int k : otherWeirdKeys) { 
			if (findParam(k) == null) 
				System.out.printf("(%d)", k);			
		}
		
		System.out.printf("\n");
	}
	final FrameProcessor fp;	
}
