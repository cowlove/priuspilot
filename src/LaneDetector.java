

import java.awt.Color;
import java.awt.Graphics2D;

//import math.*;
//import math.Geometry.*;


public class LaneDetector {

    public class LaneData {

        public int weight = 0;
        public int valid = 0; // recent valid frames, used to calculate focus
        public RunningLeastSquares slope = new RunningLeastSquares(5);  // running avg slope, used for focus
        public RunningLeastSquares intercept = new RunningLeastSquares(5); // avg intercept, used for focus
        //public double focus; // focus, 1.0 -> fullFocus
        public double position;  // instantaneous value, valid when weight > 0
       // public double targetSlope = 0;
        //public double targetSlopeWidth = 0;
        AutoFocusParameter afSlope;
        public double currentSlope;

    }
    // inputs
    public double targetSlope = 1.2;
    public double targetSlopeErr = 0.9;
    public int fullFocusDelay = 15;
    public double fullFocus = 0.20;
    public int fullFocusAttack = 4;   // factor of attack/decay time for focus
    public Geometry.Point startingVanish = new Geometry().new Point(0.5, 0.15);
    public double vanishErr = 0.30;
    public double vanishFullFocus = 0.15;
    public double colorThresholdPercent;  // take whitest % of the pixels as colorThreshold
    public int colorThreshold = 100;
    
    public ScanZonePair zones = new ScanZonePair();
      
     // output variables
    public LaneData left;
    public LaneData right;
    public Geometry.Point currentVanish = new Geometry().new Point(startingVanish.x, startingVanish.y);
    private int width, height;
    
    public LaneDetector(int w, int h) {
        width = w;
        height = h;
        leftLine = new PixelGroup(width, height);
        rightLine = new PixelGroup(width, height);
        reset();
    }
    public PixelGroup leftLine, rightLine;

    public void reset() {
        left = new LaneData();
        left.afSlope = new AutoFocusParameter(-targetSlope, targetSlopeErr, fullFocus, 200 * fullFocusDelay, fullFocusDelay);
        right = new LaneData();
        right.afSlope = new AutoFocusParameter(+targetSlope, targetSlopeErr, fullFocus, 200 * fullFocusDelay, fullFocusDelay);
        currentVanish = new Geometry().new Point(startingVanish.x, startingVanish.y);
    }

    void prepareLaneData(LaneData ld, double targSlope) {
       // ld.focus = 1.0 - (1.0 - fullFocus) * ld.valid / fullFocusDelay;
       // ld.targetSlope = ld.slope.predict(now) * (1.0 - ld.focus * ld.focus) + targSlope * ld.focus * ld.focus;
       // ld.targetSlopeWidth = targetSlopeErr * ld.focus;
    }    
    
    //pixel count normalized from a 160 pixel-width image. 
    int normalizedPixelCount(int p) {
    	return p * 160 / width;
    }
   
    RunningAverage posAvg = new RunningAverage(10);
    // main routine for processing one side's data.  Called twice, once for 
    // PixelGroup left and once for PixelGroup right.
    //
    void processPixelGroup(int[] data, OriginalImage orig,
            PixelGroup pg, LaneData ld, double targSlope) {

        pg.clear();
        pg.set(data, ld.afSlope.low(), ld.afSlope.hi(), 
               currentVanish.x, currentVanish.y, vanishErr * ld.afSlope.curFocus, colorThreshold);
        pg.debugMarkup = true;
        pg.orig = orig;
        pg.whist.clear();
        pg.whist.mark = colorThreshold;
        
        // Scan over only the scanzone area.   Come in from either side
        // to keep the shoddy line follwing implementation in pg.doGroup()
        // happy.
        if (targSlope < 0) {
            for (int x = 0; x < Math.min(width, pg.vanishX); x++) {
                    int ystart = pg.ystart(x);
                    int yend = pg.yend(x); //pg.yend(x);
                    for (int y = yend - 1; y >= ystart; y--) {
                        pg.doGroup(x, y);
                    }
            }
        } else {
            for (int x = width; x >= Math.max(0, pg.vanishX); x--) {
        	int ystart = pg.ystart(x);
        	int yend = pg.yend(x); 
        	for (int y = yend - 1; y >= ystart; y--) {
                   pg.doGroup(x, y);
                }
            }
        }
        
        /*
        System.out.printf("avg color diff %d\n", (int)pg.caa.avgDiff.calculate());
        pg.caa.avgDiff.clear();
        int xstart = 0, xend = width, xinc = 1;
        if (targSlope >= 0) { 
        	xstart = width; xend = 0; xinc = -1;
        }
        for (int x = xstart; x != xend; x += xinc) {
            int ystart = pg.ystart(x);
            int yend = pg.yend(x); //pg.yend(x);
            for (int y = yend - 1; y >= ystart; y--) {
                if (pg.caa.diff(pg.getOrigPixel(x, y)) < 10) {
                	pg.setPixel(x, y, 0xff);
                }
            }
        }
        */
        //pg.caa.avgDiff.clear();
        
        //pg.whist.print();
        colorThreshold = pg.whist.getThreshold(colorThresholdPercent);
        //System.out.println("color threshold " + colorThreshold);
        pg.emphasizeLaneLines();

        ld.weight = pg.avgSlope.count;
        if (ld.weight > normalizedPixelCount(15)) {
            ld.currentSlope = pg.avgSlope.calculate();
            ld.slope.add(now, ld.currentSlope, ld.weight);
            ld.intercept.add(now, pg.avgIntercept.calculate(), ld.weight);
            ld.valid = Math.min(ld.valid + fullFocusAttack, fullFocusDelay);
            //ld.position = pg.lanePosHist.calc() / width - 0.5;
            ld.position = ((double) height - pg.avgIntercept.calculate()) / ld.currentSlope / width - 0.5;
            ld.afSlope.add(ld.currentSlope, ld.weight);

        } else {
            ld.valid = Math.max(0, ld.valid - 1);
            ld.currentSlope = Double.NaN;
            ld.weight = 0;
            ld.afSlope.add(0, 0);
        }
        
        if (ld.valid == 0) {
            ld.slope.clear();
            ld.intercept.clear();
        }

        ;
        // set up scanZone showing region to scan for next iteration.
        int handed = (int) (targSlope / Math.abs(targSlope));
        Geometry.Point vp = new Geometry().new Point((int) (width * currentVanish.x),
                (int) (height * currentVanish.y));
        double verr = vanishErr * ld.afSlope.curFocus * width;
        double botSlope, topSlope;
        ScanZonePair.ScanZone sz;
        if (handed < 0) {
        	topSlope = ld.afSlope.hi();
        	botSlope = ld.afSlope.low();
        	sz = zones.lsz;
        } else {
           	topSlope = ld.afSlope.low();
        	botSlope = ld.afSlope.hi();
        	sz = zones.rsz;
        }
        
        Geometry.Line l = Geometry.lineFromSlopePointDistance(topSlope, vp,-verr * handed);
        sz.m1 = l.m;
        sz.b1 = (int)l.b;
        l = Geometry.lineFromSlopePointDistance(botSlope, vp,verr * handed);
        sz.m2 = l.m;
        sz.b2 = (int)l.b;
    }
    
