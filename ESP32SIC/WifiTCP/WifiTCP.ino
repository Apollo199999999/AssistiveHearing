#ifdef ESP32              // depending on the microcontroller-type 
#include <WiFi.h>         // include the right library for ESP32
#elif defined(ESP8266)
#include <ESP8266WiFi.h>  // or ESP8266
#endif

// this tcp_server demo-code creates its own WiFi-network 
// where the tcp_client demo-code connects to
// the ssid and the portNumber must be the same to make it work

const char* ssid     = "ESP32-AP";
const uint16_t portNumber = 50000; // System Ports 0-1023, User Ports 1024-49151, dynamic and/or Private Ports 49152-65535

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
}

void loop() {
  char TCP_Char;
  char serialChar;
  int buzzerData[4] = {9,9,9,9};

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

//          Serial.println(i, DEC);
      }  

      // if characters have been typed into the serial monitor  
      while (Serial.available()) {  
        char serialChar = Serial.read(); // take character out of the serial buffer
        Serial.write(serialChar); // print local echo
        client.write(serialChar); // send character over TCP to client
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
    tone(16, buzzerData[0] * 30);
    //R
    digitalWrite(17, HIGH);
    tone(17, buzzerData[2] * 30);

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
