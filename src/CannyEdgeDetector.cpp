
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


#define null 0	
#define private
#define public 

#include <math.h>
#include <string.h>
#include <stdio.h>

#define PI M_PI
//#define abs(x) fabs(x)
//#define exp(x) 0
//#define abs(x) ((x) < 0 ? -(x) : (x))
#define abs(x) fabs(x) 
#define round(x) roundf(x)
//#define memset(a,b,c) while(0) {}

	static float GAUSSIAN_CUT_OFF = 0.005;
	static float MAGNITUDE_SCALE = 100;
	static float MAGNITUDE_LIMIT = 1000;
	static int MAGNITUDE_MAX = (int) (MAGNITUDE_SCALE * MAGNITUDE_LIMIT);


class CannyEdgeDetector {
#undef public
public:
#define public 
	// statics

	// fields
	//public ScanZonePair zones = new ScanZonePair();
	int yend(int x) { return height; }
	int ystart(int x) { return 0; }
	
	// new stuff to limit the canny processing to the area between new lines. 
	// currently unused, need to modify all iteration loops to only go from
	// xstart(y) to xend(y) for each y.  May be more diff, possibly need
	// ystart(x) and yend(x) for loops that cannot be turned inside out. 
	/*
	int xstart(int y) { 
		return max(0, (m1 == 0 && b1 == 0) ? 0 : (int)((y - b1) / m1));
	}
	int xend(int y) { 
		return min(width, (m2 == 0 && b2 == 0) ? width : (int)((y - b2) / m2));
	}
	*/
	int height;
	int width;
	private int picsize;
	private int *data;
	private int *magnitude;
	
	public float gaussianKernelRadius;
	public float lowThreshold;
	public float highThreshold;
	public int gaussianKernelWidth;
	private bool contrastNormalized;

	private float *xConv;
	private float *yConv;
	private float *xGradient;
	private float *yGradient;
	
	// constructors		memset(magnitude, 0, picsize * sizeof(magnitude[0]);

	
	/**
	 * Constructs a new detector with default parameters.
	 */
	
	public CannyEdgeDetector() {
		lowThreshold = 2;
		highThreshold = 8;
		gaussianKernelRadius = 2.0;
		gaussianKernelWidth = 8;
		contrastNormalized = false;
		data = null;
	}

	// accessors
	

	/**
	 * The low threshold for hysteresis. The default value is 2.5.
	 * 
	 * @return the low hysteresis threshold
	 */
	
	public float getLowThreshold() {
		return lowThreshold;
	}
	
	/**
	 * Sets the low threshold for hysteresis. Suitable values for this parameter
	 * must be determined experimentally for each application. It is nonsensical
	 * (though not prohibited) for this value to exceed the high threshold value.
	 * 
	 * @param threshold a low hysteresis threshold
	 */
	
	public void setLowThreshold(float threshold) {
		lowThreshold = threshold;
	}
 
	/**
	 * The high threshold for hysteresis. The default value is 7.5.
	 * 
	 * @return the high hysteresis threshold
	 */
	
	public float getHighThreshold() {
		return highThreshold;
	}
	
	/**
	 * Sets the high threshold for hysteresis. Suitable values for this
	 * parameter must be determined experimentally for each application. It is
	 * nonsensical (though not prohibited) for this value to be less than the
	 * low threshold value.
	 * 
	 * @param threshold a high hysteresis threshold
	 */
	
	public void setHighThreshold(float threshold) {
		highThreshold = threshold;
	}

	/**
	 * The number of pixels across which the Gaussian kernel is applied.
	 * The default value is 16.
	 * 
	 * @return the radius of the convolution operation in pixels
	 */
	
	public int getGaussianKernelWidth() {
		return gaussianKernelWidth;
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
		this->gaussianKernelWidth = gaussianKernelWidth;
	} 

	/**
	 * The radius of the Gaussian convolution kernel used to smooth the source
	 * image prio
		detector.setGaussianKernelRadius(30)r to gradient calculation. The default value is 16.
	 * 
	 * @return the Gaussian kernel radius in pixels
	 */
	
	public float getGaussianKernelRadius() {
		return gaussianKernelRadius;
	}
	
	/**
	 * Sets the radius of the Gaussian convolution kernel used to smooth the
	 * source image prior to gradient calculation.
	 * 
	 * @return a Gaussian kernel radius in pixels, must exceed 0.1f.
	 */
	
	public void setGaussianKernelRadius(float gaussianKernelRadius) {
		this->gaussianKernelRadius = gaussianKernelRadius;
	}
	
	/**
	 * Whether the luminance data extracted from the source image is normalized
	 * by linearizing its histogram prior to edge extraction. The default value
	 * is false.
	 * 
	 * @return whether the contrast is normalized
	 */
	
	public bool isContrastNormalized() {
		return contrastNormalized;
	}
	
	/**
	 * Sets whether the contrast is normalized
	 * @param contrastNormalized true if the contrast should be normalized,
	 * false otherwise
	 */
	
