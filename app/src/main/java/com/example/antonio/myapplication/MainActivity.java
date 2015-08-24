package com.example.antonio.myapplication;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.plus.People.LoadPeopleResult;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;
import com.google.android.gms.plus.model.people.PersonBuffer;
import com.neurosky.thinkgear.TGDevice;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class MainActivity extends Activity implements
        ConnectionCallbacks, OnConnectionFailedListener, View.OnClickListener {

    private static final String TAG = "android-plus-quickstart";

    private static final int STATE_DEFAULT = 0;
    private static final int STATE_SIGN_IN = 1;
    private static final int STATE_IN_PROGRESS = 2;

    private static final int RC_SIGN_IN = 0;

    private static final String SAVED_PROGRESS = "sign_in_progress";

    private GoogleApiClient mGoogleApiClient;

    // We use mSignInProgress to track whether user has clicked sign in.
    // mSignInProgress can be one of three values:
    //
    //       STATE_DEFAULT: The default state of the application before the user
    //                      has clicked 'sign in', or after they have clicked
    //                      'sign out'.  In this state we will not attempt to
    //                      resolve sign in errors and so will display our
    //                      Activity in a signed out state.
    //       STATE_SIGN_IN: This state indicates that the user has clicked 'sign
    //                      in', so resolve successive errors preventing sign in
    //                      until the user has successfully authorized an account
    //                      for our app.
    //   STATE_IN_PROGRESS: This state indicates that we have started an intent to
    //                      resolve an error, and so we should not start further
    //                      intents until the current intent completes.
    private int mSignInProgress;
    private PendingIntent mSignInIntent;
    private int mSignInError;
    private SignInButton mSignInButton;
    private Button mSignOutButton;
    //private TextView mStatus;



    //Del proyecto de lector de EEG

    BluetoothAdapter bluetoothAdapter;
    int pestaneos = 0;
    private String URL_NEW_DATA = "http://192.168.1.37/new_data.php";

    //TextView tv;
    TextView txtPestaneos, txtConcentracion, txtMeditacion,txtTime,perBarCon,perBarMed;

    TextView txtEstado;
    Integer atention=0;
    Integer meditation=0;

    Date time=null;
    long seg=0;
    List<String> evolCon=new ArrayList<>();
    List<String> evolMed=new ArrayList<>();
    private static final int PROGRESS = 0x1;

    private ProgressBar mProgressCon;
    private ProgressBar mProgressMed;
    private int mProgressStatusCon = 0;
    private int mProgressStatusMed = 0;
    boolean hilo=true;

    private Handler mHandler = new Handler();

    TGDevice tgDevice;
    final boolean rawEnabled = true;




    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSignInButton = (SignInButton) findViewById(R.id.sign_in_button);
        mSignOutButton = (Button) findViewById(R.id.sign_out_button);
        //mStatus = (TextView) findViewById(R.id.sign_in_status);
        mSignInButton.setOnClickListener(this);
        mSignOutButton.setOnClickListener(this);



        txtEstado = (TextView)findViewById(R.id.txtEstado);
        txtPestaneos = (TextView)findViewById(R.id.txtPestaneos);
        txtConcentracion = (TextView)findViewById(R.id.txtConcentracion);
        txtMeditacion = (TextView)findViewById(R.id.txtMeditacion);
        txtTime = (TextView)findViewById(R.id.txtTime);
        mProgressCon = (ProgressBar) findViewById(R.id.barCon);
        mProgressMed = (ProgressBar) findViewById(R.id.barMed);
        perBarCon=(TextView)findViewById(R.id.perBarCon);
        perBarMed=(TextView)findViewById(R.id.perBarMed);



        //Creamos un hilo para las barras de progreso.

        new Thread(new Runnable() {
            @Override
            public void run() {
                while(hilo) {
                    // Update the progress bar
                    mHandler.post(new Runnable() {
                        public void run() {
                            mProgressCon.setProgress(mProgressStatusCon);
                            mProgressMed.setProgress(mProgressStatusMed);
                        }
                    });
                }
            }
        }).start();

        //tv.setText("texto RAW");
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null) {
            // Alert user that Bluetooth is not available
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }else {
        	/* create the TGDevice */
            tgDevice = new TGDevice(bluetoothAdapter, handler);

        }


        //Iniciamos el cliente de google+

        if (savedInstanceState != null) {
            mSignInProgress = savedInstanceState
                    .getInt(SAVED_PROGRESS, STATE_DEFAULT);
        }
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Plus.API, Plus.PlusOptions.builder().build())
                .addScope(Plus.SCOPE_PLUS_LOGIN)
                .build();
    }

    @Override
    public void onDestroy() {
        tgDevice.close();
        super.onDestroy();

    }


    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TGDevice.MSG_STATE_CHANGE:

                    switch (msg.arg1) {
                        case TGDevice.STATE_IDLE:
                            txtEstado.setText("Dispositivo en reposo");
                            break;
                        case TGDevice.STATE_CONNECTING:
                            txtEstado.setText("Conectando...");
                            break;
                        case TGDevice.STATE_CONNECTED:
                            tgDevice.start();
                            txtEstado.setText("Conectado");
                            findViewById(R.id.btnConectar).setEnabled(false);
                            findViewById(R.id.botonparar).setEnabled(true);


                            break;
                        case TGDevice.STATE_NOT_FOUND:
                            txtEstado.setText("Dispositivo no encontrado");
                            break;
                        case TGDevice.STATE_NOT_PAIRED:
                            txtEstado.setText("Dispositivo no vinculado");
                            break;
                        case TGDevice.STATE_DISCONNECTED:
                            txtEstado.setText("Desconectado");
                    }

                    break;
                case TGDevice.MSG_POOR_SIGNAL:
                    //signal = msg.arg1;
                    //tv.append("PoorSignal: " + msg.arg1 + "\n");
                    break;
                case TGDevice.MSG_RAW_DATA:
                    //raw1 = msg.arg1;
                    //tv.setText("Got raw: " + msg.arg1 + "\r");
