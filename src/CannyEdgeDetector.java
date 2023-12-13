import java.awt.Point;
import java.awt.image.BufferedImage;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import java.awt.Rectangle;

//import laneDetector;

/**
 * <p><em>This software has been released into the public domain.
 * <strong>Please read the notes in this source file for additional information.
 * </strong></em></p>
 * 
 * <p>This class provides a configurable implementation of the Canny edge
 * detection algorithm. This classic algorithm has a number of shortcomings,
 * but remains an effective tool in many scenarios. <em>This class is designed
 * for single threaded use only.</em></p>
 * 
 * <p>Sample usage:</p>
 * 
 * <pre><code>
 * //create the detector
 * CannyEdgeDetector detector = new CannyEdgeDetector();
 * //adjust its parameters as desired
 * detector.setLowThreshold(0.5f);
 * detector.setHighThreshold(1f);
 * //apply it to an image
 * detector.setSourceImage(frame);
 * detector.process();
 * BufferedImage edges = detector.getEdgesImage();
 * </code></pre>lan
 * 
 * <p>For a more complete understanding of this edge detector's parameters
 * consult an explanation of the algorithm.</p>
 * 
 * @author Tom Gibara
 *
 */

class CannyResults { 
	CannyResults(int h, int w) { 
		this.h = h; this.w = w;
		results = new Integer[h * w];
		gradResults = new float[h * w *2];
	}
	int w, h;
	Integer [] results;
	float [] gradResults;
	int last = 0, index = 0, length = 0;
	
	void rewind() { last = 0; index = 0; }
	void clear() { rewind(); length = 0; l.clear(); }
	ArrayList<Point> l = new ArrayList<Point>();
	
	void add(int x, int y) {
		l.add(new Point(x, y));
/*		int diff = x + y * w - last;
		while(diff > results[0].MAX_VALUE) {
			results[index++] = results[0].MAX_VALUE;
			diff -= results[0].MAX_VALUE;
		}
		if (diff > 0) 
			results[index++] = diff;
		length = index;
		last += diff;
		*/
	}
	Point next = new Point(0, 0);
}

public class CannyEdgeDetector {
	public CannyResults results = null;

	// statics	
	private final static float GAUSSIAN_CUT_OFF = 0.005f;

	// fields
	public ScanZonePair zones = new ScanZonePair();

	int yend(int x) {  
		if (zones == null) return height;
		int y = zones.yend(x) - kwidth;
		return Math.max(Math.min(y, height - kwidth), kwidth); // limit between kwidth and height - kwidth
	}
	int ystart(int x) {
		if (zones == null) return 0;
		int y = zones.ystart(x) + kwidth;
		return Math.max(Math.min(y, height - kwidth), kwidth); // limit between kwidth and height - kwidth
	}
	
	// new stuff to limit the canny processing to the area between new lines. 
	// currently unused, need to modify all iteration loops to only go from
	// xstart(y) to xend(y) for each y.  May be more diff, possibly need
	// ystart(x) and yend(x) for loops that cannot be turned inside out. 
	/*
	int xstart(int y) { 
		return Math.max(0, (m1 == 0 && b1 == 0) ? 0 : (int)((y - b1) / m1));
	}
	int xend(int y) { 
		return Math.min(width, (m2 == 0 && b2 == 0) ? width : (int)((y - b2) / m2));
	}
	*/
	int height;
	private int width;
	private int[] data;
	
	public float gaussianKernelRadius;
	public float threshold;
	public int gaussianKernelWidth;

	public float[] xConv;
	public float[] yConv;
	public float[] xGradient;
	public float[] yGradient;
	
	// constructors
	
	/**
	 * Constructs a new detector with default parameters.
	 */
	
	public CannyEdgeDetector() {
		threshold = 7.5f;
		gaussianKernelRadius = 2f;
		gaussianKernelWidth = 16;
	}
	
	/**
	 * The number of pixels across which the Gaussian kernel is applied.
	 * This implementation will reduce the radius if the contribution of pixel
	 * values is deemed negligable, so this is actually a maximum radius.
	 * 
	 * @param gaussianKernelWidth a radius for the convolution operation in
	 * pixels, at least 2.
	 */
	
	public void setGaussianKernelWidth(int gaussianKernelWidth) {
		if (gaussianKernelWidth < 2) throw new IllegalArgumentException();
		this.gaussianKernelWidth = gaussianKernelWidth;
	} 
	
	/**
	 * Sets the radius of the Gaussian convolution kernel used to smooth the
	 * source image prior to gradient calculation.
	 * 
	 * @return a Gaussian kernel radius in pixels, must exceed 0.1f.
	 */
	
	public void setGaussianKernelRadius(float gaussianKernelRadius) {
		if (gaussianKernelRadius < 0.1f) throw new IllegalArgumentException();
		this.gaussianKernelRadius = gaussianKernelRadius;
	}

