#include <Arduino.h>
#include <BlockNot.h>   

// timer
BlockNot secondTimer(400);

void setup() {
  // Configure the buzzers
  pinMode(16, OUTPUT);
  pinMode(17, OUTPUT);
  
  Serial.begin(115200);

  secondTimer.STOP;
}

// Whether buzzing should be continuous
bool continuousBuzzing = true;

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
  // If all 4 values in the buzzerData array aren't 9, it means that they have been populated via TCP.
  // TODO: Move this to a separate timer so it doesn't block TCP operations in loop()
  if (buzzerData[0] != 9 && buzzerData[1] != 9 && buzzerData[2] != 9 && buzzerData[3] != 9) {
    noTone(16);
    digitalWrite(16, LOW);

    noTone(17);
    digitalWrite(17, LOW);

    // L
    if (buzzerData[0] != 4) {
      digitalWrite(16, HIGH);
      tone(16, exp(buzzerData[0] * 2) + 30);
    }
    
    //R
    if (buzzerData[2] != 4) {
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

