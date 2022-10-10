import math.*;

class Average {
	double sum;
	long count;
	void add(double d) { if (!Double.isNaN(d)) { sum += d; count++; } } 
	double average() { return count > 0 ? sum / count : 0;  }
}

public class PidControl {
	static final int EXPECTED_FPS = 30;
	Average avgRmsErr = new Average();
    class PID {
        PID(double ap, double ai, double ad, double aj, double al) {
            p = ap;
            i = ai;
            d = ad;
            j = aj;
            l = al;
        }
        PID() { p = i = d = j = l = 0; } 
        public PID clone() {
        	return new PID(p, i, d, j, l);
        }
        double p, i, d, j,  l;
        public String toString(String pref) { 
        	return String.format("%sp=%.2f, %si=%.2f, %sd=%.2f, %sj=%.2f, %sl=%.2f", 
        			pref, p, pref, i, pref, d, pref, j, pref, l); 
        }
    }
    class GainChannel  {
    	double loGain = 1, hiGain = 0;
    	Double loTrans = Double.NaN, hiTrans = Double.NaN, max = Double.NaN;
    	double limitToMax(double v) { 
    		if (Double.isNaN(max))
    			return v;
    		if (max > 0) 
    			return Math.min(max / loGain, Math.max(-max / loGain, v));
    		return 0;
    	}
    	double getCorrection(double v) {
    		// TODO his does not make sense to me, check it out
    		double c = loGain * v; 
    		if ((!Double.isNaN(loTrans) && v < loTrans))
    			c += loGain * hiGain * (v - loTrans);
			if ((!Double.isNaN(hiTrans) && v > hiTrans)) 
				c += loGain * hiGain * (v - hiTrans);		
	   		if (!Double.isNaN(max)) { 
    			if (c < -max) c = -max;
    			if (c > max) c = max;
    		}
    		return c;
    	}
    	protected GainChannel clone() { 
    		GainChannel n = new GainChannel();
    		n.loGain = loGain;
    		n.hiGain = hiGain;
    		n.loTrans = loTrans;
    		n.hiTrans = hiTrans;
    		n.max = max;
    		return n;
    	}
    	
    }
    class GainControl {
    	GainChannel p = new GainChannel();
    	GainChannel i = new GainChannel();
    	GainChannel d = new GainChannel();
    	GainChannel j = new GainChannel();
    	GainChannel l = new GainChannel();
    	protected GainControl clone() { 
    		GainControl n = new GainControl();
    		n.p = p.clone();
    		n.i = i.clone();
    		n.d = d.clone();
    		n.l = l.clone();
    		n.j = j.clone();
    		return n;
    	}
    }
    public String toString(String pref) { 
    	return String.format("%se=%.2f, %sdef=%.2f, %sq=%.2f, %sdrms=%f, ", pref, corr, 
    			pref, defaultValue.calculate(), pref, quality, pref, drms) + err.toString(pref);
    }
    double getAvgRmsErr() {
    	return avgRmsErr.average();
    }
    void setGains(double gp, double gi, double gd, double gj, double gl) { 
    	gain.p.loGain = gp;
      	gain.i.loGain = gi;
      	gain.d.loGain = gd;
      	gain.j.loGain = gj;
      	gain.l.loGain = gl;
    }
    PID err = new PID(); 
    PID period = new PID(0.05, 5, .8, .3, 3.0);  // TODO - change from explicit frame counts to a time period
    PID gainX = new PID(2.5, 0.000, 1.2, 0, 0.0);
    GainControl gain = new GainControl();
    double finalGain = 1.85;
    double manualTrim = -0.03; 
    int derrDegree = 3;
    int fadeCountMin = (int)Math.floor(period.d * EXPECTED_FPS * 0.2); 
    int fadeCountMax = (int)Math.floor(period.d * EXPECTED_FPS * 0.6);
    double qualityFadeGain = 4.0;
    double qualityFadeThreshold = 0.10;
    double quality = 0.0;
    
    
    // these values are set in reset() method
    double i;
    RunningQuadraticLeastSquares dd, d, p;
	   
    RunningAverage defaultValue = new RunningAverage(150);
    long starttime = 0;
	public double corr = 0;
 
	void reset() {
		p = new RunningQuadraticLeastSquares(1, (int)(period.p * 2 * EXPECTED_FPS), period.p);
        i = 0f;
        dd = new RunningQuadraticLeastSquares(derrDegree, (int)(period.j * 2 * EXPECTED_FPS), period.j);
        d = new RunningQuadraticLeastSquares(derrDegree, (int)(period.d * 2 * EXPECTED_FPS), period.d);
        defaultValue.clear();
        starttime = 0;
    }
    
    String description;
    public PidControl(String name) { 
    	reset();
    	description = name;
    }
    
    void rebase(long newStart) {
    	double delta = (newStart - starttime) / 1000;
    	starttime = newStart;
    	d.rebase(-delta);
    	dd.rebase(-delta);
    	p.rebase(-delta);
    }
    
    double lastVal, drms;
    double add(double val, long time) {
        if (starttime == 0) 
        	starttime = time;
        else if (time - starttime > 2 * 1000) { 
        	rebase(time);
        }
        lastVal = val;
        
        double n = ((double) (time - starttime)) / 1000;
        if (Double.isNaN(val) || Double.isInfinite(val)) { 
        	val = Double.NaN;
        	d.removeAged(n);
        	dd.removeAged(n);
        	p.removeAged(n);
        } else {         
	        val += manualTrim;
	        d.add(n, val);
	        dd.add(n, val);
	        p.add(n, val);
	        i = gain.i.limitToMax(i + val);
        }
        
        err.p = gain.p.getCorrection(p.calculate());
                
        drms = d.rmsError();
        avgRmsErr.add(drms);
        
        if (d.size() < fadeCountMin || Double.isNaN(drms) || 
        		drms > qualityFadeThreshold * (qualityFadeGain + 1))
        	quality = 0;
        else if (drms < qualityFadeThreshold)  
        	quality = 1.0;
        else   
        	quality = 1.0 - ( drms / qualityFadeThreshold - 1) / qualityFadeGain;  
     
        quality *= quality;
   
       if (d.size() >= fadeCountMin && d.size() < fadeCountMax)  {
        	quality *= (double)(d.size() - fadeCountMin) / (double)(fadeCountMax - fadeCountMin);
       }
        err.d = gain.d.getCorrection(d.slope(n, 1));
        err.j = gain.j.getCorrection(d.slope(n, 2));
	    err.i = gain.i.getCorrection(i);
	           
	    corr = -(err.p + err.d + err.i + err.l) * finalGain * quality + 
	    	0 * (1 - quality);
	    defaultValue.add(corr);
	    if (Double.isNaN(corr))
	    	corr = 0.0;
	    return corr;
    }

    void copySettings(PidControl pid) { 
    	gain = pid.gain.clone();
    	period = pid.period.clone();
    	finalGain = pid.finalGain;
    	this.qualityFadeThreshold =  pid.qualityFadeThreshold;
    	this.qualityFadeGain = pid.qualityFadeGain;
    	this.manualTrim = pid.manualTrim;    	
    }
}
 
