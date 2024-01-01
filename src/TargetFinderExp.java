import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.awt.Point;

@SuppressWarnings("unused")
class TargetFinderExperimental extends TargetFinder { 
	HoughTransform h = null;
	Rectangle vanRec;
	GnuplotWrapper gp = new GnuplotWrapper();
	GnuplotWrapper gp2 = new GnuplotWrapper();

	TargetFinderExperimental(int w, int ht, Rectangle sa1, int houghSz) {  
		super(w, ht);
		houghSz = 180;
		
		param.name = "TFResearch";
		param.gaussianKernelRadius = (float)0.3; // TODO- bug in canny stuff, artifacts show up above 1.0
		param.threshold1 = param.threshold2 = 5;  // Range between 13 or 5 
		
		sa = new Rectangle(0,0,0,0);
		sa.width = w;
		sa.height = ht;
		sa.y = 0;
		sa.x = 0;

		h = new HoughTransform(houghSz, houghSz);
	}

	Rectangle []findAll(OriginalImage oi, Rectangle recNO) {
		if (!Main.debug("EXP"))
			return null;

		//c.reset();
		c.zones.height = sa.height;
		c.zones.clear();
		setCanny(param);
		c.zones.lsz.m1 = c.zones.lsz.m2 = Double.NaN;
		c.processData(oi, sa);
		canny = c.getData();

		// Filter for magnitudes perpendicular to the vanishing point lines 
		for(int y = 0; y < height; y++) { 
			for(int x = 0; x < width; x++) { 
				final int i = x + y * width;
				double gdir = Math.atan2(c.xGradient[i], c.yGradient[i]);
				double pdir = Math.atan2(x - vanRec.x, y - vanRec.y);
				double cosa = Math.cos(gdir - pdir + Math.PI / 2);
				c.results.gradResults[i] = (float)(	
					c.results.gradResults[i] * cosa * Math.abs(cosa)); 
			}
		} 
		if ((Main.debugInt("EXP",0) & 1) == 1) { 
			gp2.startNew();
			gp2.add3DGridF(c.results.gradResults, sa.width, sa.height, true);
			gp2.draw("set palette defined (-1 0 0 0, 1 1 1 0)\n");
		}
		//c.results.gradResults[0] = 250;
		//c.results.gradResults[1] = -250;

		// rotate a few degrees and add 
		float [] gr = new float[width * height];
		for(int y = 0; y < height; y++) { 
			for(int x = 0; x < width; x++) { 
				double pang = Math.atan2(y - vanRec.y, x - vanRec.x);
				double pdis = Math.sqrt((x - vanRec.x) * (x - vanRec.x) + (y - vanRec.y) *(y - vanRec.y));
				
				double lwAng = Main.debugDouble("LWANG", 1.5);
				int x2 = (int)Math.round(Math.cos(pang + Math.PI / 180 * lwAng) * pdis) + vanRec.x;
				int y2 = (int)Math.round(Math.sin(pang + Math.PI / 180 * lwAng) * pdis) + vanRec.y;

				gr[x + y * width] = c.results.gradResults[x + y * width];
				if (lwAng > 0 && x2 >= 0 && x2 < width && y2 >= 0 && y2 < height) 
					gr[x + y * width] = c.results.gradResults[x + y * width] - 
					c.results.gradResults[x2 + y2 * width];
			}
		}
		c.results.clear();
		for(int y = 0; y < height; y++) { 
			for(int x = 0; x < width; x++) {
				final int i = x + y * width; 
				c.results.gradResults[i] = Math.abs(gr[i]);
				if (c.results.gradResults[i] > c.threshold) { 
					c.results.add(x, y);
				} 
			}
		}

		h.clear();
		h.setAngleRange(0,180);
		h.setRadRange(-vanRec.width/3,vanRec.width/3	);
		h.origin.x = vanRec.x + vanRec.width / 2;
		h.origin.y = vanRec.y + vanRec.height / 2;

		if (true) { 	
			for (int x = 0; x < sa.width; x++) { 
				for(int y = 0; y < sa.height; y++) {
					float wt =  c.results.gradResults[y*sa.width+x]; 
					h.add(x, y, wt);
				}
			}
		} else { 
			for( Point p : c.results.l ) {
				h.add(p.x, p.y, 1);
			}
		}
		int[] vp = new int[vanRec.width * vanRec.height];
		h.projectIntoRect(vp, vanRec, 1);

		if ((Main.debugInt("EXP") & 2) == 2) { 
			gp.startNew();
			gp.add3DGridF(gr, sa.width, sa.height, true);
			gp.draw("set palette defined (-1 0 0 1, 0 0 0 0, 1 1 0 0)\n");
			
		}
		if (false) { 
			gp.startNew();
			gp.add3DGrid(vp, vanRec.width, vanRec.height);
			gp.draw();
		}
		if (false) { 
			gp2.startNew();
			gp2.add3DGrid(h.hough, h.angSz, h.radSz);
			gp2.draw();
		}
		return null;
	}

	public void markup(OriginalImage coi, int rescale) {
		if (Main.debug("MARKUP_EXP") && Main.debug("EXP")) { 
			for( Point p : c.results.l )  
				coi.putPixel(p.x + sa.x, p.y + sa.y, -1);		
		}	

	}
	public void setVanRect(Rectangle r) { 
		vanRec = r;
	}
}
		