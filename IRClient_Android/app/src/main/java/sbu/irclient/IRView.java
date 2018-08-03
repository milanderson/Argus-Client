package sbu.irclient;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import static sbu.irclient.IRClient.BLbutton;
import static sbu.irclient.IRClient.rec;

public class IRView extends View {
    ReentrantLock writing = new ReentrantLock();
    private ArrayList<ObjRect> rects = new ArrayList<ObjRect>();
    ArrayList<MaskPoint> pts = new ArrayList<MaskPoint>();
    private Paint brush;
    private int lineWidth = 2;
    private int lineColor = Color.GREEN;
    private int maskSize;
    private Rect highlightedRect;

    public IRView(Context context) {
        super(context);

        brush = new Paint();
        brush.setColor(lineColor);
        brush.setStrokeWidth(lineWidth);
        brush.setStyle(Paint.Style.STROKE);
    }

    public void setRectList(String buf){
        //Log.d(IRClient.TAG, "setting rect list");
        try {
            writing.lock();
            rects.clear();
            writing.unlock();

            int i = 0;
            int x, y, w, h, iNext;
            while (i < buf.length() - 13) {
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

                iNext = nextComma(buf, i);
                String guess1 = buf.substring(i, iNext);
                //Log.d(IRClient.TAG, guess1 + " ");
                i = iNext + 1;

                iNext = nextComma(buf, i);
                String guess2 = buf.substring(i, iNext);
                //Log.d(IRClient.TAG, guess2 + " ");
                i = iNext + 1;

                iNext = nextComma(buf, i);
                String guess3 = buf.substring(i, iNext);
                //Log.d(IRClient.TAG, guess3 + " ");
                i = iNext + 1;

                if (rects.size() < 100) {
                    writing.lock();
                    ObjRect newRect = new ObjRect();
                    newRect.setRect(new Rect(this.getWidth() - (x + w), y, this.getWidth() - x, y + h));
                    newRect.setGuess1(guess1);
                    newRect.setGuess2(guess2);
                    newRect.setGuess3(guess3);
                    rects.add(newRect);
                    writing.unlock();
                }
            }
            invalidate();
        } catch (Exception e) {
            Log.e(IRClient.TAG, "Error setting rect list. " + e.toString());
        }
    }

    public boolean setButtons(int x, int y){
        /*
        highlightedRect = null;
        for(ObjRect rect : rects){
            if(rect.getRect().contains(x, y)){
                highlightedRect = rect.getRect();
                if(rect.getGuess1().length() > 1) {
                    IRClient.TLbutton.setText(rect.getGuess1());
                } else {
                    IRClient.TLbutton.setText("Unknown");
                }
                if(rect.getGuess2().length() > 1) {
                    IRClient.TRbutton.setText(rect.getGuess2());
                } else if(rect.getGuess1().length() > 1){
                    IRClient.TRbutton.setText("Other");
                } else {
                    IRClient.TRbutton.setText("");
                }
                if(rect.getGuess3().length() > 1) {
                    IRClient.BLbutton.setText(rect.getGuess3());
                    IRClient.BRbutton.setText("Other");
                } else if(rect.getGuess2().length() > 1){
                    IRClient.BLbutton.setText("Other");
                    IRClient.BRbutton.setText("");
                } else {
                    IRClient.BLbutton.setText("");
                    IRClient.BRbutton.setText("");
                }
                */

                if(IRClient.TLbutton.getText().length() > 1) {
                    IRClient.TLbutton.setVisibility(View.VISIBLE);
                }
                if(IRClient.TRbutton.getText().length() > 1) {
                    IRClient.TRbutton.setVisibility(View.VISIBLE);
                }
                if(IRClient.BRbutton.getText().length() > 1) {
                    IRClient.BRbutton.setVisibility(View.VISIBLE);
                }
                if(IRClient.BLbutton.getText().length() > 1) {
                    IRClient.BLbutton.setVisibility(View.VISIBLE);
                }

                return true;
                /*
            }
        }
        return false;
        */
    }

    private int isConnected(float[] bitMask, int index, int rowLen, MaskPoint pt){
        int y = index / rowLen;
        int x = index % rowLen;

        // check if points are in same row
        if(pt.y == y){
            return -1;
        }

        // check if index is disconnected from anchor
        if(pt.x - x <= 0) {
            // check if '1' sequence extending from anchor reaches current index
            // excluding end of sequence (another anchor)
            for (int i = 1; pt.x + i < x; i += 1) {
                if (bitMask[index + rowLen + i] <= 0 || bitMask[index + rowLen + i + 1] == 0) {
                    return -1;
                }
            }
        } else {
            // check if '1' sequence extending from current index reaches anchor
            // excluding end of sequence (next index)
            boolean connected = true;
            for (int i = 1; x + i < pt.x - 1; i += 1) {
                if (bitMask[index + i] <= 0 || bitMask[index + i + 1] == 0) {
                    connected = false;
                }
            }
            // check if '1' sequence extending left from anchor reaches index
            if(!connected){
                for (int i = 0; i + x < pt.x; i++) {
                    if (bitMask[index + rowLen + i] <= 0) {
                        return -1;
                    }
                }
            }
        }

        // find and return end of '1' sequence
        for(int i = 1; i + x < rowLen; i+=1) {
            if(bitMask[index + i] != 1){
                return index + i -1;
            }

        }
        return index - x + rowLen - 1;
    }