	public void processData(int []d, int pw, int ph, Rectangle r) {
		if (r == null) {
			r = new Rectangle(0, 0, pw, ph);
		}
		width = r.width;
		height = r.height;
		zones.height = height;
		zones.width = width;
		zones.lsz.m1 = zones.lsz.m2 = Double.NaN;

		initArrays();
		results.clear();

		for(int x = 0; x < r.width; x++) {
			int lasty = yend(x);
			for (int y = ystart(x); y < lasty; y++) {
				data[x + y * width] = d[(r.y + y) * pw + r.x + x];
			}
		}
		process2();
	}

	public void processData(OriginalImage i, Rectangle r) { 
		if (r == null) 
			r = new Rectangle(0, 0, i.width, i.height);
		width = r.width;
		height = r.height;
		zones.height = height;
		zones.width = width;
		initArrays();
		results.clear();
	
		for(int x = 0; x < r.width; x++) {
			int lasty = yend(x);
			for (int y = ystart(x); y < lasty; y++) {
				// TODO exception right here with y=-2147483538
				data[x + y * width] = i.getPixelLum(r.x + x, r.y + y);
			}
		}

		process2();
	}
	public void process2() {
		computeGradients(gaussianKernelRadius, gaussianKernelWidth);
	}

	public int [] getData() { return data; } 
	
	private void initArrays() {
		final int picsize = width * height;
		if (data == null || picsize != data.length) {
			data = new int[picsize];
			//magnitude = new int[picsize];

			xConv = new float[picsize];
			yConv = new float[picsize];
			xGradient = new float[picsize];
			yGradient = new float[picsize];
		}
		// TODO- figure out border artifact problems, this is 
		// pretty inefficient
		data = new int[picsize];
		results = new CannyResults(width, height);
	}

	float kernel[];
	float diffKernel[];
	int kwidth;

	int oldKernelWidth = 0;
	float oldKernelRadius = 0;
	public void makeKernel(float kernelRadius, int kernelWidth) { 
		//generate the gaussian convolution masks, cache them until kernelWidth or kernelRadius changes
		
		if (oldKernelWidth != kernelWidth || oldKernelRadius != kernelRadius) {
			kernel = new float[kernelWidth];
			diffKernel = new float[kernelWidth];
			for (kwidth = 0; kwidth < kernelWidth; kwidth++) {
				float g1 = gaussian(kwidth, kernelRadius);
				if (g1 <= GAUSSIAN_CUT_OFF && kwidth >= 2) break;
				float g2 = gaussian(kwidth - 0.5f, kernelRadius);
				float g3 = gaussian(kwidth + 0.5f, kernelRadius);
				kernel[kwidth] = (g1 + g2 + g3) / 3f / (2f * (float) Math.PI * kernelRadius * kernelRadius);
				diffKernel[kwidth] = g3 - g2;
			}
			oldKernelRadius = kernelRadius;
			oldKernelWidth = kernelWidth;
		}
	}	
	
	
	// do gradients for column x.  Needs convultion results xConv/yConv to be completed
	// up to column and including (x + kwidth - 1)
	private void doGradients(int x) {
		int initY = width * (ystart(x) + (kwidth - 1));
		int maxY = width * (yend(x) - (kwidth - 1));
		for (int y = initY; y < maxY; y += width) {
			float sum = 0f;
			int index = x + y;
			for (int i = 1; i < kwidth; i++)
				sum += diffKernel[i] * (yConv[index - i] - yConv[index + i]);

			xGradient[index] = sum;
		}

		for (int y = initY; y < maxY; y += width) {
			float sum = 0.0f;
			int index = x + y;
			int yOffset = width;
			for (int i = 1; i < kwidth; i++) {
				sum += diffKernel[i] * (xConv[index - yOffset] - xConv[index + yOffset]);
				yOffset += width;
			}
			yGradient[index] = sum;
		}
	}