    long now;
    public void process(int[] data, OriginalImage orig, long ms) {
    	double validPct = Math.min(1.0, (double)(left.valid + right.valid) / fullFocusDelay);
        double vanishFocus = 1.0 - (1.0 - vanishFullFocus) * validPct;
        now = ms;
        // calculate the intersection of L&R lines, the new vanishing point
        double a1 = -left.slope.predict((double)now), c1 = -left.intercept.predict((double)now);
        double a2 = -right.slope.predict((double)now), c2 = -right.intercept.predict((double)now);

        // If one side is missing, concoct a line that intersects the other, valid
        // scanzone line at the middle of the screen.   
        // TODO- there's a bug here.  With the threshold set too low (0.05), this code kicks in and 
        // sets the scan zone, but then doesn't set it the next iteration, but slope isn't yet set
        
        if (left.slope.count <= right.slope.count * 0.2) { 
        	a1 = -a2;
        	int y = (int)(-a2 * width / 2 - c2);
        	c1 = -y - (width / 2) * a1;
        } else if(right.slope.count <= left.slope.count * 0.2) { 
        	a2 = -a1;
        	int y = (int)(-a1 * width / 2 - c1);
        	c2 = -y - (width / 2) * a2;        	
        }
        
        double vanishX = (c2 - c1) / (a1 - a2) / width;
        double vanishY = (c1 * a2 - c2 * a1) / (a1 - a2) / height;
        if (a1 -a2 == 0) {
        	vanishX = startingVanish.x;
        	vanishY = startingVanish.y;
        }
        if (vanishFocus > 0.3) {
            currentVanish.x = startingVanish.x * vanishFocus + vanishX * (1.0 - vanishFocus);
            currentVanish.y = startingVanish.y * vanishFocus + vanishY * (1.0 - vanishFocus);
        } else {
            currentVanish.x = vanishX;
            currentVanish.y = vanishY;
        }

        prepareLaneData(left, -targetSlope);
        prepareLaneData(right, targetSlope);
        /*
        double scanGap = (1 - currentVanish.y) / (right.targetSlope + right.targetSlopeWidth)
                    - (1 - currentVanish.y) / (left.targetSlope - left.targetSlopeWidth);
        double maxScanGap = 2.0;
        if (scanGap > maxScanGap) {
            // defocus the shallowest (ie- farthest away from center) scanzone
            // to narrow the gap.  TODO- could defocus suspect scanzone by just the right
        	// amount, but a fixed .4 seems to work fine, and leaves a very visible cue
        	// as to what happened to the scanzone. 
            if (Math.abs(right.targetSlope) > Math.abs(left.targetSlope))
                left.targetSlopeWidth += .4;
            else
                right.targetSlopeWidth += .4;
            //(1 - currentVanish.y) / (scanGap - maxScanGap);
        }
		*/

        leftLine.debugNotes.clear();
        leftLine.debugPixelCount = 0;
        processPixelGroup(data, orig, leftLine, left, -targetSlope);
        rightLine.debugNotes.clear();
        rightLine.debugPixelCount = leftLine.debugPixelCount;
        processPixelGroup(data, orig, rightLine, right, targetSlope);
        
        //if (count++ % 100 == 0) 
        //	System.out.println(String.format("left ct=%d, right ct=%d", leftLine.colorThreshold, rightLine.colorThreshold));
    }
    int count = 0;
    
    public void writeDebugText(Graphics2D g2, int scale) {
        leftLine.debugNotes.writeText(g2, scale);
        rightLine.debugNotes.writeText(g2, scale);

    }
}
