import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;


//import math.Average;
//import math.RunningAverage;
//import math.RunningLeastSquares;
//import math.JamaLeastSquaresFit;
//import math.Geometry;
//import math.Geometry.*;


public class TargetFinderRectangle extends TargetFinder {
	CannyEdgeDetectorOriginal c = new CannyEdgeDetectorOriginal();
	final int bpp = 3;
	double slope = 0.000000001;
	int hist = 4;
	RunningLeastSquares ls1;
	RunningLeastSquares ls2;

	int []data;
	int x1, y1, x2, y2;
	final int DONE_MASK = 0x1000000;
	
	int [] xhist, yhist;
	int border = 0;
	float threshold1 = 8F, threshold2 = 4F;
	float gaussianKernelRadius = .5F;
	int gaussianKernelWidth = 8;
	final double aspect = 44F / 35;
	
	Rectangle []findAll(ByteBuffer bb, Rectangle r) {
		ls1 = new RunningLeastSquares(hist);
		ls2 = new RunningLeastSquares(hist);
		
		yhist = new int[height];
        xhist = new int[width];
     
        //c.zones.clear();
		x1 = r.x;
		x2 = r.x + r.width;
		y1 = r.y;
		y2 = r.y + r.height;
		
		BufferedImage cimage = new BufferedImage(r.width, r.height, BufferedImage.TYPE_3BYTE_BGR);
		byte [] pixels = new byte[r.width * r.height * 3];
		for(int x = 0; x < r.width; x++) { 
			for(int y = 0; y < r.height; y++) { 
				for(int b = 0; b < 3; b++) { 
					pixels[(x + y * r.width) * 3 + b] = bb.get((x + r.x + (y + r.y) * width) * 3 + b); 
				}
			}
		}
		c.setContrastNormalized(false);
		c.setLowThreshold(threshold2);
		c.setHighThreshold(threshold1);
		c.setGaussianKernelRadius(gaussianKernelRadius);
		c.setGaussianKernelWidth(gaussianKernelWidth);

		border = (int)(gaussianKernelRadius * 5);

		cimage.getWritableTile(0, 0).setDataElements(0, 0, r.width, r.height, pixels);
        c.setSourceImage(cimage);
        c.process();
        c.createEdgeImage();
        data = c.getData();
        
        makeHist();
        Rectangle rec = findBestRect();
        Rectangle []ra = {rec};
        return ra;
	}
	
	private void makeHist() {
		for (int x = x1 + border; x < x2 - border; x++) {
			int run = 0;
			for (int y = y1 + border; y < y2 - border; y++) {
				int i = x - x1 + (y - y1) * (x2 - x1);
				if ((data[i] & 0xff) > 0) {
					if (run++ > hist)
						data[i] = 0xff0001;
				} else { 
					if (run >= hist) 
						xhist[x] += run;
					run = 0;
				}
			}
		}
		for (int y = y1 + border; y < y2 - border; y++) {
			int run = 0;
			for (int x = x1 + border; x < x2 - border; x++) {
				int i = x - x1 + (y - y1) * (x2 - x1);
				if ((data[i] & 0xff) > 0) {
					if (run++ > hist)
						data[i] = 0xff01;
				} else { 
					if (run >= hist) 
						yhist[y] += run;
					run = 0;
				}
			}
		}
	}
	
	TargetFinderRectangle(int w, int h)  {
		super(w, h);
        // normalize gaussian parameters to 160x120 image.
        c.setGaussianKernelRadius(0.5F);
        c.setGaussianKernelWidth(4);
	}
	
	Rectangle findBestRect() {
		/*
		for (int x = x1; x < x2; x++) {
			if ((x - x1) % 10 == 0) 
				System.out.printf("\nX %03d\t", x);
			System.out.printf("%d\t", xhist[x]);
		}
		for (int y = y1; y < y2; y++) {
			if ((y - y1) % 10 == 0) 
				System.out.printf("\nY %03d\t", y);
			System.out.printf("%d\t", yhist[y]);
		}
		System.out.printf("\n");
*/
		
		double best = 0;
		int bestx = 0, besty = 0, bestw = 0;
		int b = border;
		int minscore = 60;
		
		for(int x = x1 + b; x < x2 - b; x++) { 
			for(int y = y1 + b; y < y2 - b; y++) { 
				for(int w = 55; w < 65; w++) {
					int h = (int)Math.round(aspect * w);
					int quicksum = 0;
					for (int f = -fudge; f <= +fudge; f++) { 
						if (x + w + f < x2 - b && y + h + f < y2 - b && x + f > x1 + b && y + f > y1 + b) { 
							for(int rx = x; rx < x + w; rx++) 
								quicksum += xhist[rx + f] + xhist[rx + w + f];
							for(int ry = y; ry < y + h; ry++) 
								quicksum += yhist[ry + f] + yhist[ry + h + f];
						}
					}

					int leg1 = 1, leg2 = 1, leg3 = 1, leg4 = 1;
					if (quicksum > minscore) {
						for (int f = -fudge; f <= +fudge; f++) {  
							if (x + w + f < x2 - b && y + h + f < y2 - b && x + f > x1 + b && y + f > y1 + b) { 
								//sum = xhist[x + f] + xhist[x + w + f] + yhist[y + f] + yhist[y + h + f];
								for(int rx = x; rx < x + w; rx++) {
									if ((data[rx - x1 + (y - y1 + f) * (x2 - x1)] & 0xff) == 0x01)
										leg1++;
									if ((data[rx - x1 + (y - y1 + h + f) * (x2 - x1)] & 0xff) == 0x01)
										leg2++;
								}
								for(int ry = y; ry < y + h; ry++) {
									if ((data[x - x1 + f + (ry - y1) * (x2 - x1)] & 0xff) == 0x01)
										leg3++;
									if ((data[x - x1 + w + f + (ry - y1) * (x2 - x1)] & 0xff) == 0x01)
										leg4++;
	
								}
							}
						}
					}
					double score = ((double)leg1) +  leg2 + leg3 + leg4;
					if (score > best) { 
						best = score; 
//						System.out.printf("%d, %d, %d, %d\n", leg1, leg2, leg3, leg4);
						bestx = x; besty = y; bestw = w;
					}
				}
			}
		}
		System.out.printf("%d,%d,%d %.1f\n", bestx, besty, bestw, best);
		if (best < minscore) 
			return null;
		else
			return new Rectangle(bestx, besty, bestw, (int)Math.round(bestw * aspect));
		}
}
