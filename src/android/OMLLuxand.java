package com.luxand.dsi;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.DrawableRes;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.luxand.FSDK;
import com.topdata.apptopponto.R;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class OMLLuxand extends Activity implements OnClickListener {

    private boolean mIsFailed = false;
    private Preview mPreview;

    private ProcessImageAndDrawResults mDraw;

    private String database = "Memory50.dat";
    private int loginTryCount = 3;
    private int timeOut;
    private String template = "";
    float livenessParam = 0f;
    float matchFacesParam = 0f;

    private String launchType = "FOR_REGISTER";
    private Handler handlerDateTime;

    /**
     * Called when the activity is first created.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Constants.sDensity = getResources().getDisplayMetrics().scaledDensity;

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Lock orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Camera layer and drawing layer
        Bundle data = getIntent().getExtras();
        if (data != null) {
            this.database = data.getString("DB_NAME", "memory.dat");
            Log.d("com.luxand.dsi", database);
            this.loginTryCount = data.getInt("LOGIN_TRY_COUNT", 3);
            this.launchType = data.getString("TYPE", "FOR_REGISTER");
            this.timeOut = data.getInt("TIMEOUT", 15000);
            this.template = data.getString("TEMPLATE", "");
            this.livenessParam = data.getFloat("LIVENESS_PARAM", 0.7f);
            this.matchFacesParam = data.getFloat("MATCH_FACES_PARAM", 0.95f);
        }

        // Inializa o processamento da face e desenha os resultados
        mDraw = new ProcessImageAndDrawResults(this, this.launchType.equals("FOR_REGISTER"), loginTryCount, timeOut, template, livenessParam, matchFacesParam);

        mDraw.setOnImageProcessListener(new OnImageProcessListener() {
            @Override
            public void handle(JSONObject obj) {
                //handlerDateTime.removeCallbacksAndMessages(null);

                Intent data = new Intent();

                data.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                if (obj == null) obj = new JSONObject();
                data.putExtra("data", obj.toString());
                setResult(RESULT_OK, data);
                _pause();
                finish();
            }
        });

        mPreview = new Preview(this, mDraw);
        mDraw.mTracker = new FSDK.HTracker();

        String templatePath = this.getApplicationInfo().dataDir + "/" + database;
        if (FSDK.FSDKE_OK != FSDK.LoadTrackerMemoryFromFile(mDraw.mTracker, templatePath)) {
            int res = FSDK.CreateTracker(mDraw.mTracker);
            if (FSDK.FSDKE_OK != res) {
                showErrorAndClose("Error creating tracker", res);
            }
        }

        resetTrackerParameters();

        this.getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#272727"))); //BLACK BACKGROUND

        setContentView(mPreview); //creates MainActivity contents
        addContentView(mDraw, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // Menu
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View viewBottom = inflater.inflate(getAppResource("bottom_menu", "layout"), null);
        addContentView(viewBottom, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        updateTextView(Constants.ENQUADRE_ROSTO);
        updateDateTime();
    }

    /**
     * Método responsável por atualizar o texto de visualização (ID: textInfo)
     *
     * @param texto String que substituirá o texto atual do rodapé
     */
    public void updateTextView(final String texto) {
        TextView textInfo = findViewById(R.id.textInfo);
        textInfo.setText(texto);
        textInfo.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    }

    /**
     * Método responsável por atualizar o texto de data/hora (ID: dateTimeText)
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void updateDateTime() {

        this.handlerDateTime = new Handler();

        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                Date dateTime = new Date();
                String dateTimeString = formatter.format(dateTime);

                TextView dateTimeText = findViewById(R.id.dateTimeText);
                dateTimeText.setText(dateTimeString);

                handlerDateTime.postDelayed(this, 1000);
            }
        };

        this.handlerDateTime.post(runnable);
    }

    /**
     * Método responsável por atualizar a imagem de enquadrado da face (ID: faceFrame)
     */
    public void updateImageView(@DrawableRes int faceFrame) {

        ImageView imageView = findViewById(R.id.faceFrame);

        imageView.setX(mDraw.leftFrame);
        imageView.setY(mDraw.topFrame);

        LayoutParams layoutParams = imageView.getLayoutParams();
        layoutParams.width = Math.round(mDraw.leftFrame + mDraw.rightFrame / 2);
        layoutParams.height = Math.round(mDraw.topFrame + mDraw.bottomFrame) / 2;
        imageView.setLayoutParams(layoutParams);

        imageView.setBackgroundResource(faceFrame);
    }

    public void showErrorAndClose(String error, int code) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(error + ": " + code)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                })
                .show();
    }

    public void showMessage(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                })
                .setCancelable(false) // cancel with button only
                .show();
    }

    private void resetTrackerParameters() {

        int[] errpos = new int[1];

        // Parâmetros de vivacidade
        FSDK.SetTrackerMultipleParameters(mDraw.mTracker,
                "DetectLiveness=true;" + // enable liveness
                        "SmoothAttributeLiveness=true;" + // use smooth minimum function for liveness values
                        "AttributeLivenessSmoothingAlpha=1;" + // smooth minimum parameter, 0 -> mean, inf -> min
                        "LivenessFramesCount=5;", errpos); // minimal number of frames required to output liveness attribute

        if (errpos[0] != FSDK.FSDKE_OK) {
            showErrorAndClose("Error setting tracker parameters 1, position", errpos[0]);
            return;
        }
        //--------------------------

        // Parâmetros de detecção com máscara
        /*FSDK.SetTrackerMultipleParameters(mDraw.mTracker,
                "RecognizeFaces=false;" +
                        "HandleArbitraryRotations=true;" +
                        "DetermineFaceRotationAngle=false;" +
                        "InternalResizeWidth=1024;" +
                        "FaceDetectionThreshold=5;", errpos);

        if (errpos[0] != FSDK.FSDKE_OK) {
            showErrorAndClose("Error setting tracker parameters 2, position", errpos[0]);
            return;
        }

        Log.d("teste", getApplicationInfo().dataDir);

        if (FSDK.FSDKE_OK != FSDK.SetTrackerMultipleParameters(mDraw.mTracker,
                "FaceDetectionModel=fd_masks1.bin;" +
                        "TrimFacesWithUncertainFacialFeatures=false;", errpos))
        {
            showErrorAndClose("Error setting tracker parameters 2 (SET_MODEL), position", errpos[0]);
            return;
        }*/
        //--------------------------

        // Parâmetros deteção
        FSDK.SetTrackerMultipleParameters(mDraw.mTracker,
                "DetectFacialFeatures=true;" +
                        "ContinuousVideoFeed=true;" +
                        "FacialFeatureJitterSuppression=0;" +
                        "RecognitionPrecision=1;" +
                        "Threshold=0.996;" +
                        "Threshold2=0.9995;" +
                        "ThresholdFeed=0.97;" +
                        "MemoryLimit=2000;" +
                        "HandleArbitraryRotations=false;" +
                        "DetermineFaceRotationAngle=true;" +
                        "InternalResizeWidth=256;" +
                        "FaceDetectionThreshold=5;", errpos);
        if (errpos[0] != FSDK.FSDKE_OK) {
            showErrorAndClose("Error setting tracker parameters 2, position", errpos[0]);
            return;
        }
        //--------------------------

        // Parâmetros deteção de características facias
        FSDK.SetTrackerMultipleParameters(mDraw.mTracker,
                "DetectAge=true;" +
                        "DetectGender=true;" +
                        "DetectExpression=true;", errpos);

        if (errpos[0] != FSDK.FSDKE_OK) {
            showErrorAndClose("Error setting tracker parameters 3, position", errpos[0]);
            return;
        }
        //--------------------------

        // Parâmetros detecção de sorriso mais rápida
        FSDK.SetTrackerMultipleParameters(mDraw.mTracker,
                "AttributeExpressionSmileSmoothingSpatial=0.5;" +
                        "AttributeExpressionSmileSmoothingTemporal=10;", errpos);
        if (errpos[0] != FSDK.FSDKE_OK) {
            showErrorAndClose("Error setting tracker parameters 4, position", errpos[0]);
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == getAppResource("helpButton", "id")) {
            String help_text = "Luxand Face Recognition\n\nJust tap any detected face and name it. The app will recognize this face further. For best results, hold the device at arm's length. You may slowly rotate the head for the app to memorize you at multiple views. The app can memorize several persons. If a face is not recognized, tap and name it again.\n\nThe SDK is available for mobile developers: www.luxand.com/facesdk";
            showMessage(help_text);
        } else if (view.getId() == getAppResource("clearButton", "id")) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Are you sure to clear the memory?")
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int j) {
                            pauseProcessingFrames();
                            FSDK.ClearTracker(mDraw.mTracker);
                            resetTrackerParameters();
                            resumeProcessingFrames();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int j) {
                        }
                    })
                    .setCancelable(false) // cancel with button only
                    .show();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        _pause();
    }

    private void _pause() {
        pauseProcessingFrames();
        String templatePath = this.getApplicationInfo().dataDir + "/" + database;
        FSDK.SaveTrackerMemoryToFile(mDraw.mTracker, templatePath);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mIsFailed)
            return;
        resumeProcessingFrames();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        setResult(RESULT_CANCELED);
        _pause();
        finish();
    }

    private void pauseProcessingFrames() {
        mDraw.mStopping = 1;

        // It is essential to limit wait time, because mStopped will not be set to 0, if no frames are feeded to mDraw
        for (int i = 0; i < 100; ++i) {
            if (mDraw.mStopped != 0) break;
            try {
                Thread.sleep(10);
            } catch (Exception ignored) {
            }
        }
    }

    private void resumeProcessingFrames() {
        mDraw.mStopped = 0;
        mDraw.mStopping = 0;
    }

    private int getAppResource(String name, String type) {
        return getResources().getIdentifier(name, type, getPackageName());
    }
}