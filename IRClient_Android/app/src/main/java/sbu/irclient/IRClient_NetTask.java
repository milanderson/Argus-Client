package sbu.irclient;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class IRClient_NetTask extends AsyncTask<Void, Void, Void> {
    public static String IR_REQ_ST = "SlideShow";
    public static String SERVER_IP = "130.245.158.1";
    public static int SERVER_PORT = 50505;
    public static boolean ready = false;
    public static DataOutputStream msgOut, OOBOut;
    public static InputStream msgIn, OOBIn;
    public static Queue<Long> sendTime = new LinkedList<Long>();
    FileInputStream inputStream;

    @Override
    protected Void doInBackground(Void... voids) {
        int bytesRead;
        Log.d(IRClient.TAG, "Socket init");
        InetSocketAddress hostMain;
        Socket msgSock;

        try {
            hostMain = new InetSocketAddress(SERVER_IP, SERVER_PORT);

            msgSock = new Socket(hostMain.getAddress(), hostMain.getPort());
            msgSock.setSoTimeout(1000);

            Log.d(IRClient.TAG, "Initializing message socket");
            msgOut = new DataOutputStream(msgSock.getOutputStream());
            msgIn = msgSock.getInputStream();

            msgOut.writeUTF("Slideshow");
            msgOut.flush();

            byte stMsg[] = new byte[20];
            msgIn.read(stMsg);

            ready = true;
        } catch (Exception e) {
            Log.e(IRClient.TAG, "Socket init error");
            Log.e(IRClient.TAG, e.toString());
            return null;
        }
        byte recvBuf[] = new byte[2400];
        int ticker = 0;

        byte[] buf = null;

        while (true) {
            try {
                while(IRClient.frame == null){}
                buf = IRClient.frame;
                IRClient.frameInUse = true;

                //Log.d(IRClient.TAG, "Sending chunk size " + Integer.toString(buf.length));
                msgOut.write(buf, 0, buf.length);
                //msgOut.writeUTF("Done");
                msgOut.flush();

                IRClient.frameInUse = false;
                IRClient.frame = null;

                ticker++;

                if (msgIn.available() >= 1682 && ( bytesRead = msgIn.read(recvBuf, 0, 1690)) > 0){
                    long recvTime = System.currentTimeMillis();
                    Log.e(IRClient.TAG, "RTT: " + Long.toString(recvTime - sendTime.poll()));

                    String recvMsg = new String(recvBuf, 0 , bytesRead);
                    //Log.d(IRClient.TAG, "Recieved " + Integer.toString(recvMsg.length()) + " bytes:"  + recvMsg);

                    //IRClient.overlayView.setRectList(recvMsg);
                    float[] bitMask = new float[29*29];
                    int maskItem = 0;
                    int lastItem = 0;
                    for (int item = 0; item < recvMsg.length(); item++){
                        if(recvMsg.charAt(item) == ','){
                            if(item != lastItem){
                                bitMask[maskItem] = Float.parseFloat(recvMsg.substring(lastItem, item));
                                maskItem++;
                                lastItem = item + 1;
                            }
                        }
                        if(recvMsg.charAt(item) == 'R'){
                            break;
                        }
                    }
                    IRClient.overlayView.setContoursFromBitMask(bitMask);
                } else {
                    IRClient.overlayView.clear();
                }
            } catch (Exception e) {
                Log.e(IRClient.TAG, e.toString());
                continue;
            }
        }
        //return null;
    }
}
