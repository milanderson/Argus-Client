package sbu.IRClient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

public class Net_ClassRequester extends Thread {
    public static String SERVER_IP = "http://130.245.158.1:50505";
    public final int SERVER_PORT = 50505;
    public long[] avgTimes = new long[4];

    static {
        System.loadLibrary("native-lib");
    }

    @Override
    public void run() {
        avgTimes[0] = 0;avgTimes[1] = 0;avgTimes[2] = 0; avgTimes[3] = 0;
        URL url = null;

        try {
            Looper.prepare();
        } catch (Exception e){ }

        while(true) {
            try {
                Log.d(IRClient.TAG, "Initializing network thread");
                //HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                //urlConnection.setDoOutput(true);
//                urlConnection.setDoInput(true);
//                urlConnection.setRequestMethod("POST");
//                urlConnection.setRequestProperty("Content-Type", "application/octet-stream");
//                urlConnection.setRequestProperty("Connection", "Keep-Alive");
//                urlConnection.setRequestProperty("User-Agent", "Android Argus Client");

                IRClient.NetMonitor.startResponseThread();

                Log.d(IRClient.TAG, "network thread initialized");
            } catch (Exception e) {
                Log.e(IRClient.TAG, "Socket init error. " + e.toString());
                IRClient.NetMonitor.startNetExit("Connection Error");
                return;
            }
            IRClient.NetMonitor.setNetStatusOK();

            Frame frame;
            String netReply;
            HttpURLConnection urlConnection = null;

            while (true) {
                try {
                    IRClient.NetMonitor.awaitSendTask();

                    if(interrupted()) {
                        // check if shutdown is required
                        if (!IRClient.NetMonitor.isNetStatusOK()) {
                            if(urlConnection != null){
                                urlConnection.disconnect();
                            }
                            return;
                        }
                    }

                    // check if a frame is available
                    frame = IRClient.NetMonitor.getFrame();
                    netReply = IRClient.NetMonitor.getReply();
                    if (frame != null) {
                        Log.d(IRClient.TAG, "Connecting to server");
                        String fullURL = SERVER_IP;

                        url = new URL(fullURL);
                        urlConnection = (HttpURLConnection) url.openConnection();

                        urlConnection.setDoOutput(true);
                        urlConnection.setDoInput(true);
                        urlConnection.setRequestMethod("POST");
                        urlConnection.setFixedLengthStreamingMode(((320*240*3)/2) + 4);
                        urlConnection.setRequestProperty("User-Agent", "Android Argus Client");

                        //Log.d(IRClient.TAG, url.toString());

                        // testing for various resize methods
                        /*
                        long stTime;
                        long edTime;
                        avgTimes[3]++;

                        Log.d(IRClient.TAG, "Convert to YUV custom");
                        stTime = System.currentTimeMillis();
                        byte[] smallYUV_java = shrinkYUV420(frame.bytes, 480, 640, 300, 300);
                        edTime = System.currentTimeMillis();
                        avgTimes[0] += edTime - stTime;
                        Log.d(IRClient.TAG, "YUV Resize took: " + Long.toString(edTime - stTime));

                        Log.d(IRClient.TAG, "Convert to YUV jni");
                        stTime = System.currentTimeMillis();
                        byte[] smallYUV_jni = resizeYUV420(frame.bytes, 640, 480, 320, 240);
                        edTime = System.currentTimeMillis();
                        avgTimes[1] += edTime - stTime;
                        Log.d(IRClient.TAG, "YUV Resize took: " + Long.toString(edTime - stTime));

                        Log.d(IRClient.TAG, "Convert to JPG");
                        stTime = System.currentTimeMillis();
                        ByteBuffer jpg = shinkYUV420toBitmap(frame.bytes, 480, 640, 320, 240);
                        edTime = System.currentTimeMillis();
                        avgTimes[2] += edTime - stTime;
                        Log.d(IRClient.TAG, "JPG Resize took: " + Long.toString(edTime -stTime));

                        Log.d(IRClient.TAG, "Avg custom: " + Long.toString(avgTimes[0]/avgTimes[3]) + " Avg native: " + Long.toString(avgTimes[1]/avgTimes[3]) + " Avg JPG: " + Long.toString(avgTimes[2]/avgTimes[3]));


                        //Log.d(IRClient.TAG, "Sending chunk size " + Integer.toString(buf.length));
                        //Log.d(IRClient.TAG, "sending frame " + String.format("%04d", frame.fID));
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        YuvImage yuvImage = new YuvImage(frame.bytes, ImageFormat.NV21, 480, 640, null);
                        yuvImage.compressToJpeg(new Rect(0, 0, 480, 640), 70, out);
                        byte[] imageBytes = out.toByteArray();


                        String line = "";
                        int printLine = 0;
                        Log.d(IRClient.TAG, "color array from byte " + Integer.toString(640*480) + " to " + Integer.toString(frame.bytes.length));
                        for(int i = 640*480; i < (640*480) + 640; i++){
                            line += Byte.toString(frame.bytes[i]) + " ";
                            printLine++;
                            if(printLine >= 160){
                                Log.d(IRClient.TAG, line);
                                line = "";
                                printLine = 0;
                            }
                        }
                        printLine = 0;
                        line = "";
                        Log.d(IRClient.TAG, "color array from byte " + Integer.toString(320*240) + " to " + Integer.toString(smallYUV_jni.length));
                        for(int i = 320*240; i < (320*240) + 320; i++){
                            line += Byte.toString(smallYUV_jni[i]) + " ";
                            printLine++;
                            if(printLine >= 160){
                                Log.d(IRClient.TAG, line);
                                line = "";
                                printLine = 0;
                            }
                        }
                        */

                        byte[] smallYUV_jni = resizeYUV420(frame.bytes, 640, 480, 320, 240);

                        OutputStream msgOut = urlConnection.getOutputStream();
                        msgOut.write(String.format("%04d", frame.fID).getBytes());
                        msgOut.write(smallYUV_jni);
                        msgOut.flush();

                        InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                        //BufferedReader br = new BufferedReader(in);
                        IRClient.NetMonitor.addResponseConn(in);
                        Log.d(IRClient.TAG, "resp conn added");
                    }

                    /*
                    // check if a reply is available
                    if (netReply != null) {
                        netReply.replaceAll("[^A-Za-z ]", "");
                        netReply = netReply + "^";

                        replyOut.write(netReply.getBytes());
                    }
                    */

                } catch (Exception e) {
                    Log.d(IRClient.TAG,"Network speaking thread error. " + e.toString());
                    //IRClient.NetMonitor.startNetExit("Network Error");
                }
            }
        }
    }

