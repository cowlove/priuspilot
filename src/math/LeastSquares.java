package math;

public class LeastSquares { 
	public double X = 0, Y = 0, XX = 0, XY = 0;
	public int count = 0;
	public void clear() { X = Y = XX = XY = 0; count = 0; } 
	public void add(double x, double y) { 
		count++;
        X += x;
        Y += y;
        XX += x * x;
        XY += x * y;
	}
	public double slope() {
        if (count < 2) return 0.0;
        return (count * XY - X * Y) / (count * XX - X * X);
	}
	public double intercept() { 
		if (count < 1) return 0.0;
		return (Y - slope() * X) / count;
	}
	public double err(double x, double y) {
		return Math.abs((-x * slope()) + y - intercept()) / 
			Math.sqrt(slope() * slope() + 1);
	}
}
