#ifdef ESP32              // depending on the microcontroller-type 
#include <WiFi.h>         // include the right library for ESP32
#elif defined(ESP8266)
#include <ESP8266WiFi.h>  // or ESP8266
#endif
#include <Arduino.h>
#include <driver/adc.h>
// this tcp_server demo-code creates its own WiFi-network 
// where the tcp_client demo-code connects to
// the ssid and the portNumber must be the same to make it work

const char* ssid     = "ESP32-AP";
const uint16_t portNumber = 50000; // System Ports 0-1023, User Ports 1024-49151, dynamic and/or Private Ports 49152-65535

#define AUDIO_BUFFER_MAX 800

uint8_t audioBuffer[AUDIO_BUFFER_MAX];
uint8_t transmitBuffer[AUDIO_BUFFER_MAX];
uint32_t bufferPointer = 0;

bool transmitNow = false;

hw_timer_t * timer = NULL; // our timer
portMUX_TYPE timerMux = portMUX_INITIALIZER_UNLOCKED; 

void IRAM_ATTR onTimer() {
  portENTER_CRITICAL_ISR(&timerMux); // says that we want to run critical code and don't want to be interrupted
  int adcVal = adc1_get_raw(ADC1_CHANNEL_7); // reads the ADC
  uint8_t value = map(adcVal, 0 , 4096, 0, 255);  // converts the value to 0..255 (8bit)
  audioBuffer[bufferPointer] = value; // stores the value
  bufferPointer++;
 
  if (bufferPointer == AUDIO_BUFFER_MAX) { // when the buffer is full
    bufferPointer = 0;
    memcpy(transmitBuffer, audioBuffer, AUDIO_BUFFER_MAX); // copy buffer into a second buffer
    transmitNow = true; // sets the value true so we know that we can transmit now
  }
  portEXIT_CRITICAL_ISR(&timerMux); // says that we have run our critical code
}



WiFiServer server(portNumber);
WiFiClient client;
bool connected = false;

void setup() {
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

  timer = timerBegin(0, 80, true); // 80 Prescaler
  timerAttachInterrupt(timer, &onTimer, true); // binds the handling function to our timer 
  timerAlarmWrite(timer, 125, true);
  timerAlarmEnable(timer);

}

void loop() {
  char TCP_Char;
  char serialChar;
  int buzzerData[4] = {9,9,9,9};
  // Serial.println(audioBuffer[bufferPointer], DEC);
  
  if (!connected) {
    // listen for incoming clients
    client = server.available();
    if (client) {
      Serial.println("\n Got a client connected to my WiFi !");
      if (client.connected()) {
        Serial.println("an now this client has connected over TCP!");
        Serial.println("if client sends characters");
        Serial.println("they were printed to the serial monitor");
        connected = true;
      } else {
        Serial.println("but it's not connected over TCP!");        
        client.stop();  // close the connection:
      }
    }
  } 
  else {
    if (client.connected()) {
      
      // if characters sended from client is in the buffer
      int i = 0;
      while ( client.available() ) { 
          // Client sends data
          TCP_Char = client.read(); // take one character out of the TCP-receive-buffer
          if (i < 3) {
            // print it to the serial monitor
             buzzerData[i] = TCP_Char;
             i++;
          }
          else if (i == 3) {  // print it to the serial monitor
            buzzerData[i] = TCP_Char;
            i = 0;
          }

      }  

        if (transmitNow) { // checks if the buffer is full
          transmitNow = false;
          client.write((const uint8_t *)audioBuffer, sizeof(audioBuffer)); // sending the buffer to our server
          Serial.println("audio transmit");
        }
    } 
    else {
      Serial.println("Client has disconnected the TCP-connection");
      client.stop();  // close the connection:
      connected = false;
    }
  }
  
  // Play buzzer if applicable
  if (buzzerData[0] != 9 && buzzerData[1] != 9 && buzzerData[2] != 9 && buzzerData[3] != 9) {
    // L
    digitalWrite(16, HIGH);
    tone(16, exp(buzzerData[0] * 2) + 30);

    //R
    digitalWrite(17, HIGH);
    tone(17, exp(buzzerData[2] * 2) + 30);

    delay(1000);
    
    noTone(16);
    digitalWrite(16, LOW);

    noTone(17);
    digitalWrite(17, LOW);

    if (buzzerData[1] == 0 && buzzerData[3] == 0) {
      delay(1000);
    }
  }
}
