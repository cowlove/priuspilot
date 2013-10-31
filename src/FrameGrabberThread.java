import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


class FrameGrabberThread extends Thread { 
	FrameProcessor fp; 
	FrameGrabberThread(FrameProcessor fp) {
		this.fp = fp;
		   this.start();
	}
    public final FrameInfo frame = new FrameInfo();
    
	void post(boolean drop, long time, OriginalImage rgb) { 
	      synchronized (frame) {
	            while (drop == false && frame.rgb != null) {
	                try {
	                    frame.wait();
	                } catch (InterruptedException ex) {
	                   // Logger.getLogger(VidListener.class.getName()).log(Level.SEVERE, null, ex);
	                }
	            }
	            frame.rgb = rgb;
	            frame.time = time;
	            frame.dropped++;
	            frame.notify();
	        }
		
	}
	@Override
    public void run() {
    	IntervalTimer intTimer = new IntervalTimer(1);
        while (true) {
            OriginalImage rgb;
            long time;
            synchronized (frame) {
                while (frame.rgb == null) {
                    try {
                        frame.wait();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(FrameProcessor.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                rgb = frame.rgb;
                frame.rgb = null;
                time = frame.time;
                fp.framesDropped = --frame.dropped;
                frame.notify();
            }
            try {
				fp.processFrame(time, rgb);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }
	synchronized public boolean spaceAvailable() {
		// TODO Auto-generated method stub
		return frame.rgb == null;
	}	
}

//import com.centralnexus.input.Joystick;


// TODO - add -click frame,x,y arguments to command line and -key frame,'key' to command line
// TODO - add -continuousTarget commandline flag 
// TODO - abort target lock when target moves out of target find search area. 
// TODO fix errors when manual lock after moving recticle with clicks
// TODO investigate best-box symmetry search for target finding
// TODO investigate "closed circles" search for target finding
// TODO - add intentional steering wobble to help investigate steering pauses. 

// TODO - rotating log with "keep" button
// TODO fix PID so that periods are specified in time, not frames.  Hope that this fixes differences
// between 30fps and 10fps operation.  
// TODO - mmmap v4l2 interface

// backspace - triggers TargetFinder to set template
// space - cancels current operations, aborts TargetFinder, aborts TemplateDetector, returns target to center screen
// space - toggles back on TemplateDetector

class FrameInfo {
    OriginalImage rgb = null;
    int dropped = 0;
    long time;
}