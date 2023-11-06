import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import java.util.zip.GZIPInputStream;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.BasicStroke;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;


import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;

//import math.RunningAverage;
//import math.RunningLeastSquares;
/*
import org.gstreamer.Bus;
import org.gstreamer.Element;
import org.gstreamer.Gst;
import org.gstreamer.GstObject;
import org.gstreamer.Pipeline;
import org.gstreamer.State;

import com.centralnexus.input.*;
*/
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;


//import com.jme3.input.*;

// mplayer -tv device=/dev/video0:driver=v4l2:height=240:width=320  -vo jpeg tv://
// java -classpath /home/jim/workspace/WebCamCap/bin:/home/jim/Downloads/jna-3.2.4.jar:/home/jim/Downloads/gstreamer-java-1.3.jar:/home/jim/Downloads/swt.jar Silly
//  java -classpath /home/jim/NetBeansProjects/WebCamCap/build/classes/:/home/jim/Downloads/jna-3.2.4.jar:/home/jim/Downloads/gstreamer-java-1.3.jar:/home/jim/Downloads/swt.jar Silly
// 
// todo- args
// -latencytest <filename,filename...>


/*
class VidListener implements org.gstreamer.elements.RGBDataSink.Listener {

    final FrameGrabberThread ft;
    boolean dropFrames;
   
    public VidListener(boolean dropFrames, FrameGrabberThread ft) {
        this.ft = ft;
        this.dropFrames = dropFrames;
    }

    public void rgbFrame(int w, int h, IntBuffer rgb) {
    	
    	//ft.post(dropFrames, Calendar.getInstance().getTimeInMillis(), rgb);
    }
}
*/
class TemplateArgs {
	public int x1 = 0, y1, x2, y2;
	boolean isValid() { return x1 > 0; } 
	void set(String s) {
        String [] temp = s.split(",");
        x1 = Integer.parseInt(temp[0]);
        y1 = Integer.parseInt(temp[1]);
        x2 = Integer.parseInt(temp[2]);
        y1 = Integer.parseInt(temp[3]);
	}
}

public class Silly {
    /**
     * @param args
     * @throws IOException
     * @throws IOException
     */
	static ByteBuffer rgb32toBgr24(ByteBuffer bb) {
		int picsize = bb.capacity();
		ByteBuffer newbb = ByteBuffer.allocate(picsize * 3 / 4);
		int offset = 0;
		for(int i = 0; i < bb.capacity(); i += 4) { 
			byte r = bb.get(i + 1);
			byte g = bb.get(i + 2);
			byte b = bb.get(i + 3);
			newbb.put(offset++, b);
			newbb.put(offset++, g);
			newbb.put(offset++, r);
		}
		newbb.rewind();
		//System.out.println("Shrinkin rgb32 " + bb.capacity() + " bytes to bgr24 " + (offset) + " bytes");
		return newbb;
	}
	static long now() { 
		return Calendar.getInstance().getTimeInMillis();
	}
	
	/*
	public static int debugFlags = 0;
	public static final int DEBUG_FPS = 1;
	public static final int DEBUG_CONT_TF = 4;
	public static final int DEBUG_SHOW_TF = 8;
	public static final int DEBUG_SERIAL = 16;
	public static final int DEBUG_LINES = 32;
	public static fstainal int DEBUG_MARKUP = 64;
	public static final int DEBUG_COPY_IMAGE = 128; 
	*/
	