    public void setContoursFromBitMask(String recvMsg){
        Log.d(IRClient.TAG, "setting bitmask");

        writing.lock();
        pts.clear();
        writing.unlock();

        BitMask mask = new BitMask(recvMsg);
        maskSize = mask.getLength();

        try {

            // find first point
            MaskPoint startPoint = null;
            for (int lineNum = 0; lineNum < mask.getLength(); lineNum++) {
                int line[] = mask.getLine(lineNum);
                int xCoord = 0;
                for (int item = 0; item < line.length; item += 2) {
                    if (line[item] == 1) {
                        startPoint = new MaskPoint(xCoord, lineNum, line[item + 1], item);
                        item = line.length - 2;
                        lineNum = mask.getLength();
                    }

                    //Log.d(IRClient.TAG, "1");
                    xCoord += line[item + 1];
                }
            }

            if (startPoint == null) {
                Log.d(IRClient.TAG, "No points found");
                invalidate();
                return;
            }

            //Log.d(IRClient.TAG, "startpoint: " + Integer.toString(startPoint.x) + " " + Integer.toString(startPoint.y));

            // wrap around the bitmask finding the next point
            MaskPoint currPoint = startPoint;
            MaskPoint nextPoint = null;
            int lineNum = (int) startPoint.x + 1;
            int direction = 1;
            int prevDir = direction;
            do {
                //Log.d(IRClient.TAG, "loop top");
                int line[] = mask.getLine(lineNum);

                int xCoord = 0;
                int missCount = 0;
                boolean found = false;
                for (int item = 0; item < line.length; item += 2) {
                    if (line[item] == 1) {
                        int xCoord2 = xCoord + line[item + 1] - 1;
                        if (    ((xCoord == currPoint.x) ||
                                 (xCoord2 == currPoint.x && lineNum == currPoint.y)) &&
                                !currPoint.isConnected(xCoord, lineNum)) {
                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(currPoint.x) + "," + Integer.toString(currPoint.y) + " to " + Integer.toString(xCoord) + ", " + Integer.toString(lineNum));
                            MaskPoint newPt = new MaskPoint(xCoord, lineNum, line[item + 1], item);
                            currPoint.connections.add(newPt);
                            newPt.connections.add(currPoint);
                            pts.add(currPoint);
                            currPoint = newPt;
                            found = true;
                            missCount = 0;

                            item = line.length - 2;
                        } else if ( ((xCoord2 == currPoint.x) ||
                                    (lineNum == currPoint.y && xCoord == currPoint.x)) &&
                                    !currPoint.isConnected(xCoord2, lineNum)) {
                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(currPoint.x) + "," + Integer.toString(currPoint.y) + " to " + Integer.toString(xCoord2) + ", " + Integer.toString(lineNum));
                            MaskPoint newPt = new MaskPoint(xCoord2, lineNum, 1, item);
                            currPoint.connections.add(newPt);
                            newPt.connections.add(currPoint);
                            pts.add(currPoint);
                            currPoint = newPt;
                            found = true;
                            missCount = 0;

                            item = line.length - 2;
                        } else if ((currPoint.x > xCoord && currPoint.x < xCoord2) &&
                                    !currPoint.isConnected(currPoint.x, lineNum)){
                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(currPoint.x) + "," + Integer.toString(currPoint.y) + " to " + Integer.toString(currPoint.x) + ", " + Integer.toString(lineNum));
                            MaskPoint newPtA = new MaskPoint(currPoint.x, lineNum, xCoord2 - currPoint.x + 1, item);
                            currPoint.connections.add(newPtA);
                            newPtA.connections.add(currPoint);

                            MaskPoint newPtB = null;
                            if(direction == 1){
                                if(currPoint.index > 1) {
                                    int newXCoord = currPoint.x - mask.getLine(currPoint.y)[currPoint.index - 1] - 1;
                                    if(newXCoord > xCoord){
                                        newPtB = new MaskPoint(newXCoord, lineNum, newXCoord - xCoord + 1, item);
                                        direction *= -1;
                                    }
                                }
                                if(newPtB == null){
                                    newPtB = new MaskPoint(xCoord, lineNum, line[item + 1], item);
                                }
                            } else {
                                if(currPoint.index < mask.getLine(currPoint.y).length - 2){
                                    int newXCoord = currPoint.x + currPoint.runLength + mask.getLine(currPoint.y)[currPoint.index + 3];
                                    if(newXCoord < xCoord2) {
                                        newPtB = new MaskPoint(newXCoord, lineNum, xCoord2 - newXCoord + 1, item);
                                        direction *= -1;
                                    }
                                }
                                if (newPtB == null){
                                    newPtB = new MaskPoint(xCoord2, lineNum, 1, item);
                                }
                            }
                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(newPtA.x) + "," + Integer.toString(newPtA.y) + " to " + Integer.toString(newPtB.x) + ", " + Integer.toString(newPtB.y));
                            newPtA.connections.add(newPtB);
                            newPtB.connections.add(newPtA);
                            pts.add(currPoint);
                            pts.add(newPtA);

                            currPoint = newPtB;
                            found = true;
                            missCount = 0;

                            item = line.length - 2;
                        } else if((currPoint.x < xCoord && currPoint.x + currPoint.runLength - 1 > xCoord2) &&
                                !currPoint.isConnected(currPoint.x, lineNum)){
                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(currPoint.x) + "," + Integer.toString(currPoint.y) + " to " + Integer.toString(xCoord) + ", " + Integer.toString(currPoint.y));
                            MaskPoint newPtA = new MaskPoint(xCoord, currPoint.y, currPoint.runLength - (xCoord - currPoint.x), currPoint.index);
                            newPtA.connections.add(currPoint);
                            currPoint.connections.add(newPtA);

                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(newPtA.x) + "," + Integer.toString(newPtA.y) + " to " + Integer.toString(xCoord) + ", " + Integer.toString(lineNum));
                            MaskPoint newPtB = new MaskPoint(xCoord, lineNum, line[item + 1], item);
                            newPtB.connections.add(newPtA);
                            newPtA.connections.add(newPtB);

