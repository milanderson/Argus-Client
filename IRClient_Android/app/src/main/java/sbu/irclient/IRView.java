package sbu.irclient;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class IRView extends View {
    ReentrantLock writing = new ReentrantLock();
    private ArrayList<Rect> rects = new ArrayList<Rect>();
    ArrayList<MaskPoint> pts = new ArrayList<MaskPoint>();
    private Paint brush;
    private int lineWidth = 2;
    private int lineColor = Color.GREEN;
    private int maskSize;

    public IRView(Context context) {
        super(context);

        brush = new Paint();
        brush.setColor(lineColor);
        brush.setStrokeWidth(lineWidth);
        brush.setStyle(Paint.Style.STROKE);
    }

    public void setRectList(String buf){
        writing.lock();
        rects.clear();
        writing.unlock();

        int i = 0;
        int x, y, w, h, iNext;
        while(i < buf.length() - 13) {
            if (buf.charAt(i) == 'R') {
                break;
            }

            iNext = nextComma(buf, i);
            y = Integer.parseInt(buf.substring(i, iNext));
            y = y * this.getHeight() / 640;
            //Log.d(IRClient.TAG, Integer.toString(x) + " ");
            i = iNext + 1;

            iNext = nextComma(buf, i);
            x = Integer.parseInt(buf.substring(i, iNext));
            x = x * this.getWidth() / 480;
            //Log.d(IRClient.TAG, Integer.toString(y) + " ");
            i = iNext + 1;

            iNext = nextComma(buf, i);
            h = Integer.parseInt(buf.substring(i, iNext));
            h = h * this.getHeight() / 640;
            //Log.d(IRClient.TAG, Integer.toString(w) + " ");
            i = iNext + 1;

            iNext = nextComma(buf, i);
            w = Integer.parseInt(buf.substring(i, iNext));
            w = w * this.getWidth() / 480;
            //Log.d(IRClient.TAG, Integer.toString(h) + " ");
            i = iNext + 1;

            if (rects.size() < 100) {
                writing.lock();
                rects.add(new Rect(this.getWidth() - (x + w), y, this.getWidth() - x, y + h));
                writing.unlock();
            }
        }
        invalidate();
    }

    private int isConnected(float[] bitMask, int index, int rowLen, MaskPoint pt){
        int y = index / rowLen;
        int x = index % rowLen;

        if(pt.x - x <= 0) {
            if(pt.x - x < -1){
                return -1;
            }
        } else {
            for (int i = 1; x + i < pt.x - 1; i += 1) {
                if (bitMask[index + i] <= 0) {
                    return -1;
                }
            }
        }

        for(int i = 1; i + x < rowLen; i+=1) {
            if(bitMask[index + i] != 1){
                return index + i -1;
            }
        }
        return index - x + rowLen - 1;
    }

    public void setContoursFromBitMask(float[] bitMask){
        writing.lock();
        pts.clear();
        writing.unlock();

        int rowLen = (int)Math.sqrt(bitMask.length);
        //Log.e(IRClient.TAG, Integer.toString(rowLen));
        ArrayList<MaskPoint> currPts = new ArrayList<MaskPoint>();


        // loop through bitmask one row at a time
        for (int i = bitMask.length - 1 - rowLen; i >= 0; i-= rowLen){

            // clear old unconnected points from point list
            for(int ptI = 0; ptI < currPts.size(); ptI++){
                if(currPts.get(ptI).y > ((i + 1)/rowLen) + 1){
                    MaskPoint pt1 = currPts.get(ptI);
                    int add = 0;
                    if(ptI + 1 < currPts.size()) {
                        MaskPoint pt2 = currPts.get(ptI + 1);
                        if (pt1.lPt == null && pt2.rPt == null && pt1.y == pt2.y) {
                            Log.d(IRClient.TAG, "box top");
                            pt1.lPt = pt2;
                            pt2.rPt = pt1;

                            pts.add(pt2);
                            currPts.remove(ptI + 1);
                            add++;
                        }
                    }
                    pts.add(pt1);
                    currPts.remove(ptI);
                    ptI += add;
                }
            }

            // loop through each element in row
            for (int j = i; j < i + rowLen; j++) {
                boolean pointFound = false;

                // check if the bit is marked as masking an object (set to 1)
                if (bitMask[j] > 0) {
                    // create new points
                    MaskPoint newPtA = new MaskPoint(j % rowLen, j/rowLen);

                    //check if bit is 'connected' to previous points
                    for(int ptI = 0; ptI < currPts.size(); ptI++){
                        //Log.d(IRClient.TAG, "checking point " + Integer.toString(ptI));
                        MaskPoint currPt = currPts.get(ptI);
                        int nextPt = isConnected(bitMask, j, rowLen, currPt);

                        //Log.d(IRClient.TAG, "Check point x,y: " + Float.toString(currPt.x) + " " + Float.toString(currPt.y));
                        //Log.d(IRClient.TAG, "Curr point x,y: " + Float.toString(newPtA.x) + " " + Float.toString(newPtA.y));
                        //Log.d(IRClient.TAG, "Next point x,y: " + Integer.toString(nextPt%rowLen) + " " + Integer.toString(nextPt/rowLen));

                        // if connected add the appropriate references
                        if(nextPt != -1){
                            pointFound = true;

                            // move the old connected point into the point list
                            pts.add(currPt);
                            currPts.remove(currPt);
                            ptI--;

                            // handle degenerate cases
                            boolean rtPtFound = false;
                            if(nextPt != j) {
                                rtPtFound = true;
                                j = nextPt - 1;
                            }


                            //// add appropriate references

                            // check point connects to current index and next item
                            if(currPt.rPt == null && currPt.lPt == null) {
                                //Log.d(IRClient.TAG, "Adding left and right points");

                                currPt.lPt = newPtA;
                                newPtA.rPt = currPt;
                                currPts.add(newPtA);

                                if(rtPtFound) {
                                    MaskPoint newPtB = new MaskPoint(nextPt % rowLen, nextPt / rowLen);
                                    currPt.rPt = newPtB;
                                    newPtB.lPt = currPt;
                                    currPts.add(newPtB);
                                } else {
                                    // handle degenerate case
                                    currPt.rPt = newPtA;
                                }
                            } else if(currPt.lPt == null){
                                //Log.d(IRClient.TAG, "Adding left point");
                                currPt.lPt = newPtA;
                                newPtA.rPt = currPt;
                                currPts.add(newPtA);
                            } else if(currPt.rPt == null) {
                                //Log.d(IRClient.TAG, "Adding right point");
                                currPt.rPt = newPtA;
                                newPtA.lPt = currPt;
                                currPts.add(newPtA);
                            }

                            ptI = currPts.size() + 1;
                        }
                    }

                    if(!pointFound){
                        currPts.add(newPtA);


                        //Log.d(IRClient.TAG, "NewPtA x,y: " + Float.toString(newPtA.x) + " " + Float.toString(newPtA.y));
                        int nextPt = j;
                        while( nextPt < i + rowLen - 1 && bitMask[nextPt+1] > 0){
                            nextPt++;
                        }

                        if (nextPt != j){
                            MaskPoint newPtB = new MaskPoint(nextPt%rowLen, nextPt/rowLen);
                            //Log.d(IRClient.TAG, "NewPtB x,y: " + Float.toString(newPtB.x) + " " + Float.toString(newPtB.y));
                            currPts.add(newPtB);

                            newPtA.rPt = newPtB;
                            newPtB.lPt = newPtA;
                        }
                        j = nextPt;
                    }
                }

            }
        }

        // add any remaining points to the point list
        for(int ptI = 0; ptI < currPts.size() - 1; ptI++){
            MaskPoint pt1 = currPts.get(ptI);
            MaskPoint pt2 = currPts.get(ptI + 1);
            if(pt1.rPt == null && pt2.lPt == null){
                pt1.rPt = pt2;
                pt2.lPt = pt1;

                pts.add(pt1);
                pts.add(pt2);
                ptI++;
            } else if(pt1.lPt == null && pt2.rPt == null){
                pt1.lPt = pt2;
                pt2.rPt = pt1;

                pts.add(pt1);
                pts.add(pt2);
                ptI++;
            }
        }
        Log.d(IRClient.TAG, "pts has " + Integer.toString(pts.size()) + " pts");
        maskSize = rowLen;
        invalidate();
    }

    public void clear(){
        rects.clear();
        invalidate();
    }

    private int nextComma(String buf, int index){
        while(buf.charAt(index) != ','){
            index++;
        }
        return index;
    }

    @Override
    protected void onDraw(Canvas canvas){
        //Log.d(IRClient.TAG, "Drawing rects 0-" + Integer.toString(rects.size()));

        writing.lock();
        //for(Rect rect : rects){
        //    canvas.drawRect(rect, brush);
        //}

        if(pts.size() > 0 && maskSize > 0) {
            float xRatio = canvas.getWidth() / maskSize;
            float yRatio = canvas.getHeight() / maskSize;
            for (MaskPoint point : pts) {
                //Log.d(IRClient.TAG, "ptA x: " + Float.toString(point.x * xRatio) + " y " + Float.toString(point.y * yRatio));
                if(point.lPt != null) {
                    //Log.d(IRClient.TAG, "ptB x: " + Float.toString(point.lPt.x * xRatio) + " y " + Float.toString(point.lPt.y * yRatio));
                    canvas.drawLine(point.x * xRatio, point.y * yRatio, point.lPt.x * xRatio, point.lPt.y * yRatio, brush);
                }
                //canvas.drawLine(point.x * xRatio, point.y * yRatio, point.rPt.x * xRatio, point.rPt.y * yRatio, brush);
            }
        }
        writing.unlock();
    }
}
