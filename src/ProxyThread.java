import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

public class ProxyThread extends Thread {

	private Socket socket = null;
	private static final int BUFFER_SIZE = 32768;

	private PrintStream out;
	private BufferedReader in;

	private InputStream is;

	private String inputLine;
	private String urlToCall;
	private String fileName;

	private URL url;
	private URLConnection conn;

	private HashMap<String, String> headerFields;

	public ProxyThread(Socket socket) {
		super("ProxyThread");
		this.socket = socket;
	}

	public void run() {
		// get input from user
		// send request to server
		// get response from server
		// send response to user

		try {
			setup();
			getClientRequest();
			sendRequestAndGetServerContent();
			sendClientModifiedContent();
			closeResources();

		} catch (IOException e) {}
	}

	// helper methods for organization

	private void setup() throws IOException {
		out = new PrintStream(socket.getOutputStream());
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

		urlToCall = "";
		headerFields = new HashMap<String, String>();
	}

	private void getClientRequest() throws IOException {
		int cnt = 0;

		while ((inputLine = in.readLine()) != null) {
			if (cnt == 0) { // first line: GET URL PROTOCOL

				// parse the first line of the request to find the url
				String[] tokens = inputLine.split(" ");

				if (tokens.length < 3) {
					System.err.println("Bad request: " + inputLine);
					break;
				}

				if (!tokens[0].equals("GET")) {
					break;
				}

				urlToCall = tokens[1];

			} else { // subsequent lines

				if (inputLine.trim().equals("")) // empty line means end of
													// header
					break;

				String[] tokens = inputLine.split(":"); // non-empty line: it's
														// a header field

				if (tokens.length < 2) {
					System.out.println("Invalid line in request header: "
							+ inputLine);
					continue;
				}
				headerFields.put(tokens[0], tokens[1].trim());

			}

			cnt++;
		}
	}

	private void sendRequestAndGetServerContent() throws IOException {
		url = new URL(urlToCall);
		conn = url.openConnection();

		// add select header fields that we received to the clients to
		// the http connection
		for (String f : headerFields.keySet()) {
			if (f.equals("Host") || f.equals("Referer")
					|| f.equals("User-Agent") || f.equals("Accept")
					|| f.equals("Cookie")) {
				conn.addRequestProperty(f, headerFields.get(f));
			}
		}

		// disable gzipping content
		conn.setRequestProperty("Accept-Encoding", "deflate");

		conn.setDoInput(true);
		// not doing HTTP posts
		conn.setDoOutput(false);

		// Get the response
		is = conn.getInputStream();
	}

	private void sendClientModifiedContent() throws IOException {
		// send status to client (getHeaderField(0) returns the status
		// line)
		out.println(conn.getHeaderField(0));

		// send select header fields we received from the server to the
		// client
		for (String f : conn.getHeaderFields().keySet()) {
			if (f != null
					&& (f.equals("Server") || f.equals("Expires")
							|| f.equals("Set-Cookie") || f
								.equals("Content-Type"))) {
				out.println(f + ": " + conn.getHeaderField(f));
			}
		}

		// send empty line that separates response header from the
		// resource
		out.println();

		// send the body, reading from the server and writing to the
		// client
		// if the Content-Type field is "text/html", it's a HTML page
		// if the Content-Type field is "image/jpeg", it's a JPG file
		// and so on
		PrintStream ps = null;
		fileName = null;

		// if requesting an image create a new PrintStream
		if (conn.getContentType() != null && conn.getContentType().equals("image/jpeg"))
		{
			// create a new file of the type specified (get file extension from
			// Content-Type field)
			fileName = conn.getContent().hashCode() + ".jpeg";
			ps = new PrintStream(new FileOutputStream(fileName));
		}

		byte by[] = new byte[BUFFER_SIZE];
		int index = is.read(by, 0, BUFFER_SIZE);

		while (index != -1) {
			// if content is an image, write the bytes to the file
			if (ps != null) {
				ps.write(by, 0, index);
			} else {
				out.write(by, 0, index);
			}
			index = is.read(by, 0, BUFFER_SIZE);
		}

		if (ps != null) {
			modifyImage(fileName);
			FileInputStream fis = new FileInputStream("edited_" + fileName);

			index = fis.read(by, 0, BUFFER_SIZE);
			while (index != -1) {
				out.write(by, 0, index);
				index = fis.read(by, 0, BUFFER_SIZE);
			}
			fis.close();
		}

		out.flush();
		if (ps != null) {
			ps.close();
		}
	}

	private void closeResources() throws IOException {
		if (out != null) {
			out.close();
		}
		if (in != null) {
			in.close();
		}
		if (socket != null) {
			socket.close();
		}
		if (fileName != null) {
			Files.delete(Paths.get(fileName));
			Files.delete(Paths.get("edited_" + fileName));
		}
	}

	// OpenCV code
	private void modifyImage(String fileName) {
		// Create a face detector from the cascade file
		CascadeClassifier faceDetector = new CascadeClassifier(
				"haarcascade_frontalface_alt.xml");
		Mat image = Highgui.imread(fileName);

		// Detect faces in the image.
		// MatOfRect is a special container class for Rect.
		MatOfRect faceDetections = new MatOfRect();
		faceDetector.detectMultiScale(image, faceDetections);

		// Blur each face
		for (Rect rect : faceDetections.toArray()) {
			Mat faceArea = image.submat(rect);
			Imgproc.blur(faceArea, faceArea, new Size(30, 30));
		}
		// Save the modified image
		Highgui.imwrite("edited_" + fileName, image);
	}
}
