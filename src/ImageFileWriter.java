import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import javax.imageio.ImageIO;


class ImageFileWriter {
    String fmt = "";
    int fileno = 0;
    File out = null;
    FileOutputStream fos;
    ByteBuffer bb;
    IntBuffer ib;
    String dateString = "";
    public ImageFileWriter(String f) {
        fmt = f;
    }
    boolean active = true;
    int count = 0;
    
    public void restartFile(String d) { 
    	close();
    	fos = null;
    	dateString = d;
    	if (fmt.endsWith(".dat"))
    	  	active = !active;
    	else if (fmt.endsWith(".yuv")) { 
    		active = !active;
	    	if (Silly.fc != null) { 
	    		String filename = String.format(fmt, dateString);
	    		if (active) {
		    		Silly.fc.setCaptureFile(filename);
	    			System.out.println("Opened yuv output file " + filename);
	    		} else { 
		    		Silly.fc.setCaptureFile("");
	    			System.out.println("Closed yuv output file");
	    		}
    		}
    	}
    	else 
    		active = true;
    	  
    }
    public void write(long time, BufferedImage img, OriginalImage orig) throws IOException {
        if (fmt.length() > 1 && active) {
        	if (fmt.endsWith(".gif") || fmt.endsWith(".png")) {
        		out = new File(String.format(fmt, ++fileno));
        		try {
        			if (fmt.endsWith(".gif"))
        				ImageIO.write(img, "GIF", out);
        			else if (fmt.endsWith(".png"))
        				ImageIO.write(img, "PNG", out);
        				
	            } catch (IOException e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
	            }
        	} else if(fmt.endsWith(".dat")) { 
        		if (fos == null) {  
        			String filename = String.format(fmt, dateString);
        			fos = new FileOutputStream(new File(filename));
        			bb = ByteBuffer.allocate(8);
        			ib = bb.asIntBuffer();
        			System.out.println("Opened output video file " + filename);
        		}
        		ib.rewind();
        		bb.rewind();
        		//rgb.rewind();
        		ib.put((int)(time >> 32));
        		ib.put((int)(time & 0xffffffff));
        		//ib.put(rgb);
        		fos.getChannel().write(bb);
        		orig.writeOut(fos.getChannel());
        		
        		// tmp- limit file sizes to 2000 frames
        		if (count++ > 2000) {
        				active = false;
        				close();
        				System.out.println("Automatically closing .dat image file");
        		}
        	} else if(fmt.endsWith(".yuv")) { 
        		if (count++ > 8000) {
    				active = false;
    				close();
    				System.out.println("Automatically closing .yuv image file");
        		}
    		}
        }
    }
    void close() {
    	count = 0;
    	if (fos != null) {
			try {
				fos.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	if (Silly.fc != null) 
    		Silly.fc.setCaptureFile("");
    }
}
