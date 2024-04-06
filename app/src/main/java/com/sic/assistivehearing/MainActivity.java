package com.sic.assistivehearing;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;
import org.tensorflow.lite.task.core.BaseOptions;
import org.w3c.dom.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    //region Global Variables
    // Global AudioClassifer variables
    AudioClassifier classifier;
    TensorAudio tensorAudio;
    AudioRecord recorder;

    public ArrayAdapter BLEListViewArrayAdapter;

    //Timer to get recording samples
    public Timer recordTimer = new Timer();

    //endregion

    //region Request Bluetooth Permissions
    // Create a request code for user's consent to bluetooth permissions.
    // This will be used in handling callback
    private final int PERMISSIONS_BLUETOOTH_REQUEST_CODE = 0;

    public String[] BluetoothPermissions = new String[]{};

    public void initializeBluetoothPerms() {
        List<String> permsList = new ArrayList<String>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            permsList.add(Manifest.permission.BLUETOOTH);
            permsList.add(Manifest.permission.BLUETOOTH_ADMIN);
        } else {
            permsList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            permsList.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permsList.add(Manifest.permission.BLUETOOTH_SCAN);
            permsList.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        BluetoothPermissions = Arrays.copyOf(permsList.toArray(new String[0]), permsList.size());
    }

    public boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean shouldShowRequestPermissionRationale(String... permissions) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void requestBluetoothPermissions() {
        initializeBluetoothPerms();

        if (!hasPermissions(this, BluetoothPermissions)) {
            // When permission is not granted by user, show them message why this permission is needed.
            if (shouldShowRequestPermissionRationale(BluetoothPermissions)) {
                Toast.makeText(this, "Please grant permissions to scan and connect to BLE devices", Toast.LENGTH_LONG).show();

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        BluetoothPermissions,
                        PERMISSIONS_BLUETOOTH_REQUEST_CODE);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        BluetoothPermissions,
                        PERMISSIONS_BLUETOOTH_REQUEST_CODE);
            }
        }
        //If permission is granted, then go ahead recording audio
        else if (hasPermissions(this, BluetoothPermissions)) {
            //Go ahead with bluetooth stuff now
            ScanBLEDevices();
        }
    }


    //endregion

    //region Request Audio Permissions
    // Create a request code for user's consent to recording audio.
    // This will be used in handling callback
    private final int PERMISSIONS_RECORD_REQUEST_CODE = 1;

    private void requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        PERMISSIONS_RECORD_REQUEST_CODE);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        PERMISSIONS_RECORD_REQUEST_CODE);
            }
        }
        //If permission is granted, then go ahead recording audio
        else if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

            //Go ahead with recording audio now
            InitialiseAudioModel();
        }
    }
    //endregion

    //region Permissions callback
    //Handling callback
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_RECORD_REQUEST_CODE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    InitialiseAudioModel();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permissions denied to record audio", Toast.LENGTH_LONG).show();
                }
                return;
            }

            case PERMISSIONS_BLUETOOTH_REQUEST_CODE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    ScanBLEDevices();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permissions denied to access Bluetooth", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    //endregion

    //region Bluetooth functions
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning;
    private Handler handler = new Handler();

    // Stops scanning after 10 second(s).
    private static final long SCAN_PERIOD = 10000;


    @SuppressLint("MissingPermission")
    public void ScanBLEDevices() {
        // Clear the listview that displays ble devices
        BLEListViewArrayAdapter.clear();

        // Get bluetooth classes
        bluetoothManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        if (!scanning) {
            // Stops scanning after a predefined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            scanning = true;


            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }


    // Device scan callback.
    @SuppressLint("MissingPermission")
    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    BluetoothDevice BLEDevice = result.getDevice();

                    // Show scan results in the listview
                    if (result.getScanRecord().getDeviceName() != null) {
                        BLEListViewArrayAdapter.add(result.getScanRecord().getDeviceName() + ": " + BLEDevice.getAddress());
                    }
                }
            };

    //endregion

    //region Audio Classification Model
    public void InitialiseAudioModel() {
        // Set general detection options, e.g. number of used threads
        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder()
                .setNumThreads(4);

        AudioClassifier.AudioClassifierOptions options =
                AudioClassifier.AudioClassifierOptions.builder()
                        .setScoreThreshold(0.3f)
                        .setBaseOptions(baseOptionsBuilder.build())
                        .build();

        try {
            // Create the classifier and required supporting objects
            classifier = AudioClassifier.createFromFileAndOptions(this, "YAMNet.tflite", options);
            tensorAudio = classifier.createInputTensorAudio();
            recorder = classifier.createAudioRecord();
            StartAudioInference();

        } catch (Exception e) {
            Log.e("AudioClassification", "TFLite failed to load with error: " + e.getMessage());
        }
    }

    public void StartAudioInference() {
        if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            return;
        }

        recorder.startRecording();

        // Each model will expect a specific audio recording length. This formula calculates that
        // length using the input buffer size and tensor format sample rate.
        // For example, YAMNET expects 0.975 second length recordings.
        // This needs to be in milliseconds to avoid the required Long value dropping decimals.
        float lengthInMilliSeconds = ((classifier.getRequiredInputBufferSize() * 1.0f) /
                classifier.getRequiredTensorAudioFormat().getSampleRate()) * 1000;

        // 0.5 is the overlap factor, i.e. 0.5 of the audio sample will overlap with the previous audio sample
        long interval = (long) (lengthInMilliSeconds * (1 - 0.5));
        recordTimer.scheduleAtFixedRate(new GetSamples(), 0, interval);

    }

    public void StopInference() {
        recorder.stop();
        recordTimer.cancel();
        recordTimer.purge();
    }


    private class GetSamples extends TimerTask {
        //This thing's a goddamn mess
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    classifyAudio();
                }
            });
        }

        public void classifyAudio() {
            tensorAudio.load(recorder);
            List<Classifications> output = classifier.classify(tensorAudio);
            List<Category> categories = output.get(0).getCategories();

            if (categories.size() > 0) {
                //Get the most likely emotion
                //Initialize max with first element of array.
                float maxProbability = categories.get(0).getScore();
                int maxIndex = 0;
                //Loop through the array
                for (int i = 0; i < categories.size(); i++) {
                    //Compare elements of array with max
                    if (categories.get(i).getScore() > maxProbability) {
                        maxProbability = categories.get(i).getScore();
                        maxIndex = i;
                    }
                }

                //Get the associated emotion
                String category = categories.get(maxIndex).getLabel();
                @SuppressLint("DefaultLocale")
                String outputText = String.format("%s %.2f", category, maxProbability * 100) + "%";

                TextView textView = (TextView) findViewById(R.id.ClassText);
                textView.setText("Detected Class: " + outputText);
            }
        }
    }

    //endregion

    //region Helper functions
    private void enableViews(View v, boolean enabled) {
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                enableViews(vg.getChildAt(i), enabled);
            }
        }
        v.setEnabled(enabled);
    }
    //endregion

    //region Activity Lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Disable the UI on audio classification first
        LinearLayout audioInferenceSection = (LinearLayout) findViewById(R.id.AudioInferenceSection);
        enableViews(audioInferenceSection, false);

        // Event handlers for the 2 buttons
        Button scanBLEBtn = (Button) findViewById(R.id.ScanBLEBtn);
        scanBLEBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanBLEBtnClicked((Button) view);
            }
        });

        Button startAudioInferenceBtn = (Button) findViewById(R.id.StartAudioInferenceBtn);
        startAudioInferenceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startAudioInferenceBtnClicked((Button) view);
            }
        });

        // Attach ArrayAdapter to BLE devices ListView
        ListView bleListView = (ListView) findViewById(R.id.BLEListView);
        List<String> bleListViewItems = new ArrayList<String>();
        BLEListViewArrayAdapter = new ArrayAdapter(this,
                android.R.layout.simple_list_item_1, bleListViewItems);
        bleListView.setAdapter(BLEListViewArrayAdapter);

        // Set placeholder text on listview
        TextView emptyText = (TextView)findViewById(android.R.id.empty);
        bleListView.setEmptyView(emptyText);
    }

    @Override
    public void onDestroy() {
        StopInference();
        super.onDestroy();
    }

    //endregion

    //region UI events
    public void scanBLEBtnClicked(Button connectBLEBtn) {
        // Request ble perms
        requestBluetoothPermissions();
    }

    public void startAudioInferenceBtnClicked(Button startAudioInferenceBtn) {
        // Request audio perms
        requestAudioPermissions();
    }

    //endregion
}