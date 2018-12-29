package sbu.IRClient;

import android.util.Log;

public class FrameTracker {
    private Frame frame = new Frame();
    private long[] timeStamps = new long[256];

    public FrameTracker(){
        frame.fID = -1;
    }

    public Frame GetFrame(){
        return frame;
    }

    public void SetFrame(byte[] bytes){
        frame.fID = (frame.fID + 1)%256;
        frame.bytes = bytes;
        timeStamps[frame.fID] = System.currentTimeMillis();
    }

    public long GetTimeStamp(int i){
        return timeStamps[i];
    }

    public void Reset(){
        for(int i = 0; i < timeStamps.length; i ++){
            timeStamps[i] = 0;
        }
        frame.bytes = null;
        frame.fID = 0;
    }
}