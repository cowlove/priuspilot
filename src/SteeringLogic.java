//import math.JamaLeastSquaresFit;
//import math.RunningAverage;
//import math.RunningLeastSquares;
//import math.RunningQuadraticLeastSquares;



class SteeringLogicSimpleLimits {
	double maxSteer =  0.45;
	double maxChange = 0.0040; // per ms
	double deadband = 0.00;
	double curveGain = 0.20;
	double speedGain = 0.00;
	double finalGain = 1.00;

	long lastMs = 0;
	double trim = -0.00;
	double lastSteer = 0;
	double steer(long ms, double st, double curve, double speed) { 
		if (Double.isNaN(lastSteer)) lastSteer = 0.0;
		if (deadband > 0 || (deadband < 0 && Math.abs(st) > Math.abs(deadband))) { 
			if (st < 0) st -= deadband;
			if (st > 0) st += deadband;
        }
		st += trim;

		double maxDelta = maxChange * (ms - lastMs);
		st = Math.min(lastSteer + maxDelta, Math.max(lastSteer - maxDelta, st));

		// move the maxSteer limit window based on curve 
		double curveMod = curve * curveGain;
		st = Math.max(-maxSteer + curveMod, Math.min(maxSteer + curveMod, st));

		final double maxSpeedMod = 0.20;
		double speedMod = Math.max(-maxSpeedMod, Math.min(maxSpeedMod, (speed - 60.0) * speedGain));
		st *= (finalGain + speedMod);
		
		totalAction += Math.abs(st - lastSteer);
		lastMs = ms;
		lastSteer = st;
		return st;
	}
	double totalAction = 0.0;
	void reset() {}
};
