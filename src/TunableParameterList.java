import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class TunableParameter { 
	char key;
	String desc;
	int places;
	public interface Adjust { 
		abstract double adjust(double v);
		//abstract String valueAsString(); 
	}
	public interface Print { 
		abstract String print();
	}
	public Print valueAsString = new Print() { public String print() { 
		return String.format("%." + places + "f", adjuster.adjust(0)); 
	} }; 
	double increment;
	Adjust adjuster;
	TunableParameter(String d, char k, double i, Adjust a, Print p) {
		desc = d; key = k; increment = i; adjuster = a;
		if (i >= 1) places = 0;
		else if (i >= .1) places = 1;
		else if (i >= .01) places = 2;
		else if (i >= .001) places = 3;
		else places = 4;
		
		if (p != null)
			valueAsString = p;
	}
	void adjust(int direction) { 
		adjuster.adjust(increment * direction);
	}
	String asString() { 
		return String.format("'" + desc + "' (key '" + key + "') is now " + valueAsString.print());
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
	void selectIndex(int i) { 
		if (i >= 0 && i < ps.size()) 
			current = ps.get(i).key;
	}
	void selectNext(int dir) { 
		TunableParameter p = currentParam();
		if (p == null) { 
			p = ps.get(0);
		} 
		int i = ps.indexOf(p);
		i += dir;
		if (i < 0) i = ps.size() - 1;
		if (i >= ps.size()) i = 0;
		current = ps.get(i).key;
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
		adjustParam(current, dir);
	}
	void adjustParam(int pk, int dir) { 
		TunableParameter p = findParam(pk);
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
		printParam(current);
	}
	void printParam(int pk) {
		TunableParameter p = findParam(pk);;
		if (p != null) 
			System.out.println(p.asString());
		
	}
	
}

