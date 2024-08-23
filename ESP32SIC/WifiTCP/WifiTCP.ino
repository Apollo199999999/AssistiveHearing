#ifdef ESP32              // depending on the microcontroller-type 
#include <WiFi.h>         // include the right library for ESP32
#elif defined(ESP8266)
#include <ESP8266WiFi.h>  // or ESP8266
#endif
#include <Arduino.h>
#include <driver/adc.h>
#include <driver/i2s.h>
// this tcp_server demo-code creates its own WiFi-network 
// where the tcp_client demo-code connects to
// the ssid and the portNumber must be the same to make it work

const char* ssid     = "ESP32-AP";
const uint16_t portNumber = 50000; // System Ports 0-1023, User Ports 1024-49151, dynamic and/or Private Ports 49152-65535

#define ADC_SAMPLES_COUNT 800

uint8_t transmitBuffer[ADC_SAMPLES_COUNT];

bool transmitNow = false;


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

  i2s_config_t i2s_config = {
      .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX | I2S_MODE_ADC_BUILT_IN),
      .sample_rate = 40000,
      .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
      .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
      .communication_format = I2S_COMM_FORMAT_I2S_LSB,
      .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
      .dma_buf_count = 2,
      .dma_buf_len = 1024,
      .use_apll = false,
      .tx_desc_auto_clear = false,
      .fixed_mclk = 0};

  //install and start i2s driver
  i2s_driver_install(I2S_NUM_0, &i2s_config, 4, &i2s_queue);

  //init ADC pad
  i2s_set_adc_mode(ADC_UNIT_1, ADC1_CHANNEL_7);

  // enable the ADC
  i2s_adc_enable(I2S_NUM_0);

  // start a task to read samples from I2S
  TaskHandle_t readerTaskHandle;
  xTaskCreatePinnedToCore(readerTask, "Reader Task", 8192, this, 1, &readerTaskHandle, 0);
}

void readerTask(void *param)
{
    I2SSampler *sampler = (I2SSampler *)param;
    while (true)
    {
        // wait for some data to arrive on the queue
        i2s_event_t evt;
        if (xQueueReceive(sampler->i2s_queue, &evt, portMAX_DELAY) == pdPASS)
        {
            if (evt.type == I2S_EVENT_RX_DONE)
            {
                size_t bytesRead = 0;
                do
                {
                    // try and fill up our audio buffer
                    size_t bytesToRead = (ADC_SAMPLES_COUNT - sampler->audioBufferPos) * 2;
                    void *bufferPosition = (void *)(sampler->currentAudioBuffer + sampler->audioBufferPos);
                    // read from i2s
                    i2s_read(I2S_NUM_0, bufferPosition, bytesToRead, &bytesRead, 10 / portTICK_PERIOD_MS);
                    sampler->audioBufferPos += bytesRead / 2;
                    if (sampler->audioBufferPos == ADC_SAMPLES_COUNT)
                    {
                        // do something with the sample - e.g. notify another task to do some processing
                        memcpy(transmitBuffer, sampler->currentAudioBuffer, ADC_SAMPLES_COUNT); // copy buffer into a second buffer
                        transmitNow = true; // sets the value true so we know that we can transmit now
                    }
                } while (bytesRead > 0);
            }
        }
    }
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
          client.write((const uint8_t *)transmitBuffer, sizeof(transmitBuffer)); // sending the buffer to our server
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

