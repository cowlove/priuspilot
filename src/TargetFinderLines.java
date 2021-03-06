import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import math.RunningAverage;
import math.RunningLeastSquares;


class Hist2D {
	int [] data;
	int minX, maxX, minY, maxY, w, h;
	void clear() { data = null; }
	
	void setSize(int x1, int x2, int y1, int y2) { 
		minX = x1; maxX = x2; minY = y1; maxY = y2;
		w = x2 - x1 + 1;
		h = y2 - y1 + 1;
		data = new int[w * h];
	}
	void setBucket(int x, int y, int n) { 
		data[x - minX + (y - minY) * w] = n;
	}
	int getBucket(int x, int y) { 
		return data[x - minX + (y - minY) * w];
	}
	void copyIn(Hist2D o) { 
		for(int x = o.minX; x <= o.maxX; x++) {
			for(int y= o.minY; y <= o.maxY; y++) { 
				setBucket(x, y, o.getBucket(x, y));
			}
		}
	}
	void add(int x, int y) { add(x, y, 1); } 
	
	void add(int x, int y, int c) {
		if (data == null) { 
			minX = maxX = x;  
			minY = maxY = y;
			w = h = 1;
			data = new int[1];
		} else {
			if (x < minX || x > maxX || y < minY || y > maxY) { 
				Hist2D o = new Hist2D();
				o.setSize(Math.min(x, minX), Math.max(x, maxX), Math.min(y, minY),
						Math.max(y, maxY));
				o.copyIn(this);
				data = o.data;
				minX = o.minX;
				maxX = o.maxX;
				minY = o.minY;
				maxY = o.maxY;
				h = o.h;
				w = o.w;
			}
		}
		data[x - minX + (y - minY) * w] += c;
	}
}

// historgram inspector to examine 2-d x vs y histogram 
class HslHist2D {
	Hist2D [] hists = new Hist2D[3];
	GnuplotWrapper [] gp = new GnuplotWrapper[3];
	HslHist2D() { 
		for(int n = 0; n < 3; n++) {
			hists[n] = new Hist2D();
			gp[n] = new GnuplotWrapper();
		}
	}
	void add(int x, int []hsl) { 
		for(int n = 0; n < 3; n++) { 
			hists[n].add(x, hsl[n]);
		}
	}
	void clear() { 
		for(int n = 0; n < 3; n++) 
			hists[n].clear();
	}
	void draw() { 
		for(int n = 0; n < 3; n++) { 
			gp[n].startNew();
			gp[n].add3DGrid(hists[n].data, hists[n].w, hists[n].h);
			gp[n].draw();
		}
	}
	
}

class PeriodicityDetector { 
	int timeout;
	PeriodicityDetector(double hystPct, int maxAvg, int t) { 
		timeout = t;
		this.hystPct = hystPct; this.maxAvg = maxAvg;
		hist = new RunningAverage(t);
		av = new RunningAverage(15);
	}
	double hystPct;
	int maxAvg;
	RunningAverage av;
	RunningAverage hist;
	boolean low = true;
	int lastLowTime, lastPeriod;
	void add(int t, int v) {
		hist.add(v);
		double avg = hist.calculate();
		int hyst = (int)(avg * hystPct);
		int lowThresh = (int)(avg - hyst);
		int highThresh = (int)(avg + hyst);
			
		if (low && v > highThresh) 
			low = false;
		else if (!low && v < lowThresh) {
			low = true;
			lastPeriod = t - lastLowTime;
			av.add(lastPeriod);
			lastLowTime = t;
		}
		if (lastLowTime - t > timeout) { 
			lastPeriod = 0;
			av.clear();
		}
	}
	int getPeriod() { 
		if (hist.calculate() > maxAvg) 
			return 0;
		return (int)av.calculate();
	}
	public void clear() {
		lastPeriod = 0;
		av.clear();
		hist.clear();
	}
	
}
class Focus { 
	static final int minWeight = 8;
	double minAngWidth, maxAngWidth;
	int minSzWidth, maxSzWidth;
	double defaultAngle = 0;
	int defaultIntercept = 0;
	double radZoneOffset = 0.50; // verticle center of the scan strip
	double angZoneOffset = 0.50;
	
