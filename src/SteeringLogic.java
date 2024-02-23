//import math.JamaLeastSquaresFit;
//import math.RunningAverage;
//import math.RunningLeastSquares;
//import math.RunningQuadraticLeastSquares;



class SteeringLogicSimpleLimits {
	double maxSteer =  0.60; // post-gain
	double maxSteerOffset = -0.10;
	double maxChange = 0.0048; // per ms pre-gain
	double deadband = Main.debugDouble("DEADBAND", 0.0); // post-gain
	double curveGain = 0.00;
	double speedGain = 0.00;
	double finalGain = .50;
	double asymDetune = -1.00;
	double trim = -0.06; // post-gain

	long lastMs = 0;
	double lastSteer = 0;
	double steer(long ms, double st, double curve, double speed) { 
		// gain is set for nominal 60mph.  increase gain for faster speeds, decrease for lower
		final double maxSpeedMod = 0.20;
		final double speedMod = Math.max(-maxSpeedMod, Math.min(maxSpeedMod, (speed - 60.0) * speedGain));
		final double gain = finalGain + speedMod;

		if (deadband < 0 && Math.abs(st) < Math.abs(deadband)) {
			st = 0;
		} else { 
			if (st < 0) st -= deadband / gain;  // convert to post-gain value 
			if (st > 0) st += deadband / gain;
		}

		double maxDelta = maxChange * (ms - lastMs);
		if (Double.isNaN(lastSteer)) 
			lastSteer = 0.0;
		st = Math.min(lastSteer + maxDelta, Math.max(lastSteer - maxDelta, st));
		lastMs = ms;
		totalAction += Math.abs(st - lastSteer);
		lastSteer = st; // pre final gain 

		st *= gain;
		
		// move the maxSteer limit window based on curve 
		double curveMod = curve * curveGain;
		st = Math.max(-maxSteer + curveMod + maxSteerOffset, 
			Math.min(maxSteer + curveMod + maxSteerOffset, st));

		if ((st < 0 && asymDetune < 0) || (st > 0 && asymDetune > 0)) { 
			st *= Math.abs(asymDetune);
		}
		st += trim;

		return st;
	}
	double totalAction = 0.0;
	void reset() {}
};
