package sbu.IRClient;

import java.util.ArrayList;

public class MaskPoint {
    int x, y;
    ArrayList<MaskPoint> connections = new ArrayList<MaskPoint>();
    int runLength, index;

    public MaskPoint (int x, int y, int runLength, int index){
        this.x = x;
        this.y = y;
        this.runLength = runLength;
        this.index = index;
    }

    public static boolean Colinear(MaskPoint ptA, MaskPoint ptB, MaskPoint ptC){
        float d = ptA.x*(ptB.y - ptC.y) + ptB.x*(ptC.y - ptA.y) + ptC.x*(ptA.y - ptB.y);
        if(d > -0.3 && d < 0.3){
            return true;
        }
        return false;
    }

    public boolean isConnected(int x, int y){
        if(this.x == x && this.y == y){
            return true;
        }

        for(MaskPoint pt: connections){
            if (pt.x == x && pt.y == y){
                return true;
            }
        }
        return false;
    }
}
