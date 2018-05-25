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
    private Paint brush;
    private int lineWidth = 2;
    private int lineColor = Color.GREEN;

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
        for(Rect rect : rects){
            canvas.drawRect(rect, brush);
        }
        writing.unlock();
    }
}
