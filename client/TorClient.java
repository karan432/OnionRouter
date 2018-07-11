import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


public class TorClient {

    static final Logger clientlog = Logger.getLogger("client");
    private DataInputStream din;
    private DataOutputStream dout;
    private BufferedReader br;
    private Socket client;
    private String directory;
    private String reply;
    private String IP[] = new String[3];
    private String E[] = new String[3];
    private String N[] = new String[3];
    private String DirIP="192.168.1.111";
    private String FinalIP;
    private final int DirPort=9090,routerPort=9091;

    TorClient() {
        clientlog.info("Tor Client initialized.");
        try {
            //connect to directorey
            client = new Socket(DirIP, DirPort);
            dout = new DataOutputStream(client.getOutputStream());
            dout.writeUTF("1");
        } catch (IOException ex) {
            clientlog.severe("Can't connect to the directory. Exiting program...");
            System.exit(0);
        }
    }

    public static void main(String args[]) throws IOException {
        clientlog.info("Tor Client running.");
        TorClient object = new TorClient();
        object.loginit();
        object.FinalIP = "192.168.0.1";
        String dir = object.DirData();
        object.splitString(dir);
        //get request from client
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter data :");
        String message = sc.nextLine();
        clientlog.info("Data to be sent received.");
        //encrypt thrice
        byte[] encrypted = object.makeOnion(message.getBytes());
        //sending to bridge node
        object.torouter1(encrypted);
        System.out.println("data received is: " +object.reply);
    }

    private void loginit() {
        FileHandler logFile;
        try {
            logFile = new FileHandler("TorClient.txt");
            clientlog.addHandler(logFile);
            SimpleFormatter formatter = new SimpleFormatter();
            logFile.setFormatter(formatter);
        } catch (IOException | SecurityException ex) {
            clientlog.severe("Exception raised in creating log file. Exiting program.");
        }
    }

    private String DirData() throws IOException {
        clientlog.info("Fetching data from Tor Directory.");
        din = new DataInputStream(client.getInputStream());
        directory = din.readUTF();
        clientlog.info("Data fetching finished.");
        return directory;
    }

    //split ip

    private void splitString(String directory) {
        this.directory = directory;
        String[] splitData = directory.split("/");
        int j = 0;
        for (int i = 0; i < 3; i++) {
            IP[i] = splitData[j++]; System.out.println("IP["+i+"]="+IP[i]);
            E[i] = splitData[j++];  System.out.println("E["+i+"]= "+E[i]);
            N[i] = splitData[j++];  System.out.println("N["+i+"]= "+N[i]);
        }
    }

    private byte[] makeOnion(byte[] message) {
        clientlog.info("Data encryption process initiated.");
        /*byte[] zeroeth = makeCell(message,FinalIP,0);
        System.out.println("zeroeth="+bytesToString(zeroeth));
        byte[] first = makeCell(zeroeth,IP[0],1);
        System.out.println("first="+bytesToString(first));
        byte[] second = makeCell(first,IP[1],2);
        System.out.println("second="+bytesToString(second));*/
        byte[] peel2=makeCell(makeCell(makeCell(message,FinalIP,0),IP[0],1),IP[1],2);
        return peel2;
        //return second;
    }
    
    private byte[] makeCell(byte[] data, String IP,int i)
    {        
        String p = IP+"::"+new String(data);        
        System.out.println("\n\n\n");
        System.out.println(i+"th peel: "+p);
        System.out.println("\n\n\n");
        byte[] peel = p.getBytes();
        return peel;
    }
    
    private byte[] encrypt(byte[] peel,int i)
    {
        byte[] enc;        
        BigInteger e = new BigInteger(E[i]);
        BigInteger n = new BigInteger(N[i]);
        enc = (new BigInteger(peel)).modPow(e, n).toByteArray();
        return enc;
    }
        

    private static String bytesToString(byte[] encrypted) {
        String test = "";
        for (byte b : encrypted) {
            test += Byte.toString(b);
        }
        return test;
    }

    private void torouter1(byte[] message_router1) {
        try {
            clientlog.info("Data being sent through Proxy Routers.");
            client = new Socket(IP[2],routerPort);
            din=new DataInputStream(client.getInputStream());
            dout = new DataOutputStream(client.getOutputStream());
            dout.writeInt(message_router1.length);
            clientlog.info("length of message is "+ message_router1.length);
            dout.write(message_router1);
            dout.flush();
            reply=din.readUTF();
            
        } catch (IOException ex) {
            clientlog.severe("Data couldn't be sent to the Router. Exiting Program"+ex.toString());
        }
    }
}