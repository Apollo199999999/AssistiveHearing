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
      while ( client.available() ) { 
          // TODO: Client sends data
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
}
