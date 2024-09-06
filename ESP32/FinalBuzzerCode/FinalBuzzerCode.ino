#include <Arduino.h>
#include <BlockNot.h>   

void setup() {
  // Configure the buzzers

  // L (front)
  pinMode(16, OUTPUT);
  pinMode(17, OUTPUT);
  digitalWrite(16, HIGH);
  digitalWrite(17, HIGH);

  // R (back)
  pinMode(32, OUTPUT);
  pinMode(33, OUTPUT);
  digitalWrite(32, HIGH);
  digitalWrite(33, HIGH);

  
  Serial.begin(115200);
}

void loop() {
  // To store buzzer commands sent via TCP from the Android app
  int buzzerData[4] = {9,9,9,9};

  int i = 0;
  while (Serial.available()) {
    // Read one character from the serial buffer sent by the client
    char serialChar = Serial.read(); 

    // Populate the buzzerData array
    if (i < 3) {
      buzzerData[i] = serialChar;
      i++;
    }
    else if (i == 3) { 
      buzzerData[i] = serialChar;
      i = 0;
    }
  }  

  
  // Play buzzer if applicable
  // If all 4 values in the buzzerData array aren't 9, it means that they have been populated via Serial.
  if (buzzerData[0] != 9 && buzzerData[1] != 9 && buzzerData[2] != 9 && buzzerData[3] != 9) {
    // L
    noTone(16);
    noTone(17);

    // R
    noTone(32);
    noTone(33);

    // L
    if (buzzerData[0] != 4) {
      tone(16, exp(buzzerData[0] * 2) + 30, 700);
      tone(17, exp(buzzerData[0] * 2) + 30, 700);
    }
   
    // R
    if (buzzerData[2] != 4) {
      tone(32, exp(buzzerData[2] * 2) + 30, 700);
      tone(33, exp(buzzerData[2] * 2) + 30, 700);
    }
  }

}  

