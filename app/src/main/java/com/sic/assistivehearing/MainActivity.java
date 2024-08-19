package com.sic.assistivehearing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
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

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.net.ServerSocket;

public class MainActivity extends AppCompatActivity {

    // Global AudioClassifer variables
    AudioClassifier classifier;
    TensorAudio tensorAudio;
    AudioRecord recorder;

    // Timer to get recording samples
    public Timer recordTimer = new Timer();

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
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permissions Denied to record audio", Toast.LENGTH_LONG).show();
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

        // Test receive TCP data
        TextView tcpText = (TextView)findViewById(R.id.TCPText);
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket("192.168.4.1", 50000);

                    BufferedReader stdIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    while (true) {
                        String response = stdIn.readLine();

                        runOnUiThread(new Runnable(){
                            @Override
                            public void run(){
                                // change UI elements here
                                tcpText.setText(response);
                            }
                        });

                    }

                } catch (UnknownHostException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                catch (Exception e) {

                    e.printStackTrace();
                }
            }
        };

        thread.start();
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
               textView.setText(outputText);
           }
        }
    }


}