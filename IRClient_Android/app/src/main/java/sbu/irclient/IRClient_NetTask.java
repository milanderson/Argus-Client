package sbu.irclient;

import android.os.AsyncTask;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

public class IRClient_NetTask extends AsyncTask<Void, Void, Void> {
    public static boolean ready = false;
    public static DataOutputStream msgOut, OOBOut;
    public static DataInputStream msgIn, OOBIn;
    public static Queue<Long> sendTime = new LinkedList<Long>();
    FileInputStream inputStream;

    @Override
    protected Void doInBackground(Void... voids) {
        Log.d(IRClient.TAG, "Socket init");
        InetSocketAddress hostMain, hostOOB;
        Socket msgSock, OOBSock;
        try {
            hostMain = new InetSocketAddress(IRClient.SERVER_IP, 50505);
            //hostOOB = new InetSocketAddress(IRClient.SERVER_IP, 50506);
            msgSock = new Socket(hostMain.getAddress(), hostMain.getPort());
            msgSock.setSoTimeout(1500);

            Log.d(IRClient.TAG, "Initializing message socket");
            msgOut = new DataOutputStream(msgSock.getOutputStream());
            msgIn = new DataInputStream(msgSock.getInputStream());
            //msgOut.writeUTF("Stream");
            msgOut.writeUTF("Slideshow");
            msgOut.flush();

            msgIn.read();

            //Log.d(IRClient.TAG, "Initializing out of band socket");
            //OOBSock = new Socket(hostOOB.getAddress(), hostOOB.getPort());
            //OOBOut = new DataOutputStream(OOBSock.getOutputStream());
            //OOBIn = new DataInputStream(OOBSock.getInputStream());
            ready = true;
        } catch (Exception e) {
            Log.e(IRClient.TAG, "Socket init error");
            Log.e(IRClient.TAG, e.toString());
            return null;
        }
        byte recvBuf[] = new byte[500];
        int ticker = 0;


        byte[] buf = null;
        /*
        while(buf == null) {
            Log.d(IRClient.TAG, "Waiting on rec file switch");
            if(IRClient.mOutputFile == null) {}
            Log.d(IRClient.TAG, "getting SPS");
            buf = MP4toNALU.initNALUDynamic();
        }

        Log.d(IRClient.TAG, "Writing init NAL");
        try {
            msgOut.write(buf, 0, buf.length);
            msgOut.flush();
        } catch (Exception e){
            Log.e(IRClient.TAG, e.toString());
            return null;
        }
        */
        while (true) {
            try {
                /*
                buf = MP4toNALU.getNextFrame();
                if (buf == null) {
                    continue;
                }
                */
                while(IRClient.frame == null){}
                buf = IRClient.frame;
                IRClient.frameInUse = true;

                //Log.d(IRClient.TAG, "Sending chunk size " + Integer.toString(buf.length));
                msgOut.write(buf, 0, buf.length);
                msgOut.writeUTF("Done");
                msgOut.flush();
                //sendTime.add(System.currentTimeMillis());

                IRClient.frameInUse = false;
                IRClient.frame = null;
                //OOBOut.writeUTF("SENT\n");
                ticker++;

                if (msgIn.read(recvBuf) > 0){//OOBIn.read(recvBuf) > 0) {
                    long recvTime = System.currentTimeMillis();
                    Log.e(IRClient.TAG, "RTT: " + Long.toString(recvTime - sendTime.poll()));
                }
            } catch (Exception e) {
                //Log.e(IRClient.TAG, e.toString());
                continue;
            }
        }

        //Log.d(IRClient.TAG, "Done");
        //try {
        //    OOBOut.writeUTF("Done\n");
        //} catch (Exception e){ }
        //return null;
    }
}