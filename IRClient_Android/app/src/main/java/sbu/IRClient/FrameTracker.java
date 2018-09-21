package sbu.IRClient;

public class FrameTracker {
    private byte[] frame;
    private long[] timeStamps = new long[256];
    private int index = 0;

    public FrameTracker(){
    }

    public byte[] GetFrame(){
        return frame;
    }

    public void SetFrame(byte[] bytes){
        frame = bytes;
        timeStamps[index] = System.currentTimeMillis();
        index = (index + 1)%256;
    }

    public int GetID(){
        return index;
    }

    public long GetTimeStamp(int i){
        return timeStamps[i];
    }
}
