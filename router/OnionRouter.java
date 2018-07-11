import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.*;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class OnionRouter {

  static final Logger routerlog = Logger.getLogger("router");
  private DataInputStream dinp;
  private DataOutputStream dout;
  private BufferedReader br;
  private Socket router;
  private String E, N, D;
  private byte[] data, decryptedData;
  private byte[] reply;
  private final String DirIP = "192.168.1.117"; // The IP address of the directory
  private Random r;
  private BigInteger p, q, e, d, n, phi;
  private ServerSocket RouterAsServer;
  private final int RouterPort = 9091, DirPort = 9090;

  OnionRouter() {
    routerlog.info("Tor Router Initialized.");
  }

  public static void main(String args[]) {
    routerlog.info("Tor Router running");
    OnionRouter OR = new OnionRouter();
    OR.loginit();
    OR.genKey();
    OR.sendToDir();
    while (true) {
      OR.data = OR.getData();
      System.out.println(OR.data.length);
      OR.decryptedData = OR.data;
      String temp[] = new String(OR.data).split("::", 2);
      if (temp.length < 2) {
        System.out.println("decrypted String= " + temp[0]);
      } else {
        System.out.println("decrypted String= " + temp[0] + "::" + temp[1].getBytes());
      }
      System.out.println("decrypted data in bytes" + bytesToString(OR.decryptedData));
      OR.sendData();
      try {
        System.out.println("sending request to source.");
        OR.dout.writeInt(OR.reply.length);
        OR.dout.write(OR.reply);
        OR.dout.flush();
        OR.RouterAsServer.close();
      } catch (IOException ioe) {
        System.out.println(" error in send data: " + ioe.toString());
      }
    }
  }

  private void loginit() {
    FileHandler logFile;
    try {
      logFile = new FileHandler("OnionRouter.txt");
      routerlog.addHandler(logFile);
      SimpleFormatter formatter = new SimpleFormatter();
      logFile.setFormatter(formatter);
    } catch (IOException | SecurityException ex) {
      routerlog.severe("Exception raised in creating log file. Exiting program.");
    }
  }

  private void genKey() {
    routerlog.info("RSA keys being generated...");
    key(1024);
    routerlog.info("RSA keys generated.");
  }

  private void key(int bitlength) {
    r = new Random();
    p = BigInteger.probablePrime(bitlength, r);
    q = BigInteger.probablePrime(bitlength, r);
    n = p.multiply(q);
    phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));
    e = BigInteger.probablePrime(bitlength / 2, r);
    while (phi.gcd(e).compareTo(BigInteger.ONE) > 0 && e.compareTo(phi) < 0) {
      e.add(BigInteger.ONE);
    }
    d = e.modInverse(phi);
    E = e.toString();
    N = n.toString();
    D = d.toString();
  }

  private void sendToDir() {
    try {
      routerlog.info("connecting to directory with IP " + DirIP);
      router = new Socket(DirIP, DirPort);
      DataOutputStream dos = new DataOutputStream(router.getOutputStream());
      dos.writeUTF("0/" + E + "/" + N);
      System.out.println("E=" + E);
      System.out.println("N=" + N);
      dos.flush();
      dos.close();
      router.close();
    } catch (IOException ex) {
      routerlog.severe("Unable to connect to Directory. Exiting program." + ex.toString());
      System.exit(0);
    }
  }

  private byte[] getData() {
    routerlog.info("Waiting to receive data.");
    try {
      RouterAsServer = new ServerSocket(RouterPort, 10);
      Socket DataSender = RouterAsServer.accept();
      routerlog.info("Connection with client established.");
      dinp = new DataInputStream(DataSender.getInputStream());
      dout = new DataOutputStream(DataSender.getOutputStream());
      int len = dinp.readInt();
      System.out.println("Length found=" + len);
      byte[] receivedData = new byte[len];
      dinp.readFully(receivedData, 0, len);
      routerlog.info("Data Received.");
      System.out.println("Data received in Bytes" + bytesToString(receivedData));
      return receivedData;
    } catch (IOException ex) {
      routerlog.severe("Data receiving failed." + ex.toString());
    }
    return null;
  }

  private byte[] encrypt(byte[] peel) {
    byte[] enc;
    BigInteger e = new BigInteger(E);
    BigInteger n = new BigInteger(N);
    enc = (new BigInteger(peel)).modPow(e, n).toByteArray();
    return enc;
  }

  private byte[] decrypt() {
    System.out.println("Data to decrypt in bytes: " + bytesToString(data));
    return (new BigInteger(data)).modPow(new BigInteger(D), new BigInteger(N)).toByteArray();
  }

  private byte[] decrypt(byte[] message) {
    return (new BigInteger(message)).modPow(new BigInteger(D), new BigInteger(N)).toByteArray();
  }

  private void sendData() {
    String decrypted = new String(decryptedData);
    String st[] = new String(decrypted).split("::");
    if (st.length >= 2) {
      String[] parts = (decrypted).split("::", 2);
      String nextIp = parts[0];
      String m = parts[1];
      byte[] msg = m.trim().getBytes();
      System.out.println("next peel in bytes: " + bytesToString(msg));
      int l = msg.length;
      try {
        System.out.println("so you want me to send to : " + nextIp);
        System.out.println("the data i am sending is : " + bytesToString(msg));
        Thread.sleep(4000);
        Socket RouterAsClient = new Socket(nextIp, RouterPort);
        DataOutputStream dos = new DataOutputStream(RouterAsClient.getOutputStream());
        DataInputStream din = new DataInputStream(RouterAsClient.getInputStream());
        dos.writeInt(l);
        dos.write(msg);
        dos.flush();
        System.out.println("waiting for reply from server... ");
        int len = din.readInt();
        reply = new byte[len];
        din.readFully(reply, 0, len);
        System.out.println("data received from server.");
      } catch (InterruptedException ie) {
        routerlog.severe("thread sleep interrrupted");
      } catch (IOException ex) {
        routerlog.severe("Attempt to establish connection with other router failed. Exiting progream.");
        reply = new String("error connecting with node: " + nextIp).getBytes();
      }
    } else {
      String siteToOpen = st[0];
      System.out.println("fetching data from site: " + siteToOpen);
      try {
        URLConn urlConn = new URLConn(siteToOpen);
        reply = (urlConn.URLResponse()).getBytes();
        System.out.println("data received.");
        urlConn.finalize();
      } catch (Exception ioe) {
        System.out.println("error creating url connection: " + ioe.toString());
        reply = new String("couldnt connect to site: " + siteToOpen).getBytes();
      }
    }
  }

  private static String bytesToString(byte[] e) {
    String test = "";
    for (byte b : e) {
      test += Byte.toString(b);
    }
    return test;
  }
}

class URLConn {
  URL url;
  URLConnection urlConnection;
  HttpURLConnection connection = null;
  BufferedReader in;
  String urlString = "";

  URLConn(String siteToOpen) {
    try {
      url = new URL(siteToOpen);
      urlConnection = url.openConnection();
      connection = null;
      if (urlConnection instanceof HttpURLConnection) {
        connection = (HttpURLConnection) urlConnection;
      } else {
        System.out.println("Please enter an HTTP URL.");
      }
      in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  String URLResponse() {
    String urlString = "";
    String current;
    try {
      while ((current = in.readLine()) != null) {
        urlString += current;
      }
      in.close();
      connection.disconnect();
      return urlString;
    } catch (IOException ioe) {
      System.out.println(ioe.toString());
      return "error occured while reading data from server";
    }
  }

  public void finalize() throws IOException {
    in.close();
    connection.disconnect();
  }
}
