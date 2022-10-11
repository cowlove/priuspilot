import java.awt.Point;
import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;



class HoughTransform {
	// TODO - handle angMin-angMax range that crosses 360 degrees. 
	static int countC = 0;
	int id = countC++;
	Point origin = new Point(0, 0);
	public double angMin, angMax;
	public double radMin, radMax;
	float radStepInv;
	int angSz, radSz;
	
	float maxhough = 0;
	float [] hough;
	float sinLookup[];
	float cosLookup[];
	float ang2TanLookup[];
	
	int bestA = 0, bestR = 0;
	final boolean print = false;
	
	// look for nearly-parallel lines between minA and maxA apart.  Hough map is multiplied
	// by itself for each discrete angStep angle between minA and maxA.  New map with the best
	// peak is retained as the new map, and adjust bestA, bestAng, bestR, bestRad accordingly.
	float [] corHough = null;
	
	// obsoleted, rewrote this as applyCorrelationRad below
	void applyCorrelationAngle(double minA, double maxA, boolean leftSide) { 
		maxhough = 0;
		float [] nh = null, bestNh = null;
		
		double angStep = (double)(angMax - angMin) / angSz;
		double rStep = 9f / 6;
		//Math.sin(Math.toRadians(angStep)) * r * 0.5;		
		int aStep = leftSide ? -1 : 1;
		int aStart = (int)Math.floor(minA / angStep);
		int aEnd = (int)Math.ceil(maxA / angStep);
		
		for(int as = aStart; as <= aEnd; as++) { 
			int rs = (int)Math.round(as * rStep);
			nh = new float[angSz * radSz];
			for(int x = 0; x < angSz; x++) { 
				for(int y = 0; y < radSz; y++) {
					int x1 = x - as * aStep;
					int y1 = y - rs;
					if (x1 >= 0 && x1 < angSz && y1 >= 0 && y1 < radSz) { 
						int val = (int)Math.sqrt((hough[x + y * angSz] * hough[x1 + y1 * angSz]));
						//int val = (hough[x + y * angSz] + hough[x1 + y1 * angSz]) / 2;
						nh[x + y * angSz] = val;
					}		
				}
			}

			GaussianKernel gk = new GaussianKernel(blurRadius, (int)(blurRadius * 10 + 2), angSz, radSz);
			gk.blur(nh);
			if (gk.max > maxhough) { 
				maxhough = gk.max;
				bestAng = gk.bestX;
				bestRad = gk.bestY;
				bestA = as;
				bestR = rs;
				bestNh = nh;
			}
		}

		//	if (bestNh != null)  // probably don't do this, VP code would like unchanged map
		//	hough = nh;
		corHough = bestNh;
	}

	void applyCorrelationRad(double minR, double maxR, boolean leftSide) { 
		maxhough = 0;
		float [] nh = null, bestNh = null;
		
		double radStep = (double)(radMax - radMin) / radSz;

		// hard-coded emperical values correlating the typical observed relation between
		// lane angle/radius hotspots on the hough map
		double aStep = leftSide ? -1.8 : -0.8;

		int rStep = leftSide ? -1 : 1;
		int rStart = (int)Math.floor(minR / radStep);
		int rEnd = (int)Math.ceil(maxR / radStep);
		
		for(int rs = rStart; rs <= rEnd; rs++) { 
			int as = (int)Math.round(rs * aStep); // was rstep
			nh = new float[angSz * radSz];
			for(int x = 0; x < angSz; x++) { 
				for(int y = 0; y < radSz; y++) {
					int x1 = x - as;
					int y1 = y - rs;
					if (x1 >= 0 && x1 < angSz && y1 >= 0 && y1 < radSz) { 
						float val = (int)Math.sqrt((hough[x + y * angSz] * hough[x1 + y1 * angSz]));
						//int val = (hough[x + y * angSz] + hough[x1 + y1 * angSz]) / 2;
						nh[x + y * angSz] = val;
					}		
				}
			}

			GaussianKernel gk = new GaussianKernel(blurRadius, (int)(blurRadius * 10 + 2), angSz, radSz);
			gk.blur(nh);
			if (gk.max > maxhough) { 
				maxhough = gk.max;
				bestAng = gk.bestX;
				bestRad = gk.bestY;
				bestA = as;
				bestR = rs;
				bestNh = nh;
			}
		}

		//	if (bestNh != null)  // probably don't do this, VP code would like unchanged map
		//	hough = nh;
		corHough = bestNh;
	}
	
	
	float getAngSpread() { 
		return (float)(angMax - angMin) * bestA / angSz;
	}
	