	double getGradient(int x, int y)  {
		final int index = x + y * width;
		final float xGrad = xGradient[index];
		final float yGrad = yGradient[index];
		final float gradMag = hypot(xGrad, yGrad);
		return gradMag;
	}
	// compute magnitude for column x.  Needs gradients to be complete through
	// column x + 1, inclusive.  
	void doMagnitude(int x) { 
		// clear the top and bottom fringes of this column
		int maxY = Math.min(height, ystart(x) + kwidth);
		for(int y = ystart(x); y < maxY; y++) 
			data[x + y * width] = 0;
		maxY = yend(x);
		for(int y = Math.max(0, yend(x) - kwidth); y < maxY; y++) 
			data[x + y * width] = 0;
		
		
		final int initY = (ystart(x) + kwidth + 1);
		maxY = (yend(x) - (kwidth + 1));
		for (int yY = initY; yY < maxY; yY++) {
			final int index = x + yY * width;
			final int indexN = index - width;
			final int indexS = index + width;
			final int indexW = index - 1;
			final int indexE = index + 1;
			final int indexNW = indexN - 1;
			final int indexNE = indexN + 1;
			final int indexSW = indexS - 1;
			final int indexSE = indexS + 1;
			
			final float xGrad = xGradient[index];
			final float yGrad = yGradient[index];
			final float gradMag = hypot(xGrad, yGrad);

			/*
			 * An explanation of what's happening here, for those who want
			 * to understand the source: This performs the "non-maximal
			 * supression" phase of the Canny edge detection in which we
			 * need to compare the gradient magnitude to that in the
			 * direction of the gradient; only if the value is a local
			 * maximum do we consider the point as an edge candidate.
			 * 
			 * We need to break the comparison into a number of different
			 * cases depending on the gradient direction so that the
			 * appropriate values can be used. To avoid computing the
			 * gradient direction, we use two simple comparisons: first we
			 * check that the partial derivatives have the same sign (1)
			 * and then we check which is larger (2). As a consequence, we
			 * have reduced the problem to one of four identical cases that
			 * each test the central gradient magnitude against the values at
			 * two points with 'identical support'; what this means is that
			 * the geometry required to accurately interpolate the magnitude
			 * of gradient function at those points has an identical
			 * geometry (upto right-angled-rotation/reflection).
			 * 
			 * When comparing the central gradient to the two interpolated
			 * values, we avoid performing any divisions by multiplying both
			 * sides of each inequality by the greater of the two partial
			 * derivatives. The common comparand is stored in a temporary
			 * variable (3) and reused in the mirror case (4).
			 * 
			 */
			/*
			if (xGrad * yGrad <= 0f // (1)
					? Math.abs(xGrad) >= Math.abs(yGrad) // (2)
						? (tmp = Math.abs(xGrad * gradMag)) >= Math.abs(yGrad * neMag - (xGrad + yGrad) * eMag) 
							&& tmp > Math.abs(yGrad * swMag - (xGrad + yGrad) * wMag) 
						: (tmp = Math.abs(yGrad * gradMag)) >= Math.abs(xGrad * neMag - (yGrad + xGrad) * nMag) 
							&& tmp > Math.abs(xGrad * swMag - (yGrad + xGrad) * sMag) 
					: Math.abs(xGrad) >= Math.abs(yGrad) //(2)
						? (tmp = Math.abs(xGrad * gradMag)) >= Math.abs(yGrad * seMag + (xGrad - yGrad) * eMag) 
							&& tmp > Math.abs(yGrad * nwMag + (xGrad - yGrad) * wMag) 
						: (tmp = Math.abs(yGrad * gradMag)) >= Math.abs(xGrad * seMag + (yGrad - xGrad) * sMag) 
							&& tmp > Math.abs(xGrad * nwMag + (yGrad - xGrad) * nMag)
				) {
			*/
			//perform non-maximal supressionarray
/*
			float nMag = hypot(xGradient[indexN], yGradient[indexN]);
			float sMag = hypot(xGradient[indexS], yGradient[indexS]);
			float wMag = hypot(xGradient[indexW], yGradient[indexW]);
			float eMag = hypot(xGradient[indexE], yGradient[indexE]);
			float neMag = hypot(xGradient[indexNE], yGradient[indexNE]);
			float seMag = hypot(xGradient[indexSE], yGradient[indexSE]);
			float swMag = hypot(xGradient[indexSW], yGradient[indexSW]);
			float nwMag = hypot(xGradient[indexNW], yGradient[indexNW]);
			*/
			float tmp;
			boolean r = false;
			if (xGrad * yGrad <= 0f) {  /*(1)*/
				if (Math.abs(xGrad) >= Math.abs(yGrad)) {  /*(2)*/
					final float eMag = hypot(xGradient[indexE], yGradient[indexE]);
					final float neMag = hypot(xGradient[indexNE], yGradient[indexNE]);
					if ((tmp = Math.abs(xGrad * gradMag)) >= Math.abs(yGrad * neMag - (xGrad + yGrad) * eMag)) {
						float swMag = hypot(xGradient[indexSW], yGradient[indexSW]);
						float wMag = hypot(xGradient[indexW], yGradient[indexW]);
						if (tmp > Math.abs(yGrad * swMag - (xGrad + yGrad) * wMag)) 
							r = true;
					}
				} else { 
					final float neMag = hypot(xGradient[indexNE], yGradient[indexNE]);
					final float nMag = hypot(xGradient[indexN], yGradient[indexN]);
					if ((tmp = Math.abs(yGrad * gradMag)) >= Math.abs(xGrad * neMag - (yGrad + xGrad) * nMag)) {
						final float swMag = hypot(xGradient[indexSW], yGradient[indexSW]);
						final float sMag = hypot(xGradient[indexS], yGradient[indexS]);
						if (tmp > Math.abs(xGrad * swMag - (yGrad + xGrad) * sMag))
							r = true;
					}
				}
			} else { 
				if (Math.abs(xGrad) >= Math.abs(yGrad))  { /*(2)*/
					final float eMag = hypot(xGradient[indexE], yGradient[indexE]);
					final float seMag = hypot(xGradient[indexSE], yGradient[indexSE]);
					if ((tmp = Math.abs(xGrad * gradMag)) >= Math.abs(yGrad * seMag + (xGrad - yGrad) * eMag)) { 
						final float wMag = hypot(xGradient[indexW], yGradient[indexW]);
						final float nwMag = hypot(xGradient[indexNW], yGradient[indexNW]);
						if (tmp > Math.abs(yGrad * nwMag + (xGrad - yGrad) * wMag)) { 
							r = true; 
						}
					}
				} else { 
					final float seMag = hypot(xGradient[indexSE], yGradient[indexSE]);
					final float sMag = hypot(xGradient[indexS], yGradient[indexS]);
					if ((tmp = Math.abs(yGrad * gradMag)) >= Math.abs(xGrad * seMag + (yGrad - xGrad) * sMag)) { 
						final float nMag = hypot(xGradient[indexN], yGradient[indexN]);
						final float nwMag = hypot(xGradient[indexNW], yGradient[indexNW]);
						if (tmp > Math.abs(xGrad * nwMag + (yGrad - xGrad) * nMag)) {
							r = true;/*(4)*/
						}	
					}
				}
			}
			if (r) 
				results.gradResults[index] = gradMag;
			if (r && gradMag > threshold) { 
					data[index] = -1;
					results.add(x, yY);
			} else 
				data[index] = 0;			
		}
	}
		
	
	//NOTE: The elements of the method below (specifically the technique for
	//non-maximal suppression and the technique for gradient computation)
	//are derived from an implementation posted in the following forum (with the
	//clear intent of others using the code):
	//  http://forum.java.sun.com/thread.jspa?threadID=546211&start=45&tstart=0
	//My code effectively mimics the algorithm exhibited above.
	//Since I don't know the providence of the code that was posted it is a
	//possibility (though I think a very remote one) that this code violates
	//someone's intellectual property rights. If this concerns you feel free to
	//contact me for an alternative, though less efficient, implementation.
	
