import java.awt.Point;
import java.util.ArrayList;


public class NonmaxSuppression {
	int h, w;
	NonmaxSuppression(int w, int h) { this.h = h; this.w = w; }
	
	int max = 0, bestX = 0, bestY = 0;
	
	void suppressNonmax(int pic[], int dist, float thresh, ArrayList<Point> points) {
		int [] pic2 = new int[h * w];
		if (points != null) 
			points.clear();
		
		for (int x = 0; x < w; x++) { 
			for(int y = 0; y < h; y++) {				
				boolean foundBetter = false;
				int sum = 0;
				for (int dx = x - dist; dx <= x + dist && !foundBetter; dx++) {
					int rdist = dist;//(int)Math.floor(Math.sqrt(dist * dist - (x - dx) * (x - dx))) + 1; 
					for (int dy = y - rdist; dy <= y + rdist && !foundBetter; dy++) {  
						if (dy >= 0 && dy < h && dx >= 0 && dx < w) {
							if ((dy != y || dx != x)) { 
								if (pic[dx + dy * w] > pic[x + y * w])
									foundBetter = true;
								// break ties to keep tied points from excluding each other
								if (pic[dx + dy * w] == pic[x + y * w] && (dy < y || (dy == y && dx < x)))
									foundBetter = true;
							}
							//sum += pic[dx + dy * w];
						}
					}
				}
				if (foundBetter == false) {   
					//System.out.printf("found local maxima a=%d,r=%d\n", a, r);
					pic2[x + y * w] = pic[x + y * w]; //sum;
					if (pic2[x + y * w] > max) {
						max = pic2[x + y * w];
						bestX = x;
						bestY = y;
					}
				} else 
					pic2[x + y * w] = 0;
			}
		}
		
		// copy back into pic[], and remove all lower than specified threshold. 
		for (int x = 0; x < w; x++) { 
			for(int y = 0; y < h; y++) {
				int pixel = pic2[x + y * w];
				if (pixel > 0 && pixel >= thresh * max) {
					if (points != null)
						points.add(new Point(x, y));
					pic[x + y * w] = pixel;
				} else 
					pic[x + y * w] = 0;
			}
		}
	}

	public void suppressNonmaxTODO(float[] hough, int dist, float thresh, ArrayList<Point> pts) {
		// TODO Auto-generated method stub
		assert(false);
	}
}
