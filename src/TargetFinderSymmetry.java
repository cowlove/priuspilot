import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;


//import math.Average;
//import math.RunningAverage;
//import math.RunningLeastSquares;
//import math.JamaLeastSquaresFit;
//import math.Geometry;
//import math.Geometry.*;


class TargetFinderSymmetry extends TargetFinder {

	TargetFinderSymmetry(int w, int h) {
		super(w, h);
		hsl = new LazyHslConvert(w, h);
	} 
	final int fudge = 0;
	byte [] hslPic = null;
	
	@Override
	Rectangle []findAll(OriginalImage oi, Rectangle rec) {
		super.findAll(oi, rec); // sets up sa, canny, width, height members.
		
		//hslPic = new byte[sa.height * sa.width * bpp];
		//copyin(bb, rec, hslPic);		
		//hsl = new LazyHslConvert(sa.width, sa.height);
		//hsl.convertHsl(hslPic, 0, 0, sa.width, sa.height);

		int cradius = 4;
        cmask = makeCannyRadiusMask(canny, sa.width, sa.height, cradius);


        // run findSymmetry on the actual canny lines?
        //drawCannyLines(orig);
        //smask = findSymmetry(orig, cmask, cradius + 1, 30, 20);


		
		//applyMask(cmask, orig);
		//applyMask(svalid, orig);
		//drawVertLine(orig, bestSymX);
//		drawSymX(orig);
//		drawCannyLines(orig);
//		copyout(orig, oi, rec);
		return null;
	}

	boolean [] makeFullMask(int w, int h) { 
		boolean [] r = new boolean[w * h];
		for(int i = 0; i < r.length; i++)
			r[i] = true;
		return r;
	}
	
	int pixError(byte [] pic, int x1, int x2, int y) {
		int err = 0;
		int i1 = saindex(x1, y);
		int i2 = saindex(x2, y);
//		for(int b = 0; b < bpp; b++) { 
//			int diff = pic[i1 * bpp + b] - pic[i2 * bpp + b];
//			if (b == 0) { 
//				if (diff > 127) diff = 255 - diff;
//				
//			}
//			err += diff * diff;
//		}
		return (int)Math.round(Math.sqrt(err));
	}

	int bestSymScore = 0;
	int [] symScores = null;

	boolean [] findSymmetry(byte [] pic, boolean [] mask, int minRadius, int maxRadius, int maxErr) {
		boolean [] result = new boolean[sa.width * sa.height];
		symScores = new int[sa.width * sa.height];
		bestSymScore = maxRadius - minRadius * 2;
		RunningAverage av = new RunningAverage(minRadius);
		for(int x = 0; x < sa.width; x++) {
			for(int y = 0; y <  sa.height; y++) {
				int count = 0;
				av.clear();
				for(int r = minRadius; r <= maxRadius; r++) { 
					if (savalid(x - r, y) && savalid(x + r, y)) { 
						int err = pixError(pic, x - r, x + r, y);
						av.add(err);
						if (av.calculate() > maxErr)
							break;
						if (mask == null || (mask[saindex(x - r, y)] && mask[saindex(x + r, y)])) { 
							if (err < maxErr) { 	
								count++;
								result[saindex(x - r, y)] = result[saindex(x - r, y)] = true;
							}
						}
					}
				}
				symScores[x + y * sa.width] = count;
			}
		}
		return result;
	}

	void drawSymX(byte[] pic) {
		for(int x = 0; x < sa.width; x++) { 
			for(int y = 0; y < sa.height; y++) { 
				int p = (symScores[x + y * sa.width] * 0xff / bestSymScore) * 30;
				if (p > 255) p = 255;
				
//				pic[(x + y * sa.width) * bpp]  = 0;//(byte)p;
//				pic[(x + y * sa.width) * bpp + 1]  = 0;//(byte)p;	
//				pic[(x + y * sa.width) * bpp + 2]  = (byte)p;//(byte)p;
			}
		}
	}

	boolean [] smask = null;
	LazyHslConvert hsl;
}

