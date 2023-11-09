import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;



class TargetFinderRed extends TargetFinder { 
	double houghGaussian = 0.3f;
	double houghThreshold = 0.9f;
    BufferedImageDisplay dd = null; 
	
	// parameters used in searching for taillights / pairs of red dots
	// night seems to need H.low drop down to about 126
	final float redPairMaxAngle = 3.0f;
    final int redPairMinWidth = 20, redPairMaxWidth = 150, redPairBorder = 1;
    
    // hate you
    int [] toIntArray(byte []in) { 
    	int []out = new int[in.length];
    	for(int i = 0; i < in.length; i++)
    		out[i] = in[i];
    	return out;
    }
    byte [] toByteArray(int []in) { 
    	byte []out = new byte[in.length];
    	for(int i = 0; i < in.length; i++)
    		out[i] = (byte)(in[i] & 0xff);
    	return out;
    }
	int [] toARGBfromGreyInt(int []in) { 
    	int []out = new int[in.length];
    	for(int i = 0; i < in.length; i++) { 
    		int x = in[i] & 0xff;
    		out[i] = 0xff000000 | (x << 16) | (x << 8) | x;
    	}	
    	return out;
	}
    
	TargetFinderRed(int w, int h) {
		super(w, h);
		param.threshold1  = 6;
		param.threshold2  = 2;
		param.gaussianKernelRadius = 0.5f;
		param.H = new ByteSpan(145, 220);   
		param.S = new ByteSpan(70, 256);
		param.L = new ByteSpan(70, 230);

		if (Silly.debug("SHOW_TF"))
			dd = new BufferedImageDisplay(w, h, BufferedImage.TYPE_INT_ARGB);
	}
	