	public void setContrastNormalized(bool contrastNormalized) {
		this->contrastNormalized = contrastNormalized;
	}
	
	// methods

	
	//Average lAvg = new Average();
	//Average rAvg = new Average();
	

	public void processData(int w, int h, char *bb) { 
		width = w;
		height = h;
		picsize = width * height;
		initArrays();
	
		for(int x = 0; x < width; x++) {
			int lasty = yend(x);
			for (int y = ystart(x); y < lasty; y++) {
				int i = x + y * width;
				int r = bb[(i * 3 + 2)];
				int g = bb[(i * 3 + 1)];
				int b = bb[(i * 3)];
				data[i] = luminance(r, g, b);
				magnitude[i] = 0;
			}
		}

		process2();
	}

	public void process2() {
		computeGradients(gaussianKernelRadius, gaussianKernelWidth);
		int low = round(lowThreshold * MAGNITUDE_SCALE);
		int high = round( highThreshold * MAGNITUDE_SCALE);
		performHysteresis(low, high);
		thresholdEdges();
		//writeEdges(data);
	}

	public int * getData() { return data; } 
	
	private void initArrays() {
		if (data == null) {
			fprintf(stderr, "initalizing arrays to %d pixels\n", picsize);
			data = new int[picsize];
			magnitude = new int[picsize];

			xConv = new float[picsize];
			yConv = new float[picsize];
			xGradient = new float[picsize];
			yGradient = new float[picsize];
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
		
		//generate the gaussian convolution masks
		float *kernel = new float[kernelWidth];
		float *diffKernel = new float[kernelWidth];
		int kwidth;
		for (kwidth = 0; kwidth < kernelWidth; kwidth++) {
			float g1 = gaussian(kwidth, kernelRadius);
			if (g1 <= GAUSSIAN_CUT_OFF && kwidth >= 2) break;
			float g2 = gaussian(kwidth - 0.5, kernelRadius);
			float g3 = gaussian(kwidth + 0.5, kernelRadius);
			kernel[kwidth] = (g1 + g2 + g3) / 3 / (2 * (float) PI * kernelRadius * kernelRadius);
			diffKernel[kwidth] = g3 - g2;
		}
		int initX = kwidth - 1;
		int maxX = width - (kwidth - 1);
		int initY = width * (kwidth - 1);
		int maxY = width * (height - (kwidth - 1));
		
		//perform convolution in x and y directions
		for (int x = initX; x < maxX; x++) {
			initY = width * (ystart(x) + (kwidth - 1));
			maxY = width * (yend(x) - (kwidth - 1));
			for (int y = initY; y < maxY; y += width) {
				int index = x + y;
				float sumX = data[index] * kernel[0];
				float sumY = sumX;
				int xOffset = 1;
				int yOffset = width;
				for(; xOffset < kwidth ;) {
					sumY += kernel[xOffset] * (data[index - yOffset] + data[index + yOffset]);
					sumX += kernel[xOffset] * (data[index - xOffset] + data[index + xOffset]);
					yOffset += width;
					xOffset++;
				}
				
				yConv[index] = sumY;
				xConv[index] = sumX;
			}
 
		}
 
		for (int x = initX; x < maxX; x++) {
			initY = width * (ystart(x) + (kwidth - 1));
			maxY = width * (yend(x) - (kwidth - 1));
			for (int y = initY; y < maxY; y += width) {
				float sum = 0;
				int index = x + y;
				for (int i = 1; i < kwidth; i++)
					sum += diffKernel[i] * (yConv[index - i] - yConv[index + i]);
 
				xGradient[index] = sum;
			}
 
		}

		for (int x = kwidth; x < width - kwidth; x++) {
			initY = width * (ystart(x) + (kwidth - 1));
			maxY = width * (yend(x) - (kwidth - 1));
			for (int y = initY; y < maxY; y += width) {
				float sum = 0.0;
				int index = x + y;
				int yOffset = width;
				for (int i = 1; i < kwidth; i++) {
					sum += diffKernel[i] * (xConv[index - yOffset] - xConv[index + yOffset]);
					yOffset += width;
				}
 
				yGradient[index] = sum;
			}
 
		}
 
		initX = kwidth;
		maxX = width - kwidth;
		initY = width * kwidth;
		maxY = width * (height - kwidth);
		for (int x = initX; x < maxX; x++) {
			initY = width * (ystart(x) + kwidth);
			maxY = width * (yend(x) - (kwidth));
			for (int y = initY; y < maxY; y += width) {
				int index = x + y;
				int indexN = index - width;
				int indexS = index + width;
				int indexW = index - 1;
				int indexE = index + 1;
				int indexNW = indexN - 1;
				int indexNE = indexN + 1;
				int indexSW = indexS - 1;
				int indexSE = indexS + 1;
				
				float xGrad = xGradient[index];
				float yGrad = yGradient[index];
				float gradMag = hypot(xGrad, yGrad);

				//perform non-maximal supressionarray
				float nMag = hypot(xGradient[indexN], yGradient[indexN]);
				float sMag = hypot(xGradient[indexS], yGradient[indexS]);
				float wMag = hypot(xGradient[indexW], yGradient[indexW]);
				float eMag = hypot(xGradient[indexE], yGradient[indexE]);
				float neMag = hypot(xGradient[indexNE], yGradient[indexNE]);
				float seMag = hypot(xGradient[indexSE], yGradient[indexSE]);
				float swMag = hypot(xGradient[indexSW], yGradient[indexSW]);
				float nwMag = hypot(xGradient[indexNW], yGradient[indexNW]);
				float tmp;
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
				if (xGrad * yGrad <= (float) 0 /*(1)*/
					? abs(xGrad) >= abs(yGrad) /*(2)*/
						? (tmp = abs(xGrad * gradMag)) >= abs(yGrad * neMag - (xGrad + yGrad) * eMag) /*(3)*/
							&& tmp > abs(yGrad * swMag - (xGrad + yGrad) * wMag) /*(4)*/
						: (tmp = abs(yGrad * gradMag)) >= abs(xGrad * neMag - (yGrad + xGrad) * nMag) /*(3)*/
							&& tmp > abs(xGrad * swMag - (yGrad + xGrad) * sMag) /*(4)*/
					: abs(xGrad) >= abs(yGrad) /*(2)*/
						? (tmp = abs(xGrad * gradMag)) >= abs(yGrad * seMag + (xGrad - yGrad) * eMag) /*(3)*/
							&& tmp > abs(yGrad * nwMag + (xGrad - yGrad) * wMag) /*(4)*/
						: (tmp = abs(yGrad * gradMag)) >= abs(xGrad * seMag + (yGrad - xGrad) * sMag) /*(3)*/
							&& tmp > abs(xGrad * nwMag + (yGrad - xGrad) * nMag) /*(4)*/
					) {
					magnitude[index] = gradMag >= MAGNITUDE_LIMIT ? MAGNITUDE_MAX : (int) (MAGNITUDE_SCALE * gradMag);
					//NOTE: The orientation of the edge is not employed by this
					//implementation. It is a simple matter to compute it at
					//this point as: atan2(yGrad, xGrad);
				} else {
					magnitude[index] = 0;
				}
			}
		}
		delete kernel;
		delete diffKernel;
	}
 
	//NOTE: It is quite feasible to replace the implementation of this method
	//with one which only loosely approximates the hypot function. I've tested
	//simple approximations such as abs(x) + abs(y) and they work fine.
	private float hypot(float x, float y) {
		//return (float) hypot(x, y);
		
		return (float) abs(x) + abs(y);
	}
 
	private float gaussian(float x, float sigma) {
		return (float) exp(-(x * x) / (2 * sigma * sigma));
	}
 
	private void performHysteresis(int low, int high) {
		//NOTE: this implementation reuses the data array to store both
		//luminance data from the image, and edge intensity from the processing.
		//This is done for memory efficiency, other implementations may wish
		//to separate these functions.
		memset(data, 0, picsize * sizeof(data[0]));
 
		int offset = 0;
		for (int x = 0; x < width; x++) {
			//for (int y = ystart(x); y < yend(x); y++) {
			for (int y = 0; y < height; y++) {
				if (data[offset] == 0 && magnitude[offset] >= high) {
					follow(x, y, offset, low);
				}
				offset++;
			}
		}
 	}
 
	private void follow(int x1, int y1, int i1, int threshold) {
		int x0 = x1 == 0 ? x1 : x1 - 1;
		int x2 = x1 == width - 1 ? x1 : x1 + 1;
		int y0 = y1 == 0 ? y1 : y1 - 1;
		int y2 = y1 == height - 1 ? y1 : y1 + 1;
		
		data[i1] = magnitude[i1];
		for (int x = x0; x <= x2; x++) {
			for (int y = y0; y <= y2; y++) {
				int i2 = x + y * width;
				if ((y != y1 || x != x1)
					&& data[i2] == 0 
					&& magnitude[i2] >= threshold 
					//&& x >= xstart(y) && x <= xend(y) 
					&& y >= ystart(x) && y <= yend(x)
					) {
					follow(x, y, i2, threshold);
					return;
				}
			}
		}
	}

	private void thresholdEdges() {
		for (int x = 0; x < width; x++) {
			for (int y = ystart(x); y < yend(x); y++) { 
				int i = y * width + x;
				data[i] = data[i] > 0 ? -1 : 0xff000000;
			}
		}
	}
	
	
	private int luminance(float r, float g, float b) {
		return round(0.299f * r + 0.587f * g + 0.114f * b);
	}
} detector;


extern "C" int *canny(int w, int h, char *pic) { 
	detector.processData(w, h, pic);
	return detector.getData();
}

