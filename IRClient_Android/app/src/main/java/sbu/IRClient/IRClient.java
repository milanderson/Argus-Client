package sbu.IRClient;

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
import android.os.AsyncTask;
import android.os.Environment;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.List;

import static android.os.SystemClock.sleep;

public class IRClient extends Activity {
    public static final String TAG = "IRClient";
    public enum State {FAILURE, RESPOND, CLASSIFY, DEFAULT, OPTIONS}
    private static State RunState = State.CLASSIFY;

    public static File mOutputFile = null;
    public static File mInitFile = null;
    public static MediaRecorder rec;
    public static List<Long> encodeTimes = new ArrayList<>(10);

    public static Pipe pipe;
    private static ByteBuffer signalInBuf = ByteBuffer.allocate(10);
    public static FrameTracker frameTracker =new FrameTracker();

    public static IRView overlayView;
    private static SurfaceView backView;
    private TextureView texView;
    private static EditText IPInput;
    private static TextView IPText;
    public static Button TLbutton, TRbutton, BRbutton, BLbutton, STbutton, OPbutton, IPbutton;
    private static CheckBox latencyCheckBox;
    public static TextView latencyView;
    public static ClassInputBar classInputBar;
    private static Toast toast;
    public static Context context;
    private static Camera cam;
    private static SurfaceTexture camSurface;
    private static boolean showLatency = false;

    private static IRClient runningClient;

    private TextView.OnEditorActionListener enterClassListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(classInputBar.getWindowToken(), 0);

