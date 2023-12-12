import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;


//import math.Average;
//import math.RunningAverage;
//import math.RunningLeastSquares;
//import math.JamaLeastSquaresFit;
//import math.Geometry;
//import math.Geometry.*;


class TargetFinderUnchanging extends TargetFinder {
	TargetFinderUnchanging(int w, int h) {
		super(w, h);
		hsl = new LazyHslConvert(w, h);
	} 
	final int fudge = 0;
	byte [] hslPic = null;
	GnuplotWrapper gp = new GnuplotWrapper();
	GnuplotWrapper gp2 = new GnuplotWrapper();



	@Override
	Rectangle []findAll(OriginalImage oi, Rectangle rec) {
		c.zones.height = rec.height;
		c.zones.clear();
		setCanny(param);
		c.zones.lsz.m1 = c.zones.lsz.m2 = Double.NaN;
		super.findAll(oi, rec); // sets up sa, canny, width, height members.
		final int debug1 = Main.debugInt("TFU1", 0); 
		if (debug1 == 1) { 
			gp.startNew();
			gp.add3DGridF(c.results.gradResults, sa.width, sa.height, true);
			gp.draw("set palette defined (-1 0 0 0, 1 1 1 0)\n");
		}
		// Filter for magnitudes perpendicular to the vanishing point lines 
		for(int y = 0; y < sa.height; y++) { 
			for(int x = 0; x < sa.width; x++) { 
				final int i = x + y * sa.width;
				double gdir = Math.atan2(c.xGradient[i], c.yGradient[i]);
				double pdir = Math.atan2(0, 1);
				double cosa = Math.cos(gdir - pdir + Math.PI / 2);
				c.results.gradResults[i] = (float)(	
					c.results.gradResults[i] * cosa * Math.abs(cosa)); 
				//pdir = Math.atan2(0, 1);
				//cosa = Math.cos(gdir - pdir + Math.PI / 2);
				//c.results.gradResults[i] += (float)(	
				//	c.results.gradResults[i] * cosa * Math.abs(cosa)); 
			}
		} 
		if (debug1 == 2) { 
			gp.startNew();
			gp.add3DGridF(c.results.gradResults, sa.width, sa.height, true);
			gp.draw("set palette defined (-1 0 0 1, 0 0 0 0, 1 1 1 0)\n");
		}

		Rectangle []ra = {rec};
        return ra;
	}

	boolean [] smask = null;
	LazyHslConvert hsl;
}

