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
		param.gaussianKernelRadius = 0.3f; // TODO- bug in canny stuff, artifacts show up above 1.0
		param.threshold1 = param.threshold2 = 5;  // Range between 13 or 5 
		
		sa = new Rectangle(0,0,0,0);
		sa.width = w;
		sa.height = ht;
		sa.y = 0;
		sa.x = 0;

		h = new HoughTransform(houghSz, houghSz);
	}

	Rectangle []findAll(OriginalImage oi, Rectangle recNO) {
		if (Silly.debug("EXP")) { 
			//c.reset();
			c.zones.height = sa.height;
			c.zones.clear();

			setCanny(param);

			c.zones.lsz.b1 = -sa.height;
			c.zones.lsz.b2 = +sa.height;
			c.zones.lsz.m1 = 1;
			c.zones.lsz.m2 = 1;
			c.zones.midX = sa.width;

			c.processData(oi, sa);
			canny = c.getData();

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

			if (true) { 
				gp.startNew();
				gp.add3DGrid(vp, vanRec.width, vanRec.height);
				gp.draw();
			}
			if (true) { 
				gp2.startNew();
				gp2.add3DGrid(h.hough, h.angSz, h.radSz);
				gp2.draw();
			}
		}	

		return null;
	}

	public void markup(OriginalImage coi, int rescale) {
		if (Silly.debug("MARKUP_EXP") && Silly.debug("EXP")) { 
			for( Point p : c.results.l )  
				coi.putPixel(p.x + sa.x, p.y + sa.y, -1);		
		}	

	}
	public void setVanRect(Rectangle r) { 
		vanRec = r;
	}
}
		