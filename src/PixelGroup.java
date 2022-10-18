


import java.awt.Color;


import java.awt.Graphics2D;
import java.util.Arrays;


//import math.Average;
//import math.RunningAverage;
//import math.RunningLeastSquares;
//import math.JamaLeastSquaresFit;
//import math.Geometry;
//import math.Geometry.*;

//plot a 2d histogram of segments on a slope/intercept axis.  Turned out not too useful 
class HistGrid {

    RunningLeastSquares[] segments;
    int segCount;

    public HistGrid(RunningLeastSquares[] segs, int c, Geometry.LinePair sz) {
        segments = segs;
        segCount = c;
        scanZone = sz;
    }
    int histGridSize = 100;
    int[] histGrid = new int[histGridSize * histGridSize];

    void clearHistGrid() {
        for (int i = 0; i < histGridSize * histGridSize; i++) {
            histGrid[i] = 0;
        }
        histGridMax = 0;
    }
    int histGridSmear = 5;
    int histGridMaxX, histGridMaxY, histGridMax;
    Geometry.LinePair scanZone;

    void markHistGridMax() {
        for (int i = 0; i < segCount; i++) {
            int x = getHistGridX(segments[i]);
            int y = getHistGridY(segments[i]);
            if (Math.abs(x - histGridMaxX) <= histGridSmear
                    && Math.abs(y - histGridMaxY) <= histGridSmear) {
            }
        }
    }

    int getHistGridY(RunningLeastSquares a) {
        double m = a.slope();
        double minSlope = Math.min(scanZone.a.m, scanZone.b.m);
        double maxSlope = Math.max(scanZone.a.m, scanZone.b.m);
        int y = (int) (histGridSize * (m - minSlope) / (maxSlope - minSlope));
        return y;
    }

    int getHistGridX(RunningLeastSquares a) {
        double b = a.intercept();
        double minInt = Math.min(scanZone.a.b, scanZone.b.b);
        double maxInt = Math.max(scanZone.a.b, scanZone.b.b);
        int x = (int) (histGridSize * (b - minInt) / (maxInt - minInt));
        return x;
    }

    void addToHistGrid(RunningLeastSquares a) {
        double b = a.intercept();
        double m = a.slope();
        double minSlope = Math.min(scanZone.a.m, scanZone.b.m);
        double maxSlope = Math.max(scanZone.a.m, scanZone.b.m);
        double minInt = Math.min(scanZone.a.b, scanZone.b.b);
        double maxInt = Math.max(scanZone.a.b, scanZone.b.b);

        int x = (int) (histGridSize * (b - minInt) / (maxInt - minInt));
        int y = (int) (histGridSize * (m - minSlope) / (maxSlope - minSlope));


        if (x < 0 || x >= histGridSize || y < 0 || y >= histGridSize) {
            x = y = 0;
        }
        for (int x1 = x - histGridSmear; x1 < x + histGridSmear; x1++) {
            for (int y1 = y - histGridSmear; y1 < y + histGridSmear; y1++) {
                if (x1 >= 0 && x1 < histGridSize && y1 >= 0 && y1 < histGridSize) {
                    int i = x1 + y1 * histGridSize;
                    histGrid[i] += a.count;
                    if (histGrid[i] > histGridMax) {
                        histGridMaxX = x1;
                        histGridMaxY = y1;
                        histGridMax = histGrid[i];
                    }
                }

            }
        }
    }

    void printHistGrid() {
        System.out.println("printHistGrid() - histGridMax " + histGridMax);

        for (int x = 0; x < histGridSize; x++) {
            for (int y = 0; y < histGridSize; y++) {
                System.out.print(String.format("%02x ", histGrid[y * histGridSize + x]));
            }
            System.out.println("");
        }
    }

    void makeHistGrid() {
        clearHistGrid();
        for (int x = 0; x < segCount; x++) {
            addToHistGrid(segments[x]);
        }
        //printHistGrid();
        markHistGridMax();
    }
}

class PixelGroup {
	OriginalImage orig;
    int colorThreshold;
    int[] data;
    int width, height;
    int vanishX, vanishY, vanishErr;
    RunningLeastSquares[] segments = new RunningLeastSquares[150];
    int segmentsIndex = 0;
    boolean goodSegment = false;
    int color = Color.red.getRGB();
    Average avgSlope = new Average(), avgIntercept = new Average();
    Geometry.LinePair scanZone = null;   
    static final private int DONE_MASK = 0xff000000;

