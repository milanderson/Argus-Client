package sbu.IRClient;

import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

import static sbu.IRClient.IRClient.NetMonitor.MSGSIZE;

public class Net_RespReader extends Thread {
    private byte[] recvBuf = new byte[MSGSIZE];
    private long recvTime = 0;
    private int fID = 0;
    private JSONArray recvJSONs;
    private String rectList = null;
    private String guesses = null;
    private long latency = 0;

    private Runnable clear_overlay = new Runnable(){
        @Override
        public void run(){
            IRClient.overlayView.clear();
        }
    };
    private Runnable update_rects = new Runnable() {
        @Override
        public void run() {
            IRClient.overlayView.setRectList(IRClient.NetMonitor.openRects());
            IRClient.NetMonitor.closeRects();
            IRClient.NetMonitor.clearRects();
        }
    };
    private Runnable update_bitmask = new Runnable() {
        @Override
        public void run() {
            IRClient.overlayView.addBitmapMasks(IRClient.NetMonitor.openMasks());
            IRClient.NetMonitor.closeMasks();
            IRClient.NetMonitor.clearMasks();
        }
    };
    private Runnable update_latency = new Runnable() {
        @Override
        public void run() {
            IRClient.latencyView.setText(Long.toString(latency) + "ms");
        }
    };
    private Runnable update_guesses = new Runnable() {
        @Override
        public void run() {
            IRClient.overlayView.setGuesses(guesses);
        }
    };

    Net_RespReader(){
        try {
            Looper.prepare();
        } catch (Exception e){
            Log.e(IRClient.TAG, "Error initializing Response thread: " +  e.toString());
        }
    }

    @Override
    public void run() {
        boolean msgComplete = false;
        InputStream connIn = null;
        String recvFrameID;
        String recvString = "";

        while(true){
            try {
                if(interrupted()) {
                    // check if shutdown is required
                    if (!IRClient.NetMonitor.isNetStatusOK()) {
                        return;
                    }
                }

                IRClient.NetMonitor.awaitReadTask();

                recvFrameID = "0";
                msgComplete = false;
                connIn = IRClient.NetMonitor.getResponseConn();


                if(connIn != null) {
                    try{
                        while(!msgComplete){

                            int numRead = connIn.read(recvBuf, 0, recvBuf.length);
                            if(numRead == -1){
                                break;
                            }
                            //Log.d(IRClient.TAG, Integer.toString(numRead) + " of " + Integer.toString(recvBuf.length));
                            if (numRead > 1) {
                                recvString += new String(recvBuf, 0, numRead);
                                //Log.d(IRClient.TAG, recvString);
                                int lineSt = recvString.indexOf('{');
                                int lineEnd = lineSt;
                                int openCount = 1;
                                //Log.d(IRClient.TAG, "openCount start: " + Integer.toString(openCount) + " of " + Integer.toString(recvString.length()));
                                if(lineEnd > 0) {
                                    do {
                                        lineEnd++;
                                        if (recvString.charAt(lineEnd) == '[') {
                                            openCount++;
                                            //Log.d(IRClient.TAG, "openCount inc: " + Integer.toString(openCount) + " at " + Integer.toString(lineEnd));
                                        }
                                        if (recvString.charAt(lineEnd) == ']') {
                                            openCount--;
                                            //Log.d(IRClient.TAG, "openCount dec: " + Integer.toString(openCount) + " at " + Integer.toString(lineEnd));
                                        }
                                    } while (openCount > 0 && lineEnd < recvString.length() - 1);
                                }

                                if(openCount == 0) {

                                    //if message is complete convert to string and shift original buffer
                                    if (lineEnd > 0) {
                                        //Log.d(IRClient.TAG, "JSONifying " + recvString.substring(lineSt - 1, lineEnd + 1));
                                        recvJSONs = new JSONArray(recvString.substring(lineSt - 1, lineEnd + 1));
                                        //Log.d(IRClient.TAG, "JSONarray len: " + Integer.toString(recvJSONs.length()));
                                        recvFrameID = new String(recvBuf, 0,  4);
                                        recvString = "";
                                        msgComplete = true;
                                    } else {
                                        recvJSONs = null;
                                        msgComplete = true;
                                        recvString = "";
                                    }
                                }
                            }
                        }
                    } catch (Exception e){
                        Log.e(IRClient.TAG, "Reading thread failed to read: " + e.toString());
                    } finally {
                        //conn.disconnect();
                    }

                }

                if (msgComplete && IRClient.getState() == IRClient.State.CLASSIFY && recvJSONs != null) {
                    recvTime = System.currentTimeMillis();
                    fID = Integer.parseInt(recvFrameID);
                    latency = recvTime - IRClient.NetMonitor.getFrameTimeStamp(fID);

                    Log.e(IRClient.TAG, "FrameID: " + recvFrameID + " latency: " + Long.toString(IRClient.NetMonitor.getFrameTimeStamp(fID)));
                    Log.e(IRClient.TAG, "Timestamp: " + Long.toString(IRClient.NetMonitor.getFrameTimeStamp(fID)) + " Latency: " + Long.toString(latency));
                    if(latency > 0 && latency < 30000) {
                        IRClient.mainHandler.post(update_latency);
                    }

                    for(int i = 0; i < recvJSONs.length(); i++) {
                        JSONObject recvJSON = recvJSONs.getJSONObject(i);

                        // Use for receiving a list of comma separated guesses
                        //Log.d(IRClient.TAG, "Updating guesses");
                        //IRClient.mainHandler.post(update_guesses);

                        // Use for receiving a plain text, comma separated list of bounding rectangles coordinates
                        //Log.d(IRClient.TAG, "adding to rectlist");
                        try {
                            rectList = "";
                            rectList = recvJSON.getString("bounding_box").substring(1, recvJSON.getString("bounding_box").length() - 1) + ",";
                            rectList += recvJSON.getString("class") + " " + recvJSON.getString("confidence").substring(0, 4) + ",";
                        } catch (Exception e) {}
                        if(rectList != null && rectList.length() > 0) {
                            IRClient.NetMonitor.addRectList(rectList);
                        }

                        // use for receiving a plain text, run length encoded NxN bitmask flagging pixels covering the object
                        try {
                            Log.d(IRClient.TAG, "adding bitmask: " + recvJSON.getString("bit_mask"));
                            IRClient.NetMonitor.addMask(recvJSON.getString("bit_mask"));
                        } catch (Exception e) {}
                    }
                    Log.d(IRClient.TAG, "Updating rects" + rectList);
                    IRClient.mainHandler.post(update_rects);
                    Log.d(IRClient.TAG, "Updating bitmasks");
                    IRClient.mainHandler.post(update_bitmask);
                }

            } catch (Exception e){
                Log.e(IRClient.TAG, "Response thread failed to process response: " + e.toString());
            }

        }
    }

}
