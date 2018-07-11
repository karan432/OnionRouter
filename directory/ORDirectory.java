import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ORDirectory {
  static final Logger dirlog = Logger.getLogger("directory");
  DataInputStream req;
  private String input;
  private String[] IP, token;
  private String[][] RSA;
  private boolean[] status;
  private int N, count, DirPort = 9090;
  private int id;
  static ServerSocket directory;

  ORDirectory() {
    dirlog.info("OR Directory initialized.");
    try {
      dirlog.info(InetAddress.getLocalHost().toString());
    } catch (UnknownHostException e) {
      dirlog.info(e.toString());
    }
    input = "";
    id = 3;
    N = 50;
    IP = new String[N];
    RSA = new String[N][2];
    status = new boolean[N];
    count = 0;
    dirlog.info("Number of routers online : " + count);
  }

  public static void main(String args[]) {
    try {
      dirlog.info("OR Directory running.");
      ORDirectory tordir = new ORDirectory(); // create object
      tordir.loginit();
      directory = new ServerSocket(tordir.DirPort, 10);
      while (true) {
        tordir.connect(); // connect to node
      }
    } catch (IOException ex) {
      dirlog.severe("Directory Port busy. Exiting program.");
    }
  }

  private void loginit() {
    FileHandler logFile;
    try {
      logFile = new FileHandler("TorDir.txt");
      dirlog.addHandler(logFile);
      SimpleFormatter formatter = new SimpleFormatter();
      logFile.setFormatter(formatter);
    } catch (IOException | SecurityException ex) {
      dirlog.severe("Exception raised in creating log file. Exiting program.");
    }
  }

  private void connect() {
    try {
      dirlog.info("Waiting to connect");
      Socket incoming = new Socket();
      try {
        incoming = directory.accept(); // accept incoming socket request
      } catch (Exception ex) {
        dirlog.warning("Couldn't connect to the node.");
        return;
      }
      dirlog.info("Node connected.");
      req = new DataInputStream(incoming.getInputStream());
      input = req.readUTF(); // read input from node
      token = input.split("/");
      switch (token[0]) { // check for header
      case "0": // if header=0 then it is a router
        dirlog.info("Node identified as Router.");
        id = 0;
        router(incoming);
        break;
      case "1":
        dirlog.info("Node identified as Client."); // if header=1 then it is a client
        id = 1;
        client(incoming);
        break;
      default:
        dirlog.warning("Node can't be identified. Closing connection...");
        id = 2;
      }
      incoming.close();
    } catch (IOException ex) {
      dirlog.severe("Couldn't receive data from Node.");
    }
  }

  private void router(Socket incoming) {
    dirlog.info("Router operations initiated.");
    boolean present = false;
    int current = 0;
    String routerIP = "" + incoming.getInetAddress().toString().substring(1); // get ip address of the router node
    if (count > 0) {
      for (int i = 0; i < count; i++) {
        if (IP[i].equals(routerIP)) {
          present = true;
          current = i;
          break;
        }
      }
    }
    if (!present) {
      current = count;
      count++;
    }
    IP[current] = routerIP;
    int track = 1;
    RSA[current][0] = token[track++]; // E // get base of RSA key of the router node
    RSA[current][1] = token[track++]; // N //get exponent of RSA key of the router node
    dirlog.info("\n=====>> IP=" + IP[current] + "\nE = " + RSA[current][0] + "\nN = " + RSA[current][1] + "\n");
    status[current] = true; // mark the router online
    dirlog.info("Number of routers online : " + count);
  }

  private void client(Socket incoming) {
    dirlog.info("Client operations initiated.");
    String metadata = "";
    if (count < 3) {
      metadata = "number of relays available is " + count;
    } else {
      Random rand = new Random();
      int[] router = new int[3];
      router[0] = rand.nextInt(count);
      System.out.println(router[0]);
      while ((router[1] = rand.nextInt(count)) == router[0]) {
        System.out.println("try" + router[1]);
      }
      System.out.println(router[1]);
      while (((router[2] = rand.nextInt(count)) == router[1]) || (router[2] == router[0])) {
        System.out.println("try" + router[2]);
      }
      System.out.println(router[2]);
      for (int node : router) {
        metadata += IP[node] + "/" + RSA[node][0] + "/" + RSA[node][1] + "/";
      }
    }
    System.out.println(metadata);
    try {
      DataOutputStream response = new DataOutputStream(incoming.getOutputStream());
      response.writeUTF(metadata);
      dirlog.info("Data sent to Client " + incoming.getInetAddress());
    } catch (IOException ex) {
      dirlog.warning("Data couldn't be sent to Client: " + incoming.getInetAddress());
    }
  }
}