	@Override 
	Rectangle [] findAll(OriginalImage oi, Rectangle saRec) {
		//saRec.width = width / 3;
		//saRec.x = (width - saRec.width) / 2 + 10;
		//saRec.height = (int)(height * .6f);
		sa = saRec;
		
		c.zones.height = sa.height;
		c.zones.clear();
	
		c.threshold = param.threshold1;
		c.setGaussianKernelRadius(param.gaussianKernelRadius);
		c.setGaussianKernelWidth(param.gaussianKernelWidth);
		border = (int)(param.gaussianKernelRadius * 5);
		fudge = 0;
		
		// Define a sub-area of the sa rect to search for red taillight pairs.
		Rectangle redSa = (Rectangle)sa.clone();
		
		//redSa.height = sa.height / 2;  // this doesn't show the blackout areas in orig image
		//redSa.y += sa.height / 2;
		final int hslbpp = 3;
		byte [] hslpic = oi.getHslRect(redSa);
		
		
		// Before hsl conversion, run canny and mask off non-interesting parts	 
		c.processData(oi, redSa);
		boolean [] mask = makeCannyRadiusMask(c.getData(), redSa.width, redSa.height, 5);
		//applyMask(mask, hslpic);
  
		// apply red filter,
		int [] red = new int[redSa.height * redSa.width];
		for(int i = 0; i < hslpic.length; i += hslbpp) {
        	int h = (int)hslpic[i] & 0xff;
        	int s = (int)hslpic[i+1] & 0xff;
        	int l = (int)hslpic[i+2] & 0xff;

        	if (param.H.in(h) && param.S.in(s) && param.L.in(l)) {
        		red[i / hslbpp] = 255;
        		int x = (i / hslbpp) % redSa.width;
        		int y = (i / hslbpp) / redSa.width;
        		x += redSa.x - sa.x;
        		y += redSa.y - sa.y;
        		/*
        		orig[(x + y * sa.width) * bpp] = 0;
        		orig[(x + y * sa.width) * bpp + 1] = 0;
        		orig[(x + y * sa.width) * bpp + 2] = 0;
        		*/
        	} else {
        		red[i / hslbpp] = l / 3;
        	}
        }

        GaussianKernel blur = new GaussianKernel(1.5f, 15, redSa.width, redSa.height);
        //blur.blur(red);
        //System.out.printf("blur max=%f, bestX=%d, bestY=%d\n", blur.max, blur.bestX, blur.bestY);
 
        
        ArrayList<Point> points = new ArrayList<Point>();
        int [] tmpRed = red.clone();
        NonmaxSuppression ns = new NonmaxSuppression(redSa.width, redSa.height);
        ns.suppressNonmax(tmpRed, 10, 0.10f/*threshold*/, points);
        ArrayList<Point> allPoints = new ArrayList<Point>(points);
        final int ddSecondImageOffset = redSa.width + 10;
        if (dd != null) { 
	        dd.clearBackground(Color.lightGray);
	        dd.setData(0, 0, redSa.width, redSa.height, toARGBfromGreyInt(red));
	        dd.setData(ddSecondImageOffset, 0, redSa.width, redSa.height, toARGBfromGreyInt(tmpRed));
	        //dd.redraw();
        }


        // iterate through list of red areas, eliminating some based on various tests. 
        for(int i = 0; false && i < points.size(); i++) { 
    		Point a = points.get(i);
    		int pix = tmpRed[a.x + a.y * redSa.width];
    		String ddLabel = String.format("%d%%", pix * 100 / ns.max);

    		// remove points not in the lower half of the search area
    		int minY = redSa.height / 3;
    		int maxY = redSa.height - 1;
    		if (false && dd != null) {
    			dd.g2.setColor(Color.yellow);
    			dd.g2.drawLine(0, minY, redSa.width - 1, minY);
    			dd.g2.drawLine(0, maxY, redSa.width - 1, maxY);
    		}
    		if (a.y < minY || a.y > maxY) { 
       			points.remove(i);
    			i--;
    			continue;
    		}
       		if (false && dd != null) 
    			dd.annotate(a.x, a.y, Color.red, ddLabel);
   		
    		// Remove areas that don't meet the intensity threshold
    		if (pix < ns.max * param.nonmaxThreshold) { 
    			points.remove(i);
    			i--;
    			continue;
    		}
       		if (dd != null) 
    			dd.annotate(a.x, a.y, Color.green, ddLabel);

    		// Remove areas that don't have an x/y and y/x size ratio less than maxRatio
    		int dx = blobXRadius(red, redSa, a);
    		int dy = blobYRadius(red, redSa, a);
       		final double maxRatio = 2.0f;
       		if (dx > dy * maxRatio || dy > dx * maxRatio) { 
       			points.remove(i);
       			i--;
       			continue;
       		}
       		if (dd != null) 
       			dd.annotate(a.x, a.y, Color.yellow, ddLabel);
        }

        for(int i = 0; false && i < points.size(); i++) { 
    		Point a = points.get(i);       
    		if (dd != null) 
    			dd.annotate(a.x + ddSecondImageOffset, a.y, Color.red, String.format("%d", i));
        }

        Rectangle best = null;
        int bestScore = 0;
        ArrayList<Rectangle> rects = new ArrayList<Rectangle>();

        int ddYTextLine = redSa.height + 10;
        for(int i = 0; i < points.size(); i++) { 
    		for(int j = i + 1; j < points.size(); j++) { 
        		Point b = (Point)points.get(j).clone();
        		Point a = (Point)points.get(i).clone();
        		double ang = a.x == b.x ? 90 : Math.toDegrees(Math.atan(Math.abs(a.y - b.y) / Math.abs(a.x - b.x)));
        		if (ang > redPairMaxAngle) 
        			continue;


        		/*
				int symCorr = testRedSymmetry(red, redSa, a, b);
        		if (dd != null) {  
           			dd.annotate(a.x + ddSecondImageOffset, a.y,  Color.green, String.format("%d", i));
           			dd.annotate(b.x + ddSecondImageOffset, b.y, Color.green, String.format("%d", j));
           			dd.annotate(10, ddYTextLine, Color.red, String.format("P%d-P%d: %d", i, j, symCorr));
           			ddYTextLine += 10;
        		}
				*/
        		
        		double redEdgeThresh = .5;
        		if (a.x < b.x) { 
	        		horizontalThreshold(red, redSa.width, redSa.height, a, b, redEdgeThresh);
        		} else { 
	        		horizontalThreshold(red, redSa.width, redSa.height, b, a, redEdgeThresh);
        		} 
	            int x = Math.min(a.x, b.x) - redPairBorder;
                int y = Math.min(a.y, b.y);
                int w = Math.abs(a.x - b.x) + redPairBorder * 2;
                
                // Form rectangle around suspected taillights. Convert from redSa to sa offests and ensure
                // rect is entirely within sa bounds
                Rectangle r = new Rectangle(x, Math.round(y - w * .4f), w, w);
                r.x += (redSa.x - sa.x);
                r.y += (redSa.y - sa.y);
                if (r.x < 0 || r.y < 0 || r.x + r.width >= sa.width || r.y + r.height >= sa.height ||
                		w < redPairMinWidth || w > redPairMaxWidth)
                	continue;
                
                // see if the rectangle can be vertically extended to include possible trailer-top marker lights
                for(Point p : allPoints) { 
                	double xerr = (double)Math.abs(p.x - (r.x + r.width /  2)) / r.width;
                	double aspect = (double)(r.y + r.height - p.y) / r.width;
                   	if (xerr < 0.2 && aspect > 1.5) {
                   		System.out.print(r.toString() + ", " + p.toString() + String.format(" xerr=%.2f, aspect=%.2f\n", 
                   				xerr, aspect));
                   	}
                	if (xerr < 0.06 && aspect > 1.55 && aspect < 2.05) {
                		r.height += r.y - p.y + redPairBorder;
                		r.y = p.y - redPairBorder;
                		break;
                	}
                }
                
                int symErr = 0; //testSymmetry(oi, sa, r);
                //System.out.printf("collinear points (%d, %d)=%d and ", a.x, a.y, red[a.x + a.y * sa.width]);
                //System.out.printf("(%d, %d)=%d rect %d,%d,%dx%d \tsym=%d\n", b.x, b.y, red[b.x + b.y * sa.width], 
                //		r.x, r.y, r.width, r.height, symErr);       
                
                // convert to global offsets
                r.x += sa.x;
                r.y += sa.y;
         		if (best == null || symErr < bestScore) {		
                	best = r;
                	bestScore = symErr;
         		}
         		if (symErr <= param.maxSymErr) 
    				rects.add(r); 
        	}
        }
        if (dd != null) 
        	dd.done(null);
        if (best != null && bestScore > param.maxSymErr) // add best if it wasn't already added
        	rects.add(best);

        if (Silly.debug("CONT_TF")) {  // we're doing TF debugging, go ahead and mess up the image 
        	//drawCannyLines(orig);        
        	//copyout(orig, oi, sa);
        }
		count++;
		Rectangle [] rval = new Rectangle[rects.size()];
		return rects.toArray(rval);
	}
	
