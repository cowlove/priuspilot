import math.JamaLeastSquaresFit;
import math.RunningAverage;
import math.RunningLeastSquares;
import math.RunningQuadraticLeastSquares;


class FeedbackLogic { 
	double delay = 0.20;
	double gain = 1.8;
	double threshold = 0.08;
	double basisFactor = 0.80;
	RunningLeastSquares history = new RunningLeastSquares(10);
	RunningQuadraticLeastSquares basis = new RunningQuadraticLeastSquares(0, 0, 1.80);
	void reset() {
		history.clear();
		basis.clear();
	}
	double feedback; 
	
	double getFeedback(long ms, double e) { 
		double bestAge = 0;
		int bestIdx = -1;
		for(int i = 0; i < history.count; i++) { 
			double age = Math.abs(ms - history.xs[i] - delay * 1000);
			if (bestIdx == -1 || age < bestAge) { 
				bestAge = age;
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
		}
		if ((e < 0 && feedback > -e) || (e > 0 && feedback < -e))
			feedback = -e;

		history.add(ms, e + feedback);
		return feedback;
	}
}

class ControlLagLogic {
	
	
	double actuationTime = 10;  // delay until control feedback is started 
	double deadTime = 400;		 // period of full control feedback
	double lagTime = 300; 		 // period whiel control feedback is phased out. 
	
	double threshold = 0.25;
	double gain = 2.0;
	double feedback;
	RunningLeastSquares ls = new RunningLeastSquares(60);
	
	double getFeedback(long now, double e) { 
		ls.add(now, e);

		double sum = 0;
		int count = 0;
		for(int i = 0; i < ls.count; i++) { 
			double age = now - ls.xs[i];
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


class SteeringLogic { 
	double maxSteer = 0.48;
	double maxSteerIncrease = 0.12;
	double maxSteerReturn = 0.22;	
	double nonLinearPoint = 0.15;
	
	//ControlLagLogic lag = new ControlLagLogic();
	FeedbackLogic lag = new FeedbackLogic();
	
	SteeringLogic() { 
		reset();
	}
	void reset() { 
		lag.reset();
		avgSteer = new JamaLeastSquaresFit(2, 0.25);
	}
    JamaLeastSquaresFit avgSteer = null;
    
    // steps - 1) maxSteerIncrease/Return are applied, average maintained of this step
    // 2) nonLinear point transformation 3) backlash logic 4) maxSteer limiter
    
    long lastMs  = 0;
    double steer(long ms, double in) {
    	// TODO - rollover/resetting of ms causes problems
  		if (avgSteer.averageY() > maxSteer || avgSteer.averageY() < -maxSteer) 
   			reset();

  		double av = avgSteer.averageY();
    	
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
   		
   		if (st > nonLinearPoint || st < -nonLinearPoint) { 
   			st = Math.sqrt(Math.abs(st / nonLinearPoint)) * nonLinearPoint * 
   				Math.abs(st) / st;
   		}
   		
   		st += lag.getFeedback(ms, st);
   	   	
   		if (st > maxSteer) st = maxSteer;
   		if (st < -maxSteer) st = -maxSteer;
   		
   		return st;
    }
}