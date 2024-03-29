//import math.*;

import java.util.ArrayList;
import java.util.function.Supplier;


class AverageNO {
	double sum;
	long count;
	void add(double d) { if (!Double.isNaN(d)) { sum += d; count++; } } 
	double average() { return count > 0 ? sum / count : 0;  }
}

public class PidControl {
	static int EXPECTED_FPS = Main.debugInt("EXPECTED_FPS", 15);
	Average avgRmsErr = new Average();
    class PID {
        PID(double ap, double ai, double ad, double aj, double al, double at) {
            p = ap;
            i = ai;
            d = ad;
            j = aj;
            l = al;
			t = at;
        }
        PID() { p = i = d = j = l = t = 0; } 
        public PID clone() {
        	return new PID(p, i, d, j, l, t);
        }
        double p, i, d, j, l, t;
        public String toString(String pref) { 
        	return String.format("%sp %.3f %si %.3f %sd %.3f %sj %.3f %sl %.3f %st %.3f", 
        			pref, p, pref, i, pref, d, pref, j, pref, l, pref, t); 
        }
    }
	class DelayChannel { 
		double delay = 0.0;
		ArrayList<Double> delayList = new ArrayList<Double>();
		double get(double t, double v) { // apply a t-second delay to value v
			if (delay == 0.0) 	 
				return v;
			delayList.add(v);
			v = delayList.get(0);
			if (delayList.size() > EXPECTED_FPS * delay)
				delayList.remove(0);
			return v;
		}
		public void reset() {
			delayList.clear();
		}
		public DelayChannel clone() { 
			DelayChannel n = new DelayChannel();
			n.delay = delay;
			return n; 
		}
	}
    class GainChannel {
		// loGain - normal gain
		// hiGain - additional gain added in starting at loTrans for negative values
		//     and hiTrans for positive value.  hiGain = 0.50 would add 50% additional
		//     gain on top of whatever loGain is set to, starting at hiTrans
		public void reset() {}
    	double loGain = 1, hiGain = 0;
    	double loTrans = Double.NaN, hiTrans = Double.NaN, max = Double.NaN;
    	double limitToMax(double v) { 
    		if (Double.isNaN(max))
    			return v;
    		if (max > 0) 
    			return Math.min(max / Math.abs(loGain), 
					Math.max(-max / Math.abs(loGain), v));
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
    	public GainChannel clone() { 
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
    	GainChannel l = new GainChannel();
		GainChannel t = new GainChannel();
		void reset() { p.reset(); i.reset(); d.reset(); l.reset(); t.reset(); }
		public GainControl clone() { 
			GainControl n =  new GainControl();
			n.p = p.clone(); n.i = i.clone(); n.d = d.clone(); n.l = l.clone(); n.t = t.clone();
			return n;
		}
    }
    class DelayControl {
    	DelayChannel p = new DelayChannel();
		DelayChannel i = new DelayChannel();
		DelayChannel d = new DelayChannel();
		DelayChannel l = new DelayChannel();
		void reset() { p.reset(); i.reset(); d.reset(); l.reset();
		}
		void init(PID pid) { p.delay = pid.p; l.delay = pid.l; i.delay = pid.i; d.delay = pid.d; }
		public DelayControl clone() { 
			DelayControl n =  new DelayControl();
			n.p = p.clone(); n.i = i.clone(); n.d = d.clone(); n.l = l.clone();
			return n;
		}
    }
	void swapGains(PidControl p) { 
		GainControl g = p.gain.clone();
		p.gain = gain.clone();
		gain = g;
	}

	GainControl gain = new GainControl();
	DelayControl delays = new DelayControl();

	public String toString(String pref) { 
    	return String.format("%se %.3f %sdef %.3f %sq %.3f %sdrms %.3f ", pref, corr, 
    			pref, defaultValue.calculate(), pref, quality, pref, drms) + err.toString(pref);
    }
    double getAvgRmsErr() {
    	return avgRmsErr.calculate();
    }
    void setGains(double gp, double gi, double gd, double gj, double gl, double gt) { 
    	gain.p.loGain = gp;
      	gain.i.loGain = gi;
      	gain.d.loGain = gd;
      	gain.l.loGain = gl;
		gain.t.loGain = gt;
    }
    PID err = new PID(); 
    PID period = new PID(0.05, 5, 0.5, 0.3, 1.2, 0);  
	PID delay = new PID(0, 0, 0, 0, 0, 0);
    double finalGain = 1.85;
    int derrDegree = 3;
    int fadeCountMin = (int)Math.floor(period.d * EXPECTED_FPS * 0.2); 
    int fadeCountMax = (int)Math.floor(period.d * EXPECTED_FPS * 0.6);
    double qualityFadeGain = 4.0;
    double qualityFadeThreshold = 0.10;
    double quality = 0.0;
    double qualityPeriod = 1.5;
	Average avgQuality = new Average();
	int lowQualityCount = 0;
    
	double pendingCorrection = 0; // used externally to manage pending correction/input 

    // these values are set in reset() method
    double i, t;
    RunningQuadraticLeastSquares p, d, l, q;
	   
    RunningAverage defaultValue = new RunningAverage(150);
    long starttime = 0;
	public double corr = 0;
 
	void reset() {
		p = new RunningQuadraticLeastSquares(1, (int)(period.p * 2 * EXPECTED_FPS), period.p);
        i = 0;
		t = 0;
        //dd = new RunningQuadraticLeastSquares(derrDegree, (int)(period.j * 2 * EXPECTED_FPS), period.j);
        d = new RunningQuadraticLeastSquares(derrDegree, (int)(period.d * 2 * EXPECTED_FPS), period.d);
		l = new RunningQuadraticLeastSquares(1, (int)(period.l * 2 * EXPECTED_FPS), period.l);
		q = new RunningQuadraticLeastSquares(2, (int)(qualityPeriod * 2 * EXPECTED_FPS), qualityPeriod);
        defaultValue.clear();
        starttime = 0;
		delays.init(delay);		
		delays.reset();
		gain.reset();
		//System.out.printf("reset()\n");
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
    	p.rebase(-delta);
		l.rebase(-delta);
		q.rebase(-delta);
    }
    
    double drms;
    double add(double val, long time) {
		delays.init(delay); // update any changes from setting pid.delay.l 
        if (starttime == 0) 
        	starttime = time;
        else if (time - starttime > 2 * 1000) { 
        	rebase(time);
        }

        double n = ((double) (time - starttime)) / 1000;
		err.t = gain.t.getCorrection(t);
        if (Double.isNaN(val) || Double.isInfinite(val)) { 
        	val = Double.NaN;
        	d.removeAged(n);
        	p.removeAged(n);
			l.removeAged(n);
			q.removeAged(n);
        } else {         
			val -= err.t;
			t = gain.t.limitToMax(t + val);
	        d.add(n, delays.d.get(n, val));
	        p.add(n, delays.p.get(n, val));
			q.add(n, val);
	        i = gain.i.limitToMax(i + delays.i.get(n, val));
        }
        //lastVal = val;
        
        err.p = gain.p.getCorrection(p.calculate());
                
        drms = q.rmsError();
        avgRmsErr.add(drms);
        
        if (q.size() < fadeCountMin || Double.isNaN(drms) || 
        		drms > qualityFadeThreshold * (qualityFadeGain + 1)) {
        	quality = 0;
		}
        else if (drms < qualityFadeThreshold)  
        	quality = 1.0;
        else   
        	quality = 1.0 - ( drms / qualityFadeThreshold - 1) / qualityFadeGain;  
     
        quality *= quality;
   
		if (q.size() >= fadeCountMin && q.size() < fadeCountMax)  {
				quality *= (double)(q.size() - fadeCountMin) / (double)(fadeCountMax - fadeCountMin);
		}
		avgQuality.add(quality);
		if (quality < 0.6) 
			lowQualityCount++;

    	err.d = gain.d.getCorrection(d.slope(n, 1));
  	    err.i = gain.i.getCorrection(i);
		err.l = gain.l.getCorrection(l.calculate());
	    if (err.l > 0 && (err.d + err.i + err.p) > 0 || err.l < 0 && (err.d + err.i + err.p) < 0) {
			err.l = 0;       
			//l.clear();
		}
	    corr = -(err.p + err.d + err.i + err.l) * finalGain * quality + 
	    	0 * (1 - quality);
		if (Double.isNaN(defaultValue.calculate()))
			defaultValue.clear();
	    defaultValue.add(corr);
	    if (Double.isNaN(corr))
	    	corr = 0.0;
		l.add(n, delays.l.get(n, corr));
		return corr;
    }

    void copySettings(PidControl pid) {  
    	gain = pid.gain.clone();
		delay = pid.delay.clone();
		delays.init(delay);
    	period = pid.period.clone();
    	finalGain = pid.finalGain;
    	this.qualityFadeThreshold =  pid.qualityFadeThreshold;
    	this.qualityFadeGain = pid.qualityFadeGain;
		this.qualityPeriod = pid.qualityPeriod;
		this.fadeCountMax = pid.fadeCountMax;
		this.fadeCountMin = pid.fadeCountMin;
    }
}
 
