//package math;

import java.util.ArrayList;


/*
 * Tips for expanding this into a general running quadratic or linear least squares regression 
 * Based on 
 * http://mathforum.org/library/drmath/view/72047.html
 * 
 *  
 * maintain sums such that  
 * Sab  is sum of X^a*Y^b for each x,y in the data set. 
 * 
 * so when adding a point x,y with weight w
 * 
 *  S00 += 1 * w;
 *  S10 += x * w;
 *  S20 += x * x * w;
 *  S30 += x * x * x * w;
 *  S40 += x * x * x * x * w;
 *  S01 += y * w;
 *  S11 += x * y * w;
 *  S21 += x * x * y * w;
 *  S31 += x * x * x * y * w;
 *  S41 += x * x * x * x * y * w;
 * 
 *  Then the parameters a,b,c of the best fit equation y = ax^2 + bx + c
 *  are given by 
 *  
  a = (S01*S10*S30 - S11*S00*S30 - S01*S20*S20
       + S11*S10*S20 + S21*S00*S20 - S21*S10*S10)
    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20)

  b = (S11*S00*S40 - S01*S10*S40 + S01*S20*S30
       - S21*S00*S30 - S11*S20*S20 + S21*S10*S20)
    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20)

  c = (S01*S20*S40 - S11*S10*S40 - S01*S30*S30
       + S11*S20*S30 + S21*S10*S30 - S21*S20*S20)
    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20)
 
  
  and the parameters m and b of the best fit y = mx + b are given by 
  
  m = (S00 * S11 - S10 * S01) / (S00 * S20 - S10 * S10)
  b = (S01 - m * S10) / S00 
  
  and of course the average is given by 
  
  y = S01 / S00  (sum of y's divided by count)
  
  variance and sum of errors could probably also be computed from these sums, dunno

  Sum of Y offset errors should be:  a^2*S40 + (b^2 + 2ac)*S20 + c^2*S00 + S02 + 2ab*S30
     + 2bc*S10 - 2a*S21 - 2b*S11 - 2c*S01.
  
 */

public class RunningQuadraticLeastSquares {
	public class Entry { 
		public double x, y, w;
		Entry(double x1, double y1, double w1) { x = x1; y = y1; w = w1; } 
	}
	
	public double lastX = 0.0, maxAge;
	public int maxCount;
	double S00, S02, S10, S20, S30, S40, S01, S11, S21, S31, S41;
	public ArrayList<Entry> hist = new ArrayList<Entry>();
	public double a,b,c; // coefficients for y = ax + b, y = axx + bx + c;
	double rmsErr;
	boolean solved;
	int degree;
	double []coefficients;
	public int getCount() {  return hist.size(); }
	
	public RunningQuadraticLeastSquares(int d, int mc, double ma) { 
		maxCount = mc;
		maxAge = ma;
		degree = d;
		clear();
	}
	
	public void clear() {
		S00 = S02 = S10 = S20 = S30 = S40 = S01 = S11 = S21 = S31 = S41 = 0.0;
		coefficients = new double[3];
		lastX = 0.0;
		solved = false;
		hist.clear();
	}
	public boolean isFull() { return hist.size() == maxCount; } 
		
	public void add(double x, double y, double w) {
		lastX = x;
		updateSums(x, y, w);
		hist.add(new Entry(x, y, w));
		removeAged(lastX);
	}
	
	public void add(double x, double y) { 
		add(x, y, 1.0);
	}
	
	public void addY(double y) {
		add(lastX + 1, y, 1.0);
	}
	
	public void addY(double y, double w) { 
		add(lastX + 1, y, w);
	}

	// add <delta> to all x values, recalculate sums. 
	public void rebase(double delta) { 
		S00 = S02 = S10 = S20 = S30 = S40 = S01 = S11 = S21 = S31 = S41 = 0.0;
		coefficients = new double[3];
		for(int i = 0; i < hist.size(); i++) {  
			Entry e = hist.get(i);
			e.x += delta;
			updateSums(e.x, e.y, e.w);
		}
		solved = false;
	}
	
	private void solve(int degree) {
		if (degree == 3) { 
			a = (S01*S10*S30 - S11*S00*S30 - S01*S20*S20
					+ S11*S10*S20 + S21*S00*S20 - S21*S10*S10)
				    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20);		  
	
			b = (S11*S00*S40 - S01*S10*S40 + S01*S20*S30
					- S21*S00*S30 - S11*S20*S20 + S21*S10*S20)
				    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20);
	
			c = (S01*S20*S40 - S11*S10*S40 - S01*S30*S30
					+ S11*S20*S30 + S21*S10*S30 - S21*S20*S20)
				    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20);
		} else if (degree == 2) { 
			a = 0;
			b = (S00 * S11 - S10 * S01) / (S00 * S20 - S10 * S10);
			c = (S01 - b * S10) / S00;
		} else if (degree == 1) { 
			a = b = 0;
			c = (S01 / S00);
		}

		double f = (a*a*S40 + (b*b + 2*a*c)*S20 + c*c*S00 + S02 + 2*a*b*S30 + 2*b*c*S10 - 2*a*S21 - 2*b*S11 - 2*c*S01)/S00;
		rmsErr = f > 0 ? Math.sqrt(f) : 0;
		
		coefficients[2] = a; coefficients[1] = b; coefficients[0] = c;
		
		solved = true;
	}
	
	private void updateSums(double x, double y, double w) { 
		S00 += 1 * w;
		S01 += y * w;
		S02 += y * y * w;
		if (degree > 1) { 
			S10 += x * w;
			S20 += x * x * w;
			S11 += x * y * w;
			if (degree > 2) { 
				S30 += x * x * x * w;
				S40 += x * x * x * x * w;
				S21 += x * x * y * w;
				S31 += x * x * x * y * w;
				S41 += x * x * x * x * y * w;	
			}
		}
		solved = false;
	}

	public void removeAged(double n){ 
		while(hist.size() > 0 && ((maxCount > 0 && hist.size() > maxCount) || 
			(maxAge > 0.0 && n - hist.get(0).x > maxAge))) 
			removeOldest();
	}
	
	private void removeOldest() { 
		Entry e = hist.get(0);
		updateSums(e.x, e.y, -e.w);
		hist.remove(0);
	}
		
	public double rmsError() { 
		calculate();
		return rmsErr;
	}
	public int size() { 
		return hist.size();
	}
	
    // Find the value of the specified derivative of solved function at value x. 
    public double slope(double x, int deriv) { 
    	calculate();
    	double y = 0;
    	for (int d = 1; d < degree; d++) { 
    		double v = Math.pow(x, d - deriv) * coefficients[d];
    		for(int i = 0; i < deriv; i++) 
    			if (d - i > 0)
    				v *= (d - i);
    			else
    				v = 0;
    		y += v;
    	}
    	return y;   	
    }

	
	public double calculate(double x) { 
		if (getCount() == 0)
			return 0;
		if (!solved) 
			solve(degree);
		return a * x * x + b * x + c;
	}
	
	
	public double calculate() { 
		double r = calculate(lastX); if (Double.isNaN(r)) r=0; return r;
	}
	
	public double pointError(double x, double y) {
		return y -  calculate(x);
	}

    public void validate() {
		double r = calculate();
		if (Double.isNaN(r) || Double.isInfinite(r))
			clear();
    }

}
