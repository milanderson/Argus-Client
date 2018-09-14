package sbu.IRClient;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class IRView extends View {
    ReentrantLock writing = new ReentrantLock();
    private ArrayList<ObjRect> rects = new ArrayList<ObjRect>();
    ArrayList<MaskPoint> pts = new ArrayList<MaskPoint>();
    private Paint brush;
    private int lineWidth = 2;
    private int lineColor = Color.GREEN;
    private int maskSize;
    private Rect highlightedRect;

    public IRView(Context context, AttributeSet attr) {
        super(context, attr);

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

    public boolean isIndexInLine(int[] line, int length) throws Exception {
        int curLen = 0;
        for (int item = 0; item < line.length; item +=2){
            if (curLen <= length && curLen + line[item + 1] >= length){
                if(line[item] == 0){
                    return false;
                }
                return true;
            }
            curLen += line[item +1];
        }

        throw new Exception("Line end reached. Line and length arguments do not match");
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
                        boolean isLinePoint = false;
                        if(line[item + 1] == 1 || lineNum == mask.getLength() - 1 ||
                                !isIndexInLine(mask.getLine(lineNum + 1), xCoord)){
                            isLinePoint = true;
                        }
                        startPoint = new MaskPoint(xCoord, lineNum, line[item + 1], item, isLinePoint);
                        startPoint.Visit();
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

            // 'wrap' around the bitmask finding the next point by moving around the polygonal hull clockwise
            MaskPoint currPoint = startPoint;
            int lineNum = (int) startPoint.x + 1;
            int direction = 1;
            int prevDir = direction;
            do {
                //Log.d(IRClient.TAG, "loop top");
                int line[] = mask.getLine(lineNum);

                int xCoord = 0;
                int missCount = 0;
                boolean found = false;
                MaskPoint connPtA, connPtB, connPtC, connPtD;
                for (int item = 0; item < line.length; item += 2) {
                    // if there is a run of 1s in the mask add the next point to our point list
                    if (line[item] == 1) {
                        int xCoord2 = xCoord + line[item + 1] - 1;
                        // search for pre-existing connection points
                        connPtA = null;
                        connPtB = null;
                        connPtC = null;
                        connPtD = null;
                        for(MaskPoint pt : pts) {
                            if(pt.x == xCoord && pt.y == lineNum) {
                                connPtA = pt;
                            }
                            if(pt.x == xCoord2 && pt.y == lineNum) {
                                connPtB = pt;
                            }
                            if(pt.x == currPoint.x && pt.y == lineNum) {
                                connPtC = pt;
                            }
                            if(pt.x == xCoord && pt.y == currPoint.y){
                                connPtD = pt;
                            }
                        }

                        // if the run of 1s starts in line with previous point, or is a left extension of previous point
                        if (    ((xCoord == currPoint.x) ||
                                 (xCoord2 == currPoint.x && lineNum == currPoint.y)) &&
                                (connPtA == null || (connPtA.isLinePoint && connPtA.VisitCount() < 2))) {
                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(currPoint.x) + "," + Integer.toString(currPoint.y) + " to " + Integer.toString(xCoord) + ", " + Integer.toString(lineNum));

                            MaskPoint newPt = connPtA;
                            if (connPtA == null) {
                                // check line thickness
                                boolean isLinePoint = false;
                                if (line[item + 1] == 1 ||
                                        (lineNum == mask.getLength() - 1 && !isIndexInLine(mask.getLine(lineNum - 1), xCoord)) ||
                                        (lineNum == 0 && !isIndexInLine(mask.getLine(lineNum + 1), xCoord)) ||
                                        (!isIndexInLine(mask.getLine(lineNum + 1), xCoord) && !isIndexInLine(mask.getLine(lineNum - 1), xCoord))) {
                                    isLinePoint = true;
                                }

                                newPt = new MaskPoint(xCoord, lineNum, line[item + 1], item, isLinePoint);
                                pts.add(currPoint);
                            }

                            currPoint.AddConnection(newPt);
                            newPt.AddConnection(currPoint);
                            newPt.Visit();
                            currPoint = newPt;
                            found = true;
                            missCount = 0;

                            item = line.length - 2;
                        // if the run of 1s ends in line with previous point, or is a right extension of previous point
                        } else if ( ((xCoord2 == currPoint.x) ||
                                    (lineNum == currPoint.y && xCoord == currPoint.x)) &&
                                (connPtB == null || (connPtB.isLinePoint && connPtB.VisitCount() < 2))) {
                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(currPoint.x) + "," + Integer.toString(currPoint.y) + " to " + Integer.toString(xCoord2) + ", " + Integer.toString(lineNum));

                            MaskPoint newPt = connPtB;
                            if(connPtB == null) {
                                // check line thickness
                                boolean isLinePoint = false;
                                if (line[item + 1] == 1 ||
                                        (lineNum == mask.getLength() - 1 && !isIndexInLine(mask.getLine(lineNum - 1), xCoord2)) ||
                                        (lineNum == 0 && !isIndexInLine(mask.getLine(lineNum + 1), xCoord2)) ||
                                        (!isIndexInLine(mask.getLine(lineNum + 1), xCoord2) && !isIndexInLine(mask.getLine(lineNum - 1), xCoord2))) {
                                    isLinePoint = true;
                                }
                                newPt = new MaskPoint(xCoord2, lineNum, 1, item, isLinePoint);
                                pts.add(currPoint);
                            }

                            currPoint.AddConnection(newPt);
                            newPt.AddConnection(currPoint);
                            newPt.Visit();
                            currPoint = newPt;
                            found = true;
                            missCount = 0;

                            item = line.length - 2;
                        // if the run of 1s crosses the previous point
                        } else if ((currPoint.x > xCoord && currPoint.x < xCoord2) &&
                                (connPtC == null || (connPtC.isLinePoint && connPtC.VisitCount() < 2))){
                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(currPoint.x) + "," + Integer.toString(currPoint.y) + " to " + Integer.toString(currPoint.x) + ", " + Integer.toString(lineNum));

                            MaskPoint newPtA = currPoint.isConnected(currPoint.x, lineNum);
                            if(newPtA == null || !newPtA.isLinePoint || newPtA.VisitCount() >= 2) {
                                // drop an 'anchor point' in the middle of the run in line with previous point
                                // check line thickness
                                boolean isLinePoint = false;
                                if (line[item + 1] == 1 ||
                                        (lineNum == mask.getLength() - 1 && (!isIndexInLine(mask.getLine(lineNum - 1), currPoint.x + 1) || !isIndexInLine(mask.getLine(lineNum - 1), currPoint.x - 1))) ||
                                        (lineNum == 0 && (!isIndexInLine(mask.getLine(lineNum + 1), currPoint.x + 1) || !isIndexInLine(mask.getLine(lineNum - 1), currPoint.x - 1))) ||
                                        (!isIndexInLine(mask.getLine(lineNum + 1), currPoint.x + 1) && !isIndexInLine(mask.getLine(lineNum - 1), currPoint.x + 1)) ||
                                        (!isIndexInLine(mask.getLine(lineNum + 1), currPoint.x - 1) && !isIndexInLine(mask.getLine(lineNum - 1), currPoint.x - 1))) {
                                    isLinePoint = true;
                                }

                                newPtA = new MaskPoint(currPoint.x, lineNum, xCoord2 - currPoint.x + 1, item, isLinePoint);

                                currPoint.AddConnection(newPtA);
                                newPtA.AddConnection(currPoint);
                                pts.add(newPtA);
                            }
                            newPtA.Visit();

                            // extend 'anchor point' to a point at the end of the run (moving clockwise based on current direction)
                            MaskPoint newPtB = null;
                            if(direction == 1){
                                if(currPoint.index > 1) {
                                    // handle crenelations (checkerboarding in the bitmask)
                                    int newXCoord = currPoint.x - mask.getLine(currPoint.y)[currPoint.index - 1] - 1;

                                    if(newXCoord > xCoord) {
                                        for(MaskPoint pt: pts){
                                            if(pt.x == newXCoord && pt.y == lineNum){
                                                newPtB = pt;
                                            }
                                        }

                                        if(newPtB == null) {
                                            // check line thickness
                                            boolean isLinePoint = false;
                                            if (line[item + 1] == 1 ||
                                                    (lineNum == mask.getLength() - 1 && (!isIndexInLine(mask.getLine(lineNum - 1), newXCoord + 1) || !isIndexInLine(mask.getLine(lineNum - 1), newXCoord - 1))) ||
                                                    (lineNum == 0 && (!isIndexInLine(mask.getLine(lineNum + 1), newXCoord + 1) || !isIndexInLine(mask.getLine(lineNum + 1), newXCoord - 1))) ||
                                                    (!isIndexInLine(mask.getLine(lineNum + 1), newXCoord + 1) && !isIndexInLine(mask.getLine(lineNum - 1), newXCoord + 1)) ||
                                                    (!isIndexInLine(mask.getLine(lineNum + 1), newXCoord - 1) && !isIndexInLine(mask.getLine(lineNum - 1), newXCoord - 1))) {
                                                isLinePoint = true;
                                            }

                                            newPtB = new MaskPoint(newXCoord, lineNum, xCoord2 - newXCoord + 1, item, isLinePoint);
                                            pts.add(newPtB);
                                        }

                                        direction *= -1;
                                        newPtB.Visit();
                                    } else {
                                        newPtB = connPtA;
                                        if(newPtB == null) {
                                            // check line thickness
                                            boolean isLinePoint = false;
                                            if (line[item + 1] == 1 ||
                                                    (lineNum == mask.getLength() - 1 && !isIndexInLine(mask.getLine(lineNum - 1), xCoord)) ||
                                                    (lineNum == 0 && !isIndexInLine(mask.getLine(lineNum + 1), xCoord)) ||
                                                    (!isIndexInLine(mask.getLine(lineNum + 1), xCoord) && !isIndexInLine(mask.getLine(lineNum - 1), xCoord))) {
                                                isLinePoint = true;
                                            }

                                            newPtB = new MaskPoint(xCoord, lineNum, xCoord2 - xCoord + 1, item, isLinePoint);
                                            pts.add(newPtB);
                                        }

                                        newPtB.Visit();
                                    }
                                }
                            } else {
                                // handle crenelations (checkerboarding in the bitmask)
                                if(currPoint.index < mask.getLine(currPoint.y).length - 2){
                                    int newXCoord = currPoint.x + currPoint.runLength + mask.getLine(currPoint.y)[currPoint.index + 3];

                                    if(newXCoord < xCoord2) {

                                        for(MaskPoint pt: pts){
                                            if(pt.x == newXCoord && pt.y == lineNum){
                                                newPtB = pt;
                                            }
                                        }

                                        if(newPtB == null) {
                                            // check line thickness
                                            boolean isLinePoint = false;
                                            if (line[item + 1] == 1 ||
                                                    (lineNum == mask.getLength() - 1 && (!isIndexInLine(mask.getLine(lineNum - 1), newXCoord + 1) || !isIndexInLine(mask.getLine(lineNum - 1), newXCoord - 1))) ||
                                                    (lineNum == 0 && (!isIndexInLine(mask.getLine(lineNum + 1), newXCoord + 1)) || !isIndexInLine(mask.getLine(lineNum + 1), newXCoord - 1)) ||
                                                    (!isIndexInLine(mask.getLine(lineNum + 1), newXCoord + 1) && !isIndexInLine(mask.getLine(lineNum - 1), newXCoord + 1)) ||
                                                    (!isIndexInLine(mask.getLine(lineNum + 1), newXCoord - 1) && !isIndexInLine(mask.getLine(lineNum - 1), newXCoord - 1))) {
                                                isLinePoint = true;
                                            }

                                            newPtB = new MaskPoint(newXCoord, lineNum, xCoord2 - newXCoord + 1, item, isLinePoint);
                                        }
                                        direction *= -1;
                                        newPtB.Visit();
                                    } else {
                                        newPtB = connPtB;
                                        if(newPtB == null) {
                                            // check line thickness
                                            boolean isLinePoint = false;
                                            if (line[item + 1] == 1 ||
                                                    (lineNum == mask.getLength() - 1 && !isIndexInLine(mask.getLine(lineNum - 1), xCoord2)) ||
                                                    (lineNum == 0 && !isIndexInLine(mask.getLine(lineNum + 1), xCoord2)) ||
                                                    (!isIndexInLine(mask.getLine(lineNum + 1), xCoord2) && !isIndexInLine(mask.getLine(lineNum - 1), xCoord2))) {
                                                isLinePoint = true;
                                            }

                                            newPtB = new MaskPoint(xCoord2, lineNum, 1, item, isLinePoint);
                                        }

                                        newPtB.Visit();
                                    }
                                }
                            }
                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(newPtA.x) + "," + Integer.toString(newPtA.y) + " to " + Integer.toString(newPtB.x) + ", " + Integer.toString(newPtB.y));
                            newPtA.AddConnection(newPtB);
                            newPtB.AddConnection(newPtA);

                            currPoint = newPtB;
                            found = true;
                            missCount = 0;

                            item = line.length - 2;
                        // the run of 1s crosses the end of the run staring from the previous point
                        } else if((currPoint.x < xCoord && currPoint.x + currPoint.runLength - 1 > xCoord2) &&
                                (connPtA == null || (connPtA.isLinePoint && connPtA.VisitCount() < 2))){
                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(currPoint.x) + "," + Integer.toString(currPoint.y) + " to " + Integer.toString(xCoord) + ", " + Integer.toString(currPoint.y));

                            MaskPoint newPtA = connPtD;
                            if(newPtA == null) {
                                boolean isLinePoint = false;
                                if (line[item + 1] == 1 ||
                                        (currPoint.y == mask.getLength() - 1 && !isIndexInLine(mask.getLine(currPoint.y - 1), xCoord)) ||
                                        (currPoint.y == 0 && !isIndexInLine(mask.getLine(currPoint.y + 1), xCoord)) ||
                                        (!isIndexInLine(mask.getLine(currPoint.y + 1), xCoord) && !isIndexInLine(mask.getLine(currPoint.y - 1), xCoord))) {
                                    isLinePoint = true;
                                }

                                newPtA = new MaskPoint(xCoord, currPoint.y, currPoint.runLength - (xCoord - currPoint.x), currPoint.index, isLinePoint);
                                pts.add(newPtA);
                            }
                            newPtA.Visit();
                            newPtA.AddConnection(currPoint);
                            currPoint.AddConnection(newPtA);

                            //Log.d(IRClient.TAG, "Connecting " + Integer.toString(newPtA.x) + "," + Integer.toString(newPtA.y) + " to " + Integer.toString(xCoord) + ", " + Integer.toString(lineNum));

                            MaskPoint newPtB = connPtA;
                            if (connPtA == null) {
                                boolean isLinePoint = false;
                                if (line[item + 1] == 1 ||
                                        (lineNum == mask.getLength() - 1 && !isIndexInLine(mask.getLine(lineNum - 1), xCoord)) ||
                                        (lineNum == 0 && !isIndexInLine(mask.getLine(lineNum + 1), xCoord)) ||
                                        (!isIndexInLine(mask.getLine(lineNum + 1), xCoord) && !isIndexInLine(mask.getLine(lineNum - 1), xCoord))) {
                                    isLinePoint = true;
                                }

                                newPtB = new MaskPoint(xCoord, lineNum, line[item + 1], item, isLinePoint);
                                pts.add(newPtB);
                            }
                            newPtB.Visit();
                            newPtB.AddConnection(newPtA);
                            newPtA.AddConnection(newPtB);


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

            IRClient.TLbutton.setVisibility(VISIBLE);
            IRClient.TRbutton.setVisibility(INVISIBLE);
            IRClient.BLbutton.setVisibility(INVISIBLE);
            IRClient.BRbutton.setVisibility(INVISIBLE);
            return;
        } else {
            IRClient.TLbutton.setText(recvMsg.substring(i, iNext));
            IRClient.TLbutton.setVisibility(VISIBLE);

            i = iNext + 1;
            iNext = recvMsg.indexOf(",", i);
        }

        if(iNext == i || iNext == -1){
            IRClient.TRbutton.setText("Other");
            IRClient.BLbutton.setText("");
            IRClient.BRbutton.setText("");

            IRClient.TRbutton.setVisibility(VISIBLE);
            IRClient.BLbutton.setVisibility(INVISIBLE);
            IRClient.BRbutton.setVisibility(INVISIBLE);
            return;
        } else {
            IRClient.TRbutton.setText(recvMsg.substring(i, iNext));
            IRClient.TRbutton.setVisibility(VISIBLE);

            i = iNext + 1;
            iNext = recvMsg.indexOf(",", i);
        }

        if(iNext == i || iNext == -1){
            IRClient.BLbutton.setText("Other");
            IRClient.BRbutton.setText("");

            IRClient.BLbutton.setVisibility(VISIBLE);
            IRClient.BRbutton.setVisibility(INVISIBLE);
            return;
        } else {
            IRClient.BLbutton.setText(recvMsg.substring(i, iNext));
            IRClient.BRbutton.setText("Other");


            IRClient.BLbutton.setVisibility(VISIBLE);
            IRClient.BRbutton.setVisibility(VISIBLE);
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

        if(IRClient.getState() == IRClient.State.CLASSIFY || IRClient.getState() == IRClient.State.RESPOND) {
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
                for(MaskPoint conn: point.GetConnections()) {
                    canvas.drawLine(point.x * xRatio, point.y * yRatio, conn.x * xRatio, conn.y * yRatio, brush);
                }
                //canvas.drawLine(point.x * xRatio, point.y * yRatio, point.rPt.x * xRatio, point.rPt.y * yRatio, brush);
            }
        }
        */
        }

        writing.unlock();
    }
}
