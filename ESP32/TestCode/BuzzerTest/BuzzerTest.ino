//const int buzzer = 16;  // Buzzer pin


void setup() {
  pinMode(16, OUTPUT);
  pinMode(17, OUTPUT);
  pinMode(32, OUTPUT);
  pinMode(33, OUTPUT);
//  digitalWrite(LED_BUILTIN, LOW);   // turn the LED on (HIGH is the voltage level)
//
//  pinMode(LED_BUILTIN, OUTPUT);
//  digitalWrite(LED_BUILTIN, LOW);   // turn the LED on (HIGH is the voltage level)
}
 
void loop()
{
  digitalWrite(16, HIGH);
  tone(16, 31, 400);
  digitalWrite(17, HIGH);
  tone(17, 31, 400);
  digitalWrite(32, HIGH);
  tone(32, 31, 400);
  digitalWrite(33, HIGH);
  tone(33, 31, 400);


  delay(1000);
}
