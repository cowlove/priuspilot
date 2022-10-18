import java.awt.Point;

//import math.RunningAverage;


class RunningAveragePoint { 
	RunningAverage ax, ay;
	RunningAveragePoint(int p) { 
		ax = new RunningAverage(p);
		ay = new RunningAverage(p);
	}
	void add(Point p) { 
		ax.add(p.x);
		ay.add(p.y);
	}
	Point calculate() { 
		return new Point((int)ax.calculate(), (int)ay.calculate());
	
	}
}