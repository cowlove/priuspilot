

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class OriginalImage {
	// direct buffer and bb access methods is faster than a copy and direct array access
	final static boolean useDirect = true;
	byte[] array;
	public OriginalImage(ByteBuffer b, int width, int height) {
		if (useDirect) { 
			pixels = b;
		} else { 
			this.pixels = ByteBuffer.allocate(width * height * 4);
			b.rewind();
			pixels.rewind();
			pixels.put(b);;
			array = pixels.array();
		}
		this.width = width;
		this.height = height;
	}
	
	public int getGreyPixelABGR(int x, int y) { 
		int l = getPixelLum(x, y);
		return l << 16 | l << 8 | l;
	}
	
	public void clearPixel(int x, int y) { 
		int i = (x + y * width) * 2;
		pixels.put(i, (byte) 0);
	}
	public int getPixelABGR(int x, int y) { 
		byte [] yuv = new byte[4];
		byte [] rgb = new byte[6];
		int i = (x + y * width) * 2;
		for(int o = 0; o < yuv.length; o++) { 
			yuv[o] = pixels.get((i - (i % 4)) + o);
		}
		yuv422torgb24(yuv, rgb);
		
		int oi = (i % 4) ==  0 ? 0 : 3;
		return (((int)rgb[oi + 2] & 0xff) << 16) | (((int)rgb[oi + 1] & 0xff) << 8) | ((int)rgb[oi] & 0xff);
	}

	public int []getPixelRGBArray(int x, int y) { 
		byte [] yuv = new byte[4];
		byte [] rgb = new byte[6];
		int i = (x + y * width) * 2;
		for(int o = 0; o < yuv.length; o++) { 
			yuv[o] = pixels.get((i - (i % 4)) + o);
		}
		yuv422torgb24(yuv, rgb);
		
		int oi = (i % 4) ==  0 ? 0 : 3;
		int [] r = new int[3];
		r[0] = (int)rgb[oi + 0] & 0xff;
		r[1] = (int)rgb[oi + 1] & 0xff;
		r[2] = (int)rgb[oi + 2] & 0xff;
		return r;
	}
	
	public int getPixelLum(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height) 
			return 0; 
		int i = (x + y * width) * 2;
		if (useDirect) { 
			return (int)pixels.get(i) & 0xff;
		} else { 
			return (int)array[i] & 0xff;
		}
	}
	
	public int width, height;
	public ByteBuffer pixels;
	
	static void yuv422torgb24(byte []in, byte []out) {
		// This seems to work best so fr
		int Y1 = (int)in[0] & 0xff;
		int V = (int)in[1] & 0xff;
		int Y2 = (int)in[2] & 0xff;
		int U = (int)in[3] & 0xff;

		int R, G, B, C, D, E;
		
		int Y = Y1;
		
		C = Y - 16;
		D = U - 128;
		E = V - 128;

		R = clamp((298 * C + 409 * E + 128) >> 8);
		G = clamp((298 * C - 100 * D - 208 * E + 128) >> 8);
		B = clamp((298 * C + 516 * D + 128) >> 8);
		
		out[0] = (byte)R;
		out[1] = (byte)G;
		out[2] = (byte)B;
		
		Y = Y2;
		
		C = Y - 16;
		D = U - 128;
		E = V - 128;

		R = clamp((298 * C + 409 * E + 128) >> 8);
		G = clamp((298 * C - 100 * D - 208 * E + 128) >> 8);
		B = clamp((298 * C + 516 * D + 128) >> 8);
		
		out[3] = (byte)R;
		out[4] = (byte)G;
		out[5] = (byte)B;	
	}

	static int clamp(int x) { 
		if (x < 0) return 0;
		if (x > 255) return 255; 
		return x;
	}

	public byte getR(int x, int y) {
		// TODO Auto-generated method stub
		return 0;
	}
	public byte getG(int x, int y) {
		// TODO Auto-generated method stub
		return 0;
	}
	public byte getB(int x, int y) {
		// TODO Auto-generated method stub
		return 0;
	}

	public void putPixel(int x, int y, int p) {
		int i = (x + y * width) * 2;
		pixels.put(i, (byte)p);
		// TODO Auto-generated method stub
	}

	public byte[] getHslRect(Rectangle r) {
		byte hslpic[] = new byte[r.width * r.height * 3];
		//LazyHslConvert hsl = new LazyHslConvert(redSa.width, redSa.height);
        //hsl.convertHsl(hslpic, 0, 0, redSa.width, redSa.height);
		for(int y = 0; y < r.height; y++) { 
			for(int x = 0; x < r.width; x++) {
				int hsl[] = getHsl(x + r.x, y + r.y);
				for (int b = 0; b < 3; b++) { 
					hslpic[(y * r.width + x) * 3 + b] = (byte)hsl[b];
				}
			} 
		}
		return hslpic;
	}

	public OriginalImage deepCopy() {
		ByteBuffer bb = ByteBuffer.allocate(pixels.capacity());
		pixels.rewind();
		bb.rewind();
		bb.put(pixels);
		return new OriginalImage(bb, width, height);
	}

	public void writeOut(FileChannel channel) {
		
	}

	public void dimPixel(int x, int y) {
		int i = (x + y * width) * 2;
		pixels.put(i, (byte)(((int)pixels.get(i) & 0xff) * 5 / 6));		
	}
	
	static void rgb2hsl(int r, int g, int b, int hsl[]) {
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



	public int[] getHsl(int x, int y) {
		int [] r = this.getPixelRGBArray(x, y);
		int [] hsl = new int[3];
		rgb2hsl(r[0], r[1], r[2], hsl);
		return hsl;
	}

}
