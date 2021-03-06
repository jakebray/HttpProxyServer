import java.io.IOException;
import java.net.ServerSocket;

import org.opencv.core.Core;

public class ProxyServer {
	
	// Load the OpenCV library.
	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}
	
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = null;
        boolean listening = true;

        int port = 20000;	//default
        try {
            port = Integer.parseInt(args[0]);
        } catch (Exception e) {
            //ignore me
        }

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Started on: " + port);
        } catch (IOException e) {
            System.err.println("Could not listen on port: " + args[0]);
            System.exit(-1);
        }

        while (listening) {
            new ProxyThread(serverSocket.accept()).start();
        }
        serverSocket.close();
    }
}
