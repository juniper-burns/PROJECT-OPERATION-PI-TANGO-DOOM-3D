/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.examples.java.floorplanreconstruction;


import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tango.reconstruction.TangoPolygon;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.display.DisplayManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.media.MediaScannerConnection;
import android.view.ScaleGestureDetector;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.projecttango.tangosupport.TangoSupport;

/**
 * An example showing how to use the 3D reconstruction floor planning features to create a
 * floor plan in Java.
 *
 * This sample uses the APIs that extract a set of simplified 2D polygons and renders them on a
 * SurfaceView. The device orientation is used to automatically translate and rotate the map.
 *
 * Rendering is done in a simplistic way, using the canvas API over a SurfaceView.
 */
public class FloorPlanReconstructionActivity extends Activity implements FloorplanView
        .DrawingCallback{
    private static final String TAG = FloorPlanReconstructionActivity.class.getSimpleName();

    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private static final int PERMISSION_ALL = 0;
    public static float[] devicePosition;
    public static float[] deviceOrientation;
    public static float yawRadians;


    public static float X = -1000000;       //x coord
    public static float Y = -1000000;       //y coord
    public static List<Point> points = new ArrayList<Point>();
    private TangoFloorplanner mTangoFloorplanner;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsConnected = false;
    private boolean mIsPaused;
    private Button mPauseButton;
    private Button mUndoButton;
    private Button mWaypointButton;
    private Button mExportButton;
    private FloorplanView mFloorplanView;
    private TextView mAreaText;

    private ContextWrapper cw;
    private Canvas canvas;
    private int mDisplayRotation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPauseButton = (Button) findViewById(R.id.pause_button);
        mUndoButton = (Button) findViewById(R.id.undo_button);
        mWaypointButton = (Button) findViewById(R.id.add_waypoint_button);
        mExportButton = (Button) findViewById(R.id.export_button);

        mFloorplanView = (FloorplanView) findViewById(R.id.floorplan);
        mFloorplanView.registerCallback(this);
        mAreaText = (TextView) findViewById(R.id.area_text);

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setDisplayRotation();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            }, null);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check and request camera permission at run time.
        if (checkAndRequestPermissions()) {
            bindTangoService();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        synchronized (this) {
            try {
                if (mIsConnected) {
                    mTangoFloorplanner.stopFloorplanning();
                    mTango.disconnect();
                    mTangoFloorplanner.resetFloorplan();
                    mTangoFloorplanner.release();
                    mIsConnected = false;
                    mIsPaused = true;
                }
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Initialize Tango Service as a normal Android Service.
     */
    private void bindTangoService() {
        // Initialize Tango Service as a normal Android Service.
        // Since we call mTango.disconnect() in onPause, this will unbind Tango Service,
        // so every time when onResume gets called, we should create a new Tango object.
        mTango = new Tango(FloorPlanReconstructionActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready,
            // this Runnable will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only
            // when there is no UI thread changes involved.
            @Override
            public void run() {
                synchronized (FloorPlanReconstructionActivity.this) {
                    try {
                        TangoSupport.initialize();
                        mConfig = setupTangoConfig(mTango);
                        mTango.connect(mConfig);
                        startupTango();
                        mIsConnected = true;
                        mIsPaused = false;
                        setDisplayRotation();
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                        showsToastAndFinishOnUiThread(R.string.exception_out_of_date);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_error);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_invalid);
                    }
                }
            }
        });
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Use default configuration for Tango Service, plus color camera, low latency
        // IMU integration, depth, smooth pose and dataset recording.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        // NOTE: Low latency integration is necessary to achieve a precise alignment of virtual
        // objects with the RBG image and produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        return config;
    }

    /**
     * Set up the callback listeners for the Tango service and obtain other parameters required
     * after Tango connection.
     * Listen to updates from the point cloud.
     */
    private void startupTango() {
        mTangoFloorplanner = new TangoFloorplanner(new TangoFloorplanner
                .OnFloorplanAvailableListener() {
            @Override
            public void onFloorplanAvailable(List<TangoPolygon> polygons) {
                mFloorplanView.setFloorplan(polygons);
                calculateAndUpdateArea(polygons);
            }
        });
        // Set camera intrinsics to TangoFloorplanner.
        mTangoFloorplanner.setDepthCameraCalibration(mTango.getCameraIntrinsics
                (TangoCameraIntrinsics.TANGO_CAMERA_DEPTH));

        mTangoFloorplanner.startFloorplanning();

        // Connect listeners to tango service and forward point cloud and camera information to
        // TangoFloorplanner.
        List<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        mTango.connectListener(framePairs, new Tango.OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(TangoPoseData tangoPoseData) {
                // We are not using onPoseAvailable for this app.
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData tangoXyzIjData) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onFrameAvailable(int i) {
                // We are not using onFrameAvailable for this app.
            }

            @Override
            public void onTangoEvent(TangoEvent tangoEvent) {
                // We are not using onTangoEvent for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData tangoPointCloudData) {
                mTangoFloorplanner.onPointCloudAvailable(tangoPointCloudData);
            }
        });
    }

    /**
     * Method called each time right before the floorplan is drawn. It allows using the Tango
     * service to get the device position and orientation.
     */
    @Override
    public void onPreDrawing() {
        try {
            // Synchronize against disconnecting while using the service.
            synchronized (FloorPlanReconstructionActivity.this) {
                // Don't execute any tango API actions if we're not connected to
                // the service
                if (!mIsConnected) {
                    return;
                }

                // Calculate the device pose in OpenGL engine (Y+ up).
                TangoPoseData devicePose = TangoSupport.getPoseAtTime(0.0,
                        TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                        TangoPoseData.COORDINATE_FRAME_DEVICE,
                        TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                        mDisplayRotation);

                if (devicePose.statusCode == TangoPoseData.POSE_VALID) {
                    // Extract position and rotation around Z.
                    devicePosition = devicePose.getTranslationAsFloats();
                    deviceOrientation = devicePose.getRotationAsFloats();
                    yawRadians = yRotationFromQuaternion(deviceOrientation[0],
                            deviceOrientation[1], deviceOrientation[2],
                            deviceOrientation[3]);

                    mFloorplanView.updateCameraMatrix(devicePosition[0], -devicePosition[2],
                            yawRadians);
                } else {
                    Log.w(TAG, "Can't get last device pose");
                }
            }
        } catch (TangoErrorException e) {
            Log.e(TAG, "Tango error while querying device pose.", e);
        } catch (TangoInvalidException e) {
            Log.e(TAG, "Tango exception while querying device pose.", e);
        }
    }

    /**
     * Calculates the rotation around Y (yaw) from the given quaternion.
     */
    private static float yRotationFromQuaternion(float x, float y, float z, float w) {
        return (float) Math.atan2(2 * (w * y - x * z), w * (w + x) - y * (z + y));
    }

    /**
     * Calculate the total explored space area and update the text field with that information.
     */
    private void calculateAndUpdateArea(List<TangoPolygon> polygons) {
        double area = 0;
        for (TangoPolygon polygon: polygons) {
            if (polygon.layer == TangoPolygon.TANGO_3DR_LAYER_SPACE) {
                area += polygonArea(polygon);
            }
        }
        final String areaText = String.format("%.2f", area);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAreaText.setText(areaText);
            }
        });
    }

    /**
     * Auxiliary function that uses Green's Theorem to calculate the area of a given polygon.
     *
     * {@see http://math.blogoverflow.com/2014/06/04/greens-theorem-and-area-of-polygons/}
     */
    private double polygonArea(TangoPolygon polygon) {
        double area = 0;
        int size = polygon.vertices2d.size();
        for (int i = 0; i < size;  i++) {
            float[] v0 = polygon.vertices2d.get(i);
            float[] v1 = polygon.vertices2d.get((i + 1) % size);
            area += (v1[0] - v0[0]) * (v0[1] + v1[1]) / 2.0;
        }
        return area;
    }

    public void onPauseButtonClicked(View v) {
        if (mIsPaused) {
            mTangoFloorplanner.startFloorplanning();
            mPauseButton.setText("Pause");
        } else {
            mTangoFloorplanner.stopFloorplanning();
            mPauseButton.setText("Resume");
        }
        mIsPaused = !mIsPaused;
    }

    public void onClearButtonClicked(View v)
    {
        mTangoFloorplanner.resetFloorplan();
        points.clear();
    }

    public void onUndoClicked(View view) {
        Context context = getApplicationContext();
        CharSequence text = "Point removed!";
        int duration = Toast.LENGTH_SHORT;

        Toast.makeText(context, text, duration).show();
    }

    public void onWaypointClicked(View view) {
        Context context = getApplicationContext();
        CharSequence text = "Point added!";
        int duration = Toast.LENGTH_SHORT;

        Toast.makeText(context, text, duration).show();
    }


    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }


    public void onExportClicked(View view) {

        cw = new ContextWrapper(getApplicationContext());
        Matrix rotate = new Matrix();
        rotate.postRotate(90);
        Bitmap bmp1 = Bitmap.createBitmap(mFloorplanView.getWidth()*2, mFloorplanView.getHeight()*2, Bitmap.Config.ARGB_8888);



        CharSequence text = "Data successfully exported.";

        //Get the root directory for the external storage
        String root = Environment.getExternalStorageDirectory().toString();
        int duration = Toast.LENGTH_SHORT;
        canvas = new Canvas(bmp1);
        //view.draw(canvas);
        mFloorplanView.doDraw(canvas);

        //Path to the current directory, to where we will be saving the file to
        File myPath = new File(root + "/Tango");
        //Makes the directory if it is not there
        myPath.mkdir();

        //Generate a random name for the image file
        Random generator = new Random();
        int n = 1000;
        n = generator.nextInt(n);
        String fname = "Image-"+ n +".png";
        File file = new File (myPath, fname);





        try
        {
            Bitmap bmp2 = Bitmap.createBitmap(bmp1,0,0,bmp1.getWidth(), bmp1.getHeight(), rotate,true);
            FileOutputStream bmpFileStream = new FileOutputStream(file);
            bmp2.compress(Bitmap.CompressFormat.PNG,100,bmpFileStream);
            bmpFileStream.flush();
            bmpFileStream.close();

            //Add the photo to the gallery
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(file);
            mediaScanIntent.setData(contentUri);
            this.sendBroadcast(mediaScanIntent);

            Toast.makeText(cw, text, duration).show();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            text = "No write access to file location: " + myPath.toString();
            Toast.makeText(cw, text, duration).show();
        }


    }

    @Override
    public boolean onTouchEvent(MotionEvent ev)
    {
        //plot the point on the canvas
        try
        {
            if(ev.getAction() == MotionEvent.ACTION_DOWN)
            {
                canvas = mFloorplanView.getCanvas();
                //Add the x and y coordinates to the point
                Point p = new Point();
                p.x = (int)(ev.getRawX());
                p.y = (int)(ev.getRawY());

                points.add(p);

                Log.d(getClass().getSimpleName(), "X: " + p.x + " Y: " + p.y + "COLOR: " + Color.RED);
                mFloorplanView.releaseCanvas(canvas);

            }
        }
        catch (Exception e)
        {

        }
        return true;
    }


    /**
     * Set the display rotation.
     */
    private void setDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        mDisplayRotation = display.getRotation();
    }

    /**
     * Check we have the necessary permissions for this app, and ask for them if we haven't.
     *
     * @return True if we have the necessary permissions, false if we haven't.
     */
    private boolean checkAndRequestPermissions() {

        if (!hasPermissions(PERMISSIONS)) {
            requestStoragePermission();

            return false;
        }
        return true;
    }


    public boolean hasPermissions(String... permissions) {

            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }

        return true;
    }

    /**
     * Request the necessary permissions for this app.
     */
    private void requestStoragePermission()
    {

        if(!hasPermissions(PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
    }

    /**
     * If the user has declined the permission before, we have to explain him the app needs this
     * permission.
     */
    private void showRequestPermissionRationale() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Java Floorplan Reconstruction Example requires camera permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(FloorPlanReconstructionActivity.this,
                                new String[]{PERMISSIONS[0]}, PERMISSION_ALL);
                    }
                })
                .create();
        dialog.show();
    }

    private void showRequestPermissionRationaleStorage() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Java Floorplan Reconstruction Example requires storage permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(FloorPlanReconstructionActivity.this,
                                new String[]{PERMISSIONS[1]}, PERMISSION_ALL);
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * Display toast on UI thread.
     *
     * @param resId The resource id of the string resource to use. Can be formatted text.
     */
    private void showsToastAndFinishOnUiThread(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FloorPlanReconstructionActivity.this,
                        getString(resId), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (hasPermissions(PERMISSIONS)) {
            bindTangoService();
        } else {
            Toast.makeText(this, "Java Floorplan Reconstruction Example requires camera permission",
                    Toast.LENGTH_LONG).show();
        }
    }



}
