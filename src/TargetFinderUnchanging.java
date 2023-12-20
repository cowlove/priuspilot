import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
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
	BufferedImageDebugDisplay dd = null;

	int frameCount = 0;

	@Override
	void reset() { 
		frameCount = 0; 
		history = null;
	}

	@Override
	Rectangle []findAll(OriginalImage oi, Rectangle rec) {
		return new Rectangle[] {rec};
	}
	Rectangle []findAllNO(OriginalImage oi, Rectangle rec) {
		c.zones.height = rec.height;
		c.zones.clear();
		if (dd == null) {
			BufferedImageDisplay.nextX = 640;
			BufferedImageDisplay.nextY = 20;
			//dd = new BufferedImageDebugDisplay(rec.width, rec.height);
		}
		if (history == null) 
			history = new float[rec.width * rec.height];
		setCanny(param);
		c.zones.lsz.m1 = c.zones.lsz.m2 = Double.NaN;
		super.findAll(oi, rec); // sets up sa, canny, width, height members.
		final int debug1 = Main.debugInt("TFU1", 0); 
		if (dd != null) { 
			for(int x = 0; x < rec.width; x++)
				for(int y = 0; y < rec.height; y++) {
					dd.setPixel(x, y, oi.getPixelABGR(x + rec.x, y + rec.y));
				}
			//dd.display();
		}

		// Filter for magnitudes perpendicular to the vanishing point lines 
		for(int y = 0; y < sa.height; y++) { 
			for(int x = 0; x < sa.width; x++) { 
				final int i = x + y * sa.width;
				double gdir = Math.atan2(c.xGradient[i], c.yGradient[i]);
				double pdir = Math.atan2(0, 1);
				double cosa = Math.cos(gdir - pdir + Math.PI / 2);
				c.results.gradResults[i] = (float)(	
					c.results.gradResults[i] * cosa * cosa); 
				pdir = Math.atan2(0, 1);
				cosa = Math.cos(gdir - pdir + Math.PI / 2);
				c.results.gradResults[i] += (float)(	
					c.results.gradResults[i] * cosa * cosa); 
				if (dd != null)
					dd.setPixelGrey(x, y, (byte)c.results.gradResults[i] * 10);
			}
		} 
		Rectangle r = rec;
		float cdata[] = c.results.gradResults;

		GaussianKernel gk = new GaussianKernel(1, 10, rec.width, r.height);
		gk.blur(cdata);

		// find best line of horizontal symmetry
		int bestSymCount = 0, bestSymX = 0; 
		for(int x = 0; x < r.width; x++) { 
			int symCount = 0; // sym pixels found
			for (int x1 = 1; x1 < r.width / 2; x1++) {
				for(int y = 0; y < r.height; y++) { 
					int xl = x - x1;
					int xr = x + x1;
					if (xl > 0 && xr < r.width && y > 0) {
						if (cdata[y * sa.width + xl] != 0 && cdata[y * sa.width + xr] != 0)
							symCount++;
					}
				}
			}
			if (symCount > bestSymCount) {
				bestSymCount = symCount;
				bestSymX = x;
			}
		}

		// add in historical frames for stability and signal strength		
		for(int i = 0; i < r.width * r.height; i++) {
			cdata[i] += history[i];
		}

		// using our newfound line of vertical symmetry,
		// suppress stuff that isn't symmetric 
		float maxValue = 0;
		for(int y = 0; y < r.height && y < r.height; y++) { 
			for(int x = 1; x < r.width / 2; x++) { 
				int xl = bestSymX - x;
				int xr = bestSymX + x;
				if (xl >= 0 && xr < r.width && 
					xr < sa.width && y < sa.height && y >= 0)  {
					//float v = Math.min(cdata[y * sa.width + xl], cdata[y * sa.width + xr]);
					float v = (float)Math.sqrt(cdata[y * sa.width + xl] * cdata[y * sa.width + xr]);
					cdata[y * sa.width + xl] = cdata[y * sa.width + xr] = v;
					//if (cdata[y * sa.width + xl] == 0 || cdata[y * sa.width + xr] == 0)
					//	cdata[y * sa.width + xl] = cdata[y * sa.width + xr] = 0;
					maxValue = Math.max(cdata[y * sa.width + xl], maxValue);
					maxValue = Math.max(cdata[y * sa.width + xr], maxValue);
				}  
			}
		}

		// debug markup the line of vertical symmetry 
		for(int y = r.y; y < r.y + r.height && y < r.height; y++) { 
			if (y >= 0 && y < r.height) {
				cdata[y * sa.width + bestSymX] = 0;
			}
		}

		// look for the best pair of symmetric vertical lines, assume they are the 
		// outside vertical edges of a truck
		double bestScore = 0;
		int bestX = 0;
		for(int x = 10; x < r.width / 2; x++) { 
			int xl = bestSymX - x;
			int xr = bestSymX + x;
			double score = 0;
			for(int y = 0; y < r.height && y < r.height; y++) { 
				int i = y * sa.width;
				if (xl >= 0 && xr < r.width && 
					xr < sa.width && y < sa.height && y >= 0)  {
					score += cdata[i + xr] + cdata[i + xl];
					double value = 1;//+ Math.sqrt((double)x);
					if (cdata[i + xr] > 0) score += value;
					if (cdata[i + xl] > 0) score += value;
					if (score > bestScore) {
						bestScore = score;
						bestX = x;
					}
				}
			}
			for(int y = 0; y < r.height && y < r.height; y++) { 
				int i = y * sa.width;
				if (xl >= 0 && xr < r.width && 
					xr < sa.width && y < sa.height && y >= 0)  {
				}
			}
		}

		for(int i = 0; i < r.width * r.height; i++) {
			history[i] = cdata[i] * 0.95F;
		}

		// now that we've found outer boundaries, chop off the bottom
		double totalSum = 0;
		for(int y = 0; y < r.height && y < r.height; y++) { 
			for(int x = bestSymX - bestX; x <= bestSymX; x++) {
				if (y >= 0 && y < r.height && x >= 0 && x < r.width) { 
					totalSum += cdata[y * r.width + x];
				}
			}
		}
		double partialSum = 0;
		int bottomY = 0;
		final double vertSnipPct = Main.debugDouble("TFVSNIP", 0.04);
		for(int y = r.y + r.width - 1; y >= r.y && y >= 0; y--) { 
			for(int x = bestSymX - bestX; x <= bestSymX; x++) {
				if (y >= 0 && y < r.height && x >= 0 && x < r.width) { 
					partialSum += cdata[y * r.width + x];
					if (bottomY == 0 && partialSum > totalSum * vertSnipPct)
						bottomY = y;
				}
			}
		}

		// mark up the best score lines
		for(int y = r.y; y < r.y + r.height && y < r.height; y++) { 
			if (y >= 0 && y < r.height) {
				cdata[y * sa.width + bestSymX + bestX] = maxValue;
				cdata[y * sa.width + bestSymX - bestX] = maxValue;
			}
		}
		for(int x = 0; x < r.width; x++) { 
			cdata[bottomY * sa.width + x] = maxValue;
		}
		if (debug1 == 2) { 
			gp.startNew();
			gp.add3DGridF(c.	results.gradResults, sa.width, sa.height, true);
			gp.draw("set palette defined (-1 0 0 0, 1 1 1 1)\n");
		}

		final int border = Main.debugInt("TFBORDER", 0);
		bestX += border;
		bottomY += border;
		Rectangle []ra = {new Rectangle(r. x + bestSymX - bestX, r.y, bestX * 2, bottomY)};
		if (bestX < 2)
			ra = null;

		if (frameCount++ > 30) 
	        return ra;
		else 
			return null;
	}

	float []history = null;
	boolean [] smask = null;
	LazyHslConvert hsl;
}

