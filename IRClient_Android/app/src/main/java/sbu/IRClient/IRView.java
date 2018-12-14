package sbu.IRClient;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.constraint.ConstraintLayout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class IRView extends View {
    ReentrantLock writing = new ReentrantLock();
    private ArrayList<Bitmap> bitmapMasks = new ArrayList<>();
    public RelativeLayout labelView = null;
    private ArrayList<TextView> subscripts = new ArrayList<>();
    private ArrayList<ObjRect> rects = new ArrayList<ObjRect>();
    private Paint brush;
    private int maskColors[] = new int[3];
    private int lineWidth = 2;
    private int highlightedObj = -1;
    private Rect boundRect = null;
    Matrix rotMatrix;

    public IRView(Context context, AttributeSet attr) {
        super(context, attr);

        brush = new Paint();
        brush.setARGB(60, 60,200,60);
        brush.setStrokeWidth(lineWidth);
        brush.setStyle(Paint.Style.FILL);

        maskColors[0] = Color.CYAN;
        maskColors[1] = Color.BLUE;
        maskColors[2] = Color.GREEN;

        rotMatrix = new Matrix();
        rotMatrix.postRotate(90);
    }

    public void setRectList(ArrayList<String> rectLists){
        int NUM_SHOWABLE = 100;
        Log.d(IRClient.TAG, "setting rect list");
        try {
            writing.lock();
            rects.clear();
            labelView.removeAllViews();
            subscripts.clear();
            writing.unlock();

            int xList[] = new int[NUM_SHOWABLE];
            int yList[] = new int[NUM_SHOWABLE];

            int x1, y1, x2, y2, iNext;
            for (String rectList : rectLists) {
                int i = 0;
                String guess1 = "";
                String guess2 = "";
                String guess3 = "";

                iNext = nextComma(rectList, i);
                x1 = Integer.parseInt(rectList.substring(i, iNext));
                //Log.d(IRClient.TAG, Integer.toString(x1) + " ");
                i = iNext + 1;

                iNext = nextComma(rectList, i);
                y1 = Integer.parseInt(rectList.substring(i, iNext));
                //Log.d(IRClient.TAG, Integer.toString(y1) + " ");
                i = iNext + 1;

                iNext = nextComma(rectList, i);
                x2 = Integer.parseInt(rectList.substring(i, iNext));
                //Log.d(IRClient.TAG, Integer.toString(x2) + " ");
                i = iNext + 1;

                iNext = nextComma(rectList, i);
                y2 = Integer.parseInt(rectList.substring(i, iNext));
                //Log.d(IRClient.TAG, Integer.toString(y2) + " ");
                i = iNext + 1;

                iNext = nextComma(rectList, i);
                guess1 = rectList.substring(i, iNext);
                //Log.d(IRClient.TAG, guess1 + " ");
                i = iNext + 1;

                if(iNext < rectList.length() -1) {
                    iNext = nextComma(rectList, i);
                    guess2 = rectList.substring(i, iNext);
                    //Log.d(IRClient.TAG, guess2 + " ");
                    i = iNext + 1;
                }

                if(iNext < rectList.length() - 1) {
                    iNext = nextComma(rectList, i);
                    guess3 = rectList.substring(i, iNext);
                    //Log.d(IRClient.TAG, guess3 + " ");
                    i = iNext + 1;
                }

                if (rects.size() < NUM_SHOWABLE) {
                    writing.lock();
                    ObjRect newRect = new ObjRect();
                    newRect.setRect(new Rect(this.getWidth() - y2*3, x1*3, this.getWidth() - y1*3, x2*3));
                    newRect.setGuess1(guess1);
                    newRect.setGuess2(guess2);
                    newRect.setGuess3(guess3);
                    rects.add(newRect);
                    writing.unlock();

                    xList[rects.size()] = newRect.getRect().left;
                    yList[rects.size()] = newRect.getRect().bottom;

                    boolean showText = true;
                    int textWidth = 4 * guess1.length();
                    //// bounds check the rectangle to see if there is room inside to draw text
                    //if(newRect.getRect().width() < textWidth || newRect.getRect().left > this.getWidth() - textWidth || newRect.getRect().bottom > this.getHeight() - 10){
                    //    showText = false;
                    //}
                    // collision check rectangle to see if text will collide with another rectangle
                    for (int rectNum = 0; rectNum < NUM_SHOWABLE && rectNum < rects.size() - 1; rectNum++) {
                        if ((xList[rectNum] < newRect.getRect().left && xList[rectNum] + textWidth > newRect.getRect().left) ||
                                (xList[rectNum] > newRect.getRect().left && xList[rectNum] < newRect.getRect().left + textWidth) ||
                                (yList[rectNum] < newRect.getRect().bottom && yList[rectNum] + 10 > newRect.getRect().bottom) ||
                                (yList[rectNum] > newRect.getRect().bottom && yList[rectNum] < newRect.getRect().bottom + 10)){
                            showText = false;
                            break;
                        }
                    }
                    if(showText) {
                        writing.lock();
                        TextView text = new TextView(IRClient.context);
                        text.setText(guess1);
                        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(100, 20);
                        text.setTranslationX(newRect.getRect().left + (newRect.getRect().width()/2) - brush.measureText(guess1));
                        text.setTranslationY(newRect.getRect().bottom - 40);
                        text.setTextColor(maskColors[rects.size() - 1] - 0x00303030);
                        labelView.addView(text);
                        Log.d(IRClient.TAG,"textview added with: " + guess1);
                        writing.unlock();
                    }
                }
            }
            invalidate();
        } catch (Exception e) {
            Log.e(IRClient.TAG, "Error setting rect list. " + e.toString());
        }
    }

    public void refresh(){
        invalidate();
    }

    public boolean setButtons(int x, int y){
        writing.lock();
        highlightedObj = -1;
        writing.unlock();
        for(int i = 0; i < rects.size(); i++){
            ObjRect rect = rects.get(i);
            if(rect.getRect().contains(x, y)){
                highlightedObj = i;
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
            }
        }
        return false;
    }

    public void addBitmapMasks(ArrayList<String> rawMasks){
        writing.lock();
        bitmapMasks.clear();
        writing.unlock();

        int color = 0;
        for (String rawMask : rawMasks) {
            int[] colors = new int[rawMask.length()];
            for (int i = 0; i < rawMask.length(); i++) {
                if (rawMask.charAt(i) == '1') {
                    colors[i] = maskColors[color % maskColors.length];
                } else if (rawMask.charAt(i) == '0') {
                    colors[i] = 0;
                } else {
                    break;
                }
                int maskScale = (int)Math.sqrt((double)rawMask.length()/(320*240));
                if (i == rawMask.length() - 1) {
                    Bitmap sidewaysMap = Bitmap.createBitmap(colors, maskScale*320, maskScale*240, Bitmap.Config.ARGB_8888);
                    bitmapMasks.add(Bitmap.createBitmap(sidewaysMap, 0, 0, sidewaysMap.getWidth(), sidewaysMap.getHeight(), rotMatrix, true));
                    color++;
                }
            }
        }
    }

    public void setGuesses(String recvMsg){
        int i = 0;
        int iNext;

        iNext = recvMsg.indexOf(",");
        if(iNext == i || iNext == -1){
            IRClient.TRbutton.setText("");
            IRClient.TLbutton.setText("Unknown");
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
        setGuesses("");
        writing.unlock();

        invalidate();
    }

    private int nextComma(String buf, int index){
        while(buf.charAt(index) != ','){
            index++;
        }
        return index;
    }

    public void showGuesses(){
        if(labelView != null) {
            labelView.setVisibility(VISIBLE);
        }
    }

    public void hideGuesses(){
        if(labelView != null){
            labelView.setVisibility(INVISIBLE);
        }
    }

    @Override
    protected void onDraw(Canvas canvas){

        Log.d(IRClient.TAG, "drawing");
        writing.lock();

        if(boundRect == null){
            boundRect = new Rect(0,0,this.getWidth(),this.getHeight());
        }

        if(IRClient.getState() == IRClient.State.CLASSIFY || IRClient.getState() == IRClient.State.RESPOND) {

            // Draw Using Bounding Boxes
            //Log.d(IRClient.TAG, "Drawing rects 0-" + Integer.toString(rects.size()));
            if(IRClient.getState() == IRClient.State.RESPOND) {
                Log.d(IRClient.TAG, "DRAW OBJ " + Integer.toString(highlightedObj) + " of " + Integer.toString(rects.size()) +  " of " + Integer.toString(bitmapMasks.size()));
                if(highlightedObj != -1 && highlightedObj < rects.size() && highlightedObj < bitmapMasks.size()) {
                    brush.setColor(maskColors[highlightedObj] + 0x28000000);
                    // draw bounding box
                    Log.d(IRClient.TAG,"drawing bounding box");
                    brush.setStyle(Paint.Style.STROKE);
                    canvas.drawRect(rects.get(highlightedObj).getRect(), brush);

                    // draw bitmask
                    brush.setStyle(Paint.Style.FILL);
                    canvas.drawBitmap(bitmapMasks.get(highlightedObj), null, boundRect, brush);
                }
            } else {
                // Draw bitmasks
                for(Bitmap map : bitmapMasks){
                    canvas.drawBitmap(map, null, boundRect, brush);
                }
            }
        }

        writing.unlock();
    }
}
