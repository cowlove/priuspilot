//package math;



public class RunningLeastSquaresSine extends RunningLeastSquares {
    public RunningLeastSquaresSine(int a) {
        super(a);
    }
    boolean solved = false;
    public double a, phi, f, k;
    
    public boolean solve() {
    	if (solved == true)
    		return true;
    	
    	RunningLeastSquares line = new RunningLeastSquares(count);
 
    	double minY = ys[0], maxY = minY, sumY = 0;
    	for(int i = 0; i < count; i++) {
    		double x = xs[i];
    		double y = ys[i];
    		
    		if (y > maxY) maxY = y;
    		if (y < minY) minY = y;
    		sumY += y;
    	}
    	
    	k = sumY / count;
    	a = (maxY - minY) / 2;
    	
    	for(int i = 0; i < count; i++) {
    		double x = xs[i];
    		double y = ys[i];

    		line.add(Math.asin((y - k) / a), x);    		
    	}
 
    	f = 1 / line.slope();
    	phi = line.intercept();
    	solved = true;
    	
    	return true;
    }
    
    @Override
    public double predict(double x) {
    	solve();
    	return a * Math.sin(f * (x + phi)) + k;
    }	
   
    
    @Override
    public void add(double x, double y) { 
    	solved = false;
    	super.add(x, y);
    }
    
    @Override
    public double err(double x, double y) { 
    	return y - predict(x);
    }
    
    public void removeLast() { 
    	solved = false;
    	super.removeLast();
    }
    
    public double rmsError() {
        double s = 0;
        for (int i = 0; i < count; i++) {
            int idx = (index + size - count + i) % size;
            double e = err(xs[idx], ys[idx]);
            s += e * e;
        }
        return Math.sqrt(s / count);
    }
    
}