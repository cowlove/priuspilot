
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;


// TODO- keep a copy of the entire template image, so that it can be manually/interactively
// adjusted and sized later
//
// TODO- allow for non-square templates?  
//
// TODO - allow interactive/manual trim/movement of template selection
// TODO - more sophisticated assessment of quality, not just raw error value
// TODO - investigate correlation and normalized correlation comparisons
// 
// TODO - use variance of target pic, or running variance, to disqualify 
//        meaningless matches to blank or plain backgrounds
// TODO - Let move to better pass a configurable number of worse scores to find a 
//        new best score. 
// 
// TODOs added after pretty successful drive back from PDX, had very good success at 10FPS
// night operations, decided that detection quality, stability and precision is much more 
// critical than a raw focus on high frame rate. 
//
// TODO change PID running squares structures to have a history based on a time value, not a raw
// frame count.  The PID isn't tuned very well when frame rate slows down to 10. 
//
// TODO plan out a new frame data flow plan from scratch - 
//   1) mmap
//   2) first copy - copy into java direct buf, with rotation and windowing
//   3) second copy - out of java direct buf into int array()
//   4) accesses for comparisons - could be done in one read, with good lazy HSL comparison 

//   (If template comparison is finally moved to C++, could read directly from mmap buf when doing 
// rectangular lazy HSL conversion, doing flip/rotate by coordinate transform) 

//  
// TODO fix FrameGrabber.java double buffering bug
// 
// TODO make video grabber thread block waiting for free buffer rather than continually shuffling new frames
// 
// TODO figure out a running normalization and variance algorthm  squared pixel error = ((t - a) - (p - b))2
// where t and p are template/picture pixels, and a and b are the average normalizing values.  if a is the difference
// in average values. 
// (t - p - a)(t - p - a)  = tt -pt -ta - tp + pp + pa -ta + pa + aa = 
//	tt -2tp + pp - 2ta + 2pa + aa
// This could be calculated by summing tt, tp, pp, t and p for all pixels, then using the final averages to calculate
// 
// TODO - add proper windowing code into the jni interface, consider upgrading to windows portion of 640x480 camera feed.
//   investigate using v4l2 instead of v4l, maybe camera can do windowing
// 
// TODO - auto canny edge detection and target finder
// 
// TODO make serial interface bidirectional, tie in FP control to joystick - joystick back = cancel and aquire new target, start tracking but 
// disable steering.   joystick toggle = enable steering. 
//
// TODO - let arduino fiddle cruise control settings
//
// TODO - generalize PID controller a little bit, add new instance for cruise control setting
//
// TODO - add variance to FindResult, let code outside TemplateDetect analyze both score and var and decide if result is good. 
//  	Possibly do this by setting maxErr and minVar dynamically, perhaps based on the first few seconds of tracking.  
// 
// TODO - consider switching to _ARGB 4-byte picture encoding, use unused bit to lazily track HSL conversion, this would allow
// lazy HSL conversion to be done in the same memory read as the first error calculation that relies on it.   Definitely 
// pre-convert templates to HSL
//
// continue work on regression testing - Add logfile format specifiers - ex "%tdx,%tdy,%tds,%steer  # comment field %fps"

abstract class TemplateDetect {
	boolean active = true; // tmp- not used
	float threshold1 = 8F, threshold2 = 4F;
	float gaussianKernelRadius = 1.5F;
	int gaussianKernelWidth = 8;

	// returns a rectangle describing the provided FineResult, suitable for display on a screen
	Rectangle targetRect(FindResult f) { 
		int xs = (template.loc.width + f.scale);
		int ys = (template.loc.height + f.scale * template.loc.height / template.loc.width);
		return new Rectangle(f.x - xs / 2, f.y - ys / 2, xs, ys);
	}

	public class FindResult { 
		int x, y, scale, score = 0, var = 0;
		int compares = 0;
		FindResult(int x1, int y1, int s1) { x = x1; y = y1; scale = s1; score = 0; } 
		FindResult(int x1, int y1, int s1, int s2) { x = x1; y = y1; scale = s1; score = s2; } 
		FindResult(int x1, int y1, int s1, int s2, int v) { x = x1; y = y1; scale = s1; score = s2; var = v; } 
		FindResult(FindResult r) { this.copy(r); } 
		public FindResult() { score = 0; x = y = scale = 0; } 
		void copy(FindResult r) { x = r.x; y = r.y; scale = r.scale; score = r.score; compares = r.compares; var = r.var; }
		public FindResult copy() {
			return new FindResult(this);
			// TODO Auto-generated method stub
			
		} 
	}