	public RunningLeastSquares angle = new RunningLeastSquares(1);
	public RunningLeastSquares intercept = new RunningLeastSquares(1);
	
	// TODO- instead of storing average focus as angle/intercept, 
	// store it as angle/point and handle angle wraparound and singularities in axis intercepts
	
	double lastAngle, lastIntercept;
	double lastRadius, lastOX, lastOY;
	
	void update(int weight, Point o, double r, double a, double i) {
		if (weight > minWeight) { 
			angle.add(count, a, weight);
			lastAngle = a;
			intercept.add(count, i, weight);
			lastIntercept = i;
			lastOX = o.x;
			lastOY = o.y;
			lastRadius = r;
			count++;
		} else { 
			clear();
		}  
		if (angle.count == angle.size && angle.totalWeight() < minWeight * 2 * angle.size) { 
			clear();
		} 
		
	}
	
	
	public int getQuality() {
		return (int)angle.totalWeight();
	}
	boolean full() { 
		return angle.count == angle.size;
	}
	int count() { 
		return angle.count;
	}
	void clear() { 
		angle.clear();
		intercept.clear();
		count = 0;
	}
	double getAngWidth() { 
		return maxAngWidth - (maxAngWidth - minAngWidth) / angle.size * angle.count;
	}
	int getSzWidth() {
		return maxSzWidth - (maxSzWidth - minSzWidth) * angle.count / angle.size;
	}
	double getAngle() {
		return angle.count > 0 ? angle.averageY() : defaultAngle;
	}
	double getLastAngle() { 
		return angle.count > 0 ? lastAngle : defaultAngle;		
	}
	int getLastIntercept() { 
		return (int)Math.round(intercept.count > 0 ? lastIntercept : defaultIntercept);
	}
	int getIntercept() {
		return (int)Math.round(intercept.count > 0 ? intercept.averageY() : defaultIntercept);
	}
	
	int count = 0;
}

class TargetFinderLinePair { 
	TargetFinderLines left, right; 
}

class AverageLine { 
	AverageLine(int n) {
		
	}
	public RunningLeastSquares angle = new RunningLeastSquares(5);
	public RunningLeastSquares radius = new RunningLeastSquares(5);
	int count = 0;
	double lastAng, lastRad;
	void add(double ang, Point p, int weight) { 
		
		// d = |(x2 - x1)(y1 - y0) - (x1 - x0)(y2 - y1)| / sqrt((x2 - x1)^2 - (y2 - y1)^2)
		// d = |cos(ang)(y1) - (x1)(sin(ang))| / sqrt((cos(ang)^2 - sin(ang)^2)
		double rad = Math.toRadians(ang);
		double d = (p.y * Math.cos(rad) - p.x * Math.sin(rad)) / 
				Math.sqrt(Math.cos(rad) * Math.cos(rad) - Math.sin(rad) * Math.sin(rad));
		radius.add(count, d, weight);
		angle.add(count, ang, weight);
	}
	void clear() { 
		radius.clear();
		angle.clear();
		count = 0;
	}
	
	double getAng() { 
		return lastAng;
	}
	//
}

@SuppressWarnings("unused")
class TargetFinderLines extends TargetFinder { 
	boolean leftSide = false;
	final double toeIn = 0; // TODO -broken? 
	int rawPeakHough = 0;
	
	TargetFinderRoadColor tfrc = null;
	HslHistogram hhist = null;
	int minLineIntensity = 0;
	int houghAngSz, houghRadSz; 
	int [] hslThresh = new int[3];
	
	GnuplotWrapper gp = new GnuplotWrapper();
	GnuplotWrapper gp2 = new GnuplotWrapper();
	GnuplotWrapper gp3 = new GnuplotWrapper();
	
