package sbu.IRClient;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.widget.Button;

public class ClassInputBar extends AppCompatEditText {
    private Button button;

    public ClassInputBar(Context context) {
        super(context);
    }

    public ClassInputBar(Context context, AttributeSet attr){
        super(context, attr);
    }

    public void setButton(Button button){
        this.button = button;
    }

    public Button getButton(){
        return button;
    }
}
