package com.dairyroadsolutions.accelplot;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity {

    private static final int TRACE_COUNT = 3;
    private static final int CHART_COLUMN_COUNT = 1;
    private static final int SCREEN_BUFFER_COUNT = 3000;
    private static final int LINE_THICKNESS = 3;
    private static final boolean CYCLIC = true;
    private static final float F_SCALE_FACTOR = 1.0f/4096.0f;

    // Grid controls. It works best if they are even numbers
    private int iDivisionsX = 4;
    private int iDivisionsY = 10;

    // Chart trace controls
    private GLSurfaceView mChartSurfaceView;
    private TraceHelper classTraceHelper;
    private float fChScale[];
    private float fChOffset[];
    private static ToggleButton mStreamToggleButton;
    private static ToggleButton tbSaveData;
    private static ToggleButton tbAudioOut;
    private static RadioGroup rgCh1;
    private static RadioGroup rgCh2;

    private FilterHelper filter = new FilterHelper();

    // Data writing controls
    private boolean bWriteLocal = false;

    // debug
    private static final String strTag = MainActivity.class.getSimpleName();



    // Arduino values are stored in the Bluetooth.java object

    // NOTE: All sampling and filtering information is defined
    // in the Bluetooth.java class.

    // Data write is in the Bluetooth class

    /**
     * Set the channel scale factor.  Each trace could have a different scale
     * factor, but for this instance all traces will be set to the same value.
     * @param fScaleFactor Universal scale factor
     */
    public void setChScale(float fScaleFactor){

        // Populate the array with the scale factors
        for( int idx = 0; idx< TRACE_COUNT; ++idx) {
            fChScale[idx] = fScaleFactor/(TRACE_COUNT +1.0f);
        }

        // Update dependent objects
        Bluetooth.classChartRenderer.setChScale(fChScale);

    }

    /**
     * Set the channel offsets to avoid overlaying the data
     */
    public void setChOffset(){

        float fIncrement = classTraceHelper.getTraceIncrement();
        float fStart = classTraceHelper.getTraceStart();

        // Populate the array with the scale factors
        for( int idx = 0; idx< TRACE_COUNT; ++idx) {
            fChOffset[idx] = fStart;
            fStart = fStart + fIncrement;
        }

        // Update the dependent objects
        Bluetooth.classChartRenderer.setChOffset(fChOffset);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout mControlLayer;

        setContentView(R.layout.activity_main);

        mControlLayer = (LinearLayout)findViewById(R.id.Chart);

        mChartSurfaceView = new GLSurfaceView(this);

        mChartSurfaceView.setEGLConfigChooser(false);

        mControlLayer.addView(mChartSurfaceView);

        // Flags and initialization of the BlueTooth object
        Bluetooth.samplesBuffer=new SamplesBuffer[TRACE_COUNT];
        Bluetooth.vSetWriteLocal(bWriteLocal);

        // Flags for the data stream button
        mStreamToggleButton = (ToggleButton)findViewById(R.id.tbStream);
        mStreamToggleButton.setEnabled(false);

        // Flags for the data save button
        tbSaveData = (ToggleButton)findViewById(R.id.tbSaveData);
        tbSaveData.setEnabled(false);

        // Flags and init for the audio out
        tbAudioOut = (ToggleButton)findViewById(R.id.tbAudioOut);
        tbAudioOut.setEnabled(false);

        // Initialze audio mappings
        Bluetooth.setbADC1ToCh1Out(true);
        Bluetooth.setbADC2ToCh2Out(true);
        vUpdateChMaps();

        classTraceHelper = new TraceHelper(TRACE_COUNT);

        for(int i=0;i< TRACE_COUNT;i++)
        {
            Bluetooth.samplesBuffer[i] = new SamplesBuffer(SCREEN_BUFFER_COUNT, true);
        }

        Bluetooth.classChartRenderer = new ChartRenderer(this,SCREEN_BUFFER_COUNT,Bluetooth.samplesBuffer, TRACE_COUNT);
        Bluetooth.classChartRenderer.setCyclic(CYCLIC);
        Bluetooth.classChartRenderer.bSetDivisionsX(iDivisionsX);
        Bluetooth.classChartRenderer.bSetDivisionsY(iDivisionsY);

        fChScale = new float[TRACE_COUNT];
        setChScale(F_SCALE_FACTOR);

        fChOffset = new float[TRACE_COUNT];
        setChOffset();

        // Line thickness
        Bluetooth.classChartRenderer.setThickness(LINE_THICKNESS);

        // Number of columns of chart data
        Bluetooth.classChartRenderer.setChartColumnCount(CHART_COLUMN_COUNT);

        mChartSurfaceView.setRenderer(Bluetooth.classChartRenderer);

        // Initialize the Bluetooth object
        init();

        // Initialize the buttons
        ButtonInit();

        // Debug code to test display of screen buffers
        for( int idx=0; idx<SCREEN_BUFFER_COUNT; ++idx){
            Bluetooth.samplesBuffer[1].addSample((float)(idx>>2));
        }

        // Testing calls here. Remove comments to run them.
        filter.TestHarness();
        Bluetooth.samplesBuffer[0].TestHarness();

    }
    void init() {
        Bluetooth.gethandler(mUpdateHandler);
    }


    /**
     * When streaming stops, this need to be halted as well
     */
    private void vStopStreamDep(){

        tbSaveData.setChecked(false);
        tbSaveData.setEnabled(false);
        vUpdateSaveData();

        tbAudioOut.setChecked(false);
        tbAudioOut.setEnabled(false);
        vUpdateAudioOut();

    }

    /**
     * Handles process that should be affected by change in the SaveData button status
     */
    private void vUpdateSaveData(){
        bWriteLocal = mStreamToggleButton.isChecked();
        Bluetooth.vSetWriteLocal(bWriteLocal);
    }

    private void rgCh1Update(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.radio_ADC1_Ch1:
                if (checked)
                    // Pirates are the best
                    break;
            case R.id.radio_ADC2_Ch1:
                if (checked)
                    // Ninjas rule
                    break;
        }
    }

    /**
     * Update the channel 2 audio out options
     * @param view  View with the controls
     */
    private void rgCh2Update(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.radio_ADC1_Ch2:
                if (checked)
                    // Pirates are the best
                    break;
            case R.id.radio_ADC2_Ch2:
                if (checked)
                    // Ninjas rule
                    break;
        }
    }

    /**
     * Update the radio buttons to reflect the internal state of the Bluetooth buttons.
     */
    private void vUpdateChMaps(){

        rgCh1 = (RadioGroup)findViewById(R.id.radio_ADC_to_Ch1);
        rgCh2 = (RadioGroup)findViewById(R.id.radio_ADC_to_Ch2);

        // Channel 1
        if(Bluetooth.isbADC1ToCh1Out()){
            rgCh1.check(R.id.radio_ADC1_Ch1);
        }
        if(Bluetooth.isbADC2ToCh1Out()){
            rgCh1.check(R.id.radio_ADC2_Ch1);
        }
        if(Bluetooth.isbADC3ToCh1Out()){
            rgCh1.check(R.id.radio_ADC3_Ch1);
        }

        //Channel 2
        if(Bluetooth.isbADC1ToCh2Out()){
            rgCh2.check(R.id.radio_ADC1_Ch2);
        }
        if(Bluetooth.isbADC2ToCh2Out()){
            rgCh2.check(R.id.radio_ADC2_Ch2);
        }
        if(Bluetooth.isbADC3ToCh2Out()){
            rgCh2.check(R.id.radio_ADC3_Ch2);
        }
        return;
    }


    /**
     * Handles process that change with the AudioOut button status
     */
    private void vUpdateAudioOut(){
        Bluetooth.bAudioOut = tbAudioOut.isChecked();
        Bluetooth.classAudioHelper.vSetAudioOut(Bluetooth.bAudioOut);
    }

    /**
     * This initializes the button controls
     */
    private void ButtonInit(){

        Button btnConnectButton;
        Button btnDiscconnectButton;
        final ToggleButton tglbtnScroll;

        // Configure the stream data button
        mStreamToggleButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                // This section handles the thread
                if (Bluetooth.connectedThread != null)
                {
                    Bluetooth.bStreamData = mStreamToggleButton.isChecked();
                }

                // This section handles the dependant buttons
                if (mStreamToggleButton.isChecked() == true){
                    tbSaveData.setEnabled(true);
                    tbAudioOut.setEnabled(true);

                }else{
                    vStopStreamDep();
                }
            }

        });

        // Configure the Bluetooth connect button
        btnConnectButton = (Button)findViewById(R.id.bConnect);
        btnConnectButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent("android.intent.action.BT1"));

            }


        });

        // Configure the Bluetooth disconnect button
        btnDiscconnectButton = (Button)findViewById(R.id.bDisconnect);
        btnDiscconnectButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                mStreamToggleButton.setChecked(false);
                Bluetooth.bStreamData = false;
                Bluetooth.disconnect();
                vStopStreamDep();
            }


        });

        // Configure the save data button
        tbSaveData = (ToggleButton)findViewById(R.id.tbSaveData);
        tbSaveData.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                vUpdateSaveData();
            }
        });

        // Configure the channel 1 radio buttons
        rgCh1 = (RadioGroup)findViewById(R.id.radio_ADC_to_Ch1);
        rgCh1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                rgCh1Update(v);
            }
        });

        // Configure the channel 2 radio buttons
        rgCh2 = (RadioGroup)findViewById(R.id.radio_ADC_to_Ch2);
        rgCh2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                rgCh2Update(v);
            }
        });

        // Configure the audio out button
        tbAudioOut = (ToggleButton)findViewById(R.id.tbAudioOut);
        tbAudioOut.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                vUpdateAudioOut();
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    Handler mUpdateHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what){

                case Bluetooth.SUCCESS_DISCONNECT:
                    Toast.makeText(getApplicationContext(), "Disconnected!", Toast.LENGTH_LONG).show();
                    mStreamToggleButton.setEnabled(false);
                    break;

                case Bluetooth.SUCCESS_CONNECT:
                    Bluetooth.connectedThread = new Bluetooth.ConnectedThread((BluetoothSocket)msg.obj);
                    Toast.makeText(getApplicationContext(), "Connected!", Toast.LENGTH_LONG).show();
                    Bluetooth.connectedThread.start();
                    mStreamToggleButton.setEnabled(true);
                    break;
            }

        }

    };


    /**
     * Added this to ensure the connection to the Bluetooth
     * device is closed.
     */
    protected void OnDestroy(){
        if( Bluetooth.connectedThread != null){
            Bluetooth.disconnect();
        }
    }


}