package com.luxand.dsi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.luxand.FSDK;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class ProcessImageAndDrawResults extends View {
    public FSDK.HTracker mTracker;
    Context mContext;
    private OnImageProcessListener onImageProcessListener;

    final int MAX_FACES = 1;
    int mTouchedIndex;
    int mStopping;
    int mStopped;
    private int registerCheckCount = 0;
    private int loginCount = 0;
    private int loginTryCount = 4;
    private int timeOut;
    private long correspondingId;
    private long startTime = 0;

    Paint mPaintGreen, mPaintBlue, mPaintBlueTransparent;

    final FaceRectangle[] mFacePositions = new FaceRectangle[MAX_FACES];

    final long[] mIDs = new long[MAX_FACES];
    final Lock faceLock = new ReentrantLock();

    byte[] mYUVData;
    byte[] mRGBData;
    int mImageWidth, mImageHeight;

    boolean rotated;
    boolean isRegister;
    private boolean identified = false;

    private String generatedName;
    private String name;
    private String template = "";
    private String templateInit;

    public static final int ALREADY_REGISTERED = 1;
    public static final int REGISTERED = 2;
    public static final int NOT_REGISTERED = 3;

    public ProcessImageAndDrawResults(Context context, boolean isRegister, int loginTryCount, int timeOut, String templateRef) {
        super(context);
        this.timeOut = timeOut;
        this.templateInit = templateRef;

        this.isRegister = isRegister;
        this.loginTryCount = loginTryCount;
        mTouchedIndex = -1;

        mStopping = 0;
        mStopped = 0;
        rotated = false;
        mContext = context;
        mPaintGreen = new Paint();
        mPaintGreen.setStyle(Paint.Style.FILL);
        mPaintGreen.setColor(Color.GREEN);
        mPaintGreen.setTextSize(18 * Constants.sDensity);
        mPaintGreen.setTextAlign(Paint.Align.CENTER);
        mPaintBlue = new Paint();
        mPaintBlue.setStyle(Paint.Style.FILL);
        mPaintBlue.setColor(Color.BLUE);
        mPaintBlue.setTextSize(18 * Constants.sDensity);
        mPaintBlue.setTextAlign(Paint.Align.CENTER);

        mPaintBlueTransparent = new Paint();
        mPaintBlueTransparent.setStyle(Paint.Style.STROKE);
        mPaintBlueTransparent.setStrokeWidth(2);
        mPaintBlueTransparent.setColor(Color.argb(255, 255, 200, 0));
        mPaintBlueTransparent.setTextSize(25);

        mYUVData = null;
        mRGBData = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onDraw(Canvas canvas) {
        if (this.startTime <= 0) {
            this.startTime = System.currentTimeMillis();
        }
        if (timeOut != -1 && System.currentTimeMillis() - this.startTime > timeOut) {
            // Timeout exception
            response(true, "DETECTION_TIMEOUT", "");
            return;
        }
        if (mStopping == 1) {
            mStopped = 1;
            super.onDraw(canvas);
            return;
        }

        if (mYUVData == null || mTouchedIndex != -1) {
            super.onDraw(canvas);
            return; //nothing to process or name is being entered now
        }

        int canvasWidth = getWidth();
        //int canvasHeight = canvas.getHeight();

        // Convert from YUV to RGB
        decodeYUV420SP(mRGBData, mYUVData, mImageWidth, mImageHeight);

        // Load image to FaceSDK
        FSDK.HImage Image = new FSDK.HImage();
        FSDK.FSDK_IMAGEMODE imagemode = new FSDK.FSDK_IMAGEMODE();
        imagemode.mode = FSDK.FSDK_IMAGEMODE.FSDK_IMAGE_COLOR_24BIT;
        FSDK.LoadImageFromBuffer(Image, mRGBData, mImageWidth, mImageHeight, mImageWidth * 3, imagemode);
        FSDK.MirrorImage(Image, false);
        FSDK.HImage RotatedImage = new FSDK.HImage();
        FSDK.CreateEmptyImage(RotatedImage);

        //it is necessary to work with local variables (onDraw called not the time when mImageWidth,... being reassigned, so swapping mImageWidth and mImageHeight may be not safe)
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

        long IDs[] = new long[MAX_FACES];
        long face_count[] = new long[1];

        FSDK.FeedFrame(mTracker, 0, RotatedImage, face_count, IDs);
        //FSDK.FreeImage(RotatedImage);

        faceLock.lock();

        for (int i = 0; i < MAX_FACES; ++i) {
            mFacePositions[i] = new FaceRectangle();
            mFacePositions[i].x1 = 0;
            mFacePositions[i].y1 = 0;
            mFacePositions[i].x2 = 0;
            mFacePositions[i].y2 = 0;
            mIDs[i] = IDs[i];
        }
        if (face_count[0] <= 0) {
            return;
        }

        this.startTime = System.currentTimeMillis();

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

        String[] value = new String[1];
        float[] liveness = new float[1];

        int res = FSDK.GetTrackerFacialAttribute(mTracker, 0, IDs[0], "Liveness", value, 1024);
        if (res == FSDK.FSDKE_OK) {
            res = FSDK.GetValueConfidence(value[0], "Liveness", liveness);
        }

        if (liveness[0] > 0.999f) {
            Log.d("LIVENESS", "Está vivo");

            if (!this.isRegister) {
                // COMPARAR FACES
                identified = false;
                if (face_count[0] > 1) {
                    if (loginCount < loginTryCount) {
                        Toast.makeText(getContext(), "Múltiplas faces detectada...", Toast.LENGTH_LONG).show();
                        //return;
                    }
                } else if (face_count[0] == 1) {
                    // Mark and name faces
                    for (int i = 0; i < face_count[0]; ++i) {
                        canvas.drawRect(mFacePositions[i].x1, mFacePositions[i].y1, mFacePositions[i].x2, mFacePositions[i].y2, mPaintBlueTransparent);
                        identified = identified || compararTemplates(RotatedImage);
                    }
                    Log.e("com.luxand.dsi.Ident", identified + "");

                    if (this.loginCount <= loginTryCount && this.identified) {
                        correspondingId = IDs[0];
                        response(false, "FACE_EQUALS", template);
                        mStopping = 1;
                        return;
                    }

                    this.loginCount++;

                    if (this.loginCount >= loginTryCount) {
                        response(true, "FAIL_COMPARE", "");
                        mStopping = 1;
                        return;
                    }
                }
            } else {
                // REGISTRAR FACE
                if (face_count[0] > 1) {
                    if (registerCheckCount < loginTryCount) {
                        Toast.makeText(getContext(), "Múltiplas faces detectada...", Toast.LENGTH_LONG).show();
                        //return;
                    }
                } else if (face_count[0] == 1) {
                    if (registerCheckCount < 1) {
                        canvas.drawRect(mFacePositions[0].x1, mFacePositions[0].y1, mFacePositions[0].x2, mFacePositions[0].y2, mPaintBlueTransparent);

                        // Tenta realizar o cadastro da face
                        int r = this.register(IDs[0], RotatedImage);

                        if (r == REGISTERED) {
                            registerCheckCount = 1;
                        } else if (r == ALREADY_REGISTERED) {
                            response(true, "ALREADY_REGISTERED", "");
                            mStopping = 1;
                            return;
                        }
                    } else {
                        String name = this.performRegistrationAgain(IDs[0]);
                        Log.e("com.luxand.dsi::", name);

                        // Falha ao cadastrar/encontrar a face
                        if (name == null || !name.equals(generatedName)) {
                            //purge id
                            remove(IDs[0]);
                            response(true, "FAIL_REGISTRATION", "");
                            mStopping = 1;
                            return;
                        }

                        registerCheckCount++;
                        canvas.drawRect(mFacePositions[0].x1, mFacePositions[0].y1, mFacePositions[0].x2, mFacePositions[0].y2, mPaintBlueTransparent);

                        // Face cadastrada com sucesso
                        if (registerCheckCount >= loginTryCount) {
                            boolean ok = getTemplate(RotatedImage);

                            if (!ok) {
                                response(true, "ERROR_GET_TEMPLATE", "");
                                mStopping = 1;
                                return;
                            }

                            this.name = name;
                            this.correspondingId = IDs[0];

                            response(false, "REGISTERED", template);
                            mStopping = 1;
                            return;
                        }
                    }
                }
            }

        } else {
            Log.d("LIVENESS", "Não está vivo " + liveness[0]);
        }

        super.onDraw(canvas);
    } // end onDraw method

    /**
     * Monta o objeto de resposta que vai do plugin para o front
     *
     * @param error    Resposta com erro ou não
     * @param message  Mensagem de resposta
     * @param template Template da face detectada
     */
    private void response(boolean error, String message, String template) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("error", error);
            obj.put("message", message);
            obj.put("name", name);
            obj.put("id", this.correspondingId);
            obj.put("template", template);
        } catch (JSONException e) {
            e.printStackTrace();
            obj = new JSONObject();
        }
        if (this.onImageProcessListener != null) {
            this.onImageProcessListener.handle(obj);
        }
    }

    private String generateName() {
        return "OML-LUXAND" + new Date().getTime();
    }

    private boolean remove(long id) {
        FSDK.LockID(mTracker, id);
        int ok = FSDK.PurgeID(mTracker, id);
        FSDK.UnlockID(mTracker, id);
        return ok == FSDK.FSDKE_OK;
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
        this.template = Base64.getEncoder().encodeToString(FaceTemplate.template);

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
        FSDK.HTracker tracker = new FSDK.HTracker();
        byte[] templateBytes = Base64.getDecoder().decode(this.templateInit);
        FSDK.FSDK_FaceTemplate FaceTemplateRef = new FSDK.FSDK_FaceTemplate();
        FaceTemplateRef.template = templateBytes;

        // Busca o template a partir da imagem detectada
        FSDK.FSDK_FaceTemplate FaceTemplateDetected = new FSDK.FSDK_FaceTemplate();
        int ok = FSDK.GetFaceTemplate(imagem, FaceTemplateDetected);

        // Codifica o template da face detectada: array de bytes -> base64
        this.template = Base64.getEncoder().encodeToString(FaceTemplateDetected.template);

        float[] similarity = new float[1];
        FSDK.MatchFaces(FaceTemplateDetected, FaceTemplateRef, similarity);

        // Limpa a imagem
        FSDK.FreeImage(imagem);

        // As faces são iguais?
        return similarity[0] > 0.95f ? true : false;
    }

    private String performRegistrationAgain(long id) {
        FSDK.LockID(mTracker, id);
        String names[] = new String[1];
        FSDK.GetAllNames(mTracker, id, names, 1024);
        FSDK.UnlockID(mTracker, id);

        if (names[0] != null && names[0].length() > 0) {
            return names[0];
        } else {
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private int register(long id, FSDK.HImage imagem) {

        Boolean match = compararTemplates(imagem);

        if (match) {
            return ALREADY_REGISTERED;
        }

        FSDK.LockID(mTracker, id);
        String userName = generateName();
        generatedName = userName;

        boolean r;
        r = FSDK.SetName(mTracker, id, userName) == FSDK.FSDKE_OK;

        if (userName.length() <= 0) {
            r = false;
            FSDK.PurgeID(mTracker, id);
        }

        FSDK.UnlockID(mTracker, id);
        return r ? REGISTERED : NOT_REGISTERED;
    }

    int GetFaceFrame(FSDK.FSDK_Features Features, FaceRectangle fr) {
        if (Features == null || fr == null)
            return FSDK.FSDKE_INVALID_ARGUMENT;

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
        return 0;
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