	PeriodicityDetector pd = new PeriodicityDetector(0.15, 200, 40);
	ArrayList<ArrayList<Point>> ptHist = new ArrayList<ArrayList<Point>>();
	final int histDelay = 20; //TODO -hardcoded array limits
	boolean useLaneWidthFilter = false;
	boolean usePeriodDetection = true;
	boolean useLuminanceCheck = true;
	int debugId = debugIdCount++;
	static int debugIdCount = 0;
	double sThresh = 0;

	TargetFinderLines(int w, int ht, Rectangle sa1, boolean left, int defAngle, int houghSz, 
			int minSz, int maxSz, int minAng, int maxAng) {
		super(w, ht);
		houghAngSz = houghSz;
		houghRadSz = houghSz;
		leftSide = left;
		
		focus.minAngWidth = minAng;
		focus.maxAngWidth = maxAng;
		focus.minSzWidth = minSz;
		focus.maxSzWidth = maxSz;
		
		param.name = "TFLines," + (left ? "left" : "right");
		param.gaussianKernelRadius = 0.3f; // TODO- bug in canny stuff, artifacts show up above 1.0
		param.threshold1 = param.threshold2 = 5;  // Range between 13 or 5 
		
		sa = sa1;
		if (sa == null) { 
			sa = new Rectangle(0,0,0,0);
			sa.width = w / 2;
			sa.height = (int)(ht * .66);
			sa.y = (int)(ht * .20);
			sa.x = leftSide ? 0 : sa.width;
		}
		
		if (leftSide) { 
			focus.defaultIntercept = (int)(Math.tan(Math.toRadians(90 - defAngle)) * sa.width) - 10;
			focus.defaultAngle = 90 + defAngle;
		} else { 
			focus.defaultIntercept = -10;
			focus.defaultAngle = 90 - defAngle;
		}
		h = new HoughTransform(houghAngSz, houghRadSz);
		h.blurRadius = 0.09f;
		vanLimits = new Rectangle(w / 3, (int)(ht * 5 / 24), w / 8, ht / 9);
	}
	
	
	Rectangle vanLimits = null;
	Focus focus = new Focus();
	HoughTransform h = null;
	ArrayList<Point> lumPoints = new ArrayList<Point>();
	// Min angle from the horizon.  Delicate/critical, prevents locking on 
	// horizon or other very slight road verge, pushes tracker down to more
	// appropriate outer lane line or road edge
	int minAng = 12;
	
