package com.sic.assistivehearing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
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

import org.tensorflow.lite.DataType;
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
    NoiseSuppressor noiseSuppressor;

    // Timer to get recording samples
    public Timer recordTimer = new Timer();

    // Networking crap
    Socket socket;

    int TopLoudness = 0;
    int BottomLoudness = 0;


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
            int modelRequiredBufferSize = (int)classifier.getRequiredInputBufferSize() * DataType.FLOAT32.byteSize() * bufferSizeMultiplier;
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialization
        requestAudioPermissions();

        // Event handlers
        Button connectBtn = (Button) findViewById(R.id.ConnectBtn);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onConnectBtnClick(v);
            }
        });


    }


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
                } catch (Exception e) {
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

        TCPThread.start();

    }

    public void StopInference() {
        recorder.stop();
        recordTimer.cancel();
        recordTimer.purge();
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
            // Convert stereo AudioRecord to mono
            // Buffer reading somewhat stolen from decompiled tflite code
            float[] newData = new float[recorder.getChannelCount() * recorder.getBufferSizeInFrames()];
            int readLen = recorder.read(newData, 0, newData.length, 1);

            // Calc the volume of each of the 2 streams
            float[] LStream = new float[newData.length / 2];
            float[] RStream = new float[newData.length / 2];
            for (int i = 0; i < readLen / 2; i += 2) {
                LStream[i / 2] = newData[i];
                RStream[i / 2] = newData[i + 1];
            }

            double LLoudness  = 0.0;
            for (int i = 0; i < LStream.length; i++) {
                LLoudness += Math.abs(LStream[i]);
            }
            LLoudness = LLoudness / LStream.length;

            double RLoudness  = 0.0;
            for (int i = 0; i < RStream.length; i++) {
                RLoudness += Math.abs(RStream[i]);
            }
            RLoudness = RLoudness / RStream.length;

            if (RLoudness > LLoudness) {
                TextView text = findViewById(R.id.SourceText);
                text.setText("Bottom");
                BottomLoudness = 1;
            }
            else if (RLoudness < LLoudness){
                TextView text = findViewById(R.id.SourceText);
                text.setText("Top");
                TopLoudness = 1;
            }
            else {
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


                TextView textView = (TextView) findViewById(R.id.ClassText);
                textView.setText("ML model class: " + text);

               // 2 elements for L buzzer, 2 elements for R buzzer
               // First element stores "intensity" (1-3)
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
                       soundData[0] = (byte)(3 * TopLoudness);
                       soundData[1] = (byte)1;

                       // R
                       soundData[2] = (byte)(3 * BottomLoudness);
                       soundData[3] = (byte)1;
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
                       soundData[0] = (byte)(2 * TopLoudness);
                       soundData[1] = (byte)1;

                       // R
                       soundData[2] = (byte)(2 * BottomLoudness);
                       soundData[3] = (byte)1;
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
                       soundData[0] = (byte)(1 * TopLoudness);
                       soundData[1] = (byte)0;

                       // R
                       soundData[2] = (byte)(1 * BottomLoudness);
                       soundData[3] = (byte)0;
                       break;
                   }
               }

               TopLoudness = 0;
               BottomLoudness = 0;

               if (soundData[0] == 9) {
                   return;
               }

               Thread sendThread = new Thread() {
                   @Override
                   public void run() {
                       try {
                           OutputStream writer = socket.getOutputStream();
                           writer.write(soundData);
                           writer.flush();
                       } catch(Exception e) { Log.e("sic", "exception", e);}
                   }
               };

               try {
                   sendThread.start();
               } catch (Exception e) {
                   Log.e("sic", "exception", e);
               }

            }
        }
    }


}