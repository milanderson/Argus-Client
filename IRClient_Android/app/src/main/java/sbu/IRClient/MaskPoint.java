package sbu.IRClient;

import java.util.ArrayList;

public class MaskPoint {
    int x, y;
    private  int visited;
    private ArrayList<MaskPoint> connections = new ArrayList<MaskPoint>();
    int runLength, index;
    boolean isLinePoint;

    public MaskPoint (int x, int y, int runLength, int index, boolean isLinePoint){
        this.x = x;
        this.y = y;
        this.runLength = runLength;
        this.index = index;
        this.visited = 0;
        this.isLinePoint = isLinePoint;
    }

    public static boolean Colinear(MaskPoint ptA, MaskPoint ptB, MaskPoint ptC){
        float d = ptA.x*(ptB.y - ptC.y) + ptB.x*(ptC.y - ptA.y) + ptC.x*(ptA.y - ptB.y);
        if(d > -0.3 && d < 0.3){
            return true;
        }
        return false;
    }

    public void AddConnection(MaskPoint pt){
        if(!connections.contains(pt)){
            connections.add(pt);
        }
    }

    public ArrayList<MaskPoint> GetConnections(){
        return connections;
    }

    public MaskPoint isConnected(int x, int y){
        if(this.x == x && this.y == y){
            return this;
        }

        for(MaskPoint pt: connections){
            if (pt.x == x && pt.y == y){
                return pt;
            }
        }
        return null;
    }

    public void Visit(){
        this.visited ++;
    }

    public void ResetVisits(){
        this.visited = 0;
    }

    public int VisitCount(){
        return this.visited;
    }

    public boolean IsLinePoint(){
        return isLinePoint;
    }
}
