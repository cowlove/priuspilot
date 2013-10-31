package math;

public class Geometry {

	public class Range {

	    double min, max;

	    Range(double mi, double ma) {
	        min = mi;
	        max = ma;
	    }

	    boolean within(double x) {
	        return x >= min && x <= max;
	    }
	}

	public class Point {
	    public double x, y;
	    public Point(double x1, double y1) {
	        x = x1;
	        y = y1;
	    }
	}

	public class Line {

	    public double m;
	    public double b;

	    Line(double m1, double b1) {
	        m = m1;
	        b = b1;
	    }
	}

	public class LinePair {

	    public LinePair(Line a1, Line b1) {
	        a = a1;
	        b = b1;
	    }
	    public Line a, b;

	    boolean between(Point p) {
	        return Geometry.between(a, p, b);
	    }
	}

    public static Line lineFromPointSlope(Point p, double s) {
        double b = p.y - (int) (s * p.x);
        return new Geometry().new Line(s, b);
    }

    public static Line lineFromSlopePointDistance(double m, Point p, double d) {
        double pm = -1.0 / m;
        double dx = (int) (Math.sqrt(1.0 * d * d / (1.0 + pm * pm)) * d / Math.abs(d));
        double dy = (int) (dx * pm);
        double x = p.x - dx;
        double y = p.y - dy;
        return lineFromPointSlope(new Geometry().new Point(x, y), m);
    }

    static boolean between(double a, double b, double c) {
        return Math.min(a, c) < b && b < Math.max(a, c);
    }

    static boolean between(Line a, Point b, Line c) {
        int ay = (int) (b.x * a.m + a.b);
        int cy = (int) (b.x * c.m + c.b);
        return between(ay, b.y, cy);
    }

    static boolean between(Line a, Line b, Line c) {
        return between(a.m, b.m, c.m) && between(a.b, b.b, c.b);
    }

    static Point lineIntersect(Line l1, Line l2) {
        // calculate the intersection of L&R lines, the new vanishing point
        double a1 = -l1.m, c1 = -l1.b;
        double a2 = -l2.m, c2 = -l2.b;
        double x = (c2 - c1) / (a1 - a2);
        double y = (c1 * a2 - c2 * a1) / (a1 - a2);
        return new Geometry().new Point((int) x, (int) y);
    }
}
