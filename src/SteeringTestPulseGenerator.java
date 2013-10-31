import java.util.Calendar;


class SteeringTestPulseGenerator { 
    final static int TEST_TYPE_CONSTANT_OFFSET = 0;
    final static int TEST_TYPE_SINE = 1;
    final static int TEST_TYPE_SAWTOOTH = 2;
    final static int TEST_TYPE_SQUARE = 3;
    final static int TEST_TYPE_LAST = 3;	    
    int testType = TEST_TYPE_SINE;
	
    double duration = 0.20;
    double magnitude = 0.0; //0.10;
    double offset = 0.0;
    long startTime = 0;
    int direction = 1;
    int count = -1;

    double changeTestType(int x) { 
    	return (double)(testType = Math.max(Math.min(TEST_TYPE_LAST, testType + x), 0));
    }
    
    void startPulse(int dir) { 
    	startTime = Calendar.getInstance().getTimeInMillis();
    	direction = dir;
    	count = 1;
    	if (testType == TEST_TYPE_CONSTANT_OFFSET) {
    		offset += magnitude * dir;	
    		System.out.printf("Steering constant offset now %.2f\n", offset);
    	}
    }
    
    double currentPulse() { 
    	double t = ((double)Calendar.getInstance().getTimeInMillis() - startTime) / 1000;
    	if ((count >= 0 && t >= duration * count)  || testType == TEST_TYPE_CONSTANT_OFFSET)
    		return offset; 
    	
    	double r = 0;
    	switch(testType) {
    	default:
    	case TEST_TYPE_SINE:
    		r = Math.sin(t / duration * Math.PI) * magnitude * direction;
    		return r + offset;
    	case TEST_TYPE_SQUARE:
    		r = magnitude * direction;
    		if ((t / duration) % 2 > 1)
    			r *= -1;
    		return r + offset;
    	case TEST_TYPE_SAWTOOTH:
    		if ((t % duration) < duration / 2) 
    			r = magnitude * direction * (t % duration) / (duration / 2);
    		else
    			r = magnitude * direction * (1 - ((t % duration) - duration / 2) / (duration / 2));
    		if ((t / duration) % 2 > 1)
    			r *= -1;
    		return r + offset;
    	}
    }
   

}