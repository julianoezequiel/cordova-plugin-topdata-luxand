package com.luxand.dsi;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.luxand.FSDK;
import com.topdata.apptopponto.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class ProcessImageAndDrawResults extends View {
    public FSDK.HTracker mTracker;
    Context mContext;
    private OnImageProcessListener onImageProcessListener;

    final int MAX_FACES = 1;
    int mStopping;
    int mStopped;
    private int registerCheckCount = 0;
    private int loginCount = 0;
    private final int loginTryCount;
    private final int timeOut;
    private long startTime = 0;

    final FaceRectangle[] mFacePositions = new FaceRectangle[MAX_FACES];

    final long[] mIDs = new long[MAX_FACES];
    final Lock faceLock = new ReentrantLock();

    byte[] mYUVData;
    byte[] mRGBData;
    int mImageWidth, mImageHeight;

    boolean rotated;
    boolean isRegister;

    private String template = "";
    private final float livenessParam;
    private final float matchFacesParam;
    private final String templateInit;

    private final OMLLuxand mMainActivity;

    // PARA TESTE
    private float[] similarity = new float[1];
    private float[] liveness = new float[1];

    public static final int ALREADY_REGISTERED = 1;
    public static final int REGISTERED = 2;
    public static final int NOT_REGISTERED = 3;

    public float leftFrame;
    public float topFrame;
    public float rightFrame;
    public float bottomFrame;

    public ProcessImageAndDrawResults(OMLLuxand context, boolean isRegister, int loginTryCount, int timeOut, String templateRef, float livenessParam, float matchFacesParam) {
        super(context);

        this.timeOut = timeOut;
        this.templateInit = templateRef;
        this.livenessParam = livenessParam;
        this.matchFacesParam = matchFacesParam;

        this.mMainActivity = context;

        this.isRegister = isRegister;
        this.loginTryCount = loginTryCount;

        mStopping = 0;
        mStopped = 0;
        rotated = false;
        mContext = context;

        mYUVData = null;
        mRGBData = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onDraw(Canvas canvas) {

        if (this.startTime <= 0) {
            this.startTime = System.currentTimeMillis();

            // FRAME FIXO
            this.leftFrame = getLeft() + (getRight() - getLeft()) / 5;
            this.topFrame = getTop() + (getBottom() - getTop()) / 6;
            this.rightFrame = getRight() - (getRight() - getLeft()) / 5;
            this.bottomFrame = getBottom() - (getBottom() - getTop()) / 2;

            mMainActivity.updateImageView(R.drawable.frame_branco);
        }

        if (mYUVData == null) {
            super.onDraw(canvas);
            return; //nothing to process or name is being entered now
        }

        int canvasWidth = getWidth();
        //int canvasHeight = canvas.getHeight();

        // Convert from YUV to RGB
        decodeYUV420SP(mRGBData, mYUVData, mImageWidth, mImageHeight);

        // Load image to FaceSDK
        FSDK.HImage Image = new FSDK.HImage();
        FSDK.FSDK_IMAGEMODE imageMode = new FSDK.FSDK_IMAGEMODE();
        imageMode.mode = FSDK.FSDK_IMAGEMODE.FSDK_IMAGE_COLOR_24BIT;
        FSDK.LoadImageFromBuffer(Image, mRGBData, mImageWidth, mImageHeight, mImageWidth * 3, imageMode);
        FSDK.MirrorImage(Image, false);
        FSDK.HImage RotatedImage = new FSDK.HImage();
        FSDK.CreateEmptyImage(RotatedImage);

        //it is necessary to work with local variables (onDraw called not the time when mImageWidth,
        // being reassigned, so swapping mImageWidth and mImageHeight may be not safe)
        int ImageWidth = mImageWidth;
        //int ImageHeight = mImageHeight;

        if (rotated) {
            ImageWidth = mImageHeight;
            //ImageHeight = mImageWidth;
            FSDK.RotateImage90(Image, -1, RotatedImage);
        } else {
            FSDK.CopyImage(Image, RotatedImage);
        }

        FSDK.FreeImage(Image);

        long[] IDs = new long[MAX_FACES];
        long[] face_count = new long[1];

        int resp = FSDK.FeedFrame(mTracker, 0, RotatedImage, face_count, IDs);

        faceLock.lock();

        if (timeOut != -1 && System.currentTimeMillis() - this.startTime > timeOut && mStopping == 0) {

            FSDK.FreeImage(RotatedImage);

            // Timeout exception
            mStopping = 1;

            /*mMainActivity.updateTextView("LIVENESS: " + this.liveness[0] + " \nMATCH_FACES: " + this.similarity[0]);
            new Handler().postDelayed(new Runnable() {
                public void run() {*/
            Log.e("com.luxand", "TIME OUT");
            response(true, "DETECTION_TIMEOUT", "");
            return;
                /*}
            }, 4000);*/
        }

        if (mStopping == 1) {
            FSDK.FreeImage(RotatedImage);
            mStopped = 1;
            super.onDraw(canvas);
            return;
        } //else {

        for (int i = 0; i < MAX_FACES; ++i) {
            mFacePositions[i] = new FaceRectangle();
            mFacePositions[i].x1 = 0;
            mFacePositions[i].y1 = 0;
            mFacePositions[i].x2 = 0;
            mFacePositions[i].y2 = 0;
            mIDs[i] = IDs[i];

            if (mStopping == 0)
                mMainActivity.updateTextView(Constants.ENQUADRE_ROSTO);
        }

        if (face_count[0] <= 0) {
            FSDK.FreeImage(RotatedImage);
            return;
        }

        float ratio = (canvasWidth * 1.0f) / ImageWidth;

        for (int i = 0; i < (int) face_count[0]; ++i) {
            // OLHOS
            FSDK.FSDK_Features Eyes = new FSDK.FSDK_Features();
            FSDK.GetTrackerEyes(mTracker, 0, mIDs[i], Eyes);

            GetFaceFrame(Eyes, mFacePositions[i]);
            mFacePositions[i].x1 *= ratio;
            mFacePositions[i].y1 *= ratio;
            mFacePositions[i].x2 *= ratio;
            mFacePositions[i].y2 *= ratio;
        }

        faceLock.unlock();

        // A FACE ESTÁ ENQUADRADA NO FRAME && EXISTE APENAS UMA FACE DETECTADA
        Log.e("com.luxand", "FACE_COUNT - " + face_count[0]);
        Log.e("com.luxand", "FACE_ENQUADRADA - " + faceEnquadrada());

        if (faceEnquadrada() && face_count[0] == 1 && mStopping == 0) {

            mMainActivity.updateImageView(R.drawable.frame_amarelo);

            String[] value = new String[1];
            liveness = new float[1];

            int res = FSDK.GetTrackerFacialAttribute(mTracker, 0, IDs[0], "Liveness", value, 1024);
            if (res == FSDK.FSDKE_OK) {
                FSDK.GetValueConfidence(value[0], "Liveness", liveness);
            }

            float livenessMaior = 0f;

            livenessMaior = Math.max(liveness[0], livenessMaior);
            Log.d("com.luxand", "LIVENESS_MAIOR - " + livenessMaior);
            Log.d("com.luxand", "LIVENESS_PARAM - " + this.livenessParam);

            if (liveness[0] > this.livenessParam) {
                Log.d("com.luxand", "LIVE");

                if (!this.isRegister) {
                    // COMPARAR FACES
                    boolean identified = false;
                    if (face_count[0] > 1) {
                        if (loginCount < loginTryCount) {
                            Toast.makeText(getContext(), "Múltiplas faces detectada...", Toast.LENGTH_LONG).show();
                            //return;
                        }
                    } else if (faceEnquadrada() && face_count[0] == 1) {

                        // Mark and name faces
                        for (int i = 0; i < face_count[0]; ++i) {
                            mMainActivity.updateImageView(R.drawable.frame_amarelo);
                            identified = compararTemplates(RotatedImage);
                            FSDK.FreeImage(RotatedImage);
                        }

                        if (this.loginCount <= loginTryCount && identified) {
                            mStopping = 1;
                            mMainActivity.updateTextView(Constants.SUCESSO_RECONHECIMENTO);
                            mMainActivity.updateImageView(R.drawable.frame_verde);

                            new Handler().postDelayed(new Runnable() {
                                public void run() {
                                    Log.e("com.luxand", "FACE_EQUALS");
                                    response(false, "FACE_EQUALS", template);
                                    //return;
                                }
                            }, 1000);
                        }

                        this.loginCount++;

                        if (this.loginCount >= loginTryCount) {

                            mStopping = 1;
                                /*mMainActivity.updateTextView("LIVENESS: " + this.liveness[0] + " \nMATCH_FACES: " + this.similarity[0]);
                                new Handler().postDelayed(new Runnable() {
                                    public void run() {*/
                            Log.e("com.luxand", "FAIL_COMPARE");
                            response(true, "FAIL_COMPARE", "");
                            return;
                                    /*}
                                }, 4000);*/
                        }
                    }
                } else {
                    // REGISTRAR FACE
                    if (face_count[0] > 1) {
                        if (registerCheckCount < loginTryCount) {
                            Toast.makeText(getContext(), "Múltiplas faces detectada...", Toast.LENGTH_LONG).show();
                            //return;
                        }
                    } else if (faceEnquadrada() && face_count[0] == 1) {

                        registerCheckCount++;
                        mMainActivity.updateImageView(R.drawable.frame_amarelo);

                        // Face registrada
                        boolean ok = getTemplate(RotatedImage);

                        if (!ok) {
                            FSDK.FreeImage(RotatedImage);
                            mStopping = 1;

                                    /*mMainActivity.updateTextView("LIVENESS: " + this.liveness[0] + " \nMATCH_FACES: " + this.similarity[0]);
                                    new Handler().postDelayed(new Runnable() {
                                        public void run() {*/
                            Log.e("com.luxand", "ERROR_GET_TEMPLATE");
                            response(true, "ERROR_GET_TEMPLATE", "");
                            return;
                                        /*}
                                    }, 4000);*/
                        }

                        FSDK.FreeImage(RotatedImage);

                        mStopping = 1;
                        mMainActivity.updateTextView(Constants.SUCESSO_RECONHECIMENTO);

                        mMainActivity.updateImageView(R.drawable.frame_verde);

                        new Handler().postDelayed(new Runnable() {
                            public void run() {
                                Log.e("com.luxand", "REGISTERED");
                                response(false, "REGISTERED", template);
                                //return;
                            }
                        }, 1000);
                    }
                }

            } else {
                Log.d("com.luxand", "FAKE - " + liveness[0]);
                FSDK.FreeImage(RotatedImage);
                return;
            }
        } else {
            FSDK.FreeImage(RotatedImage);
            if (mStopping == 0) {
                mMainActivity.updateTextView(Constants.ENQUADRE_ROSTO);
                mMainActivity.updateImageView(R.drawable.frame_branco);
            }

            return;
        }

        FSDK.FreeImage(RotatedImage);

        super.onDraw(canvas);
    } // end onDraw method

    private boolean faceEnquadrada() {

        int margemAcerto = 150;
        float leftAcerto = leftFrame - margemAcerto;
        float topAcerto = topFrame - margemAcerto;
        float rightAcerto = rightFrame + margemAcerto;
        float bottomAcerto = bottomFrame + margemAcerto;

        return (mFacePositions[0].x1 >= leftAcerto) &&
                (mFacePositions[0].y1 >= topAcerto) &&
                (mFacePositions[0].x2 <= rightAcerto) &&
                (mFacePositions[0].y2 <= bottomAcerto);
    }

    /**
     * Monta o objeto de resposta que vai do plugin para o front
     *
     * @param error    Resposta com erro ou não
     * @param message  Mensagem de resposta
     * @param template Template da face detectada
     */
    private void response(boolean error, String message, String template) {
        FSDK.FreeTracker(mTracker);

        JSONObject obj = new JSONObject();
        try {
            obj.put("error", error);
            obj.put("message", message);
            obj.put("template", template);
        } catch (JSONException e) {
            e.printStackTrace();
            obj = new JSONObject();
        }
        if (this.onImageProcessListener != null) {
            this.onImageProcessListener.handle(obj);
        }

    }

    /**
     * Busca o template de acordo com a face detectada
     *
     * @param imagem Imagem detectada
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean getTemplate(FSDK.HImage imagem) {

        FSDK.FSDK_FaceTemplate FaceTemplate = new FSDK.FSDK_FaceTemplate();

        // Busca o template
        int ok = FSDK.GetFaceTemplate(imagem, FaceTemplate);
        this.template = Base64.encodeToString(FaceTemplate.template, Base64.DEFAULT);

        // Limpa a imagem
        FSDK.FreeImage(imagem);

        return ok == FSDK.FSDKE_OK;
    }

    /**
     * @return True se o template da face detectada é igual ao template original
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private Boolean compararTemplates(FSDK.HImage imagem) {

        // Decodifica o template de referência: base64 -> array de bytes
        byte[] templateBytes = Base64.decode(this.templateInit, Base64.DEFAULT);
        FSDK.FSDK_FaceTemplate FaceTemplateRef = new FSDK.FSDK_FaceTemplate();
        FaceTemplateRef.template = templateBytes;

        // Busca o template a partir da imagem detectada
        FSDK.FSDK_FaceTemplate FaceTemplateDetected = new FSDK.FSDK_FaceTemplate();
        FSDK.GetFaceTemplate(imagem, FaceTemplateDetected);

        // Codifica o template da face detectada: array de bytes -> base64
        this.template = Base64.encodeToString(FaceTemplateDetected.template, Base64.DEFAULT);

        this.similarity = new float[1];
        FSDK.MatchFaces(FaceTemplateDetected, FaceTemplateRef, this.similarity);

        // Limpa a imagem
        FSDK.FreeImage(imagem);

        Log.d("com.luxand", "MATCH_FACES_PARAM - " + this.matchFacesParam);

        // As faces são iguais?
        return this.similarity[0] > this.matchFacesParam;
    }

    void GetFaceFrame(FSDK.FSDK_Features Features, FaceRectangle fr) {
        if (Features == null || fr == null)
            return;

        float u1 = Features.features[0].x;
        float v1 = Features.features[0].y;
        float u2 = Features.features[1].x;
        float v2 = Features.features[1].y;
        float xc = (u1 + u2) / 2;
        float yc = (v1 + v2) / 2;
        int w = (int) Math.pow((u2 - u1) * (u2 - u1) + (v2 - v1) * (v2 - v1), 0.5);

        fr.x1 = (int) (xc - w * 1.6 * 0.9);
        fr.y1 = (int) (yc - w * 1.1 * 0.9);
        fr.x2 = (int) (xc + w * 1.6 * 0.9);
        fr.y2 = (int) (yc + w * 2.1 * 0.9);
        if (fr.x2 - fr.x1 > fr.y2 - fr.y1) {
            fr.x2 = fr.x1 + fr.y2 - fr.y1;
        } else {
            fr.y2 = fr.y1 + fr.x2 - fr.x1;
        }
    }

    public void setOnImageProcessListener(OnImageProcessListener onImageProcessListener) {
        this.onImageProcessListener = onImageProcessListener;
    }

    static public void decodeYUV420SP(byte[] rgb, byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;
        int yp = 0;
        for (int j = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);
                if (r < 0) r = 0;
                else if (r > 262143) r = 262143;
                if (g < 0) g = 0;
                else if (g > 262143) g = 262143;
                if (b < 0) b = 0;
                else if (b > 262143) b = 262143;

                rgb[3 * yp] = (byte) ((r >> 10) & 0xff);
                rgb[3 * yp + 1] = (byte) ((g >> 10) & 0xff);
                rgb[3 * yp + 2] = (byte) ((b >> 10) & 0xff);
                ++yp;
            }
        }
    }
}