	// compute mirror image correlation (not pixel error) of the two specified rid areas
	private int testRedSymmetry(int []pic, Rectangle r, Point a, Point b) { 
		int xsa = blobXRadius(pic, r, a);
		int ysa = blobYRadius(pic, r, a);
		int xsb = blobXRadius(pic, r, b);
		int ysb = blobYRadius(pic, r, b);
	
		int xs = Math.max(xsa, xsb);
		int ys = Math.max(ysa, ysb);

		double sum = 0;
		for(int y = -ys; y <= +ys; y++) { 	
			for(int x = -xs; x <= +xs; x++) {
				int pa = 0, pb = 0;
				if (a.y + y >= 0 && a.y + y < r.height && a.x + x >= 0 && b.x + x < r.width) 
					pa = pic[a.x + x + (a.y + y) * r.width];
				if (b.y + y >= 0 && b.y + y < r.height && b.x - x >= 0 && b.x + x < r.width) 
					pb = pic[b.x - x + (b.y + y) * r.width];
				
				sum += pa * pb;
			}
		}
		int pixels = (xs * 2 + 1) * (ys * 2 + 1);
		return (int)Math.round(sum / pixels);
	}

	// compute mean pixel error for horizontal symmetry in the given rectangle area
	private int testSymmetry(byte[] pic, Rectangle r) {
//		int sum = 0;
//		for(int y = r.y; y <= r.y + r.height; y++) { 	
//			for(int x = 0; x < r.width / 2; x++) { 
//				for(int b = 0; b < bpp; b++) {
//					int x1 = r.x + x;
//					int x2 = r.x + r.width - x;
//					int p1 = (int)pic[(x1 + y * sa.width) * bpp + b] & 0xff;
//					int p2 = (int)pic[(x2 + y * sa.width) * bpp + b] & 0xff;
//					int err = Math.abs(p1 - p2);
//					//if (b == 0 && err > 128)  // HSL angular value
//					//	err = (255 - err) * 2;
//					sum += err * err;
//				}
//			}
//		}
//		return sum / sa.width / sa.height;
		return 0;
	}

	// move Points a and b outward from each other until pixel values fall beneath the specified threshold
	// used to find the outermost edges of the taillight pair 
	Point horizontalThreshold(int []p, int w, int h, Point a, Point b, double thresh) { 
		int amin = (int)Math.round(p[a.x + a.y * w] * thresh);
		int bmin = (int)Math.round(p[b.x + b.y * w] * thresh);
		while(a.x >= 0 && a.x < w && a.y >= 0 && a.y < h && p[a.x + a.y * w] >= amin &&
			  b.x >= 0 && b.x < w && b.y >= 0 && b.y < h && p[b.x + b.y * w] >= bmin) { 
			a.x--;
			b.x++;
		}
		return a;
	}

	int count = 0;

	int blobXRadius(int []pic, Rectangle r, Point a) { 
		int dx = 0;
		while(a.x + dx < r.width && a.x - dx >= 0 && pic[a.x + dx + a.y * r.width] > 0 && pic[a.x - dx + a.y * r.width] > 0)
			dx++;
		return dx;
	}
	int blobYRadius(int []pic, Rectangle r, Point a) { 
		int dy = 0;
  		while(a.y + dy < r.height && a.y - dy >= 0 && pic[a.x + (a.y + dy) * r.width] > 0 && pic[a.x + (a.y - dy) * r.width] > 0)
			dy++;
		return dy;
	}
	
}