            try {
                if(classInputBar.getText() != null && classInputBar.getText().length() > 1){
                    String strMsg = "reply" + classInputBar.getText().toString() + ",";
                    ByteBuffer msg = ByteBuffer.allocate(strMsg.getBytes().length);
                    msg.put(strMsg.getBytes());
                    msg.flip();
                    pipe.sink().write(msg);
                }
            } catch (Exception e){
                Log.e(TAG, "Error passing reply." + e.toString());
            }
            classInputBar.setText("");
            onRunStateChanged(null, State.CLASSIFY);
            return true;
        }
    };

    private static Camera.PreviewCallback framePasser = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {
            frameTracker.SetFrame(bytes);
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
        runningClient = this;
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        context = getApplicationContext();
        Log.d(TAG, "Starting");

        checkPermissions();

        initViews();
        initButtons();
        initInputBar();
        onRunStateChanged("", State.DEFAULT);

        // resting
        Log.d(TAG, "resting");
    }

    private void checkPermissions(){
        String[] perm = {Manifest.permission.CAMERA, Manifest.permission.INTERNET};
        boolean permOK = true;

        for(int i = 0; i < perm.length; i++){
            if(android.support.v4.content.ContextCompat.checkSelfPermission(this, perm[i]) != android.content.pm.PackageManager.PERMISSION_GRANTED){
                permOK = false;
            }
        }
        while(!permOK) {
            permOK = true;
            try {
                ActivityCompat.requestPermissions(this, perm, 0);
                Thread.sleep(1000);
                for(int i = 0; i < perm.length; i++){
                    if(android.support.v4.content.ContextCompat.checkSelfPermission(this, perm[i]) != android.content.pm.PackageManager.PERMISSION_GRANTED){
                        permOK = false;
                    }
                }

            } catch (Exception e) {
                //Log.d(TAG, e.toString());
                onRunStateChanged("Permissions Request Failed. " + e.toString(), State.FAILURE);
            }

        }
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
        overlayView = findViewById(R.id.overlayView);
        backView = findViewById(R.id.backView);
        latencyView = findViewById(R.id.LatencyView);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        Log.d(TAG, Float.toString((float)metrics.heightPixels/(float)metrics.widthPixels
        ));
        if((float)metrics.heightPixels/(float)metrics.widthPixels > 640.0/480.0){
            texView.setLayoutParams(new ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, (int)(640.0*((float)metrics.widthPixels/(float)480))));
            texView.setTranslationY((metrics.heightPixels - (int)(640.0*((float)metrics.widthPixels/480))) / 2);
        } else {
            texView.setLayoutParams(new ConstraintLayout.LayoutParams((int)(480.0*((float)metrics.heightPixels/640.0)), ViewGroup.LayoutParams.FILL_PARENT));
            texView.setTranslationX((metrics.widthPixels - (int)(480.0*((float)metrics.heightPixels/640.0))) / 2);
        }

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(getState() == State.CLASSIFY && overlayView.setButtons((int)motionEvent.getX(), (int)motionEvent.getY())) {
                    onRunStateChanged(null, State.RESPOND);
                } else if(getState() == State.RESPOND){
                    onRunStateChanged(null, State.CLASSIFY);
                }
                return false;
            }
        });

        texView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                IRClient.camSurface = surface;

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

    private void initButtons(){
        try {
            STbutton = findViewById(R.id.buttonStart);
            OPbutton = findViewById(R.id.buttonOptions);
            IPbutton = findViewById(R.id.buttonIP);
            TLbutton = findViewById(R.id.buttonTL);
            TRbutton = findViewById(R.id.buttonTR);
            BLbutton = findViewById(R.id.buttonBL);
            BRbutton = findViewById(R.id.buttonBR);

            latencyCheckBox = findViewById(R.id.LatencyCheckBox);
            latencyCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    Log.e(TAG, "Show Latency: " + Boolean.toString(isChecked));
                    showLatency = isChecked;
                }
            });

            IPInput = findViewById(R.id.inputIP);
            IPText = findViewById(R.id.optTextIP);
            IPText.setText("Current Server: " + IRClient_NetTask.SERVER_IP);
            overlayView.setGuesses("");

            findViewById(R.id.buttonOptRet).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(getState() == State.OPTIONS) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(IPInput.getWindowToken(), 0);
                        onRunStateChanged("", State.DEFAULT);
                    }
                }
            });

            IPbutton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(IPInput.getWindowToken(), 0);
                    AsyncTask<String, Void, InetAddress> task = new AsyncTask<String, Void, InetAddress>() {
                        @Override
                        protected InetAddress doInBackground(String... params){
                            try {
                                return InetAddress.getByName(params[0]);
                            } catch (Exception e) {
                                return null;
                            }
                        }
                    };
                    try{
                        if(IPInput.getText() != null && IPInput.getText().length() > 2) {
                            Log.d(TAG, "checking IP");
                            InetAddress IP = task.execute(IPInput.getText().toString()).get();
                            if (IP != null) {
                                IRClient_NetTask.SERVER_IP = IPInput.getText().toString();
                                IPText.setText("Current Server: " + IP.toString());
                            } else {
                                Log.e(TAG, "Search for: " + IPInput.getText().toString());
                                onRunStateChanged("Unknown host", State.OPTIONS);
                            }
                        }
                    } catch (Exception e){
                        Log.e(TAG, e.toString());
                    }
                }
            });

            STbutton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(getState() == State.DEFAULT) {
                        onRunStateChanged("", State.CLASSIFY);
                    }
                }
            });

            OPbutton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(getState() == State.DEFAULT){
                        onRunStateChanged("", State.OPTIONS);
                    }
                }
            });

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

    private void initCam() {
        int width = 640;
        int height = 480;

        // Media Recorder Attempt
        try {
            cam = Camera.open();
            if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                cam.setDisplayOrientation(90);
            }
            Camera.Parameters param = cam.getParameters();
            param.setPreviewSize(width, height);
            Log.e(TAG, "Picture format " + param.getPreviewFormat());

            cam.setParameters(param);
            cam.setPreviewCallback(framePasser);

            cam.setPreviewTexture(camSurface);
            cam.startPreview();

            pipe = Pipe.open();
            pipe.source().configureBlocking(false);
            pipe.sink().configureBlocking(false);
            new IRClient_NetTask().execute(this);

            toast = Toast.makeText(context, "Connecting...", Toast.LENGTH_SHORT);
            toast.show();
        } catch (Exception e){
            Log.e(TAG, "Error initializing camera: " + e.toString());
        }
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
                onRunStateChanged("Permissions Not Granted", State.FAILURE);
                return;
            }
        }
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
        try {
            if(getState() == State.CLASSIFY || getState() == State.RESPOND) {
                signalInBuf.clear();
                signalInBuf.put("NETEXIT".getBytes());
                signalInBuf.flip();
                while (signalInBuf.hasRemaining()) {
                    pipe.sink().write(signalInBuf);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Error pausing." + e.toString());
            onRunStateChanged(e.toString(), State.FAILURE);
        }
        onRunStateChanged("", State.DEFAULT);
    }

    @Override
    public void onResume(){
        super.onResume();
    }

    public static void onRunStateChanged(final String msg, State state){
        Log.d(TAG, "State: " + state.toString());
        if(msg != null && msg != "") {
            Log.d(TAG, msg);

            toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
            toast.show();
        }

        if(state == State.OPTIONS){
            runningClient.findViewById(R.id.optMenu).setVisibility(View.VISIBLE);
            STbutton.setVisibility(View.INVISIBLE);
            OPbutton.setVisibility(View.INVISIBLE);
            runningClient.findViewById(R.id.selStart).setVisibility(View.INVISIBLE);
            runningClient.findViewById(R.id.selOptions).setVisibility(View.INVISIBLE);
            TRbutton.setVisibility(View.INVISIBLE);
            TLbutton.setVisibility(View.INVISIBLE);
            BRbutton.setVisibility(View.INVISIBLE);
            BLbutton.setVisibility(View.INVISIBLE);
            classInputBar.setVisibility(View.INVISIBLE);
        }

        if(state == State.DEFAULT){
            try{
                if(cam != null) {
                    cam.stopPreview();
                    cam.setPreviewCallback(null);
                    cam.release();
                    cam = null;
                }
            } catch (Exception e){

            }
            overlayView.setBackgroundResource(R.color.white);

            runningClient.findViewById(R.id.optMenu).setVisibility(View.INVISIBLE);
            backView.setVisibility(View.INVISIBLE);
            STbutton.setVisibility(View.VISIBLE);
            OPbutton.setVisibility(View.VISIBLE);
            runningClient.findViewById(R.id.selStart).setVisibility(View.VISIBLE);
            runningClient.findViewById(R.id.selOptions).setVisibility(View.VISIBLE);
            latencyView.setVisibility(View.INVISIBLE);
            TRbutton.setVisibility(View.INVISIBLE);
            TRbutton.setText("Unknown");
            TLbutton.setVisibility(View.INVISIBLE);
            TLbutton.setText("");
            BRbutton.setVisibility(View.INVISIBLE);
            BRbutton.setText("");
            BLbutton.setVisibility(View.INVISIBLE);
            BLbutton.setText("");
            classInputBar.setVisibility(View.INVISIBLE);
        }

        if(state == State.CLASSIFY){
            if(getState() != State.CLASSIFY && getState() != State.RESPOND) {
                Log.d(TAG, "init Cam");
                runningClient.initCam();

                if(showLatency){
                    latencyView.setVisibility(View.VISIBLE);
                }
                overlayView.setBackgroundResource(R.color.transparent);
                backView.setVisibility(View.VISIBLE);
                STbutton.setVisibility(View.INVISIBLE);
                OPbutton.setVisibility(View.INVISIBLE);
                runningClient.findViewById(R.id.selStart).setVisibility(View.INVISIBLE);
                runningClient.findViewById(R.id.selOptions).setVisibility(View.INVISIBLE);
                TRbutton.setVisibility(View.INVISIBLE);
                TLbutton.setVisibility(View.INVISIBLE);
                BRbutton.setVisibility(View.INVISIBLE);
                BLbutton.setVisibility(View.INVISIBLE);
                classInputBar.setVisibility(View.INVISIBLE);
            }
            if(RunState == State.RESPOND){
                Log.d(TAG, "Resuming classification mode");
                classInputBar.setVisibility(View.INVISIBLE);
                cam.setPreviewCallback(framePasser);
                cam.startPreview();
            }
        }

        if(state == State.FAILURE){
            Log.e(TAG, "Failure, exiting.");
            sleep(1000);
            runningClient.finish();
        }

        if(state == State.RESPOND){
            latencyView.setVisibility(View.INVISIBLE);
            Log.d(TAG, "Test");
            cam.stopPreview();
            Log.d(TAG, "Waiting for response");
        }

        RunState = state;
    }

    public static State getState(){
        return RunState;
    }
}
