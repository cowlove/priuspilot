//import math.JamaLeastSquaresFit;
//import math.RunningAverage;
//import math.RunningLeastSquares;
//import math.RunningQuadraticLeastSquares;


class FeedbackLogic { 
	double delay = 0.22;
	double gain = 1.0;
	double threshold = 0.13;
	double basisFactor = 0.80;
	final double movingHistoryAge = 1.80;
	RunningLeastSquares history = new RunningLeastSquares(30); // max 1 sec worth history
	RunningQuadraticLeastSquares basis = new RunningQuadraticLeastSquares(0, 0, movingHistoryAge);
	void reset() {
		history.clear();
		basis.clear();
	}
	double feedback; 
	
	// Provide a feedback component proportional to anyhing more than 
	// <threshold> over the <basisFactor> times the rolling 
	// history of <movingHistoryAge> seconds
	// This feedback is added in after <delay> seconds, scaled by <gain> 
	double getFeedback(long ms, double e) { 
		double bestAge = 0;
		int bestIdx = -1;
		
		// find the historical data point closest to <delay> ms ago
		for(int i = 0; i < history.count; i++) { 
			double age = Math.abs(ms - history.xs[i] - delay * 1000);
			if (bestIdx == -1 || Math.abs(age) < bestAge) { 
				bestAge = Math.abs(age);
				bestIdx = i;
			}
		}
		
		
		double oe = 0, bas = 0;
		if (bestIdx != -1) { 
			oe = history.ys[bestIdx];
			bas = basis.calculate() * basisFactor;
			if (Math.signum(oe) == Math.signum(bas)) 
					oe -= bas;
			if (oe > threshold && e > 0) 
				feedback = -(oe - threshold) * gain;
			else if (oe < -threshold && e < 0)
				feedback = -(oe + threshold) * gain;
			else 
				feedback = 0;

			// misuse current time instead of time of aged point, works fine
			basis.add((double)ms / 1000, history.ys[bestIdx]);
		} else 
			feedback = 0;
		
		// prevent feedback from going past center (todo: this is arbitrary, feedback gain is applied later)
		if (false) { 
			if (e < 0 && feedback > -e)
				feedback = -e;
			if (e > 0 && feedback < -e)
				feedback = -e;
		}
		//if (Math.abs(e) > 0.0) 
		//	System.out.printf("%.2f\t%d\t%.2f\t%.2f\t%.2f\n", e, bestIdx, oe, bas, feedback);
		history.add(ms, e + feedback);
		return feedback;
	}
}


	// from pulse tests, it appears that impulses less than 100ms have little steering effect
	// For a larger pulse, the first impact on observed error value is 300ms after pulse start
    // and change in observed error peaks 700ms later

class ControlLagLogic {	
	double actuationTime = 0.100;  // delay until control feedback is started 
	double deadTime = 0.200;		 // period of full control feedback
	double lagTime = 0.400; 		 // period while control feedback is phased out. 
	
	double threshold = 0.11;
	double gain = 2.0;
	double feedback;
	RunningLeastSquares ls = new RunningLeastSquares(60); // maximum 2 seconds of data
	
	void reset() { ls.clear(); }
	
	double getFeedback(long now, double e) { 
		ls.add(now, e);

		double sum = 0;
		int count = 0;
		for(int i = 0; i < ls.count; i++) { 
			double age = ((double)now - ls.xs[i])/1000;
			if(age >= actuationTime && age <= actuationTime + deadTime) {
				sum += ls.ys[i];
				count++;
			} else if(age > actuationTime + deadTime && age <= actuationTime + deadTime + lagTime) { 
				sum += ls.ys[i] * (1 - (age - actuationTime - deadTime) / lagTime);
				count++;
			}
		}
		double avg = count > 0 ? sum / count : 0;
		
		if (avg > threshold) 
			feedback = -(avg - threshold) * gain;
		else if (avg < -threshold) 
			feedback = -(avg + threshold) * gain;
		else 
			feedback = 0;
		
		return feedback;
	}
}

class SteeringWheelClosedLoopControl {
	SteeringWheelResolverCam swrc;
	SteeringWheelClosedLoopControl(String cam) {
		swrc = new SteeringWheelResolverCam(cam, 160, 120, true);            	
		pid.finalGain = 0.0;
	}
	PidControl pid = new PidControl("SWRC");
	
	double steer(long ms, double in) {
		double ang = swrc.angle;
		double err = (ang - 90) / 180;
		double corr = pid.add(err, ms);
		return corr;
	}

	public void reset() {		
		swrc.reset();
	}
};


