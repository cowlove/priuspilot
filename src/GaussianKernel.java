
class GaussianKernel { 
	private final static float GAUSSIAN_CUT_OFF = 0.005f;
	int height, width;
	GaussianKernel(double r, int w, int pw, int ph) {
		makeKernel(r, w);
		height = ph; width = pw;
	}
	public float [] kernel = null;
	public int kwidth;
	void makeKernel(double radius, int w) { 
		kernel = new float[w];
		for (kwidth = 0; kwidth < w; kwidth++) {
			float g1 = gaussian(kwidth, radius);
			if (g1 <= GAUSSIAN_CUT_OFF && kwidth >= 2) 
				break;
			float g2 = gaussian(kwidth - 0.5f, radius);
			float g3 = gaussian(kwidth + 0.5f, radius);
			kernel[kwidth] = (g1 + g2 + g3) / 3f / (float)Math.sqrt(2f * (float) Math.PI * radius * radius);
		}
	}
	private float gaussian(double x, double sigma) {
		return (float)Math.exp(-(x * x) / (2f * sigma * sigma));
	}
	
	int max = 0, bestX = 0, bestY = 0;
	
	void blur(int [] pic) {
		int [] pic2 = new int[height * width];
		for (int x = 0; x < width; x++) { 
			for(int y = 0; y < height; y++) {				
				float gausSum = 0;
				for (int dx = x - kwidth + 1; dx < x + kwidth; dx++) {
					if (dx >= 0 && dx < width) { 
						float g = kernel[Math.abs(x - dx)];
						gausSum += g * pic[dx + y * width];
					}
				}
				pic2[x + y * width] = Math.round(gausSum);
			}
		}
	
		max = 0;
		for (int x = 0; x < width; x++) { 
			for(int y = 0; y < height; y++) {				
				float gausSum = 0;
				for (int dy = y - kwidth + 1; dy < y + kwidth; dy++) {   
					if (dy >= 0 && dy < height) {
						float g = kernel[Math.abs(y - dy)];
						gausSum += g * pic2[x + dy * width];
					}
				}
				int score = Math.round(gausSum);
				pic[x + y * width] = score;
				if (score > max || (score == max && y < bestY)) { 
					max = score;
					bestX = x;
					bestY = y;
				}
			}	
		}
	}

	public int bestColumn(int[] p) {
		int bestCol = -1, best = -1;
		for(int x = 0; x < width; x++) {
			int sum = 0;
			for(int y = 0; y < height; y++) {
				int v = p[x + y * width];
				sum += v * v;
			}
			if (sum > best) {
				bestCol = x;
				best = sum;
			}
		}
		return bestCol;
	}

}