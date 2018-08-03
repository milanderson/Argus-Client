package sbu.irclient;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static android.os.SystemClock.sleep;

public class IRClient extends Activity {
    public static final String TAG = "IRClient";
    public enum State {FAILURE, RESPOND, CLASSIFY, IDENTIFY}
    private static State RunState = State.CLASSIFY;

    public static File mOutputFile = null;
    public static File mInitFile = null;
    public static MediaRecorder rec;
    public static List<Long> encodeTimes = new ArrayList<>(10);

    public static Pipe pipe;
    private ByteBuffer signalInBuf = ByteBuffer.allocate(10);
    public static byte[] frame = null;
    public static long sendTime = 0;

    public static IRView overlayView;
    private TextureView texView;
    public static Button TLbutton, TRbutton, BRbutton, BLbutton;
    public static ClassInputBar classInputBar;
    private Toast toast;
    public static Context context;
    private Camera cam;

    private int permGranted = 0;

    private TextView.OnEditorActionListener enterClassListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(classInputBar.getWindowToken(), 0);

            try {
                String strMsg = "reply" + classInputBar.getText().toString() + ",";
                ByteBuffer msg = ByteBuffer.allocate(strMsg.getBytes().length);
                msg.put(strMsg.getBytes());
                msg.flip();
                pipe.sink().write(msg);
            } catch (Exception e){
                Log.e(TAG, "Error passing reply." + e.toString());
            }
            classInputBar.setText("");
            onRunStateChanged(null, State.RESPOND);
            return true;
        }
    };

    private Camera.PreviewCallback framePasser = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {
            frame = bytes;
            sendTime = System.currentTimeMillis();
            try {
                if(getState() == State.CLASSIFY) {
                    String testData = "frame";
                    signalInBuf.clear();
                    signalInBuf.put(testData.getBytes());
                    signalInBuf.flip();
                    while (signalInBuf.hasRemaining()) {
                        pipe.sink().write(signalInBuf);
                    }
                }
            } catch (Exception e){
                Log.e(TAG, "Failure Communicating with Network Thread. " + e.toString());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setContentView(R.layout.activity_irclient);
        context = getApplicationContext();
        Log.d(TAG, "Starting");

        if(checkPermissions() == -1){
            return;
        }

        RunState = State.CLASSIFY;
        initViews();
        initButtons();
        initInputBar();

        Log.d(TAG, "init Cam");
        //checkCapabilities();
        initCamView();

        // resting
        Log.d(TAG, "resting");
    }

    private int checkPermissions(){
        String[] perm = {Manifest.permission.CAMERA, Manifest.permission.INTERNET};
        boolean permOK = true;

        for(int i = 0; i < perm.length; i++){
            if(android.support.v4.content.ContextCompat.checkSelfPermission(this, perm[i]) != android.content.pm.PackageManager.PERMISSION_GRANTED){
                permOK = false;
            }
        }

        int attempts = 0;
        while(!permOK){
            permOK = true;
            try {
                ActivityCompat.requestPermissions(this, perm, 0);
            } catch (Exception e){
                //Log.d(TAG, e.toString());
                return -1;
            }

            for(int i = 0; i < perm.length; i++){
                if(android.support.v4.content.ContextCompat.checkSelfPermission(this, perm[i]) != android.content.pm.PackageManager.PERMISSION_GRANTED){
                    permOK = false;
                }
            }
            attempts++;
            if(attempts > 100){
                onRunStateChanged("Permissions Not Granted", State.FAILURE);
            }
        }

        return 1;
    }

    private void checkCapabilities(){
        int numCodecs = MediaCodecList.getCodecCount();

        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            MediaCodecInfo.CodecCapabilities cap = null;
            if (!codecInfo.isEncoder()) {
                continue;
            }

            try {
                cap = codecInfo.getCapabilitiesForType("video/avc");
            } catch (Exception e){
                Log.e(TAG, e.toString());
                continue;
            }

            if (cap != null) {
                int up, bot;
                boolean sup;
                bot = cap.getVideoCapabilities().getBitrateRange().getLower();
                up = cap.getVideoCapabilities().getBitrateRange().getUpper();
                Log.e(TAG, "Bitrates: " + Integer.toString(bot) + ":" + Integer.toString(up));

                cap.getVideoCapabilities().getSupportedFrameRates().getUpper();
                cap.getVideoCapabilities().getSupportedFrameRates().getLower();
                Log.e(TAG, "Framerates: " + Integer.toString(bot) + ":" + Integer.toString(up));

                sup = cap.getVideoCapabilities().isSizeSupported(640, 480);
                Log.e(TAG, "640x480: " + Boolean.toString(sup));
            }
        }
    }

    private void initViews(){
        setContentView(R.layout.activity_irclient);
        texView = findViewById(R.id.textureView);

        overlayView = new IRView(this);
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(getState() == State.CLASSIFY && overlayView.setButtons((int)motionEvent.getX(), (int)motionEvent.getY())) {
                    onRunStateChanged(null, State.RESPOND);
                }
                return false;
            }
        });
        addContentView(overlayView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void initButtons(){
        try {
            TLbutton = findViewById(R.id.buttonTL);
            TRbutton = findViewById(R.id.buttonTR);
            BLbutton = findViewById(R.id.buttonBL);
            BRbutton = findViewById(R.id.buttonBR);

            overlayView.setGuesses("");

            TLbutton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    if(getState() == State.RESPOND && TLbutton.getVisibility() == View.VISIBLE) {
                        try {
                            Log.e(TAG, "button 1");

                            if(TLbutton.getText() == "Unknown"){
                                classInputBar.setVisibility(View.VISIBLE);
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.showSoftInput(classInputBar, InputMethodManager.SHOW_IMPLICIT);
                                classInputBar.setOnEditorActionListener(enterClassListener);
                                classInputBar.requestFocus();

                                return;
                            }

                            String strMsg = "reply" + TLbutton.getText().toString() + ",";
                            ByteBuffer msg = ByteBuffer.allocate(strMsg.getBytes().length);
                            msg.put(strMsg.getBytes());
                            msg.flip();
                            pipe.sink().write(msg);
                        } catch (Exception e) {
                            Log.e(TAG, "Error clicking button." + e.toString());
                        }
                        onRunStateChanged(null, State.RESPOND);
                    }
                }
            });
            TRbutton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {

                    if (getState() == State.RESPOND && TRbutton.getVisibility() == View.VISIBLE) {
                        try {
                            Log.e(TAG, "button 2");

                            if(TRbutton.getText() == "Other"){
                                classInputBar.setVisibility(View.VISIBLE);
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.showSoftInput(classInputBar, InputMethodManager.SHOW_IMPLICIT);
                                classInputBar.setOnEditorActionListener(enterClassListener);
                                classInputBar.requestFocus();

                                return;
                            }

                            String strMsg = "reply" + TRbutton.getText().toString() + ",";
                            ByteBuffer msg = ByteBuffer.allocate(strMsg.getBytes().length);
                            msg.put(strMsg.getBytes());
                            msg.flip();
                            pipe.sink().write(msg);
                        } catch (Exception e) {
                            Log.e(TAG, "Error clicking button." + e.toString());
                        }
                        onRunStateChanged(null, State.RESPOND);
                    }
                }
            });
            BLbutton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {

                    if (getState() == State.RESPOND && BLbutton.getVisibility() == View.VISIBLE) {
                        try {
                            Log.e(TAG, "button 3");

                            if(BLbutton.getText() == "Other"){
                                classInputBar.setVisibility(View.VISIBLE);
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.showSoftInput(classInputBar, InputMethodManager.SHOW_IMPLICIT);
                                classInputBar.setOnEditorActionListener(enterClassListener);
                                classInputBar.requestFocus();

                                return;
                            }

                            String strMsg = "reply" + BLbutton.getText().toString() + ",";
                            ByteBuffer msg = ByteBuffer.allocate(strMsg.getBytes().length);
                            msg.put(strMsg.getBytes());
                            msg.flip();
                            pipe.sink().write(msg);
                        } catch (Exception e) {
                            Log.e(TAG, "Error clicking button." + e.toString());
                        }
                        onRunStateChanged(null, State.RESPOND);
                    }
                }
            });
            BRbutton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {

                    if(getState() == State.RESPOND && BRbutton.getVisibility() == View.VISIBLE) {
                        try {
                            Log.e(TAG, "button 4");

                            if(BRbutton.getText() == "Other"){
                                classInputBar.setVisibility(View.VISIBLE);
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.showSoftInput(classInputBar, InputMethodManager.SHOW_IMPLICIT);
                                classInputBar.setOnEditorActionListener(enterClassListener);
                                classInputBar.requestFocus();

                                return;
                            }

                            String strMsg = "reply" + BRbutton.getText().toString() + ",";
                            ByteBuffer msg = ByteBuffer.allocate(strMsg.getBytes().length);
                            msg.put(strMsg.getBytes());
                            msg.flip();
                            pipe.sink().write(msg);
                        } catch (Exception e) {
                            Log.e(TAG, "Error clicking button." + e.toString());
                        }
                        onRunStateChanged(null, State.RESPOND);
                    }
                }
            });
        } catch (Exception e){
            Log.e(TAG,"Error initializing buttons." + e.toString());
        }
    }

    private void initInputBar(){
        classInputBar = findViewById(R.id.classInputBar);
        classInputBar.setVisibility(View.INVISIBLE);

        classInputBar.setOnEditorActionListener(enterClassListener);
    }

    private void initCamView(){
        final IRClient client = this;
        if(texView.isAvailable()){
            Log.d(TAG, "true");
        }
        texView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                initCam();
                try {
                    cam.setPreviewTexture(surface);
                    cam.startPreview();

                    pipe = Pipe.open();
                    pipe.source().configureBlocking(false);
                    pipe.sink().configureBlocking(false);
                    new IRClient_NetTask().execute(client);

                    toast = Toast.makeText(context, "Connecting...", Toast.LENGTH_SHORT);
                    toast.show();
                } catch (Exception e){
                    Log.e(TAG, "Error initializing recorder: " + e.toString());
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                //Log.d(TAG, "surface dead, releasing camera");
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
    }

    private void initCam() {
        int width = 640;
        int height = 480;

        // Media Recorder Attempt
        cam = Camera.open();
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            cam.setDisplayOrientation(90);
        }
        Camera.Parameters param = cam.getParameters();
        param.setPreviewSize(width, height);
        Log.e(TAG, "Picture format " + param.getPreviewFormat());

        cam.setParameters(param);
        cam.setPreviewCallback(framePasser);
    }

    private void initRecorder(String fname){
        try {
            rec = new MediaRecorder();
            cam.unlock();
            rec.setCamera(cam);

            rec.setVideoSource(MediaRecorder.VideoSource.CAMERA);

            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

            rec.setVideoSize(640, 480);
            rec.setVideoFrameRate(30);

        } catch (Exception e){
            Log.e(TAG, "Error initializing recorder: " + e.toString());
        }

        //Create and thread output file
        try {
            if(fname == "Init") {
                mInitFile = File.createTempFile("IRClient_tmp", null, getApplicationContext().getCacheDir());
                //initSaveFile();

                rec.setOutputFile(mInitFile.getPath());
            } else {
                mOutputFile = File.createTempFile("IRClient_tmp", null, getApplicationContext().getCacheDir());
                //initSaveFile();

                rec.setOutputFile(mOutputFile.getPath());
            }

        } catch (Exception e) { Log.e(TAG, e.toString()); }

        try {
            rec.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            rec.reset();
            rec.release();
            return;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            rec.reset();
            rec.release();
            return;
        }

        try{
            rec.start();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void initSaveFile(){
        File saveDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CameraSample");

        if (! saveDir.exists()) {
            if (!saveDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
                return;
            }
        }

        mOutputFile = new File(saveDir.getPath() + File.separator + "IRClient_tmp.mp4");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Log.d(TAG, "Permissions Check");
        for(int i = 0; i < grantResults.length; i++){
            if(grantResults[i] != android.content.pm.PackageManager.PERMISSION_GRANTED){
                permGranted = 1;
                return;
            }
        }
        permGranted = 2;
    }

    @Override
    public void onConfigurationChanged(Configuration newConf){
        super.onConfigurationChanged(newConf);

        try {
            if (newConf.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                cam.setDisplayOrientation(0);
            } else {
                cam.setDisplayOrientation(90);
            }
        } catch (Exception e){
            Log.e(TAG, e.toString());
        }
    }

    @Override
    public void onPause(){
        super.onPause();
        if(cam != null) {
            cam.stopPreview();
            cam.setPreviewCallback(null);
            cam.release();
        }
    }

    public synchronized void onRunStateChanged(final String msg, State state){
        if(msg != null) {
            Log.d(TAG, msg);

            Runnable show_toast = new Runnable() {
                public void run() {
                    toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
                    toast.show();
                }
            };

            this.runOnUiThread(show_toast);
        }

        if(state == State.FAILURE){
            Log.e(TAG, "Failure, exiting.");
            sleep(1000);
            finish();
        }

        if(state == State.RESPOND){
            if(RunState == State.RESPOND){
                Log.d(TAG, "Resuming classification mode");
                TRbutton.setVisibility(View.INVISIBLE);
                TLbutton.setVisibility(View.INVISIBLE);
                BRbutton.setVisibility(View.INVISIBLE);
                BLbutton.setVisibility(View.INVISIBLE);
                classInputBar.setVisibility(View.INVISIBLE);
                cam.setPreviewCallback(framePasser);
                cam.startPreview();
                state = State.CLASSIFY;
            } else {
                cam.stopPreview();
                Log.d(TAG, "Waiting for response");
            }
        }

        RunState = state;
    }

    public static State getState(){
        return RunState;
    }
}
