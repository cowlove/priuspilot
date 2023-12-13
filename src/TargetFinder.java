import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;


//import math.Average;
//import math.RunningAverage;
//import math.RunningLeastSquares;
//import math.JamaLeastSquaresFit;
//import math.Geometry;
//import math.Geometry.*;

class ByteSpan { 
	int low;
	int high;
	ByteSpan(int l, int h) { low = l; high = h; } 
	boolean in(int v) { 
		return (((low < high) && v >= low && v <= high) || (low > high) && (v >= low || v <= high));
	}
}

class FinderParameters { 
	float threshold1 = 12F, threshold2 = 8F;
	float gaussianKernelRadius = 0.8F;
	int gaussianKernelWidth = 8;
	float nonmaxThreshold = 0.50f;
	ByteSpan H = new ByteSpan(0,256);   
	ByteSpan S = new ByteSpan(0,256);
	ByteSpan L = new ByteSpan(0,256);
    int maxSymErr = 50;
    String name = "";
}



class HistogramDisplay { 
	BufferedImageDisplay display = null;
	int w, h;
	HistogramDisplay() { 
		w = 320; h = 240; // hardcoded size for histogram display
	}
	class LineDrawerInfo {
		LineDrawer h;
		Color c;
	}
	ArrayList<LineDrawerInfo> hists = new ArrayList<LineDrawerInfo>();
	void add(LineDrawer h, Color c) { 
		LineDrawerInfo hi = new LineDrawerInfo();
		hi.h = h;
		hi.c = c;
		hists.add(hi);
	}
	void draw() { 
		draw(0);
	}
	void draw(double blur) { 
		if (display == null) 
			display = new BufferedImageDisplay(w, h, BufferedImage.TYPE_INT_ARGB);
		display.g2.clearRect(0, 0, w, h);
		for(LineDrawerInfo hi : hists) {
			display.g2.setColor(hi.c);
			hi.h.draw(display.g2, w, h, blur);
		}
		display.done(null);	
	}
}

interface LineDrawer { 
	public void draw(Graphics2D g2, int w, int h, double blur);
	public void add(double v);
	public void clear();
	public void recount();
}

class Histogram implements LineDrawer { 
	Histogram(double mi, double ma, int s) {
		size = s;
		min = mi;
		max = ma;
		step = (max - min) / s;
		clear();
	}
	public void clear() { 
		buckets = new int[size];
		maxbucket = -1;
		count = 0;
	}
	int count;
	int [] buckets;
	int size;
	int maxbucket = -1;
	double min, max, step;

	double percentile(double pct) {
		int i, sum = 0;
		for(i = 0; i < size - 1; i++) { 
			sum += buckets[i];
			if (sum >= count * pct)
				break;
		}
		return min + i * step; 
	}
	int bucketIndex(double v) { 
		return (int)Math.floor((v - min) / step);
	}
	public void add(double v) { 
		if (v >= min && v <= max) { 
			int i = bucketIndex(v);
			buckets[i]++;
			if (maxbucket == -1 || buckets[i] > buckets[maxbucket])
				maxbucket = i;
			count++;
		} else { 
			System.out.printf("bad val %f\n", v);
		}
	}
	
	public void draw(Graphics2D g2, int w, int h, double blur) {
		if (blur > 0) { 
			GaussianKernel gk = new GaussianKernel(blur, 10, size, 1);
			gk.blur(buckets);
			recount();
		}
		w -= 10;
		int botBorder = 50;
		if (maxbucket == -1 || buckets[maxbucket] == 0) 
			return;
		int maxval = buckets[maxbucket] + 30;
		for(int i = 0; i < size - 1; i++) { 
			g2.drawLine(i * w / size, h - botBorder - buckets[i] * h / maxval, (i + 1) * w / size, 
					h - botBorder - buckets[i + 1] * h / maxval);
		}
		double lowPct = 0.05, hiPct = 0.95;
		
		g2.drawString(String.format("%d", maxbucket), maxbucket * w / size, h  - botBorder  - buckets[maxbucket] * h / maxval);
		int p95 = (int)percentile(0.90);
		int p95i = bucketIndex(p95);		
		g2.drawString(String.format("%d", p95), p95i * w / size, h - botBorder + 20);
		int p5 = (int)percentile(0.05);
		int p5i = bucketIndex(p5);		
		g2.drawString(String.format("%d", p5), p5i * w / size, h - botBorder + 10);
	}
	public void recount() {
		count = 0;
		maxbucket = -1;
		for(int i = 0; i < size; i++) {
			count += buckets[i];
			if (maxbucket == -1 || buckets[i] > buckets[maxbucket])
				maxbucket = i;
		}
		
		
	}
}

