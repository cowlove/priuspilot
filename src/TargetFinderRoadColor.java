import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;


public class TargetFinderRoadColor extends TargetFinder {
	public class HSL {
		HSL(int h1, int s1, int l1) { 
			h = h1; s = s1; l = l1;
		}
		HSL() {}
		HSL(int[] hsl) { 
			h = hsl[0]; s = hsl[1]; l = hsl[2];
		}
		int h;
		int s;
		int l;
	}

	HSL [] rowAverageThresholds;
	TargetFinderRoadColor(int w, int h) {
		super(w, h);
		param.threshold1  = 6;
		param.threshold2  = 2;
		param.gaussianKernelRadius = 0.5f;
		param.H = new ByteSpan(145, 220);   
		param.S = new ByteSpan(70, 256);
		param.L = new ByteSpan(70, 230);
		rowAverageThresholds = new HSL[h];
		rowAverageHH = new HslHistogram[h];
		for(int i = 0; i < h; i++) { 
			rowAverageThresholds[i] = new HSL();
			rowAverageHH[i] = new HslHistogram();
		}
	}
	int [] hslThresh = new int[3];
	void draw(Graphics2D g2) { 
		if (sa != null) {
			g2.draw(scaleRect(sa, rescaleDisplay));
		}
	}
	HslHistogram hh = new HslHistogram();
	final static int rowAverageHeight = 5;
	HslHistogram [] rowAverageHH;
	@Override 
	Rectangle []findAll(OriginalImage oi, Rectangle rec) {
		sa = rec;
		hh.clear();
		HslHistogram hhline = new HslHistogram();
		for(int y = 0; y < sa.height; y++) {
			hhline.clear();
			for(int x = 0; x < sa.width; x++) { 
				/*
				int r = (int)oi.getR(x, y) & 0xff;
				int g = (int)oi.getG(x, y) & 0xff;
				int b = (int)oi.getB(x, y) & 0xff;
				int th = 40;
				if (Math.abs(r - b) > th || Math.abs(r - g) > th || Math.abs(g - b) > th) {
					//orig[idx] = orig[idx + 1] = orig[idx + 2] = 0;
				}
				
				LazyHslConvert.rgb2hsl((byte)r, (byte)g, (byte)b, hsl);
				*/
				int l = oi.getPixelLum(x + sa.x, y + sa.y);
				int [] hsl = oi.getHsl(x + sa.x, y + sa.y);
				//oi.clearPixel(x, y);
				hh.add(hsl);
				for(int i =  y -rowAverageHeight / 2; i < y + (rowAverageHeight + 1) / 2; i++) {
					if (i >= 0 && i < sa.height) 
						rowAverageHH[i].add(hsl);
				}
			}
		}
		//hh.draw(0.0);
		
		
		HslHistogram hx = (HslHistogram)hh;
		roadIntensity = hx.getHist(2).maxbucket;  // TODO- this very important parameter needs to be documented
		hslThresh[0] = (int)hh.getHist(0).percentile(0.25);
		hslThresh[1] = Math.min((int)hh.getHist(1).percentile(0.99) + 2, 255);
		hslThresh[2] = Math.min((int)hh.getHist(2).percentile(0.99) + 30, 255);

		for (int y = 0; y < sa.height; y++) { 
			rowAverageThresholds[y].h = (int)rowAverageHH[y].getHist(0).percentile(0.25);
			rowAverageThresholds[y].s = Math.min((int)rowAverageHH[y].getHist(1).percentile(0.99) + 1, 255);
			rowAverageThresholds[y].l = Math.min((int)rowAverageHH[y].getHist(2).percentile(0.99) + 28, 255);
		}
		
		//System.out.printf("%d %d %d\n", hslThresh[0], hslThresh[1], hslThresh[2]);
		return null;
	}	
	int roadIntensity;
	

	public boolean isLine(int y, int[] hsl) {
		if (y < sa.y || y >= sa.y + sa.height)
			return false;
		return hsl[1] > rowAverageThresholds[y - sa.y].s &&
				hsl[2] > rowAverageThresholds[y - sa.y].l;
				
	}
}
