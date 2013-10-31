

import math.RunningAverage;

public class AutoFocusParameter {

	double startingValue;
	double startingRange;
	double finalFocus;
	int maxWeight;
	public double curFocus; 
	public AutoFocusParameter(double s, double r, double f, int w, int as) { 
		startingValue = s;
		startingRange = r;
		finalFocus = f;
		maxWeight = w;
		av = new RunningAverage(as);
	}
	RunningAverage av; 
	public void add(double val, int weight) {
		av.add(val, weight);
		curFocus = 1.0 - (1.0 - finalFocus) * Math.min(maxWeight, av.totalWeight()) / maxWeight;
		
	}
	public double low() {
		return mid() - curFocus * startingRange;
	}
	public double hi() {
		return mid() + curFocus * startingRange;
	}
	public double mid() {
		return startingValue * curFocus + av.calculate() * (1.0 - curFocus);
	}
	public void clear() { 
		curFocus = 1.0;
		av.clear();
	}
	public boolean within(double x) { 
		return x > low() && x < hi();
	}
	public int weight() { return av.totalWeight(); } 	
}
