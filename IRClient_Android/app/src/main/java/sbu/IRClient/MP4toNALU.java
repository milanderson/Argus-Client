package sbu.IRClient;

import android.util.Log;
import java.io.FileInputStream;

public class MP4toNALU {
    private static byte[] STCODE = {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01};
    private static byte[] SPSPPS = {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
            (byte)0x67,(byte)0x42,(byte)0x80,(byte)0x1E, (byte)0xDA,(byte)0x02,
            (byte)0x80,(byte)0xF6, (byte)0x94,(byte)0x83,(byte)0x03,(byte)0x03,
            (byte)0x03,(byte)0x68,(byte)0x50,(byte)0x9A,(byte)0x80,
            (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
            (byte)0x68,(byte)0xCE,(byte)0x06,(byte)0xE2,};
    private static FileInputStream inputStream = null;
    private static boolean sampleRead = false;

    public static byte[] initNaluStatic(){
        if(inputStream == null) {
            try {
                inputStream = new FileInputStream(IRClient.mOutputFile.getPath());
            } catch (Exception e) {
                Log.e(IRClient.TAG, e.toString());
                return null;
            }
        }
        return SPSPPS;
    }

    public static byte[] initNALUDynamic(){
        if(inputStream == null) {
            try {
                inputStream = new FileInputStream(IRClient.mInitFile.getPath());
            } catch (Exception e) {
                Log.e(IRClient.TAG, e.toString());
                return null;
            }
        }
        byte[] buf = new byte[500];
        int avc = 0;

        //Log.d(IRClient.TAG, "File opened searching for 'avc'");
        while(avc < 3){
            int bytesRead = 0;
            try{
                bytesRead = inputStream.read(buf, 0, 1);
            } catch (Exception e){
                Log.e(IRClient.TAG, e.toString());
                inputStream = null;
                return null;
            }
            if(bytesRead < 0){
                inputStream = null;
                return null;
            }

            if(avc == 0){
                if(buf[0] == (byte)0x61){
                    avc++;
                } else {avc = 0;}
            } else if (avc == 1){
                if(buf[0] == (byte)0x76){
                    avc++;
                } else {avc = 0;}
            } else if (avc == 2){
                if(buf[0] == (byte)0x63){
                    avc++;
                } else {avc = 0;}
            }
        }

        //Log.d(IRClient.TAG, "'avc' found. Tracking");
        int len = 0;
        while(buf[len] != 103){
            try{
                inputStream.read(buf, len, 1);
            } catch (Exception e){
                Log.e(IRClient.TAG, e.toString());
                continue;
            }
        }
        len++;

        int SPSEND = 0;
        while(true){
            try{
                inputStream.read(buf, len, 1);
            } catch (Exception e){
                Log.e(IRClient.TAG, e.toString());
                continue;
            }
            len++;
            if(len >=500){
                inputStream = null;
                return null;
            }

            if(SPSEND == 0){
                if (buf[len - 1] == (byte)0x01) {
                    SPSEND++;
                    //Log.d(IRClient.TAG, "0x01 found");
                }
            } else if (SPSEND == 1){
                if (buf[len - 1] == (byte)0x00){
                    SPSEND++;
                    //Log.d(IRClient.TAG, "0x00 found");
                } else { SPSEND = 0; }
            } else if (SPSEND == 2) {
                if (buf[len - 1] == (byte)0x04){
                    SPSEND++;
                    //Log.d(IRClient.TAG, "0x04 found");
                } else { SPSEND = 0; }
            }

            if (SPSEND == 3) {
                byte[] outbuf = new byte[len + 9];
                System.arraycopy(STCODE, 0, outbuf, 0, 4);
                System.arraycopy(buf, 0, outbuf, 4, len-3);
                System.arraycopy(STCODE, 0, outbuf, len+1, 4);

                try{
                    inputStream.read(outbuf, len + 5, 4);
                } catch (Exception e){
                    Log.e(IRClient.TAG, e.toString());
                }

                //for(int i = 0; i < outbuf.length; i++){
                //    Log.d(IRClient.TAG, "byte " + Integer.toString(i) + ": " + Integer.toString(outbuf[i] & 0xff));
                //}

                inputStream = null;
                return outbuf;
            }
        }

    }

    public static byte[] getNextFrame(){
        int frameFound = 0;
        byte buf[] = new byte[5];

        if(inputStream == null) {
            try {
                inputStream = new FileInputStream(IRClient.mOutputFile.getPath());
            } catch (Exception e) {
                Log.e(IRClient.TAG, e.toString());
                return null;
            }
        }

        if(!sampleRead) {
            while (frameFound < 4) {
                try {
                    if(inputStream.available() < 4 - frameFound){
                        return null;
                    }
                    inputStream.read(buf, 0, 1);
                } catch (Exception e) {
                    Log.e(IRClient.TAG, e.toString());
                    return null;
                }

                if (frameFound == 0) {
                    if (buf[0] == (byte) 0x6d) {
                        frameFound++;
                    }
                } else if (frameFound == 1) {
                    if (buf[0] == (byte) 0x64) {
                        frameFound++;
                    } else {
                        frameFound = 0;
                    }
                } else if (frameFound == 2) {
                    if (buf[0] == (byte) 0x61) {
                        frameFound++;
                    } else {
                        frameFound = 0;
                    }
                } else if (frameFound == 3) {
                    if (buf[0] == (byte) 0x74) {
                        frameFound++;
                        Log.d(IRClient.TAG, "Frame found");
                    } else {
                        frameFound = 0;
                    }
                }
            }
        }

        sampleRead = true;
        try{
            if(inputStream.available() < 4){
                return null;
            }
            inputStream.read(buf, 0, 4);
        } catch (Exception e){
            Log.e(IRClient.TAG, "Error reading frame size " + e.toString());
            return null;
        }

        /* Print capture latency */
        if (!IRClient.encodeTimes.isEmpty()) {
            Log.i(IRClient.TAG, "Encoded frame found in " + Long.toString(System.currentTimeMillis() - IRClient.encodeTimes.remove(0)) + " ms");
        } else {
            Log.e(IRClient.TAG, "Encode input-time missing");
        }

        int sampleSize = (((buf[0] & 0xff) << 24) | ((buf[1] & 0xff) << 16) | ((buf[2] & 0xff) << 8) | (buf[3] & 0xff));
        //Log.d(IRClient.TAG, "sample size" + Integer.toString(sampleSize) + " frame num" + Integer.toString(buf[0] & 0xFF) + " " + Integer.toString(buf[1] & 0xFF) + " " + Integer.toString(buf[2] & 0xFF) + " " + Integer.toString(buf[3] & 0xFF));

        byte[] outBuf = new byte[sampleSize + 4];

        try{
            while(inputStream.available() < sampleSize) {}
            outBuf[0] = (byte)0x00;
            outBuf[1] = (byte)0x00;
            outBuf[2] = (byte)0x00;
            outBuf[3] = (byte)0x01;
            inputStream.read(outBuf, 4, sampleSize);
        } catch (Exception e){
            Log.e(IRClient.TAG, e.toString());
            return null;
        }

        return outBuf;
    }
}
