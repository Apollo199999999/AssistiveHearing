package com.sic.assistivehearing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.net.ServerSocket;


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

    public List<String> alertCategories = Arrays.asList(new String[]{
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

    public List<String> gtkCategories = Arrays.asList(new String[]{
        "speech",
        "sneeze",
    });

    // Global AudioClassifer variables
    AudioClassifier classifier;
    TensorAudio tensorAudio;
    AudioRecord recorder;

    // Timer to get recording samples
    public Timer recordTimer = new Timer();

    // Networking crap
    Socket socket;

    int minBufferSize = AudioTrack.getMinBufferSize(8000,
            AudioFormat.CHANNEL_CONFIGURATION_MONO,
            AudioFormat.ENCODING_PCM_16BIT);

    AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, 8000,
            AudioFormat.CHANNEL_CONFIGURATION_MONO,
            AudioFormat.ENCODING_PCM_16BIT, minBufferSize,
            AudioTrack.MODE_STREAM);

    boolean receiveAudioData = false;

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

        long interval = (long)(lengthInMilliSeconds * (1 - 0.5));
        recordTimer.scheduleAtFixedRate(new GetSamples(), 0, interval);

    }

    public void StopInference() {
        recorder.stop();
        recordTimer.cancel();
        recordTimer.purge();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialization
        requestAudioPermissions();

        // Event handlers
        Button connectBtn = (Button)findViewById(R.id.ConnectBtn);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onConnectBtnClick(v);
            }
        });


    }
    boolean connectESP = false;
    private void onConnectBtnClick(View v) {
        TextView TCPconnect = (TextView) findViewById(R.id.TCPText);

        Thread TCPThread = new Thread() {
            @Override
            public void run() {
                try {
                    socket = new Socket("192.168.4.1", 50000);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TCPconnect.setTextColor(0xFFFFFFFF);
                            TCPconnect.setText("Connected!");
                        }
                    });
                    at.play();
                    BufferedInputStream stdIn = new BufferedInputStream(socket.getInputStream());

                    while (true) {
                        if (receiveAudioData == true) {
                            byte[] music = new byte[2];
                            int i = stdIn.read(music);
                            int val = (music[0] & 0xff) |
                                    ((music[1] & 0xff) << 8);
                            short[] arr = new short[1];
                            arr[0] = (short)val;

                            at.write(arr, 0, 1);
                        }
                    }

                }
                catch (Exception e) {
                    Log.e("sic", "exception", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TCPconnect.setTextColor(0xFFFFFFFF);
                            TCPconnect.setText("Connection Failed.");
                        }
                    });
                }
            }
        };

        receiveAudioData = true;
        TCPThread.start();

    }


    @Override
    public void onDestroy() {
        StopInference();
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
               textView.setText("ML model class: " + outputText);

               // 2 elements for L buzzer, 2 elements for R buzzer
               // First element stores "intensity" (1-3)
               // Second element stores continuous (1)/intermittent(0)
               byte[] soundData = new byte[4];
               if (dangerCategories.contains(category.toLowerCase())) {
                   // L
                   soundData[0] = (byte)3;
                   soundData[1] = (byte)1;

                   // R
                   soundData[2] = (byte)3;
                   soundData[3] = (byte)1;
               } else if (alertCategories.contains(category.toLowerCase())) {
                   // L
                   soundData[0] = (byte)2;
                   soundData[1] = (byte)1;

                   // R
                   soundData[2] = (byte)2;
                   soundData[3] = (byte)1;
               } else if (gtkCategories.contains(category.toLowerCase())) {
                   // L
                   soundData[0] = (byte)1;
                   soundData[1] = (byte)0;

                   // R
                   soundData[2] = (byte)1;
                   soundData[3] = (byte)0;
               }

               if (soundData[0] == 0) {
                   return;
               }

               Thread sendThread = new Thread() {
                   @Override
                   public void run() {
                       try {
                           OutputStream writer = socket.getOutputStream();
                           writer.write(soundData);
                           writer.flush();
                           receiveAudioData = true;
                       } catch(Exception e) { Log.e("sic", "exception", e);}
                   }
               };

               try {
                   receiveAudioData = false;
                   sendThread.start();
               } catch (Exception e) {
                   Log.e("sic", "exception", e);
               }

           }
        }
    }


}