    // canny following and thresholding now done here.  Ugh.  These
    // set by LaneAnalyzer from values in the detector object.  Ugh
    public int cannyHighThreshold = 800, cannyLowThreshold = 400;
    
    // debug annotations class, for writing debug text onto the image
    // at certain points on the processing.   writeText() is called by
    // the upper-level program prior to display, to give
    // this class a chance to write debug info into the image.
    class DebugAnnotations {
        public class Note {

            int x;
            int y;
            String txt;

            Note(int ax, int ay, String as) {
                x = ax;
                y = ay;
                txt = as;
            }
        }
        public Note[] notes = new Note[500];
        int count = 0;

        void writeText(Graphics2D g2, int scale) {
            for (int i = 0; i < count; i++) {
                g2.drawString(notes[i].txt, notes[i].x * scale, notes[i].y * scale);
            }
        }

        void add(int x, int y, String s) {
            if (count < notes.length) {
                notes[count++] = new Note(x, y, s);
            }
        }

        void clear() {
            count = 0;
        }
    }
    DebugAnnotations debugNotes = new DebugAnnotations();

    void set(int[] d, double mis, double mas,
            double vx, double vy, double verr, int colorThreshold) {
        data = d;
        this.colorThreshold = colorThreshold;
        minSlope = mis;
        maxSlope = mas;
        vanishX = (int) (width * vx);
        vanishY = (int) (height * vy);
        vanishErr = (int) (width * verr);
        Geometry.Point vp = new Geometry().new Point(vanishX, vanishY);

        scanZone = new Geometry().new LinePair(Geometry.lineFromSlopePointDistance(minSlope, vp, -vanishErr),
                Geometry.lineFromSlopePointDistance(maxSlope, vp, vanishErr));
    }

    int ystart(int x) {
    	int y1 = (int) ((scanZone.a.m * x + scanZone.a.b));
        int y2 = (int) ((scanZone.b.m * x + scanZone.b.b));
        int ys = Math.max(vanishY, Math.min(y1, y2));
        ys = Math.min(height - 1, ys);
        ys = Math.max(0, ys);
        return ys;
    }

    int yend(int x) {
        int y1 = (int) ((scanZone.a.m * x + scanZone.a.b));
        int y2 = (int) ((scanZone.b.m * x + scanZone.b.b));
        int ys = Math.min(height, Math.max(y1, y2));
        ys = Math.min(height - 1, ys);
        ys = Math.max(0, ys);
        return ys;
    }

    
    
    double minSlope = -1.5, maxSlope = -0.2, maxErr = 0.45, minIntercept = 100, maxIntercept = 500;
    int minLength = 15, maxLength = 500, maxPointErr = 1;
    boolean debugMarkup = false;
    int debugPixelCount = 0;
    RunningLeastSquares ls = new RunningLeastSquares(minLength);
    int valid = 0;

    PixelGroup(int x, int y) {
        width = x;
        height = y;
    }

    int getOrigPixel(int x, int y) { 
    	return orig.getPixelABGR(x, y);
    }
    public static boolean pixelHasGoodColor(int pixel, int thresh) {
        int r = (pixel & 0xff0000) >> 16;
        int g = (pixel & 0xff00) >> 8;
        int b = (pixel & 0xff);
        double rgRatio = (double) r / g;
        if (r > thresh && g > thresh //&& rgRatio > .75 && rgRatio < .90
                ) {
            return true;
        }
       return false;

    }
    
