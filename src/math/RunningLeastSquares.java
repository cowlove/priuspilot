package math;

import java.util.ArrayDeque;


/*
 * Tips for expanding this into a general running quadratic or linear least squares regression 
 * Based on 
 * http://mathforum.org/library/drmath/view/72047.html
 * 
 *  
 * maintain sums such that  
 * Sab  is sum of X^a*Y^b for all each x and y in the data set. 
 * 
 * so when adding a point x,y with weight w
 * 
 *  S00 += 1 * w;
 *  S10 += x * w;
 *  S20 += x * x * w;
 *  S30 += x * x * x * w;
 *  S40 += x * x * x * x * w;
 *  S01 += y * w;
 *  S11 += x * y * w;
 *  S21 += x * x * y * w;
 *  S31 += x * x * x * y * w;
 *  S41 += x * x * x * x * y * w;
 * 
 *  Then the parameters a,b,c of the best fit equation y = ax^2 + bx + c
 *  are given by 
 *  
  a = (S01*S10*S30 - S11*S00*S30 - S01*S20*S20
       + S11*S10*S20 + S21*S00*S20 - S21*S10*S10)
    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20)

  b = (S11*S00*S40 - S01*S10*S40 + S01*S20*S30
       - S21*S00*S30 - S11*S20*S20 + S21*S10*S20)
    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20)

  c = (S01*S20*S40 - S11*S10*S40 - S01*S30*S30
       + S11*S20*S30 + S21*S10*S30 - S21*S20*S20)
    /(S00*S20*S40 - S10*S10*S40 - S00*S30*S30 + 2*S10*S20*S30 - S20*S20*S20)
 
  
  and the parameters m and b of the best fit y = mx + b are given by 
  
  m = (S00 * S11 - S10 * S01) / (S00 * S20 - S10 * S10)
  b = (S01 - m * S10) / S00 
  
  and of course the average is given by 
  
  y = S01 / S00  (sum of y's divided by count)
  
  variance and sum of errors could probably also be computed from these sums, dunno
  
 */
public class RunningLeastSquares extends LeastSquares {
    public double[] xs, ys, weights;
    public int index = 0, size = 0;
    double totalWeight = 0;

    public RunningLeastSquares(int s) {
        size = s;
        xs = new double[size];
        ys = new double[size];
        weights = new double[size];
    }

    @Override
    public void add(double x, double y) { 
    	add(x, y, 1.0);
    }

    public double slope() {
        if (count < 2) return 0.0;
        return (totalWeight * XY - X * Y) / (totalWeight * XX - X * X);
	}
	public double intercept() { 
		if (count < 1) return 0.0;
		return (Y - slope() * X) / (totalWeight);
	}
    
    public double predict(double x) { 
    	return slope() * x + intercept();
    }
    public double averageY() {
    	return totalWeight > 0 ? Y / totalWeight : 0;
    }
    public double totalWeight() { 
    	return totalWeight;
    }
    public void add(double x, double y, double w) {
        if (count == size) {
            X -= xs[index] * weights[index];
            Y -= ys[index] * weights[index];
            XX -= xs[index] * xs[index] * weights[index];
            XY -= xs[index] * ys[index] * weights[index];
            totalWeight -= weights[index];
        }
        
        weights[index] = w;
        xs[index] = x;
        ys[index++] = y;
        
        if (index >= size) {
            index = 0;
        }
        if (count < size) {
            count++;
        }

        X += x * w;
        Y += y * w;
        XX += x * x * w;
        XY += x * y * w;
        totalWeight += w;
    }

    public double rmsError() {
        double s = 0;
        for (int i = 0; i < count; i++) {
            int idx = (index + size - count + i) % size;
            double e = err(xs[idx], ys[idx]);
            s += e * e;
        }
        return Math.sqrt(s / count);
    }

    public void shrink(int newsize) {
        size = newsize;
        count = index = 0;
    }

    public void grow(int newsize) {
        if (newsize > size) {
            if (true /*newsize > xs.length*/) {
                int newindex = 0;
                double[] newxs = new double[newsize], newys = new double[newsize];
                double[] newwt = new double[newsize];
                int newcount = Math.min(count, newsize);
                int i = (index + (size - count)) % size;
                for (newindex = 0; newindex < newcount; newindex++) {
                    newxs[newindex] = xs[i];
                    newys[newindex] = ys[i];
                    newwt[newindex] = weights[i];
                    i = (i + 1) % size;
                }
                size = newsize;
                index = newindex % newsize;
                count = newcount;
                xs = newxs;
                ys = newys;
                weights = newwt;
            } else {
                index = count;
                size = newsize;
            }
        }
    }

    public void clear() {
        count = index = 0;
        X = Y = XX = XY = totalWeight = 0.0;
    }

    public void removeLast() {
        count--;
        index--;
        if (index < 0) {
            index = size - 1;
        }

        X -= xs[index] * weights[index];
        Y -= ys[index] * weights[index];
        XX -= xs[index] * xs[index] * weights[index];
        XY -= xs[index] * ys[index] * weights[index];
        totalWeight -= weights[index];
    }
}
		
