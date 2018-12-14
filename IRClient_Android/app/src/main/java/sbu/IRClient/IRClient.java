package sbu.IRClient;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
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
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static android.os.SystemClock.sleep;

public class IRClient extends Activity {
    public static final String TAG = "IRClient";
    public enum State {FAILURE, RESPOND, CLASSIFY, DEFAULT, OPTIONS}
    private static State RunState = State.CLASSIFY;
    public static Handler mainHandler;

    public static File mOutputFile = null;
    public static File mInitFile = null;
    public static List<Long> encodeTimes = new ArrayList<>(10);

    public static IRView overlayView;
    private static SurfaceView backView;
    private TextureView texView;
    private static EditText IPInput;
    private static TextView IPText;
    public static Button TLbutton, TRbutton, BRbutton, BLbutton, STbutton, OPbutton, IPbutton;
    private static CheckBox latencyCheckBox, latencyCorrectionCheckBox;
    public static TextView latencyView;
    public static ClassInputBar classInputBar;
    private static Toast toast;
    private static Camera cam;
    private static SurfaceTexture camSurface;
    public static Context context;

    private static boolean showLatency = false;
    private static boolean dropSlowPackets = false;

    private static IRClient runningClient;

    private TextView.OnEditorActionListener enterClassListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(classInputBar.getWindowToken(), 0);

