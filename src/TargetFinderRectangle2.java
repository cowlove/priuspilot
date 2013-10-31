import java.awt.Point;
import java.awt.Rectangle;
import java.nio.ByteBuffer;



class TargetFinderRectangle2 extends TargetFinder { 
	TargetFinderRectangle2(int w, int h) {
		super(w, h);
		
		
		param.threshold1  = 6;
		param.threshold2  = 2;
		param.gaussianKernelRadius = 0.5f;
	}
	double houghGaussian = 0.3f;
	double houghThreshold = 0.9f;
	
	@Override 
	Rectangle [] findAll(OriginalImage oi, Rectangle rec) {
		rec.width = width / 4;
		rec.x = (width - rec.width) / 2 + 20;
		rec.height = height / 2;
		sa = rec;
		
		c.zones.height = rec.height;
		c.zones.clear();
		setCanny(param);
		
		float arange = 3;
		HoughTransform hv = new HoughTransform(20, sa.width);
		hv.setAngleRange( -arange,  + arange);
		hv.radMin = border;
		hv.radMax = sa.width - border;
		hv.origin = new Point(0,0);

		HoughTransform hh = new HoughTransform(20, sa.height);
		hh.setAngleRange(90 - arange, 90 + arange);
		hh.radMin = 0;
		hh.radMax = sa.height - border;
		hh.origin = new Point(0,0);

		c.processData(oi, rec);
        canny = c.getData();
		
		for(int y = 0; y < sa.height; y++) { 
			for(int x = 0; x < sa.width; x++) { 
				if ((canny[x + y * sa.width] & 0xff) == 0xff) {  
					hh.add(x, y);
					hv.add(x, y);
				}
			}
		}
		
		hv.blurRadius = hh.blurRadius = houghGaussian;
		hv.blur(); 
		hh.blur();

		//hv.suppressNonmax(8, houghThreshold);
		//hh.suppressNonmax(8, houghThreshold);
		
		//h.dump(String.format("/tmp/hough-blur.%04d.dat", count));
		//h.dump(String.format("/tmp/hough-suppress.%04d.dat", count));

		//hh.drawOnPic(orig, sa.width, sa.height, bpp, houghThreshold);
		//hv.drawOnPic(orig, sa.width, sa.height, bpp, houghThreshold);
		
		
		count++;
		//drawCannyLines(orig);
		//copyout(orig, c.zones, oi, rec);
	//	System.out.printf("Best angle %.1f\tbest radius %d\tbest intercept %d\tquality %d\n", h.bestAngle(), 
	//			h.bestRadius(), h.bestYIntercept(), h.maxhough);

		return null;
	}
	int count = 0;
}