                            pts.add(currPoint);
                            pts.add(newPtA);

                            currPoint = newPtB;
                            found = true;
                            missCount = 0;

                            item = line.length - 2;
                        }
                    }

                    //Log.d(IRClient.TAG, "3");
                    xCoord += line[item + 1];
                }

                if (!found) {
                    //Log.d(IRClient.TAG, "not found");
                    missCount++;
                    if (direction == 0) {
                        direction -= prevDir;
                    } else {
                        prevDir = direction;
                        direction = 0;
                    }
                    if (missCount > 3) {
                        invalidate();
                        return;
                    }
                }

                lineNum += direction;
                //Log.d(IRClient.TAG, "startpoint: " + Integer.toString(startPoint.x) + " " + Integer.toString(startPoint.y));
                //Log.d(IRClient.TAG, "currpoint: " + Integer.toString(currPoint.x) + " " + Integer.toString(currPoint.y));
            } while (!(currPoint.x == startPoint.x && currPoint.y == startPoint.y) && pts.size() < 150);
        } catch (Exception e){
            Log.d(IRClient.TAG, "failed to read mask. " + e.toString());
        }

        //Log.d(IRClient.TAG, "pts has " + Integer.toString(pts.size()) + " pts");
        invalidate();
    }

    public void setGuesses(String recvMsg){
        int i = 0;
        int iNext;

        iNext = recvMsg.indexOf(",");
        if(iNext == i || iNext == -1){
            IRClient.TLbutton.setText("Unknown");
            IRClient.TRbutton.setText("");
            IRClient.BLbutton.setText("");
            IRClient.BRbutton.setText("");
            return;
        } else {
            IRClient.TLbutton.setText(recvMsg.substring(i, iNext));

            i = iNext + 1;
            iNext = recvMsg.indexOf(",", i);
        }

        if(iNext == i || iNext == -1){
            IRClient.TRbutton.setText("Other");
            IRClient.BLbutton.setText("");
            IRClient.BRbutton.setText("");
            return;
        } else {
            IRClient.TRbutton.setText(recvMsg.substring(i, iNext));
            i = iNext + 1;
            iNext = recvMsg.indexOf(",", i);
        }

        if(iNext == i || iNext == -1){
            IRClient.BLbutton.setText("Other");
            IRClient.BRbutton.setText("");
            return;
        } else {
            IRClient.BLbutton.setText(recvMsg.substring(i, iNext));
            IRClient.BRbutton.setText("Other");
        }

        return;


    }

    public void clear(){
        writing.lock();
        rects.clear();
        writing.unlock();

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

        //Log.d(IRClient.TAG, "drawing");
        writing.lock();

        /*
        // Draw Using Bounding Boxes
        //Log.d(IRClient.TAG, "Drawing rects 0-" + Integer.toString(rects.size()));
        if(IRClient.getState() == IRClient.State.RESPOND) {
            if(highlightedRect != null) {
                canvas.drawRect(highlightedRect, brush);
            }
        } else {
            for (ObjRect rect : rects) {
                canvas.drawRect(rect.getRect(), brush);
            }
        }
        */

        /*
        // Draw Using Mask Points
        if(pts.size() > 0 && maskSize > 0) {
            float xRatio = canvas.getWidth() / maskSize;
            float yRatio = canvas.getHeight() / maskSize;
            for (MaskPoint point : pts) {
                //Log.d(IRClient.TAG, "ptA x: " + Float.toString(point.x * xRatio) + " y " + Float.toString(point.y * yRatio));
                for(MaskPoint conn: point.connections) {
                    canvas.drawLine(point.x * xRatio, point.y * yRatio, conn.x * xRatio, conn.y * yRatio, brush);
                }
                //canvas.drawLine(point.x * xRatio, point.y * yRatio, point.rPt.x * xRatio, point.rPt.y * yRatio, brush);
            }
        }
        */

        writing.unlock();
    }
}
