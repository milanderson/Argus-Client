package sbu.irclient;

public class MaskPoint {
    float x, y;
    MaskPoint lPt, rPt;

    public MaskPoint (int x, int y){
        this.x = x;
        this.y = y;
        this.lPt = null;
        this.rPt = null;
    }

    public static boolean Colinear(MaskPoint ptA, MaskPoint ptB, MaskPoint ptC){
        float d = ptA.x*(ptB.y - ptC.y) + ptB.x*(ptC.y - ptA.y) + ptC.x*(ptA.y - ptB.y);
        if(d > -0.3 && d < 0.3){
            return true;
        }
        return false;
    }
}