	private void computeGradients(float kernelRadius, int kernelWidth) {
		// set up kernel, diffKernel, and kwidth values
		makeKernel(gaussianKernelRadius, gaussianKernelWidth);
		
		int initX = kwidth - 1;
		int maxX = width - (kwidth - 1);
		int initY = width * (kwidth - 1);
		int maxY = width * (height - (kwidth - 1));
		int pixels = 0;

		//perform convolution in x and y directions
		for (int x = initX; x < maxX; x++) {
			initY = width * (ystart(x) + (kwidth - 1));
			maxY = width * (yend(x) - (kwidth - 1));
			for (int y = initY; y < maxY; y += width) {
				pixels++;
				if (y == initY)
					pixels += kwidth * 2;
				int index = x + y;
				float sumX = data[index] * kernel[0];
				float sumY = sumX;
				int xOffset = 1;
				int yOffset = width;
				for(; xOffset < kwidth ;) {
					// Array bounds exception here!  -159 
					sumY += kernel[xOffset] * (data[index - yOffset] + data[index + yOffset]);
					sumX += kernel[xOffset] * (data[index - xOffset] + data[index + xOffset]);
					yOffset += width;
					xOffset++;
				}
				
				yConv[index] = sumY;
				xConv[index] = sumX;
			}
			
			// figure out which columns now have enough data to proceed with gradient & mag computation 
			if (x >= initX + kwidth - 1) 
				doGradients(x - kwidth + 1);
			if (x >= initX + kwidth + 1 && x < width - kwidth - 1) 
				doMagnitude(x - kwidth);   // clobbers data[x - kwidth] if reusing data array
				
		}
		//int bytes = pixels * (Integer.SIZE + Float.SIZE * 5);
		//System.out.println("pixels touched = " + pixels + ", approx " + (bytes / 1024) + "KB");	}
	}
	//NOTE: It is quite feasible to replace the implementation of this method
	//with one which only loosely approximates the hypot function. I've tested
	//simple approximations such as Math.abs(x) + Math.abs(y) and they work fine.
	private static float hypot(float x, float y) {
		//return (float) Math.hypot(x, y);
		return Math.abs(x) + Math.abs(y);
	}
 
	private static float gaussian(float x, float sigma) {
		return (float) Math.exp(-(x * x) / (2f * sigma * sigma));
	}
}
