import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;




public class GnuplotWrapper{
	String conf = "";
	String filename;
	String title;
	BufferedWriter writer = null;
	Process process = null;
	OutputStreamWriter out = null;
	BufferedReader in = null;
	int myInstance;
	static int instance = 0;
	GnuplotWrapper() { 
		myInstance = instance++;
		filename = String.format("/tmp/gpw.%d.dat", instance++);
	}
	int count = 0;
	
	void setDisplay() throws IOException {
		boolean toFile = false;
		if (toFile) { 
		    out.write("set term png\n");
	        out.write("set output \"" + filename + String.format(".%04d.png\"\n", count));		
		} else { 
			String s= String.format("set terminal x11 noraise size 300,200 position 500,%d\n", myInstance * 125);
			out.write(s);
			//System.out.print(s);
		}
	}
	
	void draw() { 
		try {
			writer.close();
			count++;
			Runtime.getRuntime().exec("mv " + filename + " " + filename + ".1");
			if (process == null) { 
				process = Runtime.getRuntime().exec("gnuplot -noraise -geometry +400+0");
	            OutputStream stdin = process.getOutputStream ();
	            InputStream stderr = process.getErrorStream ();
	            InputStream stdout = process.getInputStream ();

	            //in = new InputStreamReader(process.getInputStream());
	            in= new BufferedReader (new InputStreamReader(stderr));
	            //BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));

				out = new OutputStreamWriter(process.getOutputStream());
	            out.write("set pm3d map\n");
	            out.write("set colorbox\n");
	            
	            setDisplay();

	            out.write("splot \"" + filename + ".1\"" +
	            		" with pm3d\n"
	            		//" with lines linecolor rgb \"#000000\"\n"
	            		);
	            out.flush();


			} else { 
				setDisplay();
	            out.write("set title \"" + title + "\"\n");
	            out.write("replot\n");
	            out.write("print \"done\"\n");
	            out.flush();
	            String line;
	            do {
	            	line = in.readLine();
	            } while(!line.contains("done"));
			}
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	void draw3D() { 
		try {
			writer.close();
			Runtime.getRuntime().exec("mv " + filename + " " + filename + ".1");
			if (process == null) { 
				process = Runtime.getRuntime().exec("gnuplot");
	            out = new OutputStreamWriter(process.getOutputStream());
	            out.write("set pm3d at b\nunset colorbox\n");
	            out.write("splot \"" + filename + ".1\" with lines linecolor rgb #000000\n");
	            out.flush();	            
			}
            out.write("replot\n");
            out.flush();
	        	
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	void draw2D(int ns) { 
		try {
			writer.close();
			Runtime.getRuntime().exec("mv " + filename + " " + filename + ".1");
			if (process == null) { 
				process = Runtime.getRuntime().exec("gnuplot");
	            out = new OutputStreamWriter(process.getOutputStream());
	            setDisplay();
	            out.write("plot ");
	            for (int n = 0; n < ns; n++) { 
	            	out.write(String.format("\"" + filename + ".1\" u 1:%d with lines ", n + 2));
	            	if (options != null && options[n] != null) 
	            		out.write(options[n]);
	            	if (n == ns - 1) out.write("\n");
	            	else out.write(",");
	            }
	            out.flush();	            
			}
            out.write("replot\n");
            out.flush();
	        	
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	void startNew() { 
		try {
			writer = new BufferedWriter(new FileWriter(filename));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	void add3DGrid(float []data, int xs, int ys) { 
		try {
			for(int x = 0; x < xs; x++) { 
				for(int y = 0; y < ys; y++) { 
					writer.write(String.format("%d %d %f\n", x, y, data[x + y * xs]));
				}
				writer.write("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	void add3DGrid(int []data, int xs, int ys) { 
		try {
			for(int x = 0; x < xs; x++) { 
				for(int y = 0; y < ys; y++) { 
					writer.write(String.format("%d %d %d\n", x, y, data[x + y * xs]));
				}
				writer.write("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	void addXY(double x, double y) { 
		try {
			writer.write(String.format("%.3f %.3f\n", x, y));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	void addXYZ(double x, double y, double z) { 
		
	}

	public void add3DGrid(double[] data, int xs, int ys) {
		try {
			for(int x = 0; x < xs; x++) { 
				for(int y = 0; y < ys; y++) { 
					writer.write(String.format("%d %d %f\n", x, y, data[x + y * xs]));
				}
				writer.write("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void addArray(double[] x, int len) {
		try {
			for (int i = 0; i < len;i ++) 
				writer.write(String.format("%d %f\n", i, x[i]));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void addArrays(double[][] x, int len) {
		try {
			for (int n = 0; n < len; n++) { 
				writer.write(String.format("%d ", n));
				for (int i = 0; i < x.length;i ++) {
					if (x[i] != null)
						writer.write(String.format("%f ", x[i][n]));
				}
				writer.write("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	String[] options = null;
	public void addOptions(String []strings) {
		options = strings;
	}

}