	void reset() {
		focus.clear();
		pd.clear();
		count = 0;
		this.ptHist.clear();
	}
	@Override 
	Rectangle []findAll(OriginalImage oi, Rectangle recNO) {
		c.zones.height = sa.height;
		c.zones.clear();

		setCanny(param);

		int intercept = 0;
		double ang = 0;
						
		ang = focus.getAngle();
		intercept = focus.getIntercept();
		
		// Hough angles and angle range are perpendiclar to target line
		double angOffset = leftSide ? focus.angZoneOffset : (1 - focus.angZoneOffset);
		double angMax = ((ang - 90) % 360) + focus.getAngWidth() * angOffset;
		double angMin = ((ang - 90) % 360) - focus.getAngWidth() * (1 - angOffset);
		h.setAngleRange(angMin, angMax);
		
		int szWidth = focus.getSzWidth();
		double toe = leftSide ? toeIn : -toeIn;
		double sr = Math.abs(Math.cos(Math.toRadians(ang)));
		double szVert = sr != 0 ? ((double)szWidth / sr) : szWidth;
		c.zones.lsz.b1 = (int)(intercept - szVert * focus.radZoneOffset); 
		c.zones.lsz.b2 = (int)(intercept + szVert * (1 - focus.radZoneOffset));
		c.zones.lsz.m1 = Math.tan(Math.toRadians(ang + toe)); 
		c.zones.lsz.m2 = Math.tan(Math.toRadians(ang - toe));
		c.zones.midX = sa.width;
		if (leftSide) {
			// on right side, origins b1 and b2 are on the narrow side of the grid, 
			// narrow them them according to the toe-in angle.
			double vDist = Math.tan(Math.toRadians(ang)) * sa.width;
			double toeDist = vDist * 
				(Math.sin(Math.toRadians(ang + toeIn)) / Math.sin(Math.toRadians(ang)) - 1);
			//toeDist = 25; // todo -figure out toeDist
			c.zones.lsz.b1 -= toeDist;
			c.zones.lsz.b2 += toeDist;
			//if (h.id == 1)
			//	System.out.printf("%.1f %.1f\n", szVert, toeDist);
		}
		
		szWidth = focus.getSzWidth();

		// Pick origin for hough transform- the intercept of the scanzone with lower edge or 
		// far side edge of sa rectangle. 
		h.origin = new Point();
		h.origin.y = sa.height;
		double ox = lineAtY(focus.lastOX, focus.lastOY, focus.lastRadius, ang, h.origin.y);
		if (Double.isNaN(ox) || ox < 0 || ox > sa.width) {
			ox = leftSide ? 0 : sa.width - 1;
			h.origin.y = (int)Math.round(lineAtX(0, intercept,
					0, ang, ox));
		}
		h.origin.x = (int)Math.round(ox);
	
		if (h.id == 0) { 
			// TMP HACK: for swrc use, clear out the scan zone until we get
			// better working scan zone implementation
			//c.zones.lsz.m1 = Double.NaN; 
		}
		h.setRadRange(-szWidth * focus.radZoneOffset, szWidth * (1 - focus.radZoneOffset));
		
		// h.radMax = (float)Math.max(h.maxLineRadius(rec, c.zones.`, c.zones.lsz.b1),
		// h.maxLineRadius(rec, c.zones.lsz.m2, c.zones.lsz.b2));
		
		// scan zones are now set up, proceed with canny and hough processing 
		c.processData(oi, sa);
        canny = c.getData();
		
        // auto-tune canny thresholds to try and keep a reasonable number of edge points
        // TODO- normalize the point count to the scan area
        if (c.results.l.size() > 900 && param.threshold1 < 13)
        	param.threshold1 = param.threshold2 += 1;
        if (c.results.l.size() < 500 && param.threshold1 > 4)
        	param.threshold1 = param.threshold2 -= 1;
        
        //if (h.id == 0) 
        //	System.out.printf("threshold1 = %d\n", (int)param.threshold1);
        
        lumPoints.clear();
        h.clear();
        ArrayList<Point> nonLumPoints = new ArrayList<Point>();
        
    	// retain only inner-most canny pixel for each pixel row
        if (false && !this.useLaneWidthFilter) { 
			for (int y = 0; y < sa.height; y++) { 
				int startX = leftSide ? sa.width - 1 : 0;
				int endX = leftSide ? -1 : sa.width;
				int step = leftSide ? -1 : 1;
				boolean first = true;
				int continuous = 0;
				final int reqContinuousPixels = 1;
				for(int x = startX; x != endX; x  += step) { 
					if ((canny[x + y * sa.width] & 0xff) == 0xff) {
						if (checkLuminance(oi, sa, x, y, minLineIntensity)) {
							if (++continuous == reqContinuousPixels && first) { 
								h.add(x, y);
								lumPoints.add(new Point(x - step * continuous,y));
								first = false;
							}
						} else {
							continuous = 0;
						}
					}
				}
			}
        } else  { 	        
	        for( Point p : c.results.l ) {
	        	if(!useLuminanceCheck || checkLuminance(oi, sa, p.x, p.y, minLineIntensity)) {
	        		h.add(p.x, p.y);
	        		lumPoints.add(p);
	        	} else 
	        		nonLumPoints.add(p);
	        }
        }
		
		// For a broken lane line, try to add in a couple older frames' data
		// to make the line more continuous 
        if (usePeriodDetection) { 
			pd.add(count, h.maxhough);
			int period = pd.getPeriod();
			ptHist.add((ArrayList<Point>)lumPoints.clone());
			if (ptHist.size() == histDelay) { 
				if (period >= 4 && period < histDelay /*TODO - bounds/array checking*/) {
					// Add points from about 1/3 of the way through the intermittent line
					// period
					for(Point p : ptHist.get(histDelay - period / 3 - 1)) { 
						h.add(p.x, p.y);
						lumPoints.add(p);
						//oi.putPixel(p.x + sa.x, p.y + sa.y, -1);
					}
					for(Point p : ptHist.get(histDelay - period / 2 - 1)) { 
						h.add(p.x, p.y);
						lumPoints.add(p);
						//oi.putPixel(p.x + sa.x, p.y + sa.y, -1);
					}
				}
	
				// And also, for all lines add in points from the most recent frame, to help with
				// dot smearing and to weight the average towards more current values
				for(Point p : ptHist.get(histDelay - 1))  
					h.add(p.x, p.y);
				for(Point p : ptHist.get(histDelay - 2))  
					h.add(p.x, p.y);
				
				ptHist.remove(0);
			}
        }
		h.blur();   
		
		if (useLaneWidthFilter)  
			h.applyCorrelation(1.0, 2.5, leftSide);
		
		
		// search for a nearby max, giving slight preference to inside and 
		// steeper lines.
		final double prefPoint = 0.1;
		int ca = leftSide ? 0 : h.angSz;
		h.findClosest(ca, leftSide ? h.radSz : 0, 0.7f);

		if (Silly.debug("DEBUG_LINES") && Silly.debugInt("DEBUG_LINES_ID") == h.id) {
   			//if (Silly.debug(Silly.DEBUG_LINES)) { 
			gp.startNew();
			gp.add3DGrid(h.hough, h.angSz, h.radSz);
			gp.draw();
			if (h.corHough != null) { 
				gp2.startNew();
				gp2.add3DGrid(h.corHough, h.angSz, h.radSz);
				gp2.draw();
			}
		}
		count++;
				
		double i = h.bestYIntercept();
		double a = h.bestAngle();
		//if (a < 90 && i > 0) i = -i;
		focus.update(h.maxhough, h.origin, h.bestRadius(), (h.bestAngle() + 90) % 360, h.bestYIntercept());
		a = focus.getAngle();
	
		// reset if lock is too close to the horizon
		if (minAng != 0 && focus.full() && (leftSide && (a < 90 + minAng || a > 180 - minAng)) || 
				(!leftSide && (a < 0 + minAng || a > 90 - minAng))) { 
			//System.out.print("Clearing due to angle\n");
			reset();
		}

		// Does line intercept vanLimits retangle? - todo clean this up move to its own function 
		if (focus.full() && vanLimits != null) { 
			double m1 = Math.tan(Math.toRadians(focus.getAngle()));
			double b1 = sa.y + focus.getIntercept() - sa.x * m1;
			int x = (int)((vanLimits.y - b1) / m1);
			if (x < vanLimits.x || x > vanLimits.x + vanLimits.width) {
				x = (int)((vanLimits.y + vanLimits.height - b1) / m1);
				if (x < vanLimits.x || x > vanLimits.x + vanLimits.width) {
					int y = (int)((vanLimits.x * m1 + b1));
					if (y < vanLimits.y || y > vanLimits.y + vanLimits.height) { 
						y = (int)(((vanLimits.x + vanLimits.width) * m1 + b1));						
						if (y < vanLimits.y || y > vanLimits.y + vanLimits.height) { 		
							reset();;
							//System.out.print("Clearing due to vanish rect\n");
						}
					}
				}
			}
		}

		if (Silly.debug("DEBUG_COLOR_SEGMENTATION")  && (/*h.id == 0 ||*/ h.id == 1)) { 
			// examine the area near each lane line, trying to figure out some color
			// segmentation tricks. 
			nearPixels1.clear();
			nearPixels2.clear();
			hslRoad.clear();
	
			hsl2d.clear();
			
			for(int y = (int)(sa.y + sa.height) - 20; y >= sa.y + 0; y--) {
				int x = getInstantaneousX(y);
				int s = leftSide ? -1 : 1;
				int startX = -25;
				int endX = -5;
				for(int w = startX; w < endX + y / 5; w++) {
					int normX = (w - (startX)) * 100 / (endX + y / 5 - startX); // a normalized width used for plotting  
					int x1 = x + w * s;
					if (x1 >= sa.x && x1  < sa.x + sa.width) {  
							int [] hsl = oi.getHsl(x1, y);
							int l = oi.getPixelLum(x1, y);
							//if (w < -8 && y > sa.y + sa.height * .10 && y < sa.y + sa.height * .6) {
							//	hslRoad.add(hsl[0], hsl[1], hsl[2]);
							//	oi.dimPixel(x1,  y);
							//}
							oi.dimPixel(x1,  y);
							hsl2d.add(normX, hsl);
							if ((
		//						hsl[0] < hslThresh[0] &&
							//	hsl[1] > hslThresh[1] && 
							//	hsl[2] > hslThresh[2] &&
									tfrc != null && tfrc.isLine(y, hsl) && 
									true
											)
									//|| (false && hsl[0] > 133 && hsl[0] < 140
											) {
								nearPixels1.add(hsl[0], hsl[1], hsl[2]);
								oi.putPixel(x1,  y, 0);
								
							} 
					}	 
				}
			}
			
			nearPixels1.draw(1);
			//hsl2d.draw();
		}

		//redo hough with all points (not just innermost color-segmented points
		//for the vanish point detection code. 
		h.clear();
		for( Point p : c.results.l ) 
			h.add(p.x, p.y);
        h.blur();
		return null;
	}
	
