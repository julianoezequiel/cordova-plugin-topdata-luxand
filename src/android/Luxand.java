
package com.luxand.dsi;

import org.apache.cordova.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import android.util.Log;

import com.luxand.FSDK;

public class Luxand extends CordovaPlugin {

    private static final int COMPARE_CODE = 3;
    private static final int REGISTER_CODE = 2;

    private static final String LOG_TAG = "com.luxand.oml.dsi";

    private CallbackContext callbackContext;

    private final String[] permissions = {Manifest.permission.CAMERA};

    private String dbName;
    private static int loginTryCount;
    private JSONArray reqArgs;
    String template = "";
    float livenessParam = 0f;
    float matchFacesParam = 0f;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        this.reqArgs = data;

        switch (action) {
            case "init":
                init();
                return true;
            case "register":
                template = reqArgs.getString(1);
                livenessParam = (float) reqArgs.getDouble(2);
                matchFacesParam = (float) reqArgs.getDouble(3);

                if (!hasPermisssion()) {
                    requestPermissions(REGISTER_CODE);
                } else {
                    startCamera(REGISTER_CODE, callbackContext);
                }

                return true;
            case "compare":
                template = reqArgs.getString(1);
                livenessParam = (float) reqArgs.getDouble(2);
                matchFacesParam = (float) reqArgs.getDouble(3);

                if (!hasPermisssion()) {
                    requestPermissions(COMPARE_CODE);
                } else {
                    startCamera(COMPARE_CODE, callbackContext);
                }

                return true;
            case "clearMemory": {
                boolean r = clear();

                JSONObject resData = new JSONObject();
                resData.put("status", r ? "SUCCESS" : "FAIL");

                callbackContext.success(resData);
                return true;
            }
            case "clear": {
                long id = (Long) data.get(0);

                boolean r = clearName(id);

                JSONObject resData = new JSONObject();
                resData.put("status", r ? "SUCCESS" : "FAIL");

                callbackContext.success(resData);
                return true;
            }
            default:
                return false;
        }
    }

    /**
     * Inicializa o FaceSDK de acordo com a chave de acesso
     */
    private void init() throws JSONException {
        String licence = reqArgs.getString(0);
        dbName = reqArgs.getString(1);
        loginTryCount = reqArgs.getInt(2);

        Log.d("com.luxand.dsi", licence);

        int res = FSDK.ActivateLibrary(licence);

        if (res != FSDK.FSDKE_OK) {
            callbackContext.error("Falha ao inicializar o FaceSDK " + res);
        } else {
            FSDK.Initialize();
            callbackContext.success("Inicialização realizada com sucesso");
        }
    }

    /**
     * Realiza a limpeza na base local por ID
     *
     * @param id ID a ser limpo na base local
     * @return Resposta com erro ou não
     */
    private boolean clearName(long id) {
        FSDK.HTracker tracker = new FSDK.HTracker();
        String templatePath = this.cordova.getActivity().getApplicationInfo().dataDir + "/" + dbName;

        if (FSDK.FSDKE_OK != FSDK.LoadTrackerMemoryFromFile(tracker, templatePath)) {
            int res = FSDK.CreateTracker(tracker);

            if (FSDK.FSDKE_OK != res) {
                return false;
            }
        }

        FSDK.LockID(tracker, id);
        int ok = FSDK.PurgeID(tracker, id);
        FSDK.UnlockID(tracker, id);

        if (FSDK.FSDKE_OK != ok) {
            return false;
        }

        ok = FSDK.SaveTrackerMemoryToFile(tracker, templatePath);

        return ok == FSDK.FSDKE_OK;
    }

    /**
     * Realiza a limpeza de todos os dados na base local
     *
     * @return Resposta com erro ou não
     */
    private boolean clear() {
        FSDK.HTracker tracker = new FSDK.HTracker();
        String templatePath = this.cordova.getActivity().getApplicationInfo().dataDir + "/" + dbName;

        Log.d("com.luxand.dsi", "DBName" + dbName);

        if (FSDK.FSDKE_OK != FSDK.LoadTrackerMemoryFromFile(tracker, templatePath)) {
            Log.e("com.luxand.dsi", "Tracker not loaded from memory");

            int res = FSDK.CreateTracker(tracker);

            if (FSDK.FSDKE_OK != res) {
                Log.e("com.luxand.dsi", "Tracker not loaded created");
                return false;
            } else {
                Log.d("com.luxand.dsi", "Tracker created");
            }

        } else {
            Log.d("com.luxand.dsi", "Tracker loaded from memory");
        }

        int ok = FSDK.ClearTracker(tracker);

        if (FSDK.FSDKE_OK != ok) {
            return false;
        }

        ok = FSDK.SaveTrackerMemoryToFile(tracker, templatePath);

        return ok == FSDK.FSDKE_OK;
    }

    /**
     * Inicializa a câmera para REGISTRAR ou COMPARAR faces
     *
     * @param requestCode Tipo de solicitação (REGISTRAR ou COMPARAR)
     */
    private void startCamera(int requestCode, CallbackContext callbackContext) {

        int timeOut = 15 * 1000;

        if (reqArgs.length() >= 1) {
            try {
                timeOut = reqArgs.getInt(0);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        cordova.setActivityResultCallback(this);
        keepCallback(callbackContext);
        openNewActivity(cordova.getActivity(), requestCode == REGISTER_CODE ? REGISTER_CODE : COMPARE_CODE, timeOut);
    }

    private void keepCallback(CallbackContext callbackContext) {
        PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
        r.setKeepCallback(true);
        callbackContext.sendPluginResult(r);
    }

    /**
     * Seta os parâmetros a serem utilizados na classe OMLLuxand
     */
    private void openNewActivity(Context context, int REQUEST_CODE, int timeOut) {
        Intent intent = new Intent(context, OMLLuxand.class);

        intent.putExtra("DB_NAME", dbName);
        intent.putExtra("LOGIN_TRY_COUNT", loginTryCount);
        intent.putExtra("TIMEOUT", timeOut);

        if (REQUEST_CODE == REGISTER_CODE || REQUEST_CODE == COMPARE_CODE) {
            String requestType = REQUEST_CODE == REGISTER_CODE ? "FOR_REGISTER" : "FOR_LOGIN";

            Log.d("com.luxand", "LIVENESS_PARAM -> " + livenessParam);
            Log.d("com.luxand", "MATCH_FACES_PARAM -> " + matchFacesParam);

            intent.putExtra("TYPE", requestType);
            intent.putExtra("TEMPLATE", template);
            intent.putExtra("LIVENESS_PARAM", livenessParam);
            intent.putExtra("MATCH_FACES_PARAM", matchFacesParam);
        }

        cordova.startActivityForResult(this, intent, REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) return;
        Log.d("com.luxand.dsi::", requestCode + ":" + resultCode);
        if (requestCode == COMPARE_CODE) {
            if (resultCode == Activity.RESULT_OK && data.hasExtra("data")) {
                //JSONObject res = new JSONObject();
                try {
                    JSONObject resData = new JSONObject(data.getStringExtra("data"));
                    //boolean error = data.getBooleanExtra("error", true);
                    boolean error = resData.getBoolean("error");
                    resData.put("status", error ? "FAIL" : "SUCCESS");
                    Log.d("com.luxand.dsi::", "" + resData);
                    PluginResult result = new PluginResult(PluginResult.Status.OK, resData);

                    result.setKeepCallback(true);
                    this.callbackContext.sendPluginResult(result);

                } catch (JSONException e) {
                    e.printStackTrace();
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR);
                    result.setKeepCallback(true);
                    this.callbackContext.sendPluginResult(result);
                }
            } else {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Unable to identify user");
                result.setKeepCallback(true);
                this.callbackContext.sendPluginResult(result);
            }
        } else if (requestCode == REGISTER_CODE) {
            if (resultCode == Activity.RESULT_OK && data.hasExtra("data")) {
                try {
                    JSONObject resData = new JSONObject(data.getStringExtra("data"));
                    //boolean error = data.getBooleanExtra("error", true);
                    boolean error = resData.getBoolean("error");
                    resData.put("status", error ? "FAIL" : "SUCCESS");
                    Log.d("com.luxand.dsi::", "" + resData);
                    PluginResult result = new PluginResult(PluginResult.Status.OK, resData);

                    result.setKeepCallback(true);
                    this.callbackContext.sendPluginResult(result);

                } catch (JSONException e) {
                    e.printStackTrace();
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR);
                    result.setKeepCallback(true);
                    this.callbackContext.sendPluginResult(result);
                }
            } else {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Unable to identify user");
                result.setKeepCallback(true);
                this.callbackContext.sendPluginResult(result);
            }
        }
    }

    /**
     * check application's permissions
     */
    public boolean hasPermisssion() {
        for (String p : permissions) {
            if (!PermissionHelper.hasPermission(this, p)) {
                return false;
            }
        }
        return true;
    }

    /**
     * We override this so that we can access the permissions variable, which no longer exists in
     * the parent class, since we can't initialize it reliably in the constructor!
     *
     * @param requestCode The code to get request action
     */
    public void requestPermissions(int requestCode) {
        PermissionHelper.requestPermissions(this, requestCode, permissions);
    }

    /**
     * processes the result of permission request
     *
     * @param requestCode  The code to get request action
     * @param permissions  The collection of permissions
     * @param grantResults The result of grant
     */
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        PluginResult result;
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                Log.d(LOG_TAG, "Permission Denied!");
                result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
                this.callbackContext.sendPluginResult(result);
                return;
            }
        }
        startCamera(requestCode, this.callbackContext);
    }

}