	void drawOnPicOLD(byte [] orig, int w, int h, int bpp, double thresh) { 
		int maxpixel = 1;
		int [] chart = new int[w * h];
		for(int y = 0; y < h; y++) {
			for(int x = 0; x < w; x++) { 
				int pixel = scoreOLD(x, y, thresh);
				chart[x + y * w] = pixel;
				if (pixel > maxpixel) 
					maxpixel = pixel;
			}
		}
		for(int y = 0; y < h; y++) {
			for(int x = 0; x < w; x++) { 
				int s = chart[x + y * w];
				if (s > 0) {
					for (int b = 0; b < bpp; b++) { 
						orig[(x + y * w) * bpp + b] = (byte)(s * 255 / maxpixel);
					}
				}
			}
		}
	}
	
	HoughTransform(int a, int r) { 
		angSz = a; 
		radSz = r; 
		clear();
		sinLookup = new float[angSz];
		cosLookup = new float[angSz];
		ang2TanLookup = new float[angSz];
	}

	void setRadRange(double min, double max) { 
		radMin = min;
		radMax = max;
		radStepInv = 1f/ (float)((radMax - radMin) / radSz);

	}
	void setAngleRange(double min, double max) {
		angMin = min;
		angMax = max;
		for (int a = 0; a < angSz; a++) { 
			double ang = angMin + (angMax - angMin) / angSz * a;
			if (ang > 360) 
				ang %= 360;
			sinLookup[a] = (float)Math.sin(Math.toRadians(ang));
			cosLookup[a] = (float)Math.cos(Math.toRadians(ang));
			ang2TanLookup[a] = (float)(Math.tan(Math.toRadians(ang + 90)));
		}
	}
	
	void dump(String fn) { 
		BufferedWriter f;
		try {
			f = new BufferedWriter(new FileWriter(fn));
			for (int a = 0; a < angSz; a++) { 
				for(int r = 0; r < radSz; r++) 
					f.write(String.format("%d %d %d\n", a, r, hough[a + r * angSz]));
				f.write("\n");
			}
			f.close();
		} catch (IOException e) {
			// TODO Auto-gegausSumnerated catch block
			e.printStackTrace();
		}
	}
	void clear() { 
		hough = new float[angSz * radSz];
		maxhough = 0;
	}
	
	private double maxRadius(int x, int y) { 
		double r1 = (x - origin.x) * Math.cos(Math.toRadians(angMin)) 
				+ (y - origin.y) * Math.sin(Math.toRadians(angMin));
		double r2 = (x - origin.x) * Math.cos(Math.toRadians(angMax)) 
				+ (y - origin.y) * Math.sin(Math.toRadians(angMax));
		return r1 > r2 ? r1 : r2;
	}
	
	// return the max radius encountered along the section of the
	// line contained in the rectangle.  Assumes origin and angMin-angMax
	// range has been setup.   Useful in calculating radMin-radMax range
	double maxLineRadius(Rectangle r, double m, int b) { 
		double d, dist = -1;
		
		int x = 0; 
		int y = (int)(x * m + b);
		if (y >= 0 && y < r.height) 
			dist = maxRadius(x, y);
		
		x = r.width; 
		y = (int)(x * m + b);
		d = maxRadius(x, y);
		if (y >= 0 && y <= r.height && d > dist) 
			dist = d;
		
		y = 0; 
		x = (int)((y - b) / m);
		d = maxRadius(x, y);
		if (x >= 0 && x <= r.width && d > dist) 
			dist = d;

		y = r.height; 
		x = (int)((y - b) / m);
		d = maxRadius(x, y);
		if (x >= 0 && x <= r.width && d > dist) 
			dist = d;
		
		return dist;
	}
	