    private native byte[] resizeYUV420(byte[] yuv420, int fromW, int fromH, int toW, int toH);

    private ByteBuffer shinkYUV420toBitmap(byte[] yuv420, int fromW, int fromH, int toW, int toH){
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuvImage = new YuvImage(yuv420, ImageFormat.NV21, fromW, fromH, null);
        yuvImage.compressToJpeg(new Rect(0, 0, fromW, fromH), 70, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        Bitmap bmp = Bitmap.createScaledBitmap(image, toW, toH, true);

        ByteBuffer retBuf = ByteBuffer.allocate(bmp.getByteCount());
        bmp.copyPixelsToBuffer(retBuf);

        return retBuf;
    }

    private byte[] shrinkYUV420(byte[] yuv420, int fromW, int fromH, int toW, int toH) throws Exception {
        if(fromW <= toW || fromH <= toH){
            throw new Exception("Incorrect arguments: image must shrink");
        }

        byte[] imgOut = new byte[(toW*toH*3)/2];
        int insWSpan = (fromW/toW);
        int insHSpan = (fromH/toH);

        int fromIndex;
        int toIndex = 0;
        int toCbStart = toW*toH;
        int toCrStart = toW*toH*5/4;
        int fromCbStart = fromW*fromH;
        int fromCrStart = fromW*fromH*5/4;
        for(int i = 0; i < fromW; i++){
            for(int j = 0; j < fromH; j++){
                fromIndex = (fromW * j) + i;
                int currToW = toIndex % toW;
                int currToH = toIndex / toW;

                if(     (fromW%insWSpan == 0 || fromW - i - 1 <= toW - currToW) &&
                        (fromH%insHSpan == 0 || fromH - j - 1 <= toH - currToH) &&
                        !(toIndex >= toW*toH)){
                    imgOut[toIndex] = yuv420[fromIndex];

                    int fromChromaOffset = (i>>1) + ((j*toW)>>2);
                    int toChromaOffset = (currToW>>1) + ((currToH*toW)>>2);
                    //imgOut[Math.min(toCbStart + toChromaOffset, toH*toW*5/4)] += yuv420[Math.min(fromCbStart + fromChromaOffset, fromH*fromW*5/4)]>>2;
                    //imgOut[Math.min(toCrStart + toChromaOffset,imgOut.length-1)] += yuv420[Math.min(fromCrStart + fromChromaOffset, yuv420.length - 1)]>>2;

                    toIndex++;
                }
            }
        }
        return imgOut;
    }
}
