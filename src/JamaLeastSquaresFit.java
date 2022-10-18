/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

//package math;

import java.util.ArrayDeque;

import Jama.Matrix;

/**
 *
 * @author jim
 */
public class JamaLeastSquaresFit {
	public class Entry {
		public double x, y;
	}
	public ArrayDeque<Entry> hist = new ArrayDeque<Entry>();
		
	double coefficients[];
    public JamaLeastSquaresFit(int d, double ma) {
        degree = d;
        maxAge = ma;
    }
 
    double maxAge;;
    int degree;
    //int maxsize;
    boolean solved = false;
    
    public boolean solve() {
    	if (solved == true)
    		return true;
    	int count = hist.size();
    	
    	Matrix a = new Matrix(count, degree);
    	Matrix b = new Matrix(count, 1);

    	int i = 0;
    	for(Entry e : hist) {
    		for (int d = 0; d < degree; d++) {
    			a.set(i, d, Math.pow(e.x, d));
    		}
    		b.set(i++, 0, e.y);
    	}
    	
    	try { 
    		coefficients = a.solve(b).getRowPackedCopy();
    		solved = true;
    		return true;
    	} catch(Exception ex) {
    		//System.out.println(ex.toString());
    		return false;
    	}
    }
 
    public double calculate(double x) {
    	if (!solve()) 
    		return 0.0;
    	double y = 0;
        for (int d = 0; d < degree; d++)
            y += Math.pow(x, d) * coefficients[d];
        return y;
    }
    
    // Find the value of the specified derivative of solved function at value x. 
    public double slope(double x, int deriv) { 
    	if (!solve())
    		return 0.0;
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
    
    public void add(double x, double y) {
    	Entry e = new Entry();
    	e.x = x;
    	e.y = y;
    	
    	hist.addFirst(e);
    	while(!hist.isEmpty() && x - hist.peekLast().x > maxAge)
    		hist.removeLast();
    	
    	solved = false;
     }
    
    public double err(double x, double y) { 
    	return y - calculate(x);
    }
    
    
    public double rmsError() {
        double s = 0;
        for (Entry i : hist) { 
            double e = err(i.x, i.y);
            s += e * e;
        }
        return Math.sqrt(s / hist.size());
    }
    
	public double getCoeff(int i) {
		if (solved == true) 
			return coefficients[i];
		return 0.0;
	}
	
	public double recentAvg(double d) {
		int c = 0;
		double s = 0;
	    for (Entry i : hist) { 
	    	if (i.x >= d) {
        		s += i.y;
        		c++;
        	}
        }
        return c > 0 ? s / c : 0.0;
	}

	public double averageY() {
		int c = 0;
		double s = 0;
	    for (Entry i : hist) { 
	    	s += i.y;
	    	c++;
	    }
        return c > 0 ? s / c : 0.0;
	}

	public int size() {
		// TODO Auto-generated method stub
		return hist.size();
	}

	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return hist.isEmpty();
	}

	public void removeAged(double x) {
    	while(!hist.isEmpty() && x - hist.peekLast().x > maxAge)
    		hist.removeLast();
	}

    
}
