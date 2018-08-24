package sbu.IRClient;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class IRClient_NetTask extends AsyncTask<IRClient, Void, Void> {
    public static String IR_REQ_ST = "SlideShow";
    public static String SERVER_IP = "130.245.158.1";
    public final int SERVER_PORT = 50505;
    public final int ALT_PORT = 50506;

    private int MSGSIZE = 2400;
    private Selector selector;
    SelectionKey pipeKey, sReadKey;
    private ByteBuffer signalOutBuf = ByteBuffer.allocate(200);
    private String recvMsg = null;
    private String exitMsg = null;

    private Runnable clear_overlay = new Runnable(){
        @Override
        public void run(){
            IRClient.overlayView.clear();
        }
    };
    private Runnable update_rects = new Runnable() {
        @Override
        public void run() {
            IRClient.overlayView.setRectList(recvMsg);
        }
    };
    private Runnable update_bitmask = new Runnable() {
        @Override
        public void run() {
            IRClient.overlayView.setContoursFromBitMask(recvMsg);
        }
    };
    private Runnable update_guesses = new Runnable() {
        @Override
        public void run() {
            IRClient.overlayView.setGuesses(recvMsg);
        }
    };
    private Runnable exit_nettask = new Runnable(){
        @Override
        public void run(){
            IRClient.onRunStateChanged(exitMsg, IRClient.State.DEFAULT);
        }
    };

    @Override
    protected Void doInBackground(IRClient... clients) {

        IRClient client = clients[0];
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(IRClient.context.getMainLooper());
        Looper.prepare();

        SocketChannel msgSock, replySock;
        byte stMsg[] = new byte[MSGSIZE];
        ByteBuffer recvBuf = ByteBuffer.wrap(stMsg);

        while(true) {
            try {
                Log.d(IRClient.TAG, "Initializing network thread");

                msgSock = SocketChannel.open();
                replySock = SocketChannel.open();
                msgSock.connect(new InetSocketAddress(SERVER_IP, SERVER_PORT));
                msgSock.write(ByteBuffer.wrap("Slideshow".getBytes()));

                replySock.connect(new InetSocketAddress(SERVER_IP, ALT_PORT));

                msgSock.read(recvBuf);

                selector = Selector.open();
                pipeKey = IRClient.pipe.source().register(selector, SelectionKey.OP_READ);
                msgSock.configureBlocking(false);
                sReadKey = msgSock.register(selector, SelectionKey.OP_READ);
                Log.d(IRClient.TAG, "network thread initialized");
            } catch (Exception e) {
                Log.e(IRClient.TAG, "Socket init error.\n" + e.toString());
                exitMsg = "Connection failed.";
                mainHandler.post(exit_nettask);
                return null;
            }

            int lastRead = 0;
            boolean msgComplete;
            byte[] buf = null;
            recvBuf.clear();

            while (true) {
                try {
                    selector.select();

                    Iterator<SelectionKey> iter = selector.selectedKeys().iterator();

                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();

                        // send frame data
                        if (key.isReadable() && key == pipeKey) {
                            signalOutBuf.clear();
                            IRClient.pipe.source().read(signalOutBuf);
                            signalOutBuf.flip();
                            String pipeMsg = "";

                            while (signalOutBuf.hasRemaining()) {
                                char ch = (char) signalOutBuf.get();
                                pipeMsg += ch;
                            }
                            //Log.d(IRClient.TAG, "Pipemsg: " + pipeMsg);

                            if (pipeMsg.contains("frame") && IRClient.getState() == IRClient.State.CLASSIFY) {
                                buf = IRClient.frame;
                                //Log.d(IRClient.TAG, "Sending chunk size " + Integer.toString(buf.length));

                                //String frameString = byteToHexString(buf);
                                //Log.d(IRClient.TAG, frameString);
                                int bytesWritten = 0;
                                while (bytesWritten < buf.length) {
                                    //Log.d(IRClient.TAG, Boolean.toString(sWriteKey.isWritable()));
                                    ByteBuffer frameBuf = ByteBuffer.allocate(buf.length - bytesWritten);
                                    frameBuf.put(buf, bytesWritten, buf.length - bytesWritten);
                                    frameBuf.flip();

                                    //Log.d(IRClient.TAG, "pos: " + Integer.toString(frameBuf.position()) + " remaining: " + Integer.toString(frameBuf.remaining()));
                                    bytesWritten += msgSock.write(frameBuf);
                                    //Log.d(IRClient.TAG,"wrote: " + Integer.toString(bytesWritten));
                                }
                            }

                            if(pipeMsg.contains("NETEXIT")){
                                Log.d(IRClient.TAG, pipeMsg);
                                replySock.close();
                                msgSock.close();
                                return null;
                            }

                            if (pipeMsg.contains("reply")) {
                                pipeMsg = pipeMsg.substring(pipeMsg.indexOf("reply") + 5);
                                String replyMsg = pipeMsg.substring(0, pipeMsg.indexOf(','));
                                replyMsg.replaceAll("[^A-Za-z ]","");
                                replyMsg = replyMsg + "^";

                                ByteBuffer msg = ByteBuffer.allocate(replyMsg.getBytes().length);
                                msg.put(replyMsg.getBytes());
                                msg.flip();
                                replySock.write(msg);
                            }
                        }

                        // receive and process incoming msg
                        else if (key.isReadable() && key == sReadKey && IRClient.getState() == IRClient.State.CLASSIFY) {

                            msgComplete = false;
                            // read new data
                            lastRead += msgSock.read(recvBuf);
                            // convert data to string and search for end-of message
                            String checkString = new String(stMsg, 0, lastRead);
                            //Log.d(IRClient.TAG, checkString);
                            int lineEnd = checkString.indexOf("RECEIVED");

                            //if message is complete convert to string and shift original buffer
                            if (lineEnd > 0) {
                                recvMsg = new String(stMsg, 0, lineEnd);
                                if (lastRead - (lineEnd + 8) >= 0) {
                                    System.arraycopy(stMsg, lineEnd + 8, stMsg, 0, lastRead - (lineEnd + 8));
                                    lastRead = lastRead - (lineEnd + 8);
                                    recvBuf = ByteBuffer.wrap(stMsg);
                                    recvBuf.position(lastRead);
                                } else {
                                    lastRead = 0;
                                }
                                msgComplete = true;
                            } else if (lineEnd == 0){
                                recvMsg = "";
                                msgComplete = true;
                            }

                            if (msgComplete && IRClient.getState() == IRClient.State.CLASSIFY) {
                                long recvTime = System.currentTimeMillis();
                                //Log.e(IRClient.TAG, "RTT: " + Long.toString(recvTime - IRClient.sendTime));
                                //Log.d(IRClient.TAG, "Recieved " + Integer.toString(recvMsg.length()) + " bytes:"  + recvMsg);
                                mainHandler.post(update_guesses);

                                // Use for receiving a plain text, comma separated list of bounding rectangles coordinates
                                //mainHandler.post(update_rects);

                                // use for receiving a plain text, run length encoded NxN bitmask flagging pixels covering the object
                                //mainHandler.post(update_bitmask);
                            } else {
                                mainHandler.post(clear_overlay);
                            }
                        }

                        iter.remove();
                    }

                } catch (Exception e) {
                    Log.d(IRClient.TAG,"Network thread error. " + e.toString());
                    break;
                }
            }
        }
        //return null;
    }

    private String byteToHexString(byte[] bytes){
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        int i = 0;
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
            i++;
        }
        return new String(hexChars);
    }
}