	FindResult lastResult = null;
	class Tile { 
		int scale = 0;
		Rectangle loc;
		byte [] data;
	}
	Tile template = new Tile();

	FindResult setTemplate(OriginalImage oi, int x, int y, int xs, int ys) { 
		Rectangle r = new Rectangle(x - xs / 2, y - ys / 2, xs, ys);
		setTemplate(oi, r);
		return new FindResult(x, y, 0);	
	}	

	abstract void draw(OriginalImage oi, int rescale);
	abstract void setTemplate(OriginalImage oi, Rectangle r);
	abstract FindResult find(FindResult startAt, OriginalImage oi); 
	abstract void newFrame(OriginalImage oi);
	void setSearchDist(int x, int y, int s) { 
		searchDist = new FindResult(x, y, s, 0);
	}

	static final int WORST_SCORE = 1000000000;
	FindResult searchDist; 
}

class TemplateDetectRGB extends TemplateDetect {
	int width, height;
	int frame = 0;
	static final int bpp = 3;
	boolean debug = false;
	Rectangle searchArea = null;
	double [] mask = null;
	double maskDecay = 0.95;
	BufferedWriter f2 = null;
	LazyHslConvert hsl = null;
	byte [] picX = null;
	
	TemplateDetectRGB(int w, int h) { 
		hsl = null; //new LazyHslConvert(w, h); 
		width = w; 
		height = h; 
	} 
	
	Tile scaleTile(Tile in, int scale) { 
		int xs = in.loc.width + scale;
		int ys = in.loc.height + scale;
		//(int)Math.round(xs * in.loc.height / in.loc.width);
		if (xs < 2 || ys < 2)
			return null;

		Tile out = new Tile();
		out.loc = new Rectangle(0, 0, xs, ys);
		out.data = new byte[out.loc.width * out.loc.height * bpp];
		
		BufferedImage inImg = new BufferedImage(in.loc.width, in.loc.height, BufferedImage.TYPE_3BYTE_BGR);
		BufferedImage outImg = new BufferedImage(out.loc.width, out.loc.height, BufferedImage.TYPE_3BYTE_BGR);
		
		inImg.getWritableTile(0, 0).setDataElements(0, 0, in.loc.width, in.loc.height, in.data);
		Graphics2D g = outImg.createGraphics();
		AffineTransform at = AffineTransform.getScaleInstance((double)out.loc.width / in.loc.width,
				(double)out.loc.height / in.loc.height);
		g.drawRenderedImage(inImg, at);
		
		outImg.getTile(0, 0).getDataElements(0, 0, out.loc.width, out.loc.height, out.data);
		out.scale = scale;
		return out;
	}
	byte [] convertOI(OriginalImage oi) {
		return yuv2canny(oi);
	}

	CannyEdgeDetector c = new CannyEdgeDetector();
	