class SteeringLogicSimpleLimits {
	double maxSteer =  0.50;
	double maxChange = 0.06; // per ms
	double deadband = 0.00;
	long lastMs = 0;
	double trim = -0.00;
	double lastSteer = 0;
	double steer(long ms, double st) { 
		if (Double.isNaN(lastSteer)) lastSteer = 0.0;
		if (deadband > 0 || (deadband < 0 && Math.abs(st) > Math.abs(deadband))) { 
			if (st < 0) st -= deadband;
			if (st > 0) st += deadband;
        }
		st += trim;

		double maxDelta = maxChange * (ms - lastMs) * maxChange;
		st = Math.min(lastSteer + maxDelta, Math.max(lastSteer - maxDelta, st));
		st = Math.min(maxSteer, Math.max(-maxSteer, st));

		totalAction += Math.abs(st - lastSteer);
		lastMs = ms;
		lastSteer = st;
		//System.out.printf("%.4f\n", st);
		return st;
	}
	double totalAction = 0.0;
	void reset() {}
};

class SimpleSteeringLogic {
	double threshold = 0.2;
	double pulseDuration = 0.4;
	double pulseMagnitude = 0.0;
	double pulseRest = 0.2;
	double maxSteer = 0.27;
	double pulseRemaining = 0.0;
	long lastMs = 0;	
	
    double steer(long ms, double st) {
    	if (lastMs == 0) lastMs = ms;
    
    	if (pulseRemaining <= 0.0) { 
    		if (Math.abs(pulseMagnitude) > 0.0) { 
    			pulseMagnitude = 0.0;
    			pulseRemaining = pulseRest;
    		} else if (Math.abs(st) > threshold) { 
    	   		if (st > maxSteer) st = maxSteer;
    	   		if (st < -maxSteer) st = -maxSteer;
    			pulseMagnitude = st;
    			pulseRemaining = pulseDuration;
    		}
    	} else 
    		pulseRemaining -= 0.001 * (ms - lastMs);
    	
    	lastMs = ms;
   		return pulseMagnitude;
    }
    
    void reset() {
    	pulseRemaining = pulseMagnitude = 0.0;
    }
}

class SteeringLogic { 
	double maxSteer = 0.50;
	double maxSteerIncrease = 0.10;
	double maxSteerReturn = 0.22;	
	double nonLinearPoint = 0.15;
	double feedbackGain = .4;
	
	ControlLagLogic lag = new ControlLagLogic();
	//FeedbackLogic lag = new FeedbackLogic();
	
	SteeringWheelClosedLoopControl clc = null;
	SteeringLogic(String cam) {
		if (cam != null)
			clc = new SteeringWheelClosedLoopControl(cam);
		
		reset();
	}
	void reset() { 
		lag.reset();
		avgSteer = new JamaLeastSquaresFit(2, 0.25);
		if (clc != null)
			clc.reset();
	}
    JamaLeastSquaresFit avgSteer = null;
    
	
    // steps - 1) maxSteerIncrease/Return are applied, average maintained of this step
    // 2) nonLinear point transformation 3) backlash logic 4) maxSteer limiter
    
    long lastMs  = 0;
    double steer(long ms, double in) {
    	// TODO - rollover/resetting of ms causes problems
  		if (avgSteer.averageY() > maxSteer || avgSteer.averageY() < -maxSteer) 
   			reset();
  		
  		if (clc != null)
  			return clc.steer(ms, in);
  		

  		double av = avgSteer.averageY();
    	if (Double.isNaN(av))
    		av = 0;
    	
    	// TODO ugly hack - normalize maxSteerInc/Return to 15FPS 
    	long deltaMs = ms - lastMs;
    	lastMs = ms;
    	double maxInc = maxSteerIncrease * deltaMs / 66;
    	double maxDec = maxSteerReturn * deltaMs / 66;
    	double st = in;
    	if (st > 0) { 
	        st = Math.min(st, av + maxInc);
	        st = Math.max(st, av - maxDec);
        } else { 
	        st = Math.min(st, av + maxDec);
	        st = Math.max(st, av - maxInc);
        }
   		avgSteer.add(ms, st);
   		/*
   		if (st > nonLinearPoint || st < -nonLinearPoint) { 
   			st = Math.sqrt(Math.abs(st / nonLinearPoint)) * nonLinearPoint * 
   				Math.abs(st) / st;
   		}
   		*/
   		st += lag.getFeedback(ms, st) * feedbackGain;
   	   	
   		if (st > maxSteer) st = maxSteer;
   		if (st < -maxSteer) st = -maxSteer;
   		
   		return st;
    }
}