	static CarSim sim = null;
	static FrameCaptureJNI fc;
	static FrameCaptureJNI swCam;
    public static void main(String[] args) throws IOException, InterruptedException {
        //args = Gst.init("VideoPlayer", args);

        String filename = "/dev/video3", capFile = "", swrc = null;
        int width = 320, height = 240;
        int windx = 0, windy = 0, windw = width, windh = height;
        int framerate = 30;
        int rescale = 1;
        String outputFile = null, serialDevice = null, logspec = null, logFile = null, chartFile = null, rawOutputFile = null;
        boolean dropFrames = true, noDisplay = false, repeat = false, gstreamer = false, rgb32 = false;
        int skipFrames = 0, debug = 0, capSize = 0, capCount = 0;
        double colorThreshold = 0.35;
        int frameCount = 0, pauseFrame = 0, exitFrame = 0, displayratio = 1;
        int displayMode = 15;
        int volume = 10;
        int frameInterval = 0; // minimum interval between captured frames
		String steerCmdHost = "255.255.255.255";
        boolean jni = false;
		boolean gps = false;
		String fakeGps = null;
		ArrayList<String> trimCheatFiles = new ArrayList<String>();
        boolean nightMode = false, faketime = false, useSystemClock = true, noSteer = false;
        boolean cannyDebug = false, realtime = false;
        boolean flipVideo = false;  // warning- not flipping is currently broken, see FrameCaptureJNI.cpp, flip loops is 
        // same as copyout loop 
        HashMap<Object,Object> keypressMap = new HashMap<Object,Object>();
        HashMap<Object,Point> clickMap = new HashMap<Object,Point>();
        
        int targx = 0, targy = 0, targh = 0, targw = 0;
                
        for(int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.compareTo("-nodrop") == 0) dropFrames = false;
            else if (a.compareTo("-displayratio") == 0) displayratio = Integer.parseInt(args[++i]);
            else if (a.compareTo("-volume") == 0) volume = Integer.parseInt(args[++i]);
            else if (a.compareTo("-repeat") == 0) repeat = true;
            else if (a.compareTo("-gps") == 0) gps = true;
			else if (a.compareTo("-fakeGps") == 0) fakeGps = args[++i];
			else if (a.compareTo("-trimCheat") == 0) trimCheatFiles.add(args[++i]);
            else if (a.compareTo("-rgb32") == 0) rgb32 = true;
            else if (a.compareTo("-cannyDebug") == 0) cannyDebug = true;
            else if (a.compareTo("-jni") == 0) jni = true;
            else if (a.compareTo("-nosteer") == 0) noSteer = true;
            else if (a.compareTo("-fps") == 0) framerate = Integer.parseInt(args[++i]);
            else if (a.compareTo("-rescale") == 0) rescale = Integer.parseInt(args[++i]);
            else if (a.compareTo("-skip") == 0) skipFrames = Integer.parseInt(args[++i]);
            else if (a.compareTo("-steeraddr") == 0) steerCmdHost = args[++i];
            else if (a.compareTo("-frames") == 0) frameCount = Integer.parseInt(args[++i]);
            else if (a.compareTo("-pause") == 0) pauseFrame = Integer.parseInt(args[++i]);
            else if (a.compareTo("-exit") == 0) exitFrame = Integer.parseInt(args[++i]);
            else if (a.compareTo("-displaymode") == 0) displayMode = Integer.parseInt(args[++i]);
            // depricated - use -out and let ImageFileWriter set the FrameCaptureJNI capfile 
            //else if (a.compareTo("-capfile") == 0) capFile = args[++i];
            else if (a.compareTo("-capcount") == 0) capCount = Integer.parseInt(args[++i]);
            else if (a.compareTo("-capsize") == 0) capSize = Integer.parseInt(args[++i]);
            else if (a.compareTo("-frameinterval") == 0) frameInterval = Integer.parseInt(args[++i]);
            else if (a.compareTo("-gstreamer") == 0) gstreamer = true;
            else if (a.compareTo("-flip") == 0) flipVideo = true;
            else if (a.compareTo("-night") == 0) nightMode = true;
            else if (a.compareTo("-faketime") == 0) faketime = true;
            else if (a.compareTo("-realtime") == 0) realtime = true;
            else if (a.compareTo("-systemclock") == 0) useSystemClock = !useSystemClock;
			
                                        
            else if (a.compareTo("-ct") == 0) colorThreshold = Double.parseDouble(args[++i]);
            else if (a.compareTo("-size") == 0) {
                String [] temp = args[++i].split("x");
                windw = width = Integer.parseInt(temp[0]);
                if (temp.length == 2)
                    height = Integer.parseInt(temp[1]);
                else
                    height = width * 3 / 4;
                windh = height;
                windx = windy = 0;
            }
            else if (a.compareTo("-target") == 0) {
                String [] temp = args[++i].split(",");
                if (temp.length == 4) { 
                    targx = Integer.parseInt(temp[0]);
                    targy = Integer.parseInt(temp[1]);
                    targw = Integer.parseInt(temp[2]);
                    targh = Integer.parseInt(temp[3]);
                }
            }
            else if (a.compareTo("-debug") == 0) {
            	String []f = args[++i].split("=");
            	if (f.length < 2)
            		debugOpts.put(f[0], "true");
            	else
            		debugOpts.put(f[0], f[1]);
            			
            }
            else if (a.compareTo("-key") == 0) {
            	// Support -key arguments in the format 1,65 1,A 1,a or 1,'a'
                String [] temp = args[++i].split(",");
                if (temp.length == 2) { 
                    int frame = Integer.parseInt(temp[0]);
                    int key = 0;
                    Pattern p = Pattern.compile("(\\d+)");
                    Matcher m = p.matcher(temp[1]);
                    if (m.find()) {
                      key = Integer.parseInt(m.group());
                    }   
                    p = Pattern.compile("'[^']'");
                    m = p.matcher(temp[1]);                    
                    if (key == 0 && m.find()) {
                       key = m.group().charAt(1);
                       key = Character.toUpperCase(key);
                    } 
                    if (key == 0 && temp[1].length() == 1) {
                       key = temp[1].charAt(0);
                       key = Character.toUpperCase(key);
                    } 
                    if (key == 0 && temp[1].contentEquals("up"))
                    	key = 38;
                    if (key == 0 && temp[1].contentEquals("down"))
                    	key = 40;
                    
                    keypressMap.put(Integer.valueOf(frame), Integer.valueOf(key));
                }
            }
            else if (a.compareTo("-click") == 0) {
                String [] temp = args[++i].split(",");
                if (temp.length == 3) { 
                    int frame = Integer.parseInt(temp[0]);
                    int x = Integer.parseInt(temp[1]);
                    int y = Integer.parseInt(temp[2]);
                    keypressMap.put(Integer.valueOf(frame), new Point(x,y));
                }
            }
            else if (a.compareTo("-window") == 0) {
                String [] temp = args[++i].split(",");
                if (temp.length == 4) { 
                    windx = Integer.parseInt(temp[0]);
                    windy = Integer.parseInt(temp[1]);
                    windw = Integer.parseInt(temp[2]);
                    windh = Integer.parseInt(temp[3]);
                }
            }
            else if (a.compareTo("-swrc") == 0) {
            	swrc = args[++i];
            }
            else if (a.compareTo("-serial") == 0) {
            	serialDevice = args[++i];            	
            }
            else if (a.compareTo("-template") == 0) {
            }
            else if (a.compareTo("-log") == 0) logFile = args[++i];
            else if (a.compareTo("-out") == 0) outputFile = args[++i];
            else if (a.compareTo("-chart") == 0) chartFile = args[++i];
            else if (a.compareTo("-raw") == 0) rawOutputFile = args[++i];
            else if (a.compareTo("-logspec") == 0) logspec = args[++i];
            else if (a.startsWith("-"))	usage();
            else filename = a;
        }

