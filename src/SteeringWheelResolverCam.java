import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.ByteBuffer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Rectangle;


class SteeringWheelResolverCam implements Runnable {
	FrameCaptureJNI cam = new FrameCaptureJNI();
	int width, height;
	BufferedImageDisplay dd = null;
	ByteBuffer bb = null;
	TargetFinderLines tf; 
	ImageFileWriter writer = null;
	SteeringWheelResolverCam(String dev, int w, int h, boolean debugDisplay) { 
		width = w;
		height = h;
		cam.configure(dev, width, height, 0, 0, width, height, false, "", 0, 0, 50, 0, false);
		cam.setCaptureFile("/host/lanedumps/swrc.yuv");
		ImageFileWriter writer = new ImageFileWriter("/host/lanedumps/swrc-%s.yuv", cam);
		bb = ByteBuffer.allocateDirect(width * height * 2);
		if (debugDisplay)
			dd = new BufferedImageDisplay(width, height, BufferedImage.TYPE_3BYTE_BGR);
		tf = new TargetFinderLines(w, h, new Rectangle(0, 0, w, h - 10), true, 0, 180, w, w, 180, 180, 0.60);
		tf.vanLimits = null;
		tf.focus.defaultIntercept = w / 2;
		tf.minAng = 0;
		tf.usePeriodDetection = false;
		tf.useLuminanceCheck = false;
		tf.param.threshold1 = tf.param.threshold2 = 8;
		tf.cannyMinPoints = 100;
		tf.cannyMaxPoints = 150;
//		tf.focus.defaultIntercept = w / 2;
	//	tf.focus.defaultAngle = 90;
		new Thread(this).start();
	}
	
	void reset() { 
		tf.reset();
	}
	void displayFrame(OriginalImage oi) { 
		if (dd == null) 
			return;		

		tf.markup(oi);
		WritableRaster r = dd.image.getWritableTile(0, 0);
		byte rgb[] = new byte[width * height * 3];
		for(int x = 0; x < width; x++)
			for(int y = 0; y < height; y++) {
			 	int i = (x + y * width) * 3;
			 	int c = oi.getPixelABGR(x, y);
		    	rgb[i] = (byte)((c & 0xff0000) >> 16);
		        rgb[i+1]  = (byte)((c & 0xff00) >> 8);
		        rgb[i+2] = (byte)(c & 0xff);
			}
		r.setDataElements(0, 0, width, height, rgb);
	
		dd.g2.setStroke(new BasicStroke(4));
        dd.g2.setColor(Color.green);
		tf.display(dd.g2, new Rectangle(0, 0, width, height), width, 3);
		
		dd.redraw();
		
		if (writer != null)
			try {
				writer.write(0, dd.image, oi);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	
	double angle, weight; 
	
	@Override
	public void run() {
		
		while(true) {
 			cam.grabFrame(bb);
			OriginalImage oi = new OriginalImage(bb, width, height);
			tf.findNearest(oi, null, 0, 0);
			angle = tf.getInstantaneousAngle();
			weight = tf.focus.getQuality();
			displayFrame(oi);
			//System.out.printf("swrc angle %.1f, int %d, quality %.1f\n", angle, tf.focus.getLastIntercept(),weight);
		}	
	}
}