	HslHistogram nearPixels1 = new HslHistogram();
	HslHistogram nearPixels2 = new HslHistogram();
	HslHistogram hslRoad = new HslHistogram();
	int count = 0;
	
	public double getAngle() {		
    	return focus.angle.predict(focus.count);
	}

	public double getInstantaneousAngle() {		
    	return focus.lastAngle;
	}
	public int getInstantaneousX(int y) {		
    	double m = Math.tan(Math.toRadians(focus.lastAngle));
		double b = (int)Math.round(sa.y + focus.lastIntercept) - sa.x * m;
		//double b = (int)Math.round(sa.y + focus.getLastIntercept() - sa.x * m;
		// x = (y - b) / m
		return (int)Math.round((y - b) / m);
	}

	HslHist2D hsl2d = new HslHist2D();
	
	private boolean checkLuminance(OriginalImage oi, Rectangle sa, int x, int y, int thresh) {
		for(int dx = -1; dx <= 1; dx++) { 
			for(int dy = -1; dy <= 1; dy++) { 			
				if (x + dx >= 0 && x + dx < sa.width && dy + y >= 0 && dy + y < sa.height) {  
					if ((int)oi.getPixelLum(x + dx + sa.x, y + dy + sa.y) >= thresh)
						return true;
				}
			}
		}
		return false;
				
	}
	