        /*
        ImageDisplay id = new ImageDisplay(256,256);
        int [] d = new int[256 * 256];
        for(int x = 0; x < 256; x++) { 
        	for (int y = 0; y < 256; y++) { 
        		d[x + y * 256] = 0xff000000 | x << 16 | y << 8 | (x + y) / 2;
        	}
        }
        id.image.getWritableTile(0, 0).setDataElements(0, 0, 256, 256, d);
        id.redraw(true);
        */
        /*
        GnuplotWrapper gw = new GnuplotWrapper();
        int y = 0;
        while(gw != null) { 
	        gw.startNew();    
	        for(int i = 0 ; i < 100; i++) { 
	        	gw.addXY(i + y, i + y++ * 2);
	        }
	        gw.draw();
        }
        */
        
		if (exitFrame > 0) 
			exitFrame += skipFrames;
        
        final FrameProcessor fp = new FrameProcessor(windw, windh,
        		outputFile, logFile, rescale, displayratio, serialDevice, swrc);
        fp.colorThresholdPercent = colorThreshold;
        fp.exitFrame = exitFrame;
        fp.pauseFrame = pauseFrame;
        fp.skipFrames = skipFrames;
        fp.sounds.volume = volume; 
        fp.tdChartFiles = chartFile;
        fp.logSpec = logspec;
        fp.zoom = width / windw;
        fp.keypresses = keypressMap;
        fp.clicks = clickMap;        
		fp.noSteering = noSteer;
		fp.steerCmdHost = steerCmdHost;
		if (gps) fp.gps.start();
		else if (fakeGps != null) fp.gps.startFake(fakeGps);
		for(String f : trimCheatFiles) { 
			fp.trimCheat.addFile(f);
		}
		
