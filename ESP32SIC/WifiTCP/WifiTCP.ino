#ifdef ESP32              // depending on the microcontroller-type 
#include <WiFi.h>         // include the right library for ESP32
#elif defined(ESP8266)
#include <ESP8266WiFi.h>  // or ESP8266
#endif
#include <Arduino.h>
#include <driver/adc.h>
#include <BlockNot.h>   
// this tcp_server demo-code creates its own WiFi-network 
// where the tcp_client demo-code connects to
// the ssid and the portNumber must be the same to make it work

const char* ssid     = "ESP32-AP";
const uint16_t portNumber = 50000; // System Ports 0-1023, User Ports 1024-49151, dynamic and/or Private Ports 49152-65535

// For writing mic data to an audio buffer
#define AUDIO_BUFFER_MAX 1600

uint8_t audioBuffer[AUDIO_BUFFER_MAX];
uint8_t transmitBuffer[AUDIO_BUFFER_MAX];
uint32_t bufferPointer = 0;

// Whether to transmit the audio data via TCP
bool transmitNow = false;

hw_timer_t * micTimer = NULL; // our timer
portMUX_TYPE timerMux = portMUX_INITIALIZER_UNLOCKED; 

void IRAM_ATTR getMicSamples() {
  portENTER_CRITICAL_ISR(&timerMux); // says that we want to run critical code and don't want to be interrupted
  int adcVal = adc1_get_raw(ADC1_CHANNEL_7); // reads the ADC
  uint16_t value = map(adcVal, 0 , 4096, 0, 65535);  // converts the value to 0..65535 (16bit)
  audioBuffer[bufferPointer] = value & 0xff; // because we can only transmit byte arrays via TCP, we need to split the 16 bit number into 2 bytes
  bufferPointer++;
  audioBuffer[bufferPointer] = (value >> 8); 
  bufferPointer++;
 
  if (bufferPointer == AUDIO_BUFFER_MAX) { // when the buffer is full
    bufferPointer = 0;
    memcpy(transmitBuffer, audioBuffer, AUDIO_BUFFER_MAX); // copy buffer into a second buffer
    transmitNow = true; // sets the value true so we know that we can transmit the audio buffer via TCP
  }
  portEXIT_CRITICAL_ISR(&timerMux); // says that we have run our critical code
}


// Configure the AP for this ESP32
WiFiServer server(portNumber);
WiFiClient client;
bool connected = false;

// timer
BlockNot secondTimer(350);

void setup() {
  // Configure the buzzers
  pinMode(16, OUTPUT);
  pinMode(17, OUTPUT);
  
  Serial.begin(115200);
  Serial.println( F("Setup-Start") );
  Serial.print("Creating AP (Access Point) with name#");
  Serial.print(ssid);
  Serial.println("#");
  WiFi.softAP(ssid);

  IPAddress IP = WiFi.softAPIP();
  Serial.print(" -> softAP with IP address: ");
  Serial.println(IP);
  server.begin();
  Serial.print("TCP-Server on port ");
  Serial.print(portNumber);
  Serial.print(" started");

  adc1_config_width(ADC_WIDTH_12Bit); // configure the analogue to digital converter
  adc1_config_channel_atten(ADC1_CHANNEL_7, ADC_ATTEN_11db); // connects the ADC 1 with channel 7 (GPIO 35)

  // We create a timer to gather audio samples from the microphone via ADC
  // In an ESP32, there are 4 hardware timers in total, denoted by an ID from 0 to 3, that we pass in timerBegin.
  // Each timer runs at 80MHz by default, that we can divide by a prescaler value to lower its rate
  // In this case, we are creating a timer that runs at a rate 80MHz/40 = 2 MHz.
  // This timer will then count upwards starting from 0, every 1 microsecond.
  micTimer = timerBegin(0, 40, true);

  // To actually get the timer to execute anything, we can get the timer to execute a function when it is interrupted.
  timerAttachInterrupt(micTimer, &getMicSamples, true); // binds the handling function to our timer 

  // We configure when we want the timer to interrupt, so that it can get audio samples.
  // In this case, it interrupts when its counter reaches 125, giving us a sample rate of 2 * 10^6 / 125 = 16000Hz
  timerAlarmWrite(micTimer, 125, true);

  // Start the timer
  timerAlarmEnable(micTimer);

  secondTimer.STOP;
}

// Whether buzzing should be continuous
bool continuousBuzzing = true;

void loop() {
  // To store buzzer commands sent via TCP from the Android app
  int buzzerData[4] = {9,9,9,9};
  char TCP_Char;
  char serialChar;
  
  if (!connected) {
    // Listen for incoming clients
    client = server.available();
    if (client) {
      Serial.println("\n A client has connected to this Wifi access point.");
      if (client.connected()) {
        Serial.println("This client is now connected over TCP.");
        connected = true;
      } else {
        Serial.println("This client is not connected over TCP. Closing the connection...");        
        client.stop();  // close the connection:
      }
    }
  } 
  else if (client.connected()) {
    int i = 0;
    // If the client has sent data over TCP, read all of it
    while (client.available()) { 
        // Read one character from the TCP buffer sent by the client
        TCP_Char = client.read(); 

        // Populate the buzzerData array
        if (i < 3) {
            buzzerData[i] = TCP_Char;
            i++;
        }
        else if (i == 3) { 
          buzzerData[i] = TCP_Char;
          i = 0;
        }
    }  

    if (transmitNow) { // checks if the audio buffer is full
      transmitNow = false;
      client.write((const uint8_t *)audioBuffer, sizeof(audioBuffer)); // sending the buffer to our server
      Serial.println("audio transmit");
    }
  }
  else {
    Serial.println("Client has disconnected from TCP.");
    client.stop();  // close the connection:
    connected = false;
  }
  
  // Play buzzer if applicable
  // If all 4 values in the buzzerData array aren't 9, it means that they have been populated via TCP.
  // TODO: Move this to a separate timer so it doesn't block TCP operations in loop()
  if (buzzerData[0] != 9 && buzzerData[1] != 9 && buzzerData[2] != 9 && buzzerData[3] != 9) {
    noTone(16);
    digitalWrite(16, LOW);

    noTone(17);
    digitalWrite(17, LOW);

    // L
    digitalWrite(16, HIGH);
    tone(16, exp(buzzerData[0] * 2) + 30);

    //R
    digitalWrite(17, HIGH);
    tone(17, exp(buzzerData[2] * 2) + 30);

    // Determine if buzzing shld be continuous
    if (buzzerData[1] == 0 && buzzerData[3] == 0) {
      continuousBuzzing = false;
    }
    else {
      continuousBuzzing = true;
    }

    secondTimer.START_RESET;
  }

  if (secondTimer.TRIGGERED && continuousBuzzing == false) {  
    noTone(16);
    digitalWrite(16, LOW);

    noTone(17);
    digitalWrite(17, LOW);

    secondTimer.STOP;
  }  
}