	public int getOffsetX() {
    	double m = Math.tan(Math.toRadians(getAngle()));
		double b = (int)Math.round(sa.y + focus.intercept.predict(focus.count)) - sa.x * m;
		//double b = (int)Math.round(sa.y + focus.getLastIntercept() - sa.x * m;
		// x = (y - b) / m
		return (int)Math.round((sa.y + sa.height - b) / m);
	}
	
	// Given the line defined by origin/rad/angle, return the Y value at 
	// given X, or NaN if line is vertical, ie doesn't cross y=X 
	double lineAtX(double ox, double oy, double rad, double ang, double x) { 
		double nA = ang - 90;   // normal angle, angle of radius line
		double npX = ox + rad * Math.cos(Math.toRadians(nA)); // intersection of
		double npY = oy + rad * Math.sin(Math.toRadians(nA)); // line and radius, "normal point"
		
		if (Math.abs(ang % 180) == 90)
			return Double.NaN;
		else 
			return npY + (x - npX) * Math.tan(Math.toRadians(ang));
	}
	
	// Similarly, ... 
	double lineAtY(double ox, double oy, double rad, double ang, double y) { 
		double nA = ang - 90;   // normal angle, angle of radius line
		double npX = ox + rad * Math.cos(Math.toRadians(nA)); // intersection of
		double npY = oy + rad * Math.sin(Math.toRadians(nA)); // line and radius, "normal point"
		
		if (Math.abs(ang % 180) == 0)
			return Double.NaN;
		else 
			return npX + (y - npY) / Math.tan(Math.toRadians(ang));
	}
	