//					raw=msg.arg1;

                    break;
                case TGDevice.MSG_HEART_RATE:
                    //tv.append("Heart rate: " + msg.arg1 + "\n");
                    break;
                case TGDevice.MSG_ATTENTION:
                    //att = msg.arg1;
//            		tv.append("Attention: " + msg.arg1 + "\n");
                    mProgressStatusCon=msg.arg1;
                    perBarCon.setText(String.valueOf(msg.arg1));
                    if (Integer.valueOf(atention)< Integer.valueOf(msg.arg1)) {
                        txtConcentracion.setText(String.valueOf(msg.arg1));
                        atention=Integer.valueOf(msg.arg1);
                    }
                    if(time==null){
                        time= new Date();
                        txtTime.setText(time.toString());
                    }
                    Log.d("Concentración", String.valueOf(txtConcentracion.getText()));
                    evolCon.add(String.valueOf(msg.arg1));
                    //Log.v("HelloA", "Attention: " + att + "\n");
                    break;
                case TGDevice.MSG_MEDITATION:
//				tv.append("meditation: " + msg.arg1 + "\n");
                    mProgressStatusMed=msg.arg1;
                    perBarMed.setText(String.valueOf(msg.arg1));
                    if (Integer.valueOf(meditation)< Integer.valueOf(msg.arg1)) {
                        txtMeditacion.setText(String.valueOf(msg.arg1));
                        meditation=Integer.valueOf(msg.arg1);
                    }
                    evolMed.add(String.valueOf(msg.arg1));
//				meditation=msg.arg1;

                    break;
                case TGDevice.MSG_BLINK:
