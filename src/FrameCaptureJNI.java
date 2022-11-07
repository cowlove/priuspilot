import java.nio.ByteBuffer;

public class FrameCaptureJNI {
	public native void configure(int id, String dev, int resWidth, int resHeight, int windX, int windY, int windWidth, int
			windHeight, boolean flip, String captureFile, int captureFileSize, int captureFileCount,
			int maxFramePeriod, int rawRecordSkip, boolean useSystemClock);
	public native int grabFrame(int id, ByteBuffer ib);
	public native long getFrameTimestamp(int id); 
	public native void close(int id);
	public native void renameCurrentCaptureFile(int id, String filename);
	public native void discardFrame(int id, String filename);
	public native void setCaptureFile(int id, String filename);
	public native void setLogData(int id, long steer, long timestamp);

	public void configure(String dev, int resWidth, int resHeight, int windX, int windY, int windWidth, int
			windHeight, boolean flip, String captureFile, int captureFileSize, int captureFileCount, 
			int maxFramePeriod, int rawRecordSkip, boolean useSystemClock) { 
		configure(id, dev, resWidth, resHeight, windX, windY, windWidth, windHeight, flip, captureFile,
				captureFileSize, captureFileCount, maxFramePeriod, rawRecordSkip, useSystemClock);
	}
	public int grabFrame(ByteBuffer ib) { return grabFrame(id, ib); } 
	public long getFrameTimestamp() { return getFrameTimestamp(id); } 
	public void setLogData(long steer, long ts) { setLogData(id, steer, ts); }
	public void close() { close(id); } 
	public void renameCurrentCaptureFile(String filename) { renameCurrentCaptureFile(id, filename); } 
	public void discardFrame(String filename) { discardFrame(id, filename); }
	public void setCaptureFile(String filename) { setCaptureFile(id, filename); } 

	int id;
	static private int nextId = 0;
	FrameCaptureJNI() { id = nextId++; } 
	static { System.loadLibrary("FrameCaptureJNI"); 
		// TODO Auto-generated method stub
	}
}
