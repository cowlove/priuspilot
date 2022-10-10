import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
interface LogWriter { 
	public void write(String s);
	public void flush();
	public void close();
}

class SynchWriter implements LogWriter { 
	BufferedWriter writer = null;
	SynchWriter(String fn) { 
		String filename = fn;
		try {
			writer = new BufferedWriter(new FileWriter(filename));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			writer = null;
		}
		System.out.println("Synchwriter is Opening logfile ");       			
	}
	@Override public void close() {
		try {
			writer.flush();
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	@Override
	public void flush() {
		try {
			writer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
    @Override  
    public void write(String s) { 
		try {
			writer.write(s);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
}

class AsynchWriter implements LogWriter { 
	AsynchWriter(String fn) { 
		filename = fn;
		try {
			writer = new BufferedWriter(new FileWriter(filename));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		t.start();
		System.out.println("Opening logfile ");       			
	}
	ArrayList<String> buffer = new ArrayList<String>();
	
	String filename = null;
	BufferedWriter writer = null;
	boolean done = false;
	
	@Override
	public void flush() {}
	
    Thread t = new Thread (new Runnable() {
        public void run() {
        	while(!done) {
        		String s = null;
            	synchronized(buffer) {
            		try {
						if (buffer.size() == 0) 
							buffer.wait();
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
	        		if (buffer.size() > 0) { 
	        			s = buffer.remove(0);
	        		}
            	}
            	if (s != null)
					try {
						writer.write(s);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
        	}
        }
    });

    @Override 
    public void close() {
		done = true;
		synchronized(buffer) { 
    		buffer.notify();
    	}
    }
    @Override  
    public void write(String s) { 
    	synchronized(buffer) { 
    		buffer.add(s);
    		buffer.notify();
    	}
    }
}

class Logfile {
	String filename = "";
	LogWriter writer = null;
	String dateString = "";
	
	public Logfile(String fn) {
		filename = fn;
	}
	public void write(String s) { 	
		if (filename.length() > 0) { 
	       	if (filename.compareTo("-") == 0) 
	       		System.out.println(s);
	       	else {
	       		s += "\n";
	       		if (writer == null) {
	       			String fn = String.format(filename, dateString);
	       			writer = new SynchWriter(fn); 
	     			System.out.println("Opening logfile " + fn);       			
	       		}
	       		writer.write(s);	       		
	       	}
	    }
	}
	public void close() { 
		if (writer != null)
			writer.close();
		writer = null;
	}
	public void restartFile(String s) { 
		dateString = s;
		close();
	}
}