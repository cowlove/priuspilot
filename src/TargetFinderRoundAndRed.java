import java.awt.Point;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;



class TargetFinderRoundAndRed extends TargetFinder {

	TargetFinderRoundAndRed(int w, int h) {
		super(w, h);
		// TODO Auto-generated constructor stub
	} 

	@Override
	Rectangle []findAll(OriginalImage oi, Rectangle rec) {
		c.zones.height = rec.height;
		c.zones.clear();
		
		//super.find(bb, rec); // sets canny
		// replicate parts of super.find here
		sa = rec;
		setCanny(param);
		
//		ByteBuffer cbb = ByteBuffer.allocate(rec.width * rec.height * bpp);
//		cbb.put(orig);
//
//		// scan zones are now set up, proceed with canny and hough processing 
//		OriginalImage coi = new OriginalImage(cbb, oi.width);
//		c.processData(rec.width, rec.height, coi);
//		c.process3();
//        canny = c.getData();
//
//        keep.clear();
//    	for(int y = 0; y < sa.height; y++) { 
//    		for(int x = 0; x < sa.width; x++) { 
//        		if ((canny[x + y * sa.width] & 0xff) == 0xff) {
//        			seen.clear(); 
//        			follow(x, y, 0);
//        		}
//        	}
//        }
//        for(Point p : keep) { 
//        	//canny[p.x + p.y * sa.width] = 0xff;
//        }
//        
//		drawCannyLines(orig);
//		copyout(orig, oi, rec);

        return null;
	}
	
	ArrayList<Point> keep = new ArrayList<Point>(); 
	Point startPoint = null;
	HashMap<Point,Integer> seen = new HashMap<Point,Integer>();
	boolean follow(int x, int y, int depth) {
		 Point p = new Point(x, y);
		 depth++;
		 if (seen.containsKey(p) && depth - seen.get(p) > 10) { 
			startPoint = null;
			seen.clear();
			return true;
		 }
		 if ((canny[x + y * sa.width] & 0xff) != 0xff) 
			 return false;

		 int er = 1; // extended scan range
		 
		 // insert surrounding points at this depth, rather than at a high depth on some returning path
		 for(int y1 = y - er; y1 <= y + er; y1++) { 
			 for(int x1 = x - er; x1 <= x + er; x1++) { 
				 if (x1 >= 0 && x1 < sa.width && y1 >= 0 && y1 < sa.height && (x1 != x || y1 != y)) {
					 Point p1 = new Point(x1, y1);
					 if ((canny[x1 + y1 * sa.width] & 0xff) != 0xff && !seen.containsKey(p1)) 
						 seen.put(p1, depth);
				 }
			 }
		 }

		 canny[x + y * sa.width] &= 0xffa0a0a0;		
		 seen.put(p, depth);
		 
		 boolean foundOne = false;
		 for(int y1 = y - 1; y1 <= y + 1; y1++) { 
			 for(int x1 = x - 1; x1 <= x + 1; x1++) { 
				 if (x1 >= 0 && x1 < sa.width && y1 >= 0 && y1 < sa.height && (x1 != x || y1 != y)) {
					 if ((canny[x1 + y1 * sa.width] & 0xff) == 0xff)
						 foundOne = true;
					 if (follow(x1, y1, depth)) { 
						keep.add(new Point(x1, y1));
						canny[x1 + y1 * sa.width] = 0xff0000fe;		
						if (startPoint == null) {
							canny[x1 + y1 * sa.width] = 0xff000001;		
							startPoint = new Point(x1, y1);
						} else if (startPoint.x == x1 && startPoint.y == y1) {
							return false;
						}
						return true;
					 }
				 }
			 }
		 }
		 
		 er = 2;
		 if (!foundOne && (bridged == 0 || --bridged == 0 )) { 
			 bridged = 5;
			 for(int y1 = y - er; y1 <= y + er; y1 ++) { 
				 for(int x1 = x - er; x1 <= x + er; x1 ++) { 
					 if (x1 >= 0 && x1 < sa.width && y1 >= 0 && y1 < sa.height && (x1 != x || y1 != y)) {
						 if (follow(x1, y1, depth)) { 
								keep.add(new Point(x1, y1));
								canny[x1 + y1 * sa.width] = 0xff00ff01;		
								if (startPoint == null) {
									canny[x1 + y1 * sa.width] = 0xff000001;		
									startPoint = new Point(x1, y1);
								} else if (startPoint.x == x1 && startPoint.y == y1) {
									return false;
								}
								return true;
							 }
					 }
				 }
			 }
		 }
		 return false;
     }
	int bridged = 0;
}