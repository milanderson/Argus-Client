package sbu.IRClient;

import android.graphics.Rect;

public class ObjRect {
    private Rect rect;
    private String guess1, guess2, guess3;

    public ObjRect(){}

    public void setRect(Rect newRect){
        rect = newRect;
    }

    public Rect getRect(){
        return rect;
    }

    public void setGuess1(String guess){
        guess1 = guess;
    }

    public void setGuess2(String guess){
        guess2 = guess;
    }

    public void setGuess3(String guess){
        guess3 = guess;
    }

    public String getGuess1(){
        return guess1;
    }

    public String getGuess2(){
        return guess2;
    }

    public String getGuess3(){
        return guess3;
    }
}
