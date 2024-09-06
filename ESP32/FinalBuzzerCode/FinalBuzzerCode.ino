#include <Arduino.h>

void setup() {
  // Configure the buzzers

  // L (front)
  pinMode(16, OUTPUT);
  pinMode(17, OUTPUT);

  // R (back)
  pinMode(32, OUTPUT);
  pinMode(33, OUTPUT);
  
  Serial.begin(115200);
}

// To store buzzer commands sent via TCP from the Android app
int buzzerData[4] = {9,9,9,9};
int i = 0;

void loop() {
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
    digitalWrite(16, LOW);
    noTone(16);
    digitalWrite(17, LOW);
    noTone(17);

    // R
    digitalWrite(32, LOW);
    noTone(32);
    digitalWrite(33, LOW);
    noTone(33);

    // L
    if (buzzerData[0] != 4) {
      digitalWrite(16, HIGH);
      tone(16, exp(buzzerData[0] * 2) + 30);

      if (buzzerData[0] > 1) {
        digitalWrite(17, HIGH);
        tone(17, exp(buzzerData[0] * 2) + 30);
      }
    }
   
    // R
    if (buzzerData[2] != 4) {
      digitalWrite(32, HIGH);
      tone(32, exp(buzzerData[2] * 2) + 30);

      if (buzzerData[2] > 1) {
        digitalWrite(33, HIGH);
        tone(33, exp(buzzerData[2] * 2) + 30);
      }
    }

    if (buzzerData[1] == 0 && buzzerData[3] == 0) {
        buzzerData[0] = 9;
        buzzerData[1] = 9;
        buzzerData[2] = 9;
        buzzerData[3] = 9;

        delay(400);

        // L
        digitalWrite(16, LOW);
        noTone(16);
        digitalWrite(17, LOW);
        noTone(17);

        // R
        digitalWrite(32, LOW);
        noTone(32);
        digitalWrite(33, LOW);
        noTone(33);
    }
    else {
      buzzerData[0] = 9;
      buzzerData[1] = 9;
      buzzerData[2] = 9;
      buzzerData[3] = 9;
    }
    
  }

}  

