

public class ScanZonePair {
	public class ScanZone { 
		public double m1 = 0, m2 = 0;
		public int b1 = 0, b2 = 0;
	    int minY;
	}
	int midX;
	public ScanZone lsz = new ScanZone(), rsz = new ScanZone();
	int height;

	public void clear() { 
		lsz.m1 = lsz.m2 = rsz.m1 = rsz.m2 = 0;
		lsz.b1 = rsz.b1 = 0;
		lsz.b2 = rsz.b2 = height;
                lsz.minY = rsz.minY = 0;
	}
	int ystart(int x) {
		if (Double.isNaN(lsz.m1))
			return lsz.minY;
		int y;
		if (x < midX) 
			y = (int)(lsz.m1 * x + lsz.b1);
		else 
			y = (int)(rsz.m1 * x + rsz.b1);
		return Math.max(lsz.minY, y);
	}
	int yend(int x) { 
		if (Double.isNaN(lsz.m1))
			return height;
		int y;
		if (x < midX) 
			y = (int)(lsz.m2 * x + lsz.b2);
		else 
			y = (int)(rsz.m2 * x + rsz.b2);
		return Math.min(height, y);
	}
}