	// Given a line defined by an arbitrary origin/radius and angle, 
	// convert it to a (0,0) origin/radius.  Return new radius.
	double convertToNormalOrigin(Point oldOrigin, double ang, double rad) { 
		// point on line normal to old origin/radius
		double nA = ang + 90;   // normal angle, angle of radius line
		double npX = oldOrigin.x + rad * Math.cos(Math.toRadians(nA)); // intersection of
		double npY = oldOrigin.y + rad * Math.sin(Math.toRadians(nA)); // line and radius, "normal point"
	
		double npA; // npA: absolute angle from (0,0) to norm pt
		if (oldOrigin.x != 0) 
			npA = Math.atan(oldOrigin.y / oldOrigin.x);  
		else 
			npA = oldOrigin.y > 0 ? 90 : -90;
		double a = npA - nA; 
		double result = Math.cos(Math.toRadians(a)) * 
				Math.sqrt(oldOrigin.x * oldOrigin.x + oldOrigin.y * oldOrigin.y);
		return result;
	}
	
	Point findRadiusNormalPoint(Point o, double r, double a) { 
		double nA = a + 90;   // normal angle, angle of radius line
		double npX = o.x + r * Math.cos(Math.toRadians(nA)); // intersection of
		double npY = o.y + r * Math.sin(Math.toRadians(nA)); // line and radius, "normal point"
		return new Point((int)Math.round(npX), (int)Math.round(npY));
	}
	
	Point findMiddleOfLine(Point o, double r, double a, Rectangle rec) {
		double n;
		ArrayList<Point> pts = new ArrayList<Point>();
		if (!Double.isNaN(n = lineAtX(o.x, o.y, r, a, 0)) && n >= 0 && n < rec.height)  
			pts.add(new Point(rec.x, (int)Math.round(n)));
		if (!Double.isNaN(n = lineAtX(o.x, o.y, r, a, rec.width)) && n >= 0 && n < rec.height)  
			pts.add(new Point(rec.width, (int)Math.round(n)));
		if (!Double.isNaN(n = lineAtY(o.x, o.y, r, a, 0)) && n >= 0 && n < rec.width)  
			pts.add(new Point((int)Math.round(n), 0));
		if (!Double.isNaN(n = lineAtY(o.x, o.y, r, a, rec.height)) && n >= 0 && n < rec.width)  
			pts.add(new Point((int)Math.round(n), rec.height));
		
		double x = 0, y = 0;
		for(Point p : pts) { 
			x += p.x;
			y += p.y;
		}
		return new Point((int)Math.round(x / pts.size()), (int)Math.round(y / pts.size()));
	}
	
	private void drawLine(Graphics2D g2, Rectangle rec, int x1, double deltaAngle) {
		double ox = h.origin.x;
		double oy = h.origin.y;
		double a = (h.bestAngle() + 90) % 180;
		double r = h.bestRadius();
		ArrayList<Point> pts = new ArrayList<Point>();
		double n;

		double nA = a + 90;   // normal angle, angle of radius line
		double npX = ox + Math.cos(Math.toRadians(nA)); // intersection of
		double npY = oy + Math.sin(Math.toRadians(nA)); // line and radius, "normal point"

		
		if (!Double.isNaN(n = lineAtX(ox, oy, r, a, 0)) && n >= 0 && n < rec.height)  
			pts.add(new Point(rec.x, (int)Math.round(n + rec.y)));
		if (!Double.isNaN(n = lineAtX(ox, oy, r, a, rec.width)) && n >= 0 && n < rec.height)  
			pts.add(new Point(rec.x + rec.width, (int)Math.round(n + rec.y)));
		if (!Double.isNaN(n = lineAtY(ox, oy, r, a, 0)) && n >= 0 && n < rec.width)  
			pts.add(new Point((int)Math.round(n + rec.x), rec.y));
		if (!Double.isNaN(n = lineAtY(ox, oy, r, a, rec.height)) && n >= 0 && n < rec.width)  
			pts.add(new Point((int)Math.round(n + rec.x), rec.y + rec.height));
		
		if (pts.size() == 2) { 
			g2.drawLine(pts.get(0).x, pts.get(0).y, pts.get(1).x, pts.get(1).y);
		}
	}
	
