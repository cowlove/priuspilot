import java.util.Calendar;


class SteeringTestPulseGenerator { 
    final static int TEST_TYPE_CONSTANT_OFFSET = 0;
    final static int TEST_TYPE_SINE = 1;
    final static int TEST_TYPE_SAWTOOTH = 2;
    final static int TEST_TYPE_SQUARE = 3;
    final static int TEST_TYPE_FLIPFLOP = 4;
	final static int TEST_TYPE_LAST = 4;	
	    
    int testType = TEST_TYPE_SINE;
	final String [] testTypeNames = {"TEST_TYPE_CONSTANT_OFFSET", 
			"TEST_TYPE_SINE",
			"TEST_TYPE_SAWTOOTH",
			"TEST_TYPE_SQUARE",
			"TEST_TYPE_FLIPFLOP"};
	
    double duration = 0.15;
    double magnitude = 0.15; //0.10;
    double offset = 0.0;
    long startTime = 0;
    int direction = 1;
    int count = -1;

    double changeTestType(int x) { 
    	testType = Math.max(Math.min(TEST_TYPE_LAST, testType + x), 0);
    	return (double)testType;
    }
    
    void startPulse(long ms, int dir) { 
    	startTime = ms;
		direction = dir;
    	count = 1;
    	if (testType == TEST_TYPE_CONSTANT_OFFSET) {
    		offset += magnitude * dir;	
    		System.out.printf("Steering constant offset now %.2f\n", offset);
    	}
    }
    
    double currentPulse(long ms, long frames) {
    	double t = (1.0 * ms - startTime) / 1000;
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
		case TEST_TYPE_FLIPFLOP:
			// flip between -magnitude/+magnitude every <duration> frames
			r = ((frames / (int)Math.floor(duration)) % 2) == 0.0 ? -magnitude : magnitude;
			if (Double.isNaN(r) || Double.isInfinite(r)) r = 0;
			return r + offset;
    	}
    }
   

}