//            		tv.append("Blink: " + msg.arg1 + "\n");
//					blink=String.valueOf(msg.arg1);
                    if(msg.arg1 > 50) {
                        pestaneos++;
                    }
                    txtPestaneos.setText(String.valueOf(pestaneos));
                    break;
                case TGDevice.MSG_RAW_COUNT:
                    //tv.append("Raw Count: " + msg.arg1 + "\n");
                    break;
                case TGDevice.MSG_LOW_BATTERY:
                    Toast.makeText(getApplicationContext(), "Low battery!", Toast.LENGTH_SHORT).show();
                    break;
                case TGDevice.MSG_RAW_MULTI:
                    //TGRawMulti rawM = (TGRawMulti)msg.obj;
                    //tv.append("Raw1: " + rawM.ch1 + "\nRaw2: " + rawM.ch2);
                default:
                    break;
            }
//			new AddNewPrediction().execute(atention, meditation, blink);
        }
    };

    public void Comenzar(View view) {
        tgDevice.connect(rawEnabled);

    }

    public void parar(View view){


        seg=evolCon.size();
        tgDevice.close();
        findViewById(R.id.botonparar).setEnabled(false);

        findViewById(R.id.botonenviar).setEnabled(true);
        findViewById(R.id.botonborrar).setEnabled(true);

        txtTime.setText(seg + " seg");

    }

    public void borrarDatos(View view){



        pestaneos = 0;
        time = null;
        evolCon.clear();
        evolMed.clear();
        mProgressStatusCon= 0;
        mProgressStatusMed=0;

        mProgressCon.setProgress(mProgressStatusCon);
        mProgressMed.setProgress(mProgressStatusMed);


        txtPestaneos.setText("00");
        txtConcentracion.setText("00");
        txtMeditacion.setText("00");
        txtTime.setText("00");


        perBarCon.setText("0");
        perBarMed.setText("0");


        findViewById(R.id.btnConectar).setEnabled(true);

        findViewById(R.id.botonenviar).setEnabled(false);
        findViewById(R.id.botonparar).setEnabled(false);
        findViewById(R.id.botonborrar).setEnabled(false);





    }

    public void enviarAlServidor (View view){

        Person currentUser = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);

        String email = Plus.AccountApi.getAccountName(mGoogleApiClient);
        String clave = currentUser.getId();

        Log.d("email : ", email);
        Log.d("id :", clave);



        new AddNewPrediction().execute(String.valueOf(txtConcentracion.getText()), String.valueOf(txtMeditacion.getText()), String.valueOf(txtPestaneos.getText()), String.valueOf(seg), String.valueOf(time.getTime()), evolCon.toString(),evolMed.toString(),email,clave);

        Log.d("Concentración", evolCon.toString());

        Log.d("FINNNNNNNNNN", "FINNNNNNNNNN");

        borrarDatos(view);

    }

    private class AddNewPrediction extends AsyncTask<String, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected Void doInBackground(String... arg) {


            String atention= arg[0];
            String meditation = arg[1];
            String blink = arg[2];
            String seg=arg[3];
            String time=arg[4];
            String evolCon=arg[5];
            String evolMed=arg[6];
            String email=arg[7];
            String clave=arg[8];
            // Preparing post params
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("atention", atention));
            params.add(new BasicNameValuePair("meditation", meditation));
            params.add(new BasicNameValuePair("blink", blink));
            params.add(new BasicNameValuePair("seg", seg));
            params.add(new BasicNameValuePair("time", time));
            params.add(new BasicNameValuePair("evolCon", evolCon));
            params.add(new BasicNameValuePair("evolMed", evolMed));
            params.add(new BasicNameValuePair("email", email));
            params.add(new BasicNameValuePair("clave", clave));