	byte [] yuv2canny(OriginalImage oi) { 
		byte [] p = new byte[width * height * bpp];
		c.threshold = 5.0F;
		c.setGaussianKernelRadius(0.8F);
		c.setGaussianKernelWidth(6);
		c.zones.lsz.m1 = Double.NaN;
		c.processData(oi, new Rectangle(0, 0, width, height));
		int [] cdata = c.getData();
		GaussianKernel gk = new GaussianKernel(.2, 6, width, height);

		int [] c2data = new int[width * height];
		//select for and copy only horizontal lines
		for(int y = 0; y < height; y++) {
			int continuous = 0;
			for(int x = 0; x < width; x++) {
				if (cdata[y * width + x] != 0) { 
					if (++continuous > 3) {
						for(int dx = x - continuous + 1; dx <= x; dx++) {
							c2data[y * width + dx] = cdata[y * width + dx];
						} 
					}
				} else {
					continuous = 0;
				}
			}
		}
		// copy for any copy only vertical lines
		for(int x = 0; x < width; x++) {
			int continuous = 0;
			for(int y = 0; y < height; y++) {
				if (cdata[y * width + x] != 0) { 
					if (++continuous > 3) {
						for(int dy = y - continuous + 1; dy <= y; dy++) {
							c2data[dy * width + x] = cdata[dy * width + x];
						} 
					}
				} else {
					continuous = 0;
				}
			}
		}
		gk.blur(c2data);
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) { 
				for(int i = 0; i < bpp; i++) {
					int pi = (y * width + x) * 3 + i;
					p[pi] = (byte)c2data[y * width + x]; 
				}
			}
		} 
		return p;
	}
	byte [] yuvToRgb(OriginalImage oi) {
		byte [] p = new byte[width * height * bpp];
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) { 
				int r[] = oi.getPixelRGBArray(x, y);
				int pi = (y * width + x) * 3; 
				for(int i = 0; i < r.length; i++) { 
					p[pi + i] = (byte)r[i];
				}
				int hsl[] = new int[3];
				OriginalImage.rgb2hsl(r[0], r[1], r[2], hsl);
				p[pi] = (byte)hsl[0];
				p[pi + 1] = (byte)hsl[1];
				p[pi + 2] = (byte)hsl[2];
			}
		}
		return p;
	}
	@Override
	void setTemplate(OriginalImage oi, Rectangle r) {
		byte [] p = convertOI(oi);
		if (hsl != null) 
			hsl.convertHsl(p, r.x, r.y, r.x + r.width, r.y + r.height);
		template.loc = new Rectangle(r);
		template.data = new byte[r.width * r.height * bpp];
		mask = new double[r.width * r.height];
		// TODO - move this iteration code out, do bounds checking vs width/height
		for(int x = 0; x < r.width; x++) { 
			for(int y = 0; y < r.height; y++) { 
				for(int b = 0; b < bpp; b++) {
					int pi = (r.x + x + (r.y + y) * width) * bpp + b;
					int ti = (x + y * r.width) * bpp + b;
					if (legalIndex(pi)) 
						template.data[ti] = p[pi];
				}
			}
		}
		scaledTiles.buildScaledTiles(template);
		scaledTiles.initialized = true;
	}

	@Override
	void newFrame(OriginalImage oi) {
		if (hsl != null) 
			hsl.clear();
		//convertHsl(pic, 0, 0, width, height);
		//System.out.printf("************ New Frame\n");
	}
	
	class ScaledTilesArray { 
		private Tile [] scaledTiles = new Tile[11];
		public Tile getTileByScale(int s) { 
			int i = s + scaledTiles.length / 2;
			if (i >= 0 && i < scaledTiles.length) 
				return scaledTiles[i];
			else
				return null;
		}
		public int maxScale() { return scaledTiles.length - (scaledTiles.length / 2) + 1; }
		public int minScale() { return -(scaledTiles.length / 2); } 
		
		public void buildScaledTiles(Tile t) { 
			for(int i = 0; i < scaledTiles.length; i++) { 
				int scale =  i - scaledTiles.length / 2;
				scaledTiles[i] = scaleTile(t, scale);
			}	
		}
		boolean initialized = false;
	}	
	ScaledTilesArray scaledTiles = new ScaledTilesArray();

	boolean legalIndex(int pi) { 
		return pi >= 0 && pi < width * height * bpp;
	}
	
	// interpolate a pixel value from non-integer rectangle
	int interpolatePixel(byte []p, double x1, double y1, double x2, double y2, int b) { 
		int ix1 = (int)Math.floor(x1);
		int iy1 = (int)Math.floor(y1);
		int ix2 = (int)Math.ceil(x2);
		int iy2 = (int)Math.ceil(y2);

		if (ix1 < 0 || ix2 > width || iy1 < 0 || iy2 > height)
			return 0;

		double sum = 0;
		for (int x = ix1; x < ix2; x++) { 
			for (int y = iy1; y < iy2; y++) { 
				double fx = x2 - x;
				double fy = y2 - y;
				
				if (fx > 1.0) fx = 1.0;
				if (fy > 1.0) fy = 1.0;
					
				if (x == ix1) fx -= x1 - ix1;
				if (y == iy1) fy -= y1 - iy1; 
				
				int pixel = (int)p[(x + y * width) * bpp + b] & 0xff;
				sum += pixel * fx * fy;
			}
		}
		sum /= (x2 - x1) * (y2 - y1);
		return (int)Math.round(sum);
	}

	boolean normalize = true;
	// relative weights of the rgb or hsl bytes in calculating error
	double [] byteWeights = new double[] {1.0, 1.0, 1.0};

	// note- testTilePrescale does not implement makeMask functionality
	// TODO- normalize saturation and/or luminescensce 
	FindResult testTilePrescale(byte []pic, int x, int y, int s, double already, boolean makeMask) { 
		FindResult r = null;
		Tile t = scaledTiles.getTileByScale(s);
		
		if (t != null) { 
			int pixels = t.loc.height * t.loc.width;
			int [] TT = new int[bpp], TP = new int[bpp], T = new int[bpp], PP = new int[bpp], P = new int[bpp];
			int score = 0;
			int var = 0;
			int [] last = new int[3];
		
			already = -1;  // already does not work with one-pass normalization
			
			if (already != -1)
				already *= pixels;
			
			for(int y1 = 0; y1 < t.loc.height; y1++) { 
				for(int x1 = 0; x1 < t.loc.width; x1++) {
					int px = x - t.loc.width / 2 + x1;
					int py = y - t.loc.height /2 + y1;
					int pi = (px + py * width) * bpp;
					int ti = (x1 + y1 * t.loc.width) * bpp;
					if (legalIndex(pi)) {
						if (hsl != null)
							hsl.verifyPixelConverted(px, py);				
						for(int b = 0; b < bpp; b++) {
							int pp = ((int)pic[pi] & 0xff);
							int pt = ((int)t.data[ti] & 0xff);
							if (hsl != null) { // H value is angular value, no error more than 180 degrees
								int err = pt - pp;
								if (err > 127) err = (255 - err) * 2;
								if (err < -127) err = (255 + err) * 2;
								score += err * err; 
							} else {
								TT[b] += pt * pt;
								PP[b] += pp * pp;
								TP[b] += pt * pp;
								T[b] += pt;
								P[b] += pp;
							}		
							pi++;
							ti++;
						}
					}
					if (already != -1 && score > already) 
						return new FindResult(x, y, s, score / pixels);;
					
				}
			}

			for(int b = 0; b < bpp; b++) { 
				if (b != 0 || hsl == null) {
					int diffAvg = (T[b] - P[b]) / pixels;
					int pAvg = P[b] / pixels;
					score += TT[b] + PP[b] - 2 * TP[b] +  2 * P[b] * diffAvg - 2 * T[b] * diffAvg 	
						+ diffAvg * diffAvg * pixels;
					var += PP[b] - 2 * pAvg + pAvg * pAvg * pixels;
				}
			}
			var /= pixels;
			score /= pixels;
			
//			System.out.printf("rscore = %.1f, score = %.1f  %.1f/%.1f\n", rscore, score,
//					T[1] / t.loc.width / t.loc.height, (double)tavg[1]);

			//System.out.printf("%d,%d,%s %d\n", x, y, s, score);
			r = new FindResult(x, y, s, score, var);
		
		}
		return r;
	}
	
	// May still be a good idea because it is cleaner and since the template scale doesn't change much, 
	// would be easier to maintain a quality mask on the template. 
	//
	// This function hasn't been maintained to follow new features added to testTilePrescale.  Needs hsl
	// code
	// 
	// Probably doesn't support non-square teplates yet 
	
	FindResult testTileJitBROKEN(byte []p, int x, int y, int s, double already, boolean makeMask) { 
		Tile t= template;
		int score = 0;
		
		if (already != -1)
			already *= t.loc.width * t.loc.height;
		
		for(int y1 = 0; y1 < t.loc.height; y1++) { 
			for(int x1 = 0; x1 < t.loc.width; x1++) { 
				double px1 = x - ((double)(t.loc.width / 2 - x1)) * (t.loc.width + s) / t.loc.width;
				double py1 = y - ((double)(t.loc.height / 2 - y1)) * (t.loc.height + s) / t.loc.height;
				double px2 = x - ((double)(t.loc.width / 2 - x1 - 1)) * (t.loc.width + s) / t.loc.width;
				double py2 = y - ((double)(t.loc.height / 2 - y1 - 1)) * (t.loc.height + s) / t.loc.height;
				
				double pixelError = 0;
				//for(int b = 1; b < 2; b++) { 
				for(int b = 0; b < bpp; b++) { 
					int err = ((int)t.data[(x1 + y1 * t.loc.width) * bpp + b] & 0xff) - 
						interpolatePixel(p, px1, py1, px2, py2, b);
					if (b == 0 && hsl != null) { // H value is angular value, no error more than 180 degrees
						if (err > 127) err = 255 - err;
						if (err < -127) err = 255 + err;
					}
					pixelError += err * err;
				}
				score += pixelError;

				if (makeMask)  
					mask[x1 + y1 * t.loc.width] += pixelError;
				if (mask[x1 + y1 * t.loc.width] < 10000)
					score += pixelError;
				if (already != -1 && score > already) 
					return new FindResult(x, y, s, score / t.loc.width / t.loc.height);					
			}
		}
		return new FindResult(x, y, s, score / t.loc.width / t.loc.height);
	}

	FindResult testTile(byte []pic, int x, int y, int s, double already, boolean makeMask) {		
		int x1 = x - (template.loc.width + s) / 2;
		int x2 = x + (template.loc.width + s) / 2;
		int y1 = y - (template.loc.height + s) / 2;
		int y2 = y + (template.loc.height + s) / 2;
			
		if (x1  < 0 || y1 < 0 || x2 >= width || y2 >= height) 
			return null;

		if (hsl != null) 
			hsl.convertHsl(pic, x1, y1, x2 + 1, y2 + 1); // +1 b/c for negative scales, seems testFilePrescale ends up out of bounds by one
		if (jit)
			return testTileJitBROKEN(pic, x, y, s, already, makeMask);
		else
			return testTilePrescale(pic, x, y, s, already, makeMask);		
	}
	
	// startAt must already be a valid findResult, with a valid score, for starting coordinate x,y,s
	// searches for a local minimum unidirectionally towards the vector dx,dy,ds
	// Will pass up to "allow" worse scores to find a new best score
	boolean moveToBetter(FindResult startAt, byte []pic, int dx, int dy, int ds, int r) { 
		FindResult best = startAt;
		final int allow = 3;
		int pass = allow;
		int x = startAt.x;
		int y = startAt.y;
		int s = startAt.scale;
		while(r-- > 0) {
			x += dx;
			y += dy;
			s += ds;
			startAt.compares++;
			FindResult next = testTile(pic, x, y, s, best.score, false);
			if (next != null) { 
				if (next.score > best.score) { 
					if (pass-- == 0) 
						break;
				} else { 			
					best = next;
					pass = allow;
				}
			}
		}
		
		if (startAt.score == best.score) 
			return false;
		int c = startAt.compares;
		startAt.copy(best);
		startAt.compares = c;
		return true;
	}
	
	// search bidrectionally from the startAt point for a local minimum back and forth 
	// along the vector dx,dy,dz.   startAt must already be a valid find result with 
	// a valid score for the starting coordinate x,y,z
	void bidirMoveToBetter(FindResult startAt, byte []pic, int dx, int dy, int ds, int r) { 
		if (!moveToBetter(startAt, pic, dx, dy, ds, r))
			moveToBetter(startAt, pic, -dx, -dy, -ds, r);
	}
	
	// Do a greedy search from the starting point on each axis in turn, keep 
	// iterating until result does not change. 
	FindResult findOptimized(FindResult startAt, byte []pic) {
		FindResult r = testTile(pic, startAt.x, startAt.y, startAt.scale, -1, false);
		if (r != null)
			startAt.copy(r);
		
		for(int i = 0; i < 10; i++) {
			double orig = startAt.score;
			bidirMoveToBetter(startAt, pic, 1, 1, 0, searchDist.x);
			bidirMoveToBetter(startAt, pic, 1, -1, 0, searchDist.x);
			bidirMoveToBetter(startAt, pic, 0, 1, 0, searchDist.x);
			bidirMoveToBetter(startAt, pic, 1, 0, 0, searchDist.x);
			//bidirMoveToBetter(startAt, pic, -2, 1, 0, searchDist.x / 2);
			//bidirMoveToBetter(startAt, pics, 2, 1, 0, searchDist.x / 2);
			//bidirMoveToBetter(startAt, pic, -1, 2, 0, searchDist.x / 2);
			//bidirMoveToBetter(startAt, pic, 1, 2, 0, searchDist.x / 2);
			bidirMoveToBetter(startAt, pic, 0, 0, 1, searchDist.scale);
			if (orig == startAt.score) 				
				break;
		}
		return startAt;
	}
	
	void trainTemplate(byte []pic, FindResult startAt) { 
		testTileJitBROKEN(pic, startAt.x, startAt.y, startAt.scale, -1, true);
		for(int i = 0; i < mask.length; i++)
			mask[i] *= maskDecay;
		dumpMask();
	
	}
		
	void dumpMask() { 
		BufferedWriter f;
		try {
			f = new BufferedWriter(new FileWriter(String.format("/tmp/tm-%04d.dat", frame)));

			for(int x = 0; x < template.loc.width; x++) { 
				for(int y = 0; y < template.loc.height; y++) { 
					f.write(String.format("%d %d %.1f\n", x, y, mask[x + y * template.loc.width]));
				}
				f.write("\n");
			}
			f.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	FindResult findBruteForce(FindResult startAt, byte [] pic) {
		FindResult best = null;
		// simple brute-force exhaustive search of the searchDist space
		for (int s = startAt.scale - searchDist.scale; s <= startAt.scale + searchDist.scale; s++) { 
			Tile t = scaledTiles.getTileByScale(s);	
			if (t != null) {
				for(int x = startAt.x - searchDist.x; x <= startAt.x + searchDist.x; x++) {
					for(int y = startAt.y - searchDist.y; y <= startAt.y + searchDist.y; y++) { 
						FindResult fr = testTile(pic, x, y, t.scale,/*r == null ? -1 : r.score*/ - 1, false);
						if (best == null || (fr != null && fr.score < best.score)) { 
							best = fr;
						}
					}
				}
			}
		}
		if (best != null) 
			startAt.copy(best);
		return best;
	}

	
	// Write out an x,y vs. score chart file suitable for gnuplot.  Also returns FindResult
	// from exhaustive search of searchDist area
	// todo - also make x,s vs score and y,s vs score graphs
	FindResult makeChartFiles(FindResult startAt, byte [] pic, String writeFile) { 
		int bestScale = 0;
		FindResult best = startAt;
		
		try {
			// re-iterate around the best result, writing files for charts showing each of the xy, xs, and ys vs score
			if (best != null && writeFile != null) { 
				BufferedWriter f = new BufferedWriter(new FileWriter(String.format(writeFile, frame, "xy")));
				for(int x = startAt.x - searchDist.x; x <= startAt.x + searchDist.x; x++) {
					for(int y = startAt.y - searchDist.y; y <= startAt.y + searchDist.y; y++) { 
						int ox = x - (startAt.x - searchDist.x);
						int oy = y - (startAt.y - searchDist.y);
						Tile t = scaledTiles.getTileByScale(best.scale);	
						FindResult fr = testTile(pic, x, y, t.scale,/*r == null ? -1 : r.score*/ - 1, false);
						f.write(String.format("%d %d %d\n", ox, oy, fr == null ? 0 : fr.score));
					}
					f.write("\n");
				}
				f.close();
				
				f = new BufferedWriter(new FileWriter(String.format(writeFile, frame, "xs")));
				for(int x = startAt.x - searchDist.x; x <= startAt.x + searchDist.x; x++) {
					for(int s = startAt.scale - searchDist.scale; s <= startAt.scale + searchDist.scale; s++) { 
						int ox = x - (startAt.x - searchDist.x);
						int os = s - (startAt.scale - searchDist.scale);
						Tile t = scaledTiles.getTileByScale(s);	
						FindResult fr = testTile(pic, x, best.y, t.scale,/*r == null ? -1 : r.score*/ - 1, false);
						f.write(String.format("%d %d %d\n", ox, os, fr == null ? 0 : fr.score));
					}
					f.write("\n");
				}
				f.close();

				f = new BufferedWriter(new FileWriter(String.format(writeFile, frame, "ys")));
				for(int y = startAt.y - searchDist.y; y <= startAt.y + searchDist.y; y++) {
					for(int s = startAt.scale - searchDist.scale; s <= startAt.scale + searchDist.scale; s++) { 
						int oy = y - (startAt.y - searchDist.y);
						int os = s - (startAt.scale - searchDist.scale);
						Tile t = scaledTiles.getTileByScale(s);	
						FindResult fr = testTile(pic, best.x, y, t.scale,/*r == null ? -1 : r.score*/ - 1, false);
						f.write(String.format("%d %d %d\n", oy, os, fr == null ? 0 : fr.score));
					}
					f.write("\n");
				}
				f.close();
				
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (best != null) { 
			int c = best.compares;
			startAt.copy(best);
			startAt.compares = c;
		}	
		return best;
	}
	
	int pixelIndex(int x, int y) { 
		return (y * width + x) * bpp;
	}
	@Override 
	// todo rescale is wrong and unneeded here, oi is pre-rescaling 
	void draw(OriginalImage oi, int rescale) {
		if (lastResult != null) {
			rescale = 1; 
			Rectangle r  = targetRect(lastResult);
			for(int y = r.y; y < r.y + r.height; y++) { 
				for(int x = r.x; x < r.x + r.width; x++) {
					oi.putPixel(x * rescale, y * rescale, picX[pixelIndex(x, y)]); 
				}
			}
		}	
	}
	@Override
	FindResult find(FindResult startAt, OriginalImage oi) { 
		picX = convertOI(oi);
		frame++;
		//return findOptimized(startAt, pic);
		lastResult = findBruteForce(startAt,picX);
		return lastResult;
	}

	boolean jit = false;
	String outDataFile = null;

/*
	
	boolean findBetter(FindResult startAt, byte []pic, int x1, int x2, int y1, int y2, int s1, int s2) {
		FindResult best = null;
		for (int s = s1; s <= s2; s++) { 
				//try {
					//BufferedWriter f = new BufferedWriter(new FileWriter(String.format("/tmp/tm-f%04ds%02d.dat", frame, index)));
					for(int x = x1; x <= x2; x++) {
						for(int y = y1; y <= y2; y++) { 
							FindResult r = testTile(pic, x, y, s, best == null ? -1.0 : best.score);
							if (best == null || (r != null && r.score < best.score)) { 	
								best  = r;
							}
							//f.write(String.format("%d %d %.1f\n", x, y, score));;
						}
						//f.write("\n");
					}
					//f.close();
				//} catch (IOException e) {
				//	// TODO Auto-generated catch block
				//	e.printStackTrace();
				//}
		}
	
		if (startAt != null && best != null)
			startAt.copy(best);
		return false;
	}
	
	FindResult find2(FindResult startAt, byte []pic) {
		findBetter(startAt, pic, startAt.x - searchDist.x, startAt.x + searchDist.x, startAt.y - searchDist.y, 
				startAt.y + searchDist.y, startAt.scale - searchDist.scale, startAt.scale + searchDist.scale);
		return startAt;
	}
	
	

	// iterate over a complete linear search of each axis in turn. 
	FindResult find3(FindResult startAt, byte []pic) {
		FindResult orig = new FindResult(0, 0, 0, 0.0);
		for(int i = 0; i < 10; i++) {
			orig.copy(startAt);
			findBetter(startAt, pic, startAt.x, startAt.x, 
					startAt.y - searchDist.y, startAt.y + searchDist.y, 
					startAt.scale, startAt.scale);
			findBetter(startAt, pic, startAt.x - searchDist.x, startAt.x + searchDist.x, startAt.y, startAt.y, startAt.scale,
					 startAt.scale);
			findBetter(startAt, pic, startAt.x, startAt.x, startAt.y, startAt.y, startAt.scale - searchDist.scale,
					 startAt.scale + searchDist.scale);
			if (orig.score == startAt.score) 
				break;
		}
		return startAt;
	}

	*/


}