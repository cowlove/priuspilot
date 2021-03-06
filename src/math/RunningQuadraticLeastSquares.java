package math;

import java.util.ArrayList;

// maintains a rolling history of data and constant-time quadtratic curve-fitting
// for that data. 

// ls = new RunningQuadraticLeastSquares(1/*degree*/, 0, 10);
// ls.add(x1,y1);
// ls.add(x2,y2); 
// ..
// y = ls.solve(x);
// y = ls.average();
// err = ls.pointError(x3, y3);
// totalRmsErr = ls.rmsError();


/*
 * Tips for expanding this into a general running quadratic or linear least squares regression 
 * Based on 
 * http://mathforum.org/library/drmath/view/72047.html
 * 
 */


public class RunningQuadraticLeastSquares {
	
	public RunningQuadraticLeastSquares(int d, int mc, double ma) { 
		maxCount = mc;
		maxAge = ma;
		degree = d;
	}
	
	public void clear() {
		S00 = S10 = S20 = S30 = S40 = S01 = S11 = S21 = S31 = S41 = 0.0;
		now = 0.0;
		solved = false;
		history.clear();
	}
		
	public void add(double x, double y, double w) {
		now = x;
		updateSums(x, y, w);
		history.add(new Entry(x, y, w));
		trimToSize(now);
	}
	
	public void add(double x, double y) { 
		add(x, y, 1.0);
	}
	
	public void addY(double y) {
		add(now + 1, y, 1.0);
	}
	
	public void addY(double y, double w) { 
		add(now + 1, y, w);
	}

	private void solve(int degree) {
		if (degree == 2) { 
			a = (S01*S10*S30 - S11*S00*S30 - S01*S20*S20
					+ S11*S10*S20 + S21*S00*S20 - S21*S10*S10)
				    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20);		  
	
			b = (S11*S00*S40 - S01*S10*S40 + S01*S20*S30
					- S21*S00*S30 - S11*S20*S20 + S21*S10*S20)
				    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20);
	
			c = (S01*S20*S40 - S11*S10*S40 - S01*S30*S30
					+ S11*S20*S30 + S21*S10*S30 - S21*S20*S20)
				    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20);
		} else if (degree == 1) { 
			a = 0;
			b = (S00 * S11 - S10 * S01) / (S00 * S20 - S10 * S10);
			c = (S01 - b * S10) / S00;
		} else if (degree == 0) { 
			a = b = 0;
			c = (S01 / S00);
		}
		solved = true;
	}
	
	private void updateSums(double x, double y, double w) { 
		S00 += 1 * w;
		S01 += y * w;
		if (degree > 0) { 
			S10 += x * w;
			S20 += x * x * w;
			S11 += x * y * w;
			if (degree > 1) { 
				S30 += x * x * x * w;
				S40 += x * x * x * x * w;
				S21 += x * x * y * w;
				S31 += x * x * x * y * w;
				S41 += x * x * x * x * y * w;	
			}
		}
		solved = false;
	}

	public void trimToSize(double n){ 
		while(history.size() > 0 && ((maxCount > 0 && history.size() > maxCount) || 
			(maxAge > 0.0 && n - history.get(0).x > maxAge))) 
			removeOldest();
	}
	
	private void removeOldest() { 
		Entry e = history.get(0);
		updateSums(e.x, e.y, -e.w);
		history.remove(0);
	}
	
	public class Entry { 
		public double x, y, w;
		Entry(double x1, double y1, double w1) { x = x1; y = y1; w = w1; } 
	}
	
	public double calculate(double x) { 
		if (getCount() == 0)
			return 0;
		if (!solved) 
			solve(degree);
		return a * x * x + b * x + c;
	}
	
	public double calculate() { 
		double r = calculate(now); if (Double.isNaN(r)) r=0; return r;
	}
	
	public double pointError(double x, double y) {
		return y -  calculate(x);
	}

	public double now = 0.0, maxAge;
	public int maxCount;
	double S00, S10, S20, S30, S40, S01, S11, S21, S31, S41;
	public ArrayList<Entry> history = new ArrayList<Entry>();
	public double a,b,c; // coefficients for y = ax + b, y = axx + bx + c;
	double rmsErr;
	boolean solved;
	int degree;
	public int getCount() {  return history.size(); }
}