class Chronograph implements LineDrawer {
	Chronograph() {}
	
	int botBorder = 30;
	ArrayList<Integer> values = new ArrayList<Integer>();
	@Override
	public void draw(Graphics2D g2, int w, int h, double blur) {
		int size = values.size();
		int maxval = 300;
		for(int i = 0; i < size - 1; i++) { 
			g2.drawLine(i * w / size, h - botBorder - values.get(i) * h / maxval, 
					(i + 1) * w / size, h - botBorder - values.get(i + 1) * h / maxval);
		}
	}

	@Override
	public void add(double v) {
		values.add((int)v);
	}

	@Override
	public void clear() {
		values.clear();
	}

	@Override
	public void recount() {
	} 
	
}

abstract class HslChart {
	LineDrawer [] hists = new LineDrawer[3];
	HistogramDisplay disp = new HistogramDisplay();
	HslChart() { 
	}
	void add(int []h) { 
		add(h[0], h[1], h[2]);
	}
	void add(int h, int s, int l) { 
		hists[0].add(h);
		hists[1].add(s);
		hists[2].add(l);
	}
	void clear() { 
		for(LineDrawer h : hists) 
			h.clear();
	}
	void draw() { 
		draw(0);
	}
	void draw(double blur) { 
		disp.draw();
	}
}

class HslHistogram extends HslChart {
	HslHistogram() { 
		hists[0] = new Histogram(0, 256, 256);
		hists[1] = new Histogram(0, 256, 256);
		hists[2] = new Histogram(0, 256, 256);
		disp.add(hists[0], Color.red);
		disp.add(hists[1], Color.yellow);
		disp.add(hists[2], Color.white);
	}
	Histogram getHist(int n) { 
		return (Histogram)hists[n];
	}
}

class HslChronograph extends HslChart {
	HslChronograph() { 
		hists[0] = new Chronograph();
		hists[1] = new Chronograph();
		hists[2] = new Chronograph();
		disp.add(hists[0], Color.red);
		disp.add(hists[1], Color.yellow);
		disp.add(hists[2], Color.white);
	}
}

class TargetFinder {
	static int luminance(int r, int g, int b) {
		return Math.round(0.299f * (r & 0xff) + 0.587f * (g & 0xff) + 0.114f * (b & 0xff));
	}
    static Rectangle scaleRect(Rectangle r, int scale) {
    	return new Rectangle(r.x * scale, r.y * scale, r.width * scale, r.height * scale);
    }
	public int rescaleDisplay = 1;
	FinderParameters param = new FinderParameters();
	
	void setCanny(FinderParameters p) { 
		c.threshold = param.threshold1;
		c.setGaussianKernelRadius(p.gaussianKernelRadius);
		c.setGaussianKernelWidth(p.gaussianKernelWidth);
		border = (int)(p.gaussianKernelRadius * 5);
	}
	
	CannyEdgeDetector c = new CannyEdgeDetector();
	//final int bpp = 3;
	public int fudge = 2;
	
	int border = 0;

	public int width, height;
	TargetFinder(int w, int h) { 
		width = w;
		height = h;
	}
	//byte [] orig;