	// used to project strong lines into external rectangle, as in the vanishing point calc
	void projectIntoRect(int []s, Rectangle rec, int scale) {
		ArrayList<Point> pts = new ArrayList<Point>();
		
		//this.suppressNonmax(angSz / 10, 0.4f, pts);
		
		if (print)
			System.out.printf("o(%d,%d) best=%d,%d %d\n", origin.x, origin.y,
					bestAng, bestRad, hough[bestAng + bestRad * angSz]);
		
		// Uncomment either OPTION A lines or OPTION B lines 
		// TODO - Make fewer passes through the target rectangle, maybe 
		// sum together a block of hough array values and make one pass through
		// the rectangle for all of them. 
		
		//for(Point p : pts) { {     // OPTION A
		//	int a = p.x, r = p.y;  // OPTION A
		
		int hStep = 3;
 		for(int a = hStep / 2; a < angSz; a += hStep) {   // OPTION B
			float ang2Tan = ang2TanLookup[a];
			float ang2TanInv = 1f / ang2Tan;
			for(int r = hStep / 2; r < radSz; r += hStep) {  // OPTION B
				int weight = 0;
				for(int da = a - hStep / 2; da <= a + hStep / 2; da++) { 
					for (int dr = r - hStep / 2; dr <= r + hStep / 2; dr++) {
						if (dr > 0 && dr < radSz && da > 0 && da < angSz)
							weight += hough[da + dr * angSz];
					}
				}
				weight = weight * angSz * radSz / 100 / 100;
				
				float ang = (float)(angMin + (angMax - angMin) / angSz * a);
				if (ang > 360) 
					ang %= 360;
				float rad = (float)(radMin + (radMax - radMin) / radSz * r);
				float ang2 = (ang + 90);
				
				// normalize weight to 100x100 grid, so different grid sizes don't 
				// weight differently.

				// stuff close to horizontal tends to introduce too much error
				final int minAng = 15;
				if (ang2 % 90 < minAng || ang2 % 90 > 90 - minAng)
					continue;
				
				// calculate point (x1,x2) on line 
				float x1 = origin.x + cosLookup[a] * rad;
				float y1 = origin.y + sinLookup[a] * rad;
							
				for(int y = 0; y < rec.height; y += scale) {
					// calculate point where this line intercepts row #y
					int x = Math.round((x1 - (y1 - rec.y - y) * ang2TanInv - rec.x) / scale);
					if (x >= 0 && x < rec.width / scale)  {
						int i = x + y / scale * rec.width / scale;
						s[i] += weight;
					}
					
				}
				for(int x = 0; x < rec.width; x += scale) {
					// calculate point where this line intercepts col #x
					int y = Math.round((y1 - (x1 - rec.x - x) * ang2Tan - rec.y) / scale);
					if (y >= 0 && y < rec.height / scale) 
						s[x / scale + y * rec.width / scale] += weight;
					if (x == 0 && print) 
						System.out.printf("\t%d,%d %.1f,%.1f p2(%.1f,%.1f) y(0,%d) w%d\n", 
								a, r, ang2, rad, x1, y1, y, weight );

				}
			}
		}		
//		System.out.printf("\n");
	}
	
	void add(int x, int y) {
		add(x, y, 1);
	}
	
	void add(int x, int y, float w) { 
		if (w == 0.0)
			return;
		for (int a = 0; a < angSz; a++) { 
			int r = (int)((((float)(x - origin.x)) * cosLookup[a] + 
					((float)(y - origin.y)) * sinLookup[a] - radMin) * radStepInv);
			if (r >= 0 && r < radSz) {
				if ((hough[a + r * angSz] += w) > maxhough) 
					maxhough = hough[a + r * angSz];
			}
		}
		
	}

