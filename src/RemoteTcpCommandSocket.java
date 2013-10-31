import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;



class RemoteTcpCommandSocket { 
    // TODO remove this to a new class RemoteCommandSocket
    double remoteInput = 0;
    int remoteInputAge = 0;

    double getSteer() { 
    	  if (remoteInputAge++ > 15) 
          	remoteInput *= 0.80;
          if (remoteInputAge > 30)
          	remoteInput = 0;
          return remoteInput;
    }
    RemoteTcpCommandSocket() {
    	background.start();
    }
    Thread background = new Thread (new Runnable() {
        public void run() {
                ServerSocket listener;
				try {
					listener = new ServerSocket(12345);
		            Socket server;
	
	                while(true) {
	                  server = listener.accept();
	                  DataInputStream in = new DataInputStream (server.getInputStream());
	                  PrintStream out = new PrintStream(server.getOutputStream());
	                  String line;
	                  while((line = in.readLine()) != null && !line.equals(".")) {
	                	  try { 
		                	  StringTokenizer st = new StringTokenizer(line);
		                	  if (st.hasMoreTokens()) {
		                		  remoteInput = Double.parseDouble(st.nextToken());
		                		  remoteInputAge = 0;
		                	  }
	                	  } catch(Exception e) {}
	                	  //System.out.println(String.format("Got %.2f", remoteInput));
	                  }
	                }
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            
        }
    });

}