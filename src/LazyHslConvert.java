import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/*
import TemplateDetect.FindResult;
import TemplateDetect.PointPair;
import TemplateDetect.Tile;
*/

class LazyHslConvert { 
	class PointPair { 
		int x1 = 0 , y1 = 0, x2 = 0, y2 = 0; 
	}

	boolean hsl = true;
	int width, height;
	final int bpp = 3;
	PointPair hslRange = new PointPair();
	boolean [] debugHslConvert = null;
	int pixelsConverted = 0;
	
	LazyHslConvert(int w, int h) { 
		hslRange = new PointPair();
		debugHslConvert = new boolean[width * height];
		width = w; height = h;
		clear();
	}
	
	boolean verifyPixelConverted(int x, int y) { 
		if (debugHslConvert[x + y * width] != true) {
			System.out.printf("%d,%d NOT CONVERTED ", x, y);
			return false;
		} else
			return true;
	}
	void convertHsl(byte []pic, int x1, int y1, int x2, int y2) { 
		
		if (!hsl)
			return;
		
		int [] hsl = new int[3];

		//System.out.printf("convertHsl1 - %d,%d,%d,%d   hslRange %d,%d +%d,%d\n", x1, y1, x2, y2, hslRange.x1, hslRange.y1, hslRange.x2, hslRange.y2);

		if (hslRange.x1 != hslRange.x2) { 
			if (hslRange.x1 < x1) x1 = hslRange.x1;
			if (hslRange.x2 > x2) x2 = hslRange.x2;
			if (hslRange.y1 < y1) y1 = hslRange.y1;
			if (hslRange.y2 > y2) y2 = hslRange.y2;
		}
		
		//System.out.printf("convertHsl2 - %d,%d,%d,%d   hslRange %d,%d +%d,%d\n", x1, y1, x2, y2, hslRange.x1, hslRange.y1, hslRange.x2, hslRange.y2);
		
		for(int y = y1; y < y2; y++) {
			for(int x = x1; x < x2; x++) { 
				if (hslRange.x2 == hslRange.x1 || !(y >= hslRange.y1 && y < hslRange.y2 &&
						x >= hslRange.x1 && x < hslRange.x2)) {
					int pi = (x + y * width) * bpp;
					rgb2hsl(pic[pi], pic[pi + 1], pic[pi + 2], hsl);
					pic[pi] = (byte)hsl[0];
					pic[pi + 1] = (byte)hsl[1];
					pic[pi + 2] = (byte)hsl[2];
					if (debugHslConvert != null) { 
						if (debugHslConvert[x + y * width] == true) { 
							System.out.printf("%d,%d ALREADY CONVERTED ", x, y);
						}
						debugHslConvert[x + y * width] = true;
					}
					pixelsConverted++;
				}
			}
		}
		
		if (hslRange.x1 == hslRange.x2) {
			hslRange.x1 = x1;
			hslRange.x2 = x2;
			hslRange.y1 = y1;
			hslRange.y2 = y2;
		}
		
		if (x1 < hslRange.x1)
			hslRange.x1 = x1;
		if (x2 > hslRange.x2)
			hslRange.x2 = x2;
		if (y1 < hslRange.y1)
			hslRange.y1 = y1;
		if (y2 > hslRange.y2)
			hslRange.y2 = y2;
		//System.out.printf("convertHsl3 - %d,%d,%d,%d   hslRange %d,%d +%d,%d\n", x1, y1, x2, y2, hslRange.x1, hslRange.y1, hslRange.x2, hslRange.y2);
	}
	
	
	static void rgb2hsl(byte br, byte bg, byte bb, int hsl[]) {
		int r = (int)br & 0xff;
		int g = (int)bg & 0xff;
		int b = (int)bb & 0xff;
		
		float var_R = ( r / 255f );                    
		float var_G = ( g / 255f );
		float var_B = ( b / 255f );
		
		float var_Min;    //Min. value of RGB
		float var_Max;    //Max. value of RGB
		float del_Max;    //Delta RGB value
						 
		if (var_R > var_G) 
			{ var_Min = var_G; var_Max = var_R; }
		else 
			{ var_Min = var_R; var_Max = var_G; }
	
		if (var_B > var_Max) var_Max = var_B;
		if (var_B < var_Min) var_Min = var_B;
	
		del_Max = var_Max - var_Min; 
								 
		float H = 0, S, L;
		L = ( var_Max + var_Min ) / 2f;
	
		if ( del_Max == 0 ) { H = 0; S = 0; } // gray
		else {                                //Chroma
			if ( L < 0.5 ) 
				S = del_Max / ( var_Max + var_Min );
			else           
				S = del_Max / ( 2 - var_Max - var_Min );
	
			float del_R = ( ( ( var_Max - var_R ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
			float del_G = ( ( ( var_Max - var_G ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
			float del_B = ( ( ( var_Max - var_B ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
	
			if ( var_R == var_Max ) 
				H = del_B - del_G;
			else if ( var_G == var_Max ) 
				H = ( 1 / 3f ) + del_R - del_B;
			else if ( var_B == var_Max ) 
				H = ( 2 / 3f ) + del_G - del_R;
			if ( H < 0 ) H += 1;
			if ( H > 1 ) H -= 1;
		}
		hsl[0] = (int)(255*H);
		hsl[1] = (int)(S*255);
		hsl[2] = (int)(L*255);
	}


	
	private void int_rgb2hsl(byte br, byte bg, byte bb, int hsl[]) {
		int r = (int)br & 0xff;
		int g = (int)bg & 0xff;
		int b = (int)bb & 0xff;
		
		final int prec = 10000;
		int var_R = ( r * prec / 255 );                    
		int var_G = ( g * prec / 255 );
		int var_B = ( b * prec / 255 );
		
		int var_Min;    //Min. value of RGB
		int var_Max;    //Max. value of RGB
		int del_Max;    //Delta RGB value
						 
		if (var_R > var_G) 
			{ var_Min = var_G; var_Max = var_R; }
		else 
			{ var_Min = var_R; var_Max = var_G; }
	
		if (var_B > var_Max) var_Max = var_B;
		if (var_B < var_Min) var_Min = var_B;
	
		del_Max = var_Max - var_Min; 
								 
		int H = 0, S, L;
		L = ( var_Max + var_Min ) / 2;
	
		if ( del_Max == 0 ) { H = 0; S = 0; } // gray
		else {                                //Chroma
			if ( L < prec / 2 ) 
				S = del_Max / ( var_Max + var_Min );
			else           
				S = del_Max / ( 2 * prec - var_Max - var_Min );
	
			int del_R = ( ( ( var_Max - var_R ) / 6 ) + ( del_Max / 2 ) ) / del_Max;
			int del_G = ( ( ( var_Max - var_G ) / 6 ) + ( del_Max / 2 ) ) / del_Max;
			int del_B = ( ( ( var_Max - var_B ) / 6 ) + ( del_Max / 2 ) ) / del_Max;
	
			if ( var_R == var_Max ) 
				H = del_B - del_G;
			else if ( var_G == var_Max ) 
				H = ( prec / 3 ) + del_R - del_B;
			else if ( var_B == var_Max ) 
				H = ( prec / 3 ) + del_G - del_R;
			if ( H < 0 ) H += prec;
			if ( H > prec ) H -= prec;
		}
		hsl[0] = (int)(H*255 / prec);
		hsl[1] = (int)(S*255 / prec);
		hsl[2] = (int)(L*255 / prec);
	}

	
	
	
	private void rgb2hsv(byte rc, byte gc, byte bc, int hsv[]) {
		int r = (int)rc & 0xff;
		int g = (int)gc & 0xff;
		int b = (int)bc & 0xff;
			
		int min;    //Min. value of RGB
		int max;    //Max. value of RGB
		int delMax; //Delta RGB value
		
		if (r > g) { min = g; max = r; }
		else { min = r; max = g; }
		if (b > max) max = b;
		if (b < min) min = b;
								
		delMax = max - min;
	 
		float H = 0, S;
		float V = max;
		   
		if ( delMax == 0 ) { H = 0; S = 0; }
		else {                                   
			S = delMax/255f;
			if ( r == max ) 
				H = (      (g - b)/(float)delMax)*60;
			else if ( g == max ) 
				H = ( 2 +  (b - r)/(float)delMax)*60;
			else if ( b == max ) 
				H = ( 4 +  (r - g)/(float)delMax)*60;   
		}
								 
		hsv[0] = (int)(H);
		hsv[1] = (int)(S*100);
		hsv[2] = (int)(V*100);
	}


	public void clear() {
		hslRange.x1 = hslRange.x2 = hslRange.y1 = hslRange.y2 = 0;
		pixelsConverted = 0;
		debugHslConvert = new boolean[width * height]; 
	}
	
}