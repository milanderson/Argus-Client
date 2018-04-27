package sbu.irclient;

import android.Manifest;
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
import android.view.TextureView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IRClient extends AppCompatActivity {
    public static String IR_REQ_ST = "Stream";
    public static String SERVER_IP = "24.189.25.87";
    public static final String TAG = "IRClient";

    public static File mOutputFile = null;
    public static File mInitFile = null;
    public static MediaRecorder rec;

    public static boolean frameInUse = false;
    public static byte[] frame = null;

    private TextureView texView;
    private boolean fileSwitched = false;
    private Camera cam;
    public static List<Long> encodeTimes = new ArrayList<>(10);
    private int permGranted = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_irclient);
        Log.d(TAG, "Starting");

        if(checkPermissions() == -1){
            return;
        }

        setContentView(R.layout.activity_irclient);
        texView = findViewById(R.id.textureView);

        Log.d(TAG, "init Cam");
        //checkCapabilities();
        initCam();

        // resting
        Log.d(TAG, "resting");
        //while(true){}
    }

    private int checkPermissions(){
        String[] perm = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.INTERNET, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        boolean permOK = true;

        for(int i = 0; i < perm.length; i++){
            if(android.support.v4.content.ContextCompat.checkSelfPermission(this, perm[i]) != android.content.pm.PackageManager.PERMISSION_GRANTED){
                permOK = false;
            }
        }

        if(!permOK){
            try {
                ActivityCompat.requestPermissions(this, perm, 0);
            } catch (Exception e){
                Log.d(TAG, e.toString());
                return -1;
            }

            while(permGranted == 0) {}
            if (permGranted == 1){
                return -1;
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

    private void initCam(){
        int width = 640;
        int height = 480;

        // Media Recorder Attempt
        cam = Camera.open();
        cam.setDisplayOrientation(90);
        Camera.Parameters param = cam.getParameters();
        param.setPreviewSize(width, height);
        cam.setParameters(param);
        cam.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] bytes, Camera camera) {
                if(!frameInUse){
                    IRClient_NetTask.sendTime.add(System.currentTimeMillis());
                    frame = bytes;
                }
            }
        });

        texView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                try {
                    cam.setPreviewTexture(surface);
                    cam.startPreview();

                    /*
                    initRecorder("Init");

                    TimeUnit.MILLISECONDS.sleep(1000);
                    */
                    new IRClient_NetTask().execute();
                    while(!IRClient_NetTask.ready){}
                } catch (Exception e){
                    Log.e(TAG, "Error initializing recorder: " + e.toString());
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                /*
                try{
                    if(!fileSwitched){
                        rec.stop();

                        initRecorder("Output");
                        Log.d(TAG, "switch successful");
                        fileSwitched = true;
                    } else {
                        encodeTimes.add(System.currentTimeMillis());
                    }
                } catch (Exception e){
                    Log.e(TAG, e.toString());
                }
                */
            }
        });
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

}
