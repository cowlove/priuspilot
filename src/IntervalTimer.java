import java.util.Calendar;

//import math.RunningAverage;


class IntervalTimer {
    IntervalTimer(int a) { av = new RunningAverage(a); }
    RunningAverage av;
    long last = 0;
    public long total = 0;
    long tick() {
        long now = Calendar.getInstance().getTimeInMillis();
        long elapsed = 0;
         if (last != 0) {
            elapsed = now - last;
            av.add((double)elapsed);
         }
        last = now;
        total++;
        return elapsed;
    }
    void start() { 
        last = Calendar.getInstance().getTimeInMillis();    	
    }
    double average() { return av.calculate(); }
}