        if (displayMode > 0) fp.displayMode = displayMode;


        if (targx != 0) { 
        	fp.tdStartX = targx;
        	fp.tdStartY = targy;
        	fp.tdTargetSize = targw;
        	fp.tdTargetAspect = (double)targh / targw;
        }
        

    	if (filename.startsWith("/dev/video")) { 
    		jni = true;
    	}
    		
    	if (filename.endsWith(".yuv") || filename.equals("stdin")) {
    		//jni = true;
    		dropFrames = false;
    	}
    	
        int count = 0;    
		IntervalTimer intTimer = new IntervalTimer(30);
	
		if (filename.equals("SIM")) { 
			long ms = 0;
			sim = new CarSim(width, height);

			while(exitFrame == 0 || --exitFrame > 0) { 
				ByteBuffer bb = sim.getFrame(ms); 
				int x = (int)intTimer.tick();
				if (realtime && x < 35) {
					Thread.sleep(35 - x);
				}
				fp.processFrame(ms, new OriginalImage(bb, width, height));
				ms += 30;
			}

		} else if (filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".gif"))  { 
    		// load in still image file, scale it to current size, use it as the image 
      		try {
      	  		File in = new File(String.format(filename));
    			BufferedImage i = ImageIO.read(in);
    			BufferedImage n = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
    			Graphics2D g = n.createGraphics();
    			AffineTransform at = AffineTransform.getScaleInstance((double)width / i.getWidth(), 
    					(double)height / i.getHeight());
    			g.drawRenderedImage(i, at);
    			
    			ByteBuffer bb = ByteBuffer.allocate(width * height * 3);
     			n.getTile(0, 0).getDataElements(0, 0, width, height, bb.array());
     			long time = 0;
     			do { 
     	 			n.getTile(0, 0).getDataElements(0, 0, width, height, bb.array());
      				fp.processFrame(time++, new OriginalImage(bb, width, height));
     			} while(repeat);
     		} catch(Exception e) { 

      		}
    	} else if (filename.endsWith(".gz") || !jni) {
        	do {
        		int picsize = height * width * 2;
        		if (rgb32) picsize = height * width * 4;
        		final int PAGE_SIZE=4096;
           		picsize = (((picsize + PAGE_SIZE - 1) & (~(PAGE_SIZE - 1))));
                		
	        	ByteBuffer timebb = ByteBuffer.allocate(8);
	        	IntBuffer ib = timebb.asIntBuffer();
	        
	        	long time = 0;
	        	FileInputStream fis = new FileInputStream(new File(filename));
	        	GZIPInputStream gis = null;
	        	if (filename.endsWith(".gz"))  
	        		gis = new GZIPInputStream(fis);
	        	ByteBuffer bb = ByteBuffer.allocate(picsize);
	        	while(fis.available() > 0) {
		        	//ByteBuffer bb = ByteBuffer.allocate(picsize);
	        		timebb.rewind();
	        		ib.rewind();
	        		bb.rewind();
	        		if (gis != null) {
	    				int needed = 8;
	    				int offset = 0;
	        			while(needed > 0) { 
	        				int got = gis.read(timebb.array(), offset, needed);
	        				//System.out.println("Read " + got + " bytes, needed " + needed);
	        				needed -= got;
	        				offset += got;
	        			}
						for (int n = 0; n < 8; n++) // fake the 8 bytes consumed by the timestamp 
							bb.put((byte)0x0);
	        				
	        			needed = picsize - 8;
	        			offset = 8;
	        			while(needed > 0) { 
	        				int got = gis.read(bb.array(), offset, needed);
	        				//System.out.println("Read " + got + " bytes, needed " + needed);
	        				needed -= got;
	        				offset += got;
	        			}
	        		} else {
	        			fis.getChannel().read(timebb);  
						for (int n = 0; n < 8; n++) // fake the 8 bytes consumed by the timestamp 
							bb.put((byte)0x0);
		    			fis.getChannel().read(bb); 
	        		}
	        		
	        		if (!faketime) { 
		        		// Byte endianess for timeval 
		        		timebb.rewind();
		        		time = 0;
		        		long posval = 0;
		        		for(int bp = 0; bp < 8; bp++) {
		        			int i1 = timebb.get() & 0xff;
			        		time += ((long)i1) << (bp * 8);
		        		}
	        		} else {
	        			time += 30;
	        		}
	        		ByteBuffer finalbb = bb;
	        		if (rgb32) 
	        			finalbb = rgb32toBgr24(bb);
	        	
	        		// broken, bb contains t(the first 8-byte timestamp
	        		//ft.post(false, time, new OriginalImage(finalbb, width)); //TODO need to pass time
        			int ms = (int)intTimer.tick();
					if (realtime && ms < 30 && fp.skipFrames  <= 0) {
						//System.out.printf("Sleeping %d ms\n", 30 - ms);
						Thread.sleep(30 - ms);
					}
	        		fp.processFrame(time, new OriginalImage(finalbb, width, height));
        			//System.out.printf("%dms\n", (int)intTimer.tick());
	        		count++;
	        		if (exitFrame >0 && count == exitFrame)
	        			break;
	        	}
	        	//fp.close();
        	} while(repeat);
 
        
        } else {
        	fc = new FrameCaptureJNI();
        	fc.configure(filename, width, height, windx, windy, windw, windh, 
        			flipVideo, capFile, capSize, capCount, 65/*max ms per frame*/,
        			0 /*raw record skip interval*/, useSystemClock);
        	if (fp.writer != null) 
        		fp.writer.fc = fc; 
        	
        	int n;
        	// BROKEN- when frames are dropped, this thread could read data into the buffer
        	// currently being used by the FrameProcessor thread.  Probably just allocate/free
        	// each buffer. 
           	ByteBuffer [] picBB = new ByteBuffer[4];
           	
        //   	if (outputFile != null) 
        //   		fc.setCaptureFile(outputFile);
           	
        	for(n = 0; n < picBB.length; n++) { 
        		picBB[n] = ByteBuffer.allocateDirect(windw * windh * 3);
        	}
        	RunningAverage avgDelay = new RunningAverage(100);
           	do { 
           		long ms = now();
       			int index = count++ % picBB.length;
       			ByteBuffer bb = picBB[index];
       			if (filename.equals("dummy")) {
       				n = windw * windh;
       				fp.noProcessing = true;
       				if (framerate > 0) 
       					Thread.sleep(1000 / framerate);
       			} else {
       				n = fc.grabFrame(bb);
	       			if (n <= 0) 
	       				fc.close();
	       			ms = fc.getFrameTimestamp();
       			}
       			// System.out.println("Got " + n + " bytes");
       			bb.rewind();
       			//  int iarray[] = ib.array();
       			//   for(int i = 0; i < width * height; i++) {
       			//  	iarray[i] = (bb.get() << 0) + (bb.get() << 8) + (bb.get() << 16);
           			// }

       			if (cannyDebug) {
       				OriginalImage oi = new OriginalImage(bb, windw, windh);
       				CannyEdgeDetector c = new CannyEdgeDetector();
       				c.threshold = 10;
       				c.setGaussianKernelRadius(0.5f);
       				c.setGaussianKernelWidth(10);
       				c.zones.height = windh;
       				c.zones.clear();
       				Rectangle r = new Rectangle(0, 0, windw, windh);
       				c.processData(oi, r);
       				System.out.printf("new frame %d\n", count);
       				for(Point p : c.results.l) 
       					System.out.printf("%d, %d\n", p.x, p.y);
       				if (count == exitFrame)
       					break;
       			} else { 
	       			if (n > 0) 
	       				fp.processFrame(ms, new OriginalImage(bb, windw, windh));
	       		}
       	 	} while(n > 0 || repeat);
        }
    	System.out.printf("%s frames %05d-%05d ", filename, skipFrames, count);
    	fp.printFinalDebugStats();
    	fp.close();
       	System.exit(0);

    }
                	
        	/*
        } else {
        	// Webcam pipeline
 	        VidListener vl = new VidListener(dropFrames, ft);
	        Element rgb = new org.gstreamer.elements.RGBDataSink("RGBDataSink", vl);
	        Pipeline pipe = null;
	
	        String caps = "video/x-raw-yuv,width=" + width + ",height=" + height + ",framerate=" + framerate + "/1";
	
	        if (filename.startsWith("/dev/video")) {
	            pipe = Pipeline.launch("v4l2src device=" + filename + " ! videoscale ! videorate ! " + caps + " ! videoscale name=last");
	        } else {
	            pipe = Pipeline.launch("filesrc location=" + filename + " ! decodebin " + " ! videoscale ! videorate ! " + caps + " ! videoscale name=last");
	        }
	        pipe.add(rgb);
	        Element e = pipe.getElementByName("last");
	        e.link(rgb);
	
	        Bus bus = pipe.getBus();
	        bus.connect(new Bus.ERROR() {
	
	            public void errorMessage(GstObject source, int code, String message) {
	                System.out.println("Error: code=" + code + " message=" + message);
	            }
	        });
	      
	        bus.connect(new Bus.EOS() {
	
	            public void endOfStream(GstObject source) {
	                System.out.println("Got EOS!");
					fp.close();
	                System.exit(0);
	            }
	        });
	
	        pipe.setState(State.PLAYING);
	        Gst.main();
        }*/
    static void usage() { 		// TODO Auto-generated method stub

        System.out.println("usage: Silly [-out <filename>] [-log <filename>] [-fps n] [-nodrop]");
        System.out.println("             [-size <h>[x<w>]] [-rescale <n>] [-displayratio <n>]");
        System.out.println("             [-displayMode <n>]");
        System.out.println("             <inputfile>");
        System.out.println("-displayMode flags: 0x1 write text, 0x2 draw lines, 0x4 show image, 0x8 show PIDS  ");
        System.out.println("-displayMode flags: 0x1 write text, 0x2 draw lines, 0x4 show image, 0x8 show PIDS  ");
        System.exit(0);
    }
    
    static Map<String,String> debugOpts = new HashMap<String,String>();
	public static boolean debug(String s) {
		String v = debugOpts.get(s);
		return v != null && v.compareToIgnoreCase("false") != 0;
	}
	public static int debugInt(String s) { 
		String v = debugOpts.get(s);
		return Integer.parseInt(v);

	}
	public static double debugDouble(String s, double def) { 
		try {
			String v = debugOpts.get(s);
			return Double.parseDouble(v);
		} catch(Exception e) { 
			return def;
		}
	}

	public static int debugInt(String s, int def) { 
		try {
			String v = debugOpts.get(s);
			return Integer.parseInt(v);
		} catch(Exception e) { 
			return def;
		}
	}
	public static double debugDouble(String s) { 
		String v = debugOpts.get(s);
		return Double.parseDouble(v);
	}
	public static String debugString(String s) { 
		return debugOpts.get(s);
	}
	
    
}