    // examines the surrounding pixels to see if one is yellow or white
    // enough to suggest that this pixel is part of a lane line.
    boolean hasGoodLaneColor(int x1, int y1) {
        int distance = 2;
        for (int y = y1 - distance; y <= y1 + distance; y++) {
            for (int x = x1 - distance; x <= x1 + distance; x++) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    if (pixelHasGoodColor(getOrigPixel(x, y), colorThreshold)) {
                        return true;
                    }

                }
            }
        }
        return false;
    }

    boolean laneHasGoodColor(RunningLeastSquares s) {
        int goodcount = 0;
        for (int i = 0; i < s.count; i++) {
            if (hasGoodLaneColor((int) s.xs[i], (int) s.ys[i])) {
                goodcount++;
            }
        }
        return goodcount > s.count * 0.70;
    }

    // debug function that prints to the console the hex pixel values surrounding a line segment
    void showLineImageContext() {
        for (int i = 0; i < ls.count; i++) {
            String line = String.format("row %d\t", (int) ls.ys[i]);
            for (int x = (int) ls.xs[i] - 3; x <= (int) ls.xs[i] + 3; x++) {
                if (x >= 0 && x < width) {
                    line += String.format("%08x\t", (int) getOrigPixel(x, (int)ls.ys[i]));
                  }
            }
            System.out.println(line);
        }
    }

    void clearSegment() {
        goodSegment = false;
        ls.clear();
        ls.shrink(minLength);
    }

    class ColorAverageRGBAnalyzer { 
    	int size = 10000;
    	
    	private void rgb2hsl(int r, int g, int b, int hsl[]) {
			
    		float var_R = ( r / 255f );                    
    		float var_G = ( g / 255f );
    		float var_B = ( b / 255f );
    		
    		float var_Min;    //Min. value of RGB
    		float var_Max;    //Max. value of RGB
    		float del_Max;    //Delta RGB value
    						 
    		if (var_R > var_G) 
    			{ var_Min = var_G; var_Max = var_R; }
    		else 
    			{ var_Min = var_R; var_Max = var_G; }

    		if (var_B > var_Max) var_Max = var_B;
    		if (var_B < var_Min) var_Min = var_B;

    		del_Max = var_Max - var_Min; 
    								 
    		float H = 0, S, L;
    		L = ( var_Max + var_Min ) / 2f;
    	
    		if ( del_Max == 0 ) { H = 0; S = 0; } // gray
    		else {                                //Chroma
    			if ( L < 0.5 ) 
    				S = del_Max / ( var_Max + var_Min );
    			else           
    				S = del_Max / ( 2 - var_Max - var_Min );
    	
    			float del_R = ( ( ( var_Max - var_R ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
    			float del_G = ( ( ( var_Max - var_G ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
    			float del_B = ( ( ( var_Max - var_B ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
    	
    			if ( var_R == var_Max ) 
    				H = del_B - del_G;
    			else if ( var_G == var_Max ) 
    				H = ( 1 / 3f ) + del_R - del_B;
    			else if ( var_B == var_Max ) 
    				H = ( 2 / 3f ) + del_G - del_R;
    			if ( H < 0 ) H += 1;
    			if ( H > 1 ) H -= 1;
    		}
    		hsl[0] = (int)(360*H);
    		hsl[1] = (int)(S*100);
    		hsl[2] = (int)(L*100);
    	}
     
    	RunningAverage ah = new RunningAverage(size), as = new RunningAverage(size), al = new RunningAverage(size);
        	
    	void add(int p) {
       		int hsl[] = new int[3];
    		rgb2hsl(((p & 0xff0000) >> 16), ((p & 0xff00) >> 8), (p & 0xff), hsl);
    		ah.add(hsl[0]);
    		as.add(hsl[1]);
            al.add(hsl[2]);
    	}
    	int diff(int p) { 
       		int hsl[] = new int[3];
    		rgb2hsl(((p & 0xff0000) >> 16), ((p & 0xff00) >> 8), (p & 0xff), hsl);
     		int h = hsl[0]  - (int)ah.calculate();
     		int s = hsl[1]  - (int)as.calculate();
     		int l = hsl[2]  - (int)al.calculate();
     	        
        	int d = (int)Math.sqrt(h * h + s * s + l * l);
        	avgDiff.add(d);
        	return d;
       
    	}  	
    	RunningAverage avgDiff = new RunningAverage(1000);
    };
    
    ColorAverageRGBAnalyzer caa = new ColorAverageRGBAnalyzer();
    
    // compares the two line segments to see if they are roughly parallel
    // and about a roadline width distance away.
    // TODO- proper line-to-line || distance, remove raw pixel use.
    boolean looksLikeLine_GeometricDistance(RunningLeastSquares a, RunningLeastSquares b) {
        int r = (int) ((height - a.intercept()) / a.slope());
        int s = height;
        double A = -b.slope();
        double B = 1;
        double C = -b.intercept();

        // Distance from line Ax + By + C = 0 to point (r,s)
        double dist = Math.abs(A * r + B * s + C) / Math.sqrt(A * A + B * B);
        return dist >= (2.5 * width / 160) && dist < (5 * width / 160);  // TODO- proper ||line distance, eliminate raw pixel use
    }
    Average avgR = new Average();
    Average avgRR = new Average();
    Average avgG = new Average();
    Average avgGG = new Average();
    Average avgRG = new Average();
    // LooksLikeLine (lll) routines- analyze the collected segments kept in the RunningLeastSquares array segments[],
    // accentuate ones that look good
    AutoFocusParameter rgColorRatio = new AutoFocusParameter(0.9, 0.40, .15, 800, 100);
    Average avgLllWidth = new Average();

    boolean looksLikeLine_widthPlusBounded(RunningLeastSquares a) {
        avgLllWidth.clear();
        int goodLine = 0;
        int firstGood;
        int maxWidth = 7 * width / 160;
        for (int i = 0; i < a.count; i++) {
            int goodPixels = 0, totalPixels = 0;
            boolean goodEnd = false;
            for (int x = (int) a.xs[i] + 1; x < Math.min((int) a.xs[i] + maxWidth, width); x++) {
            	int y = (int)a.ys[i];
                int pindex = x + (int) a.ys[i] * width;
                if (data[pindex] == color && goodPixels == 0) {
                    continue;
                }
                totalPixels++;
                int r = (getOrigPixel(x, y) & 0xff0000) >> 16;
                int g = (getOrigPixel(x, y) & 0xff00) >> 8;
                if (pixelHasGoodColor(getOrigPixel(x, y), colorThreshold)
                        && this.rgColorRatio.within((double) r / g)) {
                    goodPixels++;
                    if (goodPixels == 1)
                    	firstGood = x;
                }
                if (data[pindex] == color) {
                    goodEnd = true;
                    break;
                }
            }
            // loop over this row again, marking pixels black for debugging,
            // and adding pixels to avgRG
            if (goodPixels > 2 && goodEnd) {
                goodLine++;
                for (int x = (int) a.xs[i] + 1; x < Math.min((int) a.xs[i] + maxWidth, width); x++) {
                    int pindex = x + (int) a.ys[i] * width;
                    if (data[pindex] == color && goodPixels == 0) {
                        continue;
                    }
                    goodPixels++;
                    int y = (int) a.ys[i];
                    int r = (getOrigPixel(x, y) & 0xff0000) >> 16;
                    int g = (getOrigPixel(x, y) & 0xff00) >> 8;

                    //caa.add(getOrigPixel(x,y));
                    //caa.diff(getOrigPixel(x, y));
                    
                    if (r > 0 && g > 0) {
                        //avgG.add(g);
                        //avgGG.add(g*g);
                        //avgR.add(r);
                        //avgRR.add(r * r);
                        avgRG.add((double) r / g);
                    }

                    if (data[pindex] == color) {
                        int laneWidth = x - (int) a.xs[i];
                        if (laneWidth > 2) {
                            avgLllWidth.add(x - a.xs[i] + 2);
                        }
                        break;
                    }

                    // TMP- debug coloring.  Can remove check for 0xff000000 above
                    data[pindex] = 2;
                }
            }
        }
        // consider segment good if over 0.3 of the rows have good color, width, and boundary
        return goodLine > a.count * 0.3;
    }

    boolean looksLikeLine_dummy(RunningLeastSquares a, RunningLeastSquares b) {
        return false;
    }

    boolean looksLikeLine_PixelDistance(RunningLeastSquares a, RunningLeastSquares b) {
        int ax = (int) ((height - a.intercept()) / a.slope());
        int bx = (int) ((height - b.intercept()) / b.slope());
        double dist = Math.abs(ax - bx);
        //System.out.println(dist);
        return dist >= (0.5 * width / 160) && dist < (2.5 * width / 160);  // TODO- proper ||line distance, eliminate raw pixel use
    }

    
    // Tried to collect a historgram of lane intercept data rather than a average. 
    // worked worse. 
    class HistogramPeakDetector { 
    	double [] values = new double[5000];
    	double [] weights = new double[values.length];
    	double [] hist = new double[500];
    	int index;
    	double min, max;
    	void add(double x, double w) {
    		if (index == 0) 
    			min = max = x;
    		if (index < values.length - 1) {
    			weights[index] = w;
    			values[index++] = x;
    		}
    		if (x < min) min = x;
    		if (x > max) max = x;
    	}
    	void clear() { 
    		min = width / 2;
    		max = width * 2; 
    		index = 0;
    	}
    	double calc() {
    		Arrays.fill(hist, 0);
    		int maxhist = 0;
    		for(int i = 0; i  < index; i++) {
    			double x = values[i];
    			int hi = (int)Math.round((x - min) / (max - min) * (hist.length - 1));
    			int blur = 8;
    			for(int hi1 = hi - blur; hi1 < hi + blur; hi1++) {
    				if (hi1 >= 0 && hi1 < hist.length) { 
    					hist[hi1] += weights[i];
    					if (hist[hi1] > hist[maxhist])
    						maxhist = hi1;
    				}
    			}
    		}
    		return min + (max - min) * maxhist / (hist.length - 1) ;
    	}
    }
    HistogramPeakDetector lanePosHist = new HistogramPeakDetector();
    
    void addToLanePosHist(double m, double b, double w) { 
    	double x = (height - b) / m;
    	lanePosHist.add(x, w);
    }
    
    
    public void emphasizeLaneLines() {
        int multiplier = 20;
        // compare each lane segment found to every other lane segment, look for pairs
        // that match looksLikeLine() criterion.  For ones that match, re-add them x10
        // to the line averages to emphasize these high-quality segments.
        // TODO- this apparently takes a bunch of CPU time.  Maybe sort the segments
        // by intercept to speed search?


        for (int x = 0; x < segmentsIndex; x++) {
            if (looksLikeLine_widthPlusBounded(segments[x])) {
                avgSlope.add(segments[x].slope(), segments[x].count * multiplier);
                int interceptOffset = (int) -(Math.round(avgLllWidth.calculate() / 2) * segments[x].slope());
                avgIntercept.add(segments[x].intercept() + interceptOffset, segments[x].count * multiplier);
                colorSegment(segments[x], Color.magenta.getRGB());
                addToLanePosHist(segments[x].slope(), segments[x].intercept() + interceptOffset, 
                		segments[x].count * multiplier);             
            }
            /*for (int y = x + 1; y < segmentsIndex; y++) {
            if (looksLikeLine_dummy(segments[x], segments[y])) {
            avgSlope.add(segments[x].slope(),segments[x].count * multiplier);
            avgSlope.add(segments[y].slope(), segments[y].count * multiplier);
            avgIntercept.add(segments[y].intercept(), segments[y].count * multiplier);
            colorSegment(segments[x], Color.magenta.getRGB());
            colorSegment(segments[y], Color.magenta.getRGB());
            }
            }*/
        }

        rgColorRatio.add(avgRG.calculate(), avgRG.count);
        /*System.out.println(String.format("R: %.0f %.2f G %.0f %.2f  R/G %.4f-%.4f %.2f", avgR.calculate(),
        avgR.calculate() - Math.sqrt(avgRR.calculate()), avgG.calculate(),
        avgG.calculate() - Math.sqrt(avgGG.calculate()), rgColorRatio.low(),
        rgColorRatio.hi(), rgColorRatio.curFocus ));
         */

        avgR.clear();
        avgRR.clear();
        avgG.clear();
        avgGG.clear();
        avgRG.clear();

        //makeHistGrid();
    }

    void foundGoodSegment() {
        colorSegment(ls, color);
        if (ls.count > 50) {
            //showLineImageContext();
        }
        addSegmentToAverages();
        clearSegment();
    }

    class WhitenessHistogram {

        int[] buckets = new int[80];
        int max, count;
        int mark;

        void add(int pixel) {
            int r = (pixel & 0xff0000) >> 16;
            int g = (pixel & 0xff00) >> 8;
            int b = (pixel & 0xff);
            if (Math.abs(r - g) < r / 5) {
                int i = (r + g) / 2 * buckets.length / 256;
                buckets[i]++;
                if (buckets[i] > max) {
                    max = buckets[i];
                }
                count++;
            }
        }

        void print() {
            int height = 10;
            int mi = mark * buckets.length / 256;
            for (int h = height - 1; h >= 0; h--) {
                for (int i = 0; i < buckets.length; i++) {
                    if (buckets[i] > max * h / height) {
                        System.out.print(i == mi ? "O" : "X");
                    } else {
                        System.out.print(" ");
                    }
                }
                System.out.println("|");
            }
            System.out.println("");
        }

        void clear() {
            count = 0;
            max = 0;
            for (int i = 0; i < buckets.length; i++) {
                buckets[i] = 0;
            }
        }

        int getThreshold(double percent) {
            int target = (int) ((count - buckets[buckets.length - 1]) * percent);
            int sum = 0;
            int i;
            for (i = buckets.length * 9 / 10 - 1; i >= 0; i--) {
                sum += buckets[i];
                if (sum > target) {
                    break;
                }
            }
            return i * 256 / buckets.length;
        }
    }
    WhitenessHistogram whist = new WhitenessHistogram();

    // top level function for following a canny edge from the specified x,y starting point,
    //
    void doGroup(int x, int y) {
        int pix = getPixel(x, y);
        if ((pix & DONE_MASK) == DONE_MASK) {
            return;
        }

        whist.add(getOrigPixel(x, y));

        clearSegment();
        
        if (pix > cannyHighThreshold)
        	processPixel(x, y, cannyLowThreshold);
        else 
        	setPixel(x, y, DONE_MASK);
      
        /*  TODO- look for small broken lane markers farther off in the horizon
        for(int i = 0; i < ls.count - 1; i++) {
        	if (Math.abs(ls.xs[i] - ls.xs[i + 1]) > 1 ||
        			Math.abs(ls.ys[i] - ls.ys[i + 1]) > 1)
        			break;
        	if (i == ls.count - 2) { 
        		System.out.println("Found a dot!");
        		//colorSegment(Color.black);
        		foundGoodSegment();
        	}
        }*/
        
        // finish off an incomplete segment
        if (goodSegment) {
            foundGoodSegment();
        }
    }

	int getPixel(int x, int y) {
		return data[x + y * width];
	}

	void setPixel(int x, int y, int v) {
		data[x + y * width] = v | DONE_MASK;
	}

	void colorSegment(RunningLeastSquares s, int c) {
		for (int i = 0; i < s.count; i++) {
            setPixel((int) s.xs[i], (int) s.ys[i], c);
        }
    }

    // ls contains a good segment, add it to averages and segment array
    //
    void addSegmentToAverages() {
        avgSlope.add(ls.slope(), ls.count);
        avgIntercept.add(ls.intercept(), ls.count);
        this.addToLanePosHist(ls.slope(), ls.intercept(), ls.count);
        if (segmentsIndex < segments.length) {
            segments[segmentsIndex++] = ls;
            ls = new RunningLeastSquares(minLength);
        }
    }

    void clear() {
        segmentsIndex = 0;
        avgSlope.clear();
        avgIntercept.clear();
        lanePosHist.clear();
        clearSegment();
    }

    // recurse and find all attached pixels to current x,y, adding found pixes to xs/ys arrays.
    boolean processPixel(int x, int y, int thresh) {
    	int pixel = getPixel(x, y);
        if ((pixel & DONE_MASK) == DONE_MASK) {
            return false;
        }
        setPixel(x, y, DONE_MASK);
        if (ls.count == maxLength || y < vanishY || y < ystart(x) || y >= yend(x) || pixel < thresh) {
            return false;
        }

      
        ls.add(x, y);
        //if ((++debugPixelCount % 50) == 0) {
        //    debugNotes.add(x, y, String.format("%d", debugPixelCount));
        //}

        double rms = ls.rmsError();
        double pointErr = Math.abs(ls.err(x, y));
        double slope = ls.slope();
        
        if (
                ls.count >= minLength && ls.err(vanishX, vanishY) < vanishErr
                && slope >= minSlope && slope <= maxSlope
                && rms < maxErr && pointErr < maxPointErr &&
                hasGoodLaneColor(x, y)) {
            if (ls.count == minLength) {
                ls.grow(maxLength);
            }
            goodSegment = true;
            setPixel(x, y, Color.red.getRGB() | DONE_MASK);
        } else if (goodSegment) {
            ls.removeLast();
            foundGoodSegment();
        }

        if (debugMarkup) {
            // color current pixel to show why segment is being rejected.
        	if (!hasGoodLaneColor(x, y)) {
                setPixel(x, y, Color.gray.getRGB());
        	} else if (ls.err(vanishX, vanishY) > vanishErr) {
                setPixel(x, y, Color.cyan.getRGB());
            } else if (slope < minSlope || slope > maxSlope) {
                setPixel(x, y, Color.green.getRGB());
            } else if (rms >= maxErr) {
                setPixel(x, y, Color.blue.getRGB());
            } else if (pointErr >= maxPointErr) {
                setPixel(x, y, Color.blue.getRGB());
           } else if (ls.count < minLength) {
               setPixel(x, y, Color.pink.getRGB());
           }
        }
        
        for (int ny = y + 1; ny >= y - 1; ny--) {
        	for (int nx = x - 1; nx <= x + 1; nx++) {
        		if (nx > 0 && nx < width && ny > 0 && ny < height) {
        			if (processPixel(nx, ny, thresh)) {
        				//	return true;  // breaks up line more than expected.  Possibly sort
        				// segment points in y direction when done to increase order instead?
        			}
        		}
        	}
        }

        return true;
    }
}