	/*
	void copyin(OriginalImage oi, Rectangle r, byte [] dat) {
		ByteBuffer bb = oi.pixels;		
		for(int x = 0; x < r.width; x++) { 
			for(int y = 0; y < r.height; y++) { 
				for(int b = 0; b < bpp; b++) { 
					dat[(x + y * r.width) * bpp + b] = bb.get((x + r.x + (y + r.y) * width) * bpp + b); 
				}
			}
		}
	}
	void copyout(byte [] dat, OriginalImage oi, Rectangle r) { 
		ByteBuffer bb = oi.pixels;
		for(int x = 0; x < r.width; x++) { 
			for(int y = 0; y < r.height; y++) { 
				for(int b = 0; b < bpp; b++) { 
					bb.put((x + r.x + (y + r.y) * width) * bpp + b, dat[(x + y * r.width) * bpp + b] ); 
				}
			}
		}
	}
	void copyout(byte [] dat, ScanZonePair sz, OriginalImage oi, Rectangle r) { 
		ByteBuffer bb = oi.pixels;
		for(int x = 0; x < r.width; x++) {
			int startY = sz.ystart(x);
			int endY = sz.yend(x);
			for(int y = startY; y < endY; y++) { 
				for(int b = 0; b < bpp; b++) { 
					bb.put((x + r.x + (y + r.y) * width) * bpp + b, dat[(x + y * r.width) * bpp + b] ); 
				}
			}
		}
	}
	*/
	Rectangle []findAll(OriginalImage oi, Rectangle r) {
		BufferedImage cimage = new BufferedImage(r.width, r.height, BufferedImage.TYPE_3BYTE_BGR);
		sa = r;
		//orig = new byte[r.width * r.height * bpp];
		//copyin(oi, sa, orig);
		
		c.threshold = param.threshold1;
		c.setGaussianKernelRadius(param.gaussianKernelRadius);
		c.setGaussianKernelWidth(param.gaussianKernelWidth);
		
		border = (int)(param.gaussianKernelRadius * 5);

//		cimage.getWritableTile(0, 0).setDataElements(0, 0, r.width, r.height, orig);
        //c.setSourceImage(cimage);
        //c.process();
        //c.createEdgeImage();
//		ByteBuffer cbb = ByteBuffer.allocate(r.width * r.height * bpp);
//		cbb.put(orig);
		c.processData(oi, r);
        canny = c.getData();
        
        cmask = makeCannyRadiusMask(canny, sa.width, sa.height, cannyRadius);
		return null;
	}
	
	final int cannyRadius = 3;

	void reset() {}

	boolean [] makeCannyRadiusMask(int []c, int w, int h, int radius) {
		boolean [] result = new boolean[w * h];
		for(int x = 0; x < w; x++) { 
			for (int y = 0; y < h; y++) {
				for(int xr =  - radius; xr <= radius; xr++) {
					int rx = x + xr;
					int yradius = (int)Math.round(Math.sqrt(radius * radius - xr * xr));
					for (int ry = y - yradius; ry <= y + yradius; ry++) { 
						if (rx >= 0 && rx < w && ry >= 0 && ry < h) { 
							if ((c[(rx + ry * w)] & 0xff) == 0xff) {
								result[x + y * w] = true;
							}
						}
					}
				}
			}
		}
		return result;
	}
/*
	void drawCannyLines(byte []pic) {
		
		for(int x = 0; x < sa.width; x++) { 
			for(int y = 0; y < sa.height; y++) { 
				int pixel = canny[x + y * sa.width];
				if ((pixel == -1)) {
					pic[(x + y * sa.width) * bpp + 0] = (byte)(((pixel & 0xff0000) >> 16));
					pic[(x + y * sa.width) * bpp + 1] = (byte)(((pixel & 0xff00) >> 8));
					pic[(x + y * sa.width) * bpp + 2] = (byte)(pixel & 0xff);
				}
			}
		}
		
		//boolean []m = this.makeCannyRadiusMask(canny, sa.width, sa.height, 0);
		//drawMask(m, pic);		
	}
	*/
	/*(
	void applyMask(boolean []mask, byte []pic) { 
		for(int i = 0; i < mask.length; i++) { 
			if (mask[i] == false) {
				for (int b = 0; b < bpp; b++) { 
					pic[i * bpp + b] = 0;
				}
			}
		}
	}

	
	void drawMask(boolean []mask, byte []pic) { 
		for(int i = 0; i < mask.length; i++) { 
			if (mask[i] == true) {
				for (int b = 0; b < bpp; b++) { 
					pic[i * bpp + b] = (byte)255;
				}
			}
		}
	}
	*/
	/*
	int pindex(int x, int y) { 
		return (x + sa.x + (y + sa.y) * width) *bpp; 
	}
	*/
	int saindex(int x, int y) { 
		return x + y * sa.width;
	}
	boolean savalid(int x, int y) { 
		return x >= 0 && x < sa.width && y >= 0 && y < sa.height;
	}
	
	Rectangle sa;
	boolean [] cmask;  // mask of interesting pixels around canny edges. 
	int [] canny;

	public Rectangle findNearest(OriginalImage oi, Rectangle sa,
			int x, int y) { 
		Rectangle [] rs = findAll(oi, sa);
		if (rs == null)
			return null;
		Rectangle closest = null;
		double closestDist = 0;
		for(Rectangle r : rs) { 
			//double dist = Math.sqrt(Math.pow(r.x + r.width / 2 - x, 2) + Math.pow(r.y + r.height / 2 - y, 2));
			double dist = Math.abs(r.x + r.width / 2 - x);
			if (closest == null || dist < closestDist) {
				closest = r;
				closestDist = dist;
			}	
		}
		return closest;
	}
}
	
