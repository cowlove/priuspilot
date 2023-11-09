import java.nio.ByteBuffer;

public class ESPNowJNI {
	public native void open(String d);
    public native void write(String msg); 
    public native String read();
    ESPNowJNI() {}
    static { System.loadLibrary("ESPNowJNI"); 
        // TODO Auto-generated method stub
    }

}