	// display line, if it interstects the side of the vanLimits rect
	public void display(Graphics2D g2, Rectangle r, int x1) {
		
		Point midLine = findMiddleOfLine(h.origin, h.bestRadius(), focus.getLastAngle(), sa);
		final int txtOffset = 0;

		g2.drawString(String.format("%d %.1f/%d %.1f", c.results.l.size(), 
			getAngle(), getOffsetX(), h.getAngSpread()), midLine.x + sa.x + txtOffset, 
			midLine.y + sa.y + txtOffset);
		
		if (focus.getQuality() > Focus.minWeight) {
			//drawLine(g2, r, x1, focus.getAngWidth() * focus.angZoneOffset);
			drawLine(g2, r, x1, 0);
			//drawLine(g2, r, x1, -focus.getAngWidth() * (1 - focus.angZoneOffset));
        	//System.out.printf("ang %.2f\n", tfl.focus.angle.calculate());
		}

		final int oDot = 3;
		g2.draw(new Rectangle(sa.x + h.origin.x - oDot, sa.y + h.origin.y - oDot, oDot * 2, oDot * 2));
	}
	
	boolean insideVanRect(Point p) { 
		return p.x >= vanLimits.x && p.x < vanLimits.x + vanLimits.width &&
		p.y >= vanLimits.y && p.y < vanLimits.y + vanLimits.height;
	}
	
	static Point linePairIntercept(TargetFinderLines l1, TargetFinderLines l2) { 
		double m1 = Math.tan(Math.toRadians(l1.focus.getLastAngle()));
		double b1 = l1.sa.y + l1.focus.getLastIntercept() - l1.sa.x * m1;
		double m2 = Math.tan(Math.toRadians(l2.focus.getLastAngle()));
		double b2 = l2.sa.y + l2.focus.getLastIntercept() - l2.sa.x * m2;

		int x = (int)Math.round((b2 - b1) / (m1 - m2));
		int y = (int)Math.round(x * m2 + b2);
		return new Point(x, y);
	}
	
	static void displayLinePair(TargetFinderLines l1, TargetFinderLines l2, Graphics2D g2) {
		Point p = linePairIntercept(l1, l2);
		int rad = 5;
		Rectangle r = l1.vanLimits;
		if (l1.focus.getQuality() > Focus.minWeight && l2.focus.getQuality() > Focus.minWeight) {
			r = new Rectangle(p.x - rad, p.y - rad, rad * 2 + 1, rad * 2 + 1);
			g2.draw(r);
		}
		l1.display(g2, l1.sa, 0);
		l2.display(g2, l2.sa, l2.width);
		//System.out.printf("l weight %d\n", l1.focus.angle.weight);
	}

	static void displayLinePairToOutsideVanRec(TargetFinderLines l1, TargetFinderLines l2, Graphics2D g2) {
		l1.display(g2, l1.sa, 0);
		l2.display(g2, l2.sa, l1.width);
	}
	
	public void markup(OriginalImage coi) {
        // retain only inner-most canny pixel for each pixel row
		for (int y = 0; y < sa.height; y++) { 
			int startX = leftSide ? sa.width - 1 : 0;
			int endX = leftSide ? -1 : sa.width;
			int step = leftSide ? -1 : 1;
			for(int x = startX; x != endX; x  += step) { 
				if (y >= c.zones.ystart(x) && y < c.zones.yend(x))
					coi.dimPixel(x + sa.x, y + sa.y);
				
			}
		}
		// Draw canny lines on original image
		for( Point p : c.results.l )  
			coi.putPixel(p.x + sa.x, p.y + sa.y, -1);		
	
	}

}
