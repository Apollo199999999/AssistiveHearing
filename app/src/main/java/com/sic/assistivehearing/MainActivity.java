package com.sic.assistivehearing;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
import android.os.Bundle;
import android.renderscript.Sampler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;
import org.tensorflow.lite.task.core.BaseOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@SuppressWarnings("all")
public class MainActivity extends AppCompatActivity {
    public List<String> dangerCategories = Arrays.asList(new String[]
            {
                    "vehicle horn, car horn, honking",
                    "bicycle",
                    "alarm",
                    "siren",
                    "civil defense siren",
                    "smoke detector, smoke alarm",
                    "fire alarm",
                    "explosion",
                    "gunshot, gunfire",
                    "shatter",
            });

    public List<String> alertCategories = Arrays.asList(new String[]
            {
                    "thump",
                    "thunder",
                    "shout",
                    "bellow",
                    "whoop",
                    "yell",
                    "screaming",
                    "bark",
                    "yip",
                    "bow-wow",
                    "hiss",
                    "roar",
                    "car",
                    "bus",
                    "emergency vehicle",
                    "police car (siren)",
                    "ambulance (siren)",
                    "fire engine, fire truck (siren)",
                    "doorbell",
                    "bang",
                    "slap, smack",
                    "breaking",
                    "whip",
                    "crushing"
            });

    public List<String> gtkCategories = Arrays.asList(new String[]
            {
                    "speech",
                    "sneeze",
            });

    // Global AudioClassifer variables
    AudioClassifier classifier;
    TensorAudio tensorAudio;
    AudioRecord recorder;
    NoiseSuppressor noiseSuppressor;

    // Timer to get recording samples
    public Timer recordTimer = new Timer();

    // Allow the user to change whether the L and R channels of an AudioRecord
    // correspond to the bottom and top mics respectively, instead of the typical L and R
    // corresponding to the top and bottom mics respectively
    boolean inversedMicrophone = false;

    // Create placeholder for user's consent to record_audio permission.
    // This will be used in handling callback
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;

    private void requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            // When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();

                // Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
            }
        }
        // If permission is granted, then go ahead recording audio
        else if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

            //Go ahead with recording audio now
            InitialiseAudioModel();
        }
    }

    // Handling callback
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    InitialiseAudioModel();
                } else {
                    /* permission denied, boo! Disable the
                       functionality that depends on this permission.*/
                    Toast.makeText(this, "Permissions denied to record audio!", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    public void InitialiseAudioModel() {
        // Set general detection options, e.g. number of used threads
        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder()
                .setNumThreads(4);

        AudioClassifier.AudioClassifierOptions options =
                AudioClassifier.AudioClassifierOptions.builder()
                        .setBaseOptions(baseOptionsBuilder.build())
                        .build();

        try {
            // Create the classifier and required supporting objects
            classifier = AudioClassifier.createFromFileAndOptions(this, "YAMNet.tflite", options);
            tensorAudio = classifier.createInputTensorAudio();

            int bufferSizeInBytes = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_FLOAT);
            int bufferSizeMultiplier = 2;
            int modelRequiredBufferSize = (int) classifier.getRequiredInputBufferSize() * DataType.FLOAT32.byteSize() * bufferSizeMultiplier;
            if (bufferSizeInBytes < modelRequiredBufferSize) {
                bufferSizeInBytes = modelRequiredBufferSize;
            }

            recorder = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER,
                    16000,
                    AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_FLOAT,
                    bufferSizeInBytes);
            StartAudioInference();
            classifier.createAudioRecord();
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

        long interval = (long) (lengthInMilliSeconds * (1));
        recordTimer.scheduleAtFixedRate(new GetSamples(), 0, interval);

    }

    UsbManager usbManager;
    UsbDeviceConnection esp32Connection;
    UsbSerialPort esp32Port;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set inversedMicrophone from SharedPreferences
        SharedPreferences settings = getSharedPreferences("UserSettings", 0);
        inversedMicrophone = settings.getBoolean("inversedMicrophone", false);

        // Initialization
        requestAudioPermissions();

        // Communicate with ESP32 using USB
        usbManager = (UsbManager) getApplicationContext().getSystemService(Context.USB_SERVICE);
        ConnectESP32();

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.EXTRA_PERMISSION_GRANTED);
        registerReceiver(mUsbReceiver, filter);

        // Handlers for buttons
        Button howToBtn = findViewById(R.id.HowToBtn);
        howToBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                HowToDialog howToDialog = new HowToDialog(MainActivity.this);
                howToDialog.show();
                int width = (int)(getResources().getDisplayMetrics().widthPixels * 0.95);
                howToDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        Button settingsBtn = findViewById(R.id.SettingsBtn);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SettingsDialog settingsDialog = new SettingsDialog(MainActivity.this);
                settingsDialog.show();
                int width = (int)(getResources().getDisplayMetrics().widthPixels * 0.95);
                settingsDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);

                settingsDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        // Set inversedMicrophone from SharedPreferences
                        SharedPreferences settings = getSharedPreferences("UserSettings", 0);
                        inversedMicrophone = settings.getBoolean("inversedMicrophone", false);
                    }
                });
            }
        });

        Button aboutBtn = findViewById(R.id.AboutBtn);
        aboutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AboutDialog aboutDialog = new AboutDialog(MainActivity.this);
                aboutDialog.show();
                int width = (int)(getResources().getDisplayMetrics().widthPixels * 0.95);
                aboutDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

    }

    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (device != null) {
                    ConnectESP32();
                }

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView TCPconnect = (TextView) findViewById(R.id.ConnectionText);
                        TCPconnect.setTextColor(0xFFFF0000);
                        TCPconnect.setText("Disconnected");
                    }
                });
            }
        }
    };

    public void ConnectESP32() {
        try {
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            UsbSerialDriver driver = availableDrivers.get(0);
            esp32Connection = usbManager.openDevice(driver.getDevice());
            esp32Port = driver.getPorts().get(0);

            esp32Port.open(esp32Connection);
            esp32Port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView TCPconnect = (TextView) findViewById(R.id.ConnectionText);
                    TCPconnect.setTextColor(0xFF00FF00);
                    TCPconnect.setText("Connected");
                }
            });

        } catch (Exception ex) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView TCPconnect = (TextView) findViewById(R.id.ConnectionText);
                    TCPconnect.setTextColor(0xFFFF0000);
                    TCPconnect.setText("Disconnected");
                }
            });
        }
    }

    public void StopInference() {
        recorder.stop();
        recordTimer.cancel();
        recordTimer.purge();
    }

    @Override
    public void onDestroy() {
        StopInference();
        try {
            esp32Port.close();
            esp32Connection.close();
        } catch (Exception ex) {
            Log.e("SIC", "Exception", ex);
        }

        super.onDestroy();
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
            // Convert stereo AudioRecord to mono
            // Buffer reading somewhat stolen from decompiled tflite code
            float[] newData = new float[recorder.getChannelCount() * recorder.getBufferSizeInFrames()];
            int readLen = recorder.read(newData, 0, newData.length, 1);

            // Calc the volume of each of the 2 streams
            float[] LChannelStream = new float[newData.length / 2];
            float[] RChannelStream = new float[newData.length / 2];
            for (int i = 0; i < readLen / 2; i += 2) {
                LChannelStream[i / 2] = newData[i];
                RChannelStream[i / 2] = newData[i + 1];
            }

            double LChannelLoudness = 0.0;
            for (int i = 0; i < LChannelStream.length; i++) {
                LChannelLoudness += Math.abs(LChannelStream[i]);
            }
            LChannelLoudness = LChannelLoudness / LChannelStream.length;

            double RChannelLoudness = 0.0;
            for (int i = 0; i < RChannelStream.length; i++) {
                RChannelLoudness += Math.abs(RChannelStream[i]);
            }
            RChannelLoudness = RChannelLoudness / RChannelStream.length;

            // On most phones, the Left Channel corresponds to the top microphone,
            // while the Right Channel corresponds to the bottom microphone.
            // However, this is not guaranteed, which is why we provide users with an option
            // of inversing this, via the boolean inversedMicrophones (false by default)

            int TopLoudness = 0;
            int BottomLoudness = 0;

            TextView sourceText = findViewById(R.id.SourceText);
            if (RChannelLoudness > LChannelLoudness) {
                if (inversedMicrophone == false) {
                    // Bottom mic is louder
                    sourceText.setText("Back");
                    BottomLoudness = 1;
                } else if (inversedMicrophone == true) {
                    // Top mic is louder
                    sourceText.setText("Front");
                    TopLoudness = 1;
                }
            } else if (RChannelLoudness < LChannelLoudness) {
                if (inversedMicrophone == false) {
                    // Top mic is louder
                    sourceText.setText("Front");
                    TopLoudness = 1;
                } else if (inversedMicrophone == true) {
                    // Bottom mic is louder
                    sourceText.setText("Back");
                    BottomLoudness = 1;
                }
            } else if (RChannelLoudness == LChannelLoudness) {
                // Ring both buzzers
                sourceText.setText("Both");
                BottomLoudness = 1;
                TopLoudness = 1;
            }

            // Merge both audio streams into one for tflite
            float[] PCMData = new float[newData.length / 2];
            for (int i = 0; i < readLen / 2; i += 2) {
                PCMData[i / 2] = (newData[i] + newData[i + 1]) / 2;
            }

            tensorAudio.load(PCMData);
            List<Classifications> output = classifier.classify(tensorAudio);
            List<Category> unmodifiedCategories = output.get(0).getCategories();

            if (unmodifiedCategories.size() > 0) {
                //Get the 5 most likely sounds
                List<Category> categories = new ArrayList(unmodifiedCategories);
                List<String> categoryLabels = new ArrayList<String>();

                String text = "";
                for (int j = 0; j < 5; j++) {
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
                    String outputText = String.format("%s %.2f", category, maxProbability * 100) + "%\n";
                    text += outputText;

                    categoryLabels.add(category.toLowerCase());
                    categories.remove(maxIndex);
                }

                // Remove the last \n
                text = text.substring(0, text.length() - 1);
                TextView textView = (TextView) findViewById(R.id.ClassText);
                textView.setText(text);

                // 2 elements for L buzzer, 2 elements for R buzzer
                // First element stores "intensity" (1-3). 4 indicates dont buzz
                // Second element stores continuous (1)/intermittent(0)
                byte[] soundData = new byte[4];
                soundData[0] = 9;
                soundData[1] = 9;
                soundData[2] = 9;
                soundData[3] = 9;

                for (int i = 0; i < dangerCategories.size(); i++) {
                    // soundData alr filled
                    if (soundData[0] != 9) {
                        break;
                    }

                    if (categoryLabels.contains(dangerCategories.get(i).toLowerCase())) {
                        // L
                        if (TopLoudness == 0) {
                            soundData[0] = (byte) 4;
                            soundData[1] = (byte) 1;
                        }
                        else {
                            soundData[0] = (byte) 3;
                            soundData[1] = (byte) 1;
                        }

                        // R
                        if (BottomLoudness == 0) {
                            soundData[2] = (byte) 4;
                            soundData[3] = (byte) 1;
                        }
                        else {
                            soundData[2] = (byte) 3;
                            soundData[3] = (byte) 1;
                        }

                        break;
                    }
                }

                for (int i = 0; i < alertCategories.size(); i++) {
                    // soundData alr filled
                    if (soundData[0] != 9) {
                        break;
                    }

                    if (categoryLabels.contains(alertCategories.get(i).toLowerCase())) {
                        // L
                        if (TopLoudness == 0) {
                            soundData[0] = (byte) 4;
                            soundData[1] = (byte) 1;
                        }
                        else {
                            soundData[0] = (byte) 2;
                            soundData[1] = (byte) 1;
                        }

                        // R
                        if (BottomLoudness == 0) {
                            soundData[2] = (byte) 4;
                            soundData[3] = (byte) 1;
                        }
                        else {
                            soundData[2] = (byte) 2;
                            soundData[3] = (byte) 1;
                        }

                        break;
                    }
                }

                for (int i = 0; i < gtkCategories.size(); i++) {
                    // soundData alr filled
                    if (soundData[0] != 9) {
                        break;
                    }

                    if (categoryLabels.contains(gtkCategories.get(i).toLowerCase())) {
                        // L
                        if (TopLoudness == 0) {
                            soundData[0] = (byte) 4;
                            soundData[1] = (byte) 0;
                        }
                        else {
                            soundData[0] = (byte) 1;
                            soundData[1] = (byte) 0;
                        }

                        // R
                        if (BottomLoudness == 0) {
                            soundData[2] = (byte) 4;
                            soundData[3] = (byte) 0;
                        }
                        else {
                            soundData[2] = (byte) 1;
                            soundData[3] = (byte) 0;
                        }

                        break;
                    }
                }


                if (soundData[0] == 9) {
                    sourceText.setText("None");
                    return;
                }

                Thread sendThread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            esp32Port.write(soundData, 0);
                        } catch (Exception e) {
                            sourceText.setText("None");
                            Log.e("SIC", "Exception", e);
                        }
                    }
                };

                try {
                    sendThread.start();
                } catch (Exception e) {
                    sourceText.setText("None");
                    Log.e("SIC", "Exception", e);
                }

            }
        }
    }


}