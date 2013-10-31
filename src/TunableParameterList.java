import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class TunableParameter { 
	char key;
	String desc;
	int places;
	public interface Adjust { 
		abstract double adjust(double v);
	}
	double increment;
	Adjust adjuster;
	TunableParameter(String d, char k, double i, Adjust a) { 
		desc = d; key = k; increment = i; adjuster = a;
		if (i >= 1) places = 0;
		else if (i >= .1) places = 1;
		else if (i >= .01) places = 2;
		else if (i >= .001) places = 3;
		else places = 4;
	}
	void adjust(int direction) { 
		adjuster.adjust(increment * direction);
	}
	String asString() { 
		return String.format("'" + desc + "' (key '" + key + "') is now %." + places + "f", adjuster.adjust(0));
		
	}
}


class TunableParameterList { 
	List<TunableParameter> ps = new ArrayList<TunableParameter>();
	void add(TunableParameter p) { ps.add(p); } 
	int current;
	
	void selectParam(int c) { current = c; }
        TunableParameter currentParam() {
            return findParam(current);
        }
	TunableParameter findParam(int c) {
		Iterator<TunableParameter> it = ps.iterator();
		while(it.hasNext()) { 
			TunableParameter p = (TunableParameter)it.next();
			if (p.key == c)  
				return p;
		}
		return null;
	}
	void adjustParam(int dir) { 
		TunableParameter p = currentParam();
		if (p != null) 
			p.adjust(dir);
	}
	void printAll() {
		Iterator<TunableParameter> it = ps.iterator();
		while(it.hasNext()) { 
			TunableParameter p = (TunableParameter)it.next();
			System.out.println(p.asString());
		}		
	}
	void printCurrent() {
		TunableParameter p = currentParam();
		if (p != null) 
			System.out.println(p.asString());
		
	}
	
}