//			Log.d("Json-time", time);
//			Log.d("Json-seg", seg);
//			Log.d("param" + params);
//			Log.d("param"+params);




            ServiceHandler serviceClient = new ServiceHandler();

            String json = serviceClient.makeServiceCall(URL_NEW_DATA,
                    ServiceHandler.POST, params);

            Log.d("Cr Prediction Request: ", "> " + json);

            if (json != null) {
                try {
                    JSONObject jsonObj = new JSONObject(json);
                    boolean error = jsonObj.getBoolean("error");
                    // checking for error node in json
                    if (!error) {
                        // new category created successfully
                        Log.e("Prediction added succ ",
                                "> " + jsonObj.getString("message"));
                    } else {
                        Log.e("Add Prediction Error: ",
                                "> " + jsonObj.getString("message"));
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            } else {
                Log.e("JSON Data", "JSON data error!");
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
        }
    }



    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();

    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onClick(View v) {
        if (!mGoogleApiClient.isConnecting()) {

            switch (v.getId()) {
                case R.id.sign_in_button:
                    mSignInProgress = STATE_SIGN_IN;
                    mGoogleApiClient.connect();
                    break;
                case R.id.sign_out_button:
                    if (mGoogleApiClient.isConnected()) {
                        Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
                        Toast.makeText(MainActivity.this, R.string.status_signed_out, Toast.LENGTH_SHORT).show();
                        mGoogleApiClient.disconnect();
                    }
                    onSignedOut();
                    break;
            }
        }
    }



    @Override
    public void onConnected(Bundle connectionHint) {

        mSignInButton.setEnabled(false);
        mSignOutButton.setEnabled(true);

        Toast.makeText(MainActivity.this, R.string.status_signed_on, Toast.LENGTH_SHORT).show();

        findViewById(R.id.myapplication).setVisibility(View.VISIBLE);


        Person currentUser = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);

//        mStatus.setText(String.format(
//                getResources().getString(R.string.signed_in_as),
//                currentUser.getId()));
        mSignInProgress = STATE_DEFAULT;
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (!result.hasResolution()) {
            GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 0).show();
            return;
        }
        if (mSignInProgress != STATE_IN_PROGRESS) {
            mSignInIntent = result.getResolution();
            mSignInError = result.getErrorCode();

            if (mSignInProgress == STATE_SIGN_IN) {
                resolveSignInError();
            }
        }
    }

    private void resolveSignInError() {
        if (mSignInIntent != null) {

            try {
                mSignInProgress = STATE_IN_PROGRESS;
                startIntentSenderForResult(mSignInIntent.getIntentSender(),
                        RC_SIGN_IN, null, 0, 0, 0);
            } catch (SendIntentException e) {

                mSignInProgress = STATE_SIGN_IN;
                mGoogleApiClient.connect();
            }
        } else {
            createErrorDialog().show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RC_SIGN_IN:
                if (resultCode == RESULT_OK) {
                    mSignInProgress = STATE_SIGN_IN;
                } else {
                    mSignInProgress = STATE_DEFAULT;
                }
                if (!mGoogleApiClient.isConnecting()) {
                    mGoogleApiClient.connect();
                    Toast.makeText(MainActivity.this, R.string.status_signing_in, Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void onSignedOut() {
        mSignInButton.setEnabled(true);
        mSignOutButton.setEnabled(false);
        findViewById(R.id.myapplication).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        mGoogleApiClient.connect();
    }

    private Dialog createErrorDialog() {
        if (GooglePlayServicesUtil.isUserRecoverableError(mSignInError)) {
            return GooglePlayServicesUtil.getErrorDialog(
                    mSignInError,
                    this,
                    RC_SIGN_IN,
                    new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            mSignInProgress = STATE_DEFAULT;
                            //mStatus.setText(R.string.status_signed_out);
                        }
                    });
        } else {

            return new AlertDialog.Builder(this)
                    .setMessage(R.string.play_services_error)
                    .setPositiveButton(R.string.close,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mSignInProgress = STATE_DEFAULT;
                                   // mStatus.setText(R.string.status_signed_out);
                                }
                            }).create();
        }
    }
}


