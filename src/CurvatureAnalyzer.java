import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;

//import math.RunningAverage;
//import math.RunningQuadraticLeastSquares;


public class CurvatureAnalyzer {
	double maxSeedError = 3;
	double maxGrowError = 8.5;
	double maxGrowGap = 10;
	double growSegmentSize = 0.15;
	double seedSlope, seedIntercept;
	int height, width;
	boolean leftSide = true;
	
	CurvatureAnalyzer(boolean l, int w, int h) { 
		height = h;
		width = w;
		leftSide = l;
	}
	RunningQuadraticLeastSquares fit;
	
/*	class Entry { 
		int x, y;
		double err;
		Entry(int x1, int y1, double e) { x = x1; y = y1; err = e; }
	}
*/	int count = 0;
	int minYFollow = 0;
	
	public boolean valid() { 
		return fit != null && Math.abs(fit.a) < 0.8 && minYFollow < height / 3 &&
		fit.getCount() == fit.maxCount;
	}
	public double getCurve() {
		// todo- check quality and variance of data, return NaN
		return valid() ? fit.a : Double.NaN;
	}

	// seed the solver with points from the calculated angle/intercept line, from pixel 
	// rows height to height/2
	public void seedCurve(Rectangle offset, ArrayList<Point> l, double angle, int botInt) {
		seedSlope = Math.tan(Math.toRadians(angle));
		seedIntercept = botInt - height / seedSlope;
		fit = new RunningQuadraticLeastSquares(2, (int)(height * growSegmentSize), 0);
		int startY = height;
		if (botInt > width) 
			startY = (int)(height - (botInt - width) * seedSlope);
	
		for (int y = startY - 1; y > startY / 2; y--) { 
			int x = (int)Math.round(botInt - (height - y) / seedSlope);
			fit.add(y, x);
		}
	}

	public void growCurve(Rectangle offset, ArrayList<Point> l) { 
		// start with the average Y value of the established region, proceed downward
		// picking best match from each row, then proceed upward from the starting 
		// point
		int growGap = 0;
		boolean seenFirst = false;
		
		for(int y = height / 3 * 2; y >= 0 && growGap < maxGrowGap; y--) {
			int bestX = -1;
			double bestErr = 0;
			for(Point p : l) {
				int x = p.x + offset.x;
				if (p.y + offset.y == y)  {
					double err = Math.abs(fit.pointError(p.y + offset.y, p.x + offset.x));
					if (err <= maxGrowError && (bestX < 0 || (!leftSide && x < bestX) || (leftSide && x > bestX))) { 
						bestX = x;
						bestErr = err;
					}
				}
			}
			if (bestX >= 0) {
				growGap = 0;
				fit.add(y, bestX);
				seenFirst = true;
				minYFollow = y;
			} else if(seenFirst) 
				++growGap;
		}		
	}
	
	public void markup(OriginalImage orig) {
		if (false && fit != null) { 
			for(RunningQuadraticLeastSquares.Entry e : fit.hist) {
				orig.putPixel((int)e.y, (int)e.x, 0);
				//orig.putPixel((int)caR.fit.calculate(e.x), (int)e.x, 0xffff0000);
			}		
		}
	}
	
	public void display(Graphics2D g2) {
		if (!valid()) 
			return;
		
		int lastX = -1, lastY = -1;
		for(RunningQuadraticLeastSquares.Entry e : fit.hist) {
			//int x = (int)e.y;
			int x = (int)fit.calculate(e.x);
			int y = (int)e.x;
			if (lastY > 0)  
				g2.drawLine(lastX, lastY, x, y);
			lastX = x; 
			lastY = y;
		}		
	}
}