            try {
                if(classInputBar.getText() != null && classInputBar.getText().length() > 1){
                    NetMonitor.addReply(classInputBar.getText().toString());
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
            NetMonitor.addFrame(bytes);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        runningClient = this;
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        context = getApplicationContext();
        mainHandler = new Handler(context.getMainLooper());
        Log.d(TAG, "Starting");

        checkPermissions();

        initViews();
        initButtons();
        initInputBar();
        onRunStateChanged("", State.DEFAULT);
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
        overlayView.labelView = findViewById(R.id.labelView);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        Log.d(TAG, Float.toString((float)metrics.heightPixels/(float)metrics.widthPixels
        ));
        if((float)metrics.heightPixels/(float)metrics.widthPixels > 640.0/480.0){
            texView.setLayoutParams(new ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, (int)(640.0*((float)metrics.widthPixels/(float)480))));
            overlayView.setLayoutParams(new ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, (int)(640.0*((float)metrics.widthPixels/(float)480))));
            overlayView.labelView.setLayoutParams(new ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, (int)(640.0*((float)metrics.widthPixels/(float)480))));
            texView.setTranslationY((metrics.heightPixels - (int)(640.0*((float)metrics.widthPixels/480))) / 2);
            overlayView.setTranslationY((metrics.heightPixels - (int)(640.0*((float)metrics.widthPixels/480))) / 2);
            overlayView.labelView.setTranslationY((metrics.heightPixels - (int)(640.0*((float)metrics.widthPixels/480))) / 2);
        } else {
            texView.setLayoutParams(new ConstraintLayout.LayoutParams((int)(480.0*((float)metrics.heightPixels/640.0)), ViewGroup.LayoutParams.FILL_PARENT));
            overlayView.setLayoutParams(new ConstraintLayout.LayoutParams((int)(480.0*((float)metrics.heightPixels/640.0)), ViewGroup.LayoutParams.FILL_PARENT));
            overlayView.labelView.setLayoutParams(new ConstraintLayout.LayoutParams((int)(480.0*((float)metrics.heightPixels/640.0)), ViewGroup.LayoutParams.FILL_PARENT));
            texView.setTranslationX((metrics.widthPixels - (int)(480.0*((float)metrics.heightPixels/640.0))) / 2);
            overlayView.setTranslationX((metrics.widthPixels - (int)(480.0*((float)metrics.heightPixels/640.0))) / 2);
            overlayView.labelView.setTranslationX((metrics.widthPixels - (int)(480.0*((float)metrics.heightPixels/640.0))) / 2);
        }

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(getState() == State.CLASSIFY && overlayView.setButtons((int)motionEvent.getX(), (int)motionEvent.getY())) {
                    onRunStateChanged(null, State.RESPOND);
                } else if(getState() == State.RESPOND){
                    Log.d(TAG, "screen touched");
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

            latencyCorrectionCheckBox = findViewById(R.id.LatencyCorrectionCheckBox);
            latencyCorrectionCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    Log.e(TAG, "drop slow responses: " + Boolean.toString(isChecked));
                    dropSlowPackets = isChecked;

                    if(dropSlowPackets) {
                        final Handler handler = new Handler();
                        final int delay = 500; //milliseconds

                        handler.postDelayed(new Runnable() {
                            public void run() {
                                int x = 300000;
                                try{
                                    x = Integer.parseInt(latencyView.getText().toString());
                                } catch (Exception e) {}
                                if (x > 2500){
                                    overlayView.clear();
                                }

                                if (dropSlowPackets) {
                                    handler.postDelayed(this, delay);
                                }
                            }
                        }, delay);
                    }
                }
            });

            IPInput = findViewById(R.id.inputIP);
            IPText = findViewById(R.id.optTextIP);
            IPText.setText("Current Server:  " + Net_ClassRequester.SERVER_IP);
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

                    String urlStr = IPInput.getText().toString();
                    if(urlStr.indexOf("http://") != 0){
                        urlStr = "http://" + urlStr;
                    }
                    Net_ClassRequester.SERVER_IP = urlStr;

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
                        if(urlStr != null && urlStr.length() > 2) {
                            Log.d(TAG, "checking URL");
                            InetAddress IP = task.execute(urlStr).get();
                            IPText.setText("Current Server: " + urlStr);
                            if (IP != null) {
                                //Net_ClassRequester.SERVER_IP = urlStr;
                            } else {
                                onRunStateChanged("Unknown host", State.OPTIONS);
                            }
                        }
                    } catch (Exception e){
                        Log.e(TAG, "Error finding server: " + e.toString());
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

                            NetMonitor.addReply(TLbutton.getText().toString());
                        } catch (Exception e) {
                            Log.e(TAG, "Error clicking button." + e.toString());
                        }
                        onRunStateChanged(null, State.CLASSIFY);
                    }
                }
            });
            TRbutton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    Log.e(TAG, "button 2");

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

                            NetMonitor.addReply(TRbutton.getText().toString());
                        } catch (Exception e) {
                            Log.e(TAG, "Error clicking button." + e.toString());
                        }
                        onRunStateChanged(null, State.CLASSIFY);
                    }
                }
            });
            BLbutton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    Log.e(TAG, "button 3");

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

                            NetMonitor.addReply(BLbutton.getText().toString());
                        } catch (Exception e) {
                            Log.e(TAG, "Error clicking button." + e.toString());
                        }
                        onRunStateChanged(null, State.CLASSIFY);
                    }
                }
            });
            BRbutton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    Log.e(TAG, "button 4");

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
                            NetMonitor.addReply(BRbutton.getText().toString());
                        } catch (Exception e) {
                            Log.e(TAG, "Error clicking button." + e.toString());
                        }
                        onRunStateChanged(null, State.CLASSIFY);
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
            param.setPreviewFormat(ImageFormat.YV12);
            Log.e(TAG, "Picture format " + param.getPreviewFormat());

            cam.setParameters(param);
            cam.setPreviewCallback(framePasser);

            cam.setPreviewTexture(camSurface);
            cam.startPreview();
        } catch (Exception e){
            Log.e(TAG, "Error initializing camera: " + e.toString());
        }
    }

    public static class NetMonitor {
        public static int MSGSIZE = 10000;
        private static ReentrantLock frontToBackLock = new ReentrantLock();
        private static ReentrantLock reqToReadLock = new ReentrantLock();
        private static ReentrantLock maskWriteLock = new ReentrantLock();
        private static ReentrantLock rectWriteLock = new ReentrantLock();
        private static boolean replyReady = false;
        private static boolean frameReady = false;
        private static String netReply;
        private static InputStream respConn = null;
        private static FrameTracker frameTracker =new FrameTracker();
        private static boolean NetStatusOK = false;
        private static final Object sendLock = new Object();
        private static final Object readLock = new Object();
        private static Net_ClassRequester classRequester;
        private static Net_RespReader respReader;
        private static ArrayList<String> rawMasks = new ArrayList<String>();
        private static ArrayList<String> rectLists = new ArrayList<>();
        private static long frameUpdateInterval = 0;

        public static void startRequestThread(){
            classRequester = new Net_ClassRequester();
            classRequester.start();
        }

        public static void startResponseThread(){
            respReader = new Net_RespReader();
            respReader.start();
        }

        public static void setNetStatusOK(){
            NetStatusOK = true;
        }

        public static boolean isNetStatusOK(){
            return NetStatusOK;
        }

        public static void startNetExit(String exitMsg){
            final String netExitMsg = exitMsg;

            Runnable NETEXIT = new Runnable(){
                @Override
                public void run() {
                    NetStatusOK = false;
                    if(classRequester != null && classRequester.isAlive()){
                        classRequester.interrupt();
                    }
                    if(respReader != null && respReader.isAlive()){
                        respReader.interrupt();
                    }
                    onRunStateChanged(netExitMsg, State.DEFAULT);

                    frontToBackLock.lock();
                    frameTracker.Reset();
                    frameReady = false;
                    replyReady = false;
                    frontToBackLock.unlock();
                }
            };
            mainHandler.post(NETEXIT);
        }

        public static void awaitSendTask(){
            if(!frameReady && !replyReady) {
                try {
                    synchronized (sendLock) {
                        sendLock.wait();
                    }
                } catch (InterruptedException e) {
                }
            }
        }

        public static void awaitReadTask(){
            if(respConn == null) {
                try {
                    synchronized (readLock) {
                        readLock.wait();
                    }
                } catch (InterruptedException e) {
                }
            }
        }

        public static void addResponseConn(InputStream conn){
            reqToReadLock.lock();
            if(respConn != null){
                //respConn.disconnect();
            }
            respConn = conn;
            reqToReadLock.unlock();

            synchronized(readLock) {
                readLock.notify();
            }
        }

        public static InputStream getResponseConn(){
            reqToReadLock.lock();
            InputStream retConn = respConn;
            respConn = null;
            reqToReadLock.unlock();

            return retConn;
        }

        public static void clearRects(){
            rectWriteLock.lock();
            rectLists.clear();
            rectWriteLock.unlock();
        }

        public static void addRectList(String rectList){
            rectLists.add(rectList);
        }

        public static ArrayList<String> openRects(){
            rectWriteLock.lock();
            return rectLists;
        }

        public static void closeRects(){
            rectWriteLock.unlock();
        }

        public static void clearMasks(){
            maskWriteLock.lock();
            rawMasks.clear();
            maskWriteLock.unlock();
        }

        public static void addMask(String rawMask){
            rawMasks.add(rawMask);
        }

        public static ArrayList<String> openMasks(){
            maskWriteLock.lock();
            return rawMasks;
        }

        public static void closeMasks(){
            maskWriteLock.unlock();
        }

        public static void addReply (String reply){
            if(getState() == State.CLASSIFY || getState() == State.RESPOND) {
                frontToBackLock.lock();
                netReply = reply;
                replyReady = true;
                frontToBackLock.unlock();

                synchronized(sendLock) {
                    sendLock.notifyAll();
                }
            }
        }

        public static String getReply(){
            String reply = null;
            frontToBackLock.lock();
            if(replyReady) {
                reply = netReply;
                replyReady = false;
            }
            frontToBackLock.unlock();
            return reply;
        }

        public static void addFrame ( byte[] frame){

            if(getState() == State.CLASSIFY || getState() == State.RESPOND) {
                if(System.currentTimeMillis() - frameUpdateInterval > 5000) {
                    frontToBackLock.lock();
                    frameTracker.SetFrame(frame);
                    frameReady = true;
                    frameUpdateInterval = System.currentTimeMillis();
                    frontToBackLock.unlock();

                    synchronized (sendLock) {
                        sendLock.notifyAll();
                    }
                }
            }
        }

        public static Frame getFrame() {
            Frame retFrame = null;
            frontToBackLock.lock();
            if(frameReady) {
                retFrame = frameTracker.GetFrame();
                frameReady = false;
            }
            frontToBackLock.unlock();
            return retFrame;
        }

        public static long getFrameTimeStamp(int fID){
            long retTime;
            frontToBackLock.lock();
            retTime = frameTracker.GetTimeStamp(fID);
            frontToBackLock.unlock();
            return retTime;
        }

    }

    @Override
    public void onBackPressed() {
        if(getState() == State.RESPOND){
            onRunStateChanged("", State.CLASSIFY);
        } else if (getState() == State.CLASSIFY || getState() == State.OPTIONS){
            onRunStateChanged("", State.DEFAULT);
        }
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
            Log.e(TAG, "Error setting orientation: " + e.toString());
        }
    }

    @Override
    public void onPause(){
        super.onPause();
        try {
            if(getState() == State.CLASSIFY || getState() == State.RESPOND) {
                NetMonitor.startNetExit(null);
            }
        } catch (Exception e) {
            Log.d(TAG, "Error pausing." + e.toString());
            onRunStateChanged(e.toString(), State.FAILURE);
        }
        onRunStateChanged("", State.DEFAULT);
    }

    @Override
    public void onResume(){
        if(dropSlowPackets) {
            final Handler handler = new Handler();
            final int delay = 500; //milliseconds

            handler.postDelayed(new Runnable() {
                public void run() {
                    int x = 30000;
                    try {
                        x = Integer.parseInt(latencyView.getText().toString());
                    } catch (Exception e){}
                    if (x > 2500){
                        overlayView.clear();
                    }

                    if (dropSlowPackets) {
                        handler.postDelayed(this, delay);
                    }
                }
            }, delay);
        }

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
                if(NetMonitor.isNetStatusOK()){
                    NetMonitor.startNetExit("");
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
            TRbutton.setText("");
            TLbutton.setVisibility(View.INVISIBLE);
            TLbutton.setText("Unknown");
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

                toast = Toast.makeText(context, "Connecting, Please Wait", Toast.LENGTH_SHORT);
                toast.show();
                NetMonitor.startRequestThread();

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
                overlayView.showGuesses();
                overlayView.refresh();
                classInputBar.setVisibility(View.INVISIBLE);
                TRbutton.setVisibility(View.INVISIBLE);
                TLbutton.setVisibility(View.INVISIBLE);
                BRbutton.setVisibility(View.INVISIBLE);
                BLbutton.setVisibility(View.INVISIBLE);
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
            Log.d(TAG, "Waiting for response");
            overlayView.hideGuesses();
            overlayView.refresh();
            cam.stopPreview();

            Log.d(TAG, "moving on");
        }

        RunState = state;
    }

    public static State getState(){
        return RunState;
    }
}
