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
    if (buzzerData[0] != 0) {
      digitalWrite(16, HIGH);
      tone(16, exp(buzzerData[0] * 2) + 30);
    }
    
    //R
    if (buzzerData[2] != 0) {
      digitalWrite(17, HIGH);
      tone(17, exp(buzzerData[2] * 2) + 30);
    }

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

