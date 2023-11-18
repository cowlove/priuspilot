//package math;

public class RunningAverage extends Average { 
	public double [] values;
	int [] weights;
	int index = 0;
	public int size = 0;
	public int weight = 0;
	public RunningAverage(int s) {
		size = s;
		values = new double[size];
		weights = new int[size];
	}
	public int totalWeight() { return weight; } 
	public double calculate() { 
		if (weight > 0) 
			return sum / weight;
		else 
			return 0;	
	}
	public void add(double x) { add(x, 1); } 
	public void add(double x, int w) { 
		if (count == size) {
			sum -= values[index];
			weight -= weights[index];
		}
		weights[index] = w;
		values[index++] = x * w;
		
		if (index >= size)
			index = 0;
		if (count < size)
			count++;
		
		weight += w; 
		sum += x * w;
	}
	public void clear() { sum = weight = count = index = 0; }
    
	public void validate() {
		if (Double.isInfinite(calculate()) || Double.isNaN(calculate()))
			clear();
    }
}