	int bestAng, bestRad;
	double blurRadius = 15;
	
	void blur() { 
		GaussianKernel gk = new GaussianKernel(blurRadius, (int)(blurRadius * 10 + 2), angSz, radSz);
		gk.blur(hough);
		bestAng = gk.bestX;
		bestRad = gk.bestY;
		maxhough = gk.max;
	}
	
	// moves bestAng and bestRad to the closest point to (x,y) that still has over 
	// thresh portion of maxhough;
	
	void findClosest(int x, int y, float thresh) {
		double bestDist = -1;
		for(int a = 0; a < angSz; a++) {
			for (int r = 0; r < radSz; r++) { 
				float v = hough[a + r * angSz];
				if (v >= maxhough * thresh) {
					double d = Math.sqrt((x - a) * (x - a) + (y - r) * (y  - r));;
					if (bestDist < 0 || d < bestDist) { 
						bestAng = a;
						bestRad = r;
						bestDist = d;
					}
				}
				
			}
		}
	}
	boolean isOnBestLine(int x, int y) { 
		double radStep = (radMax - radMin) / radSz;
		int r = (int)(((x - origin.x) * cosLookup[bestAng] + (y - origin.y) * sinLookup[bestAng] - radMin) / radStep);
		return r == bestRad;

	}
	
	// return the best hough angle.  Note hough angle is 90 more than the line's 
	// angle
	double bestAngle() {
		return angMin + (angMax - angMin) / angSz * bestAng;
	}
	double bestRadius() { 
		return (radMin + (radMax - radMin) / radSz * bestRad);
	}
	
	int scoreOLD(int x, int y, double thresh) {
		int minW = (int)Math.round(maxhough * thresh);
		int pixel = 0;
		for (int a = 0; a < angSz; a++) { 
			double ang = angMin + (angMax - angMin) / angSz * a;
			if (ang > 360) 
				ang %= 360;
			double radStep = (radMax - radMin) / radSz;
			int r = (int)(((x - origin.x) * cosLookup[a] + (y - origin.y) * sinLookup[a] - radMin) / radStep);
			if (r >= 0 && r < radSz) {
				float w = hough[a + r * angSz];
				if (w > minW) 
					pixel += w;
			}
		}
		return pixel;
	}
	ArrayList<Point> x;
	
	void suppressNonmax(int dist, float thresh, ArrayList<Point> pts) { 
		NonmaxSuppression ns = new NonmaxSuppression(angSz, radSz);
		ns.suppressNonmaxTODO(hough, dist, thresh, pts);
		maxhough = ns.max;
		bestAng = ns.bestX;
		bestRad = ns.bestY; 
	}
	
	int bestYIntercept() {
		// TODO fragile - what if angle is 180 degrees off? 
		double lx = origin.x + bestRadius() * Math.cos(Math.toRadians(bestAngle()));
		double ly = origin.y + bestRadius() * Math.sin(Math.toRadians(bestAngle()));
		double i = ly - lx * Math.tan(Math.toRadians(bestAngle() - 90));
		return (int)Math.round(i);
	}

	// experimental finish - don't look for the global max or local max, just 
	// use the global CG of the hough results.  Doesn't even work well enough to focus lane zones.
	public void findCG(int ca) {
		float asum = 0, rsum = 0, sum = 0;
		for(int a = 0; a < angSz; a++) {
			for (int r = 0; r < radSz; r++) { 
				float v = hough[a + r * angSz];
				asum += a * v;
				rsum += r * v;
				sum += v;
			}
		}
		if (sum >= 0.0) { 
			bestAng = Math.round(asum / sum);
			bestRad = Math.round(rsum / sum);
			// leave maxhough unchanged
		} else { 
			bestAng = bestRad = 0;
		}
	}
	
	
}