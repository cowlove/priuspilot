//package math;


public class Average {
    public double sum = 0;
    public int count = 0;
    public void add(double x) { sum += x; count++; }
    public void add(double x, int w) { sum += x * w; count += w; }
    public void clear() { count = 0; sum = 0; } 
    public double calculate() { if (count > 0) return sum / count; else return 0; }
}
