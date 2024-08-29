//const int buzzer = 16;  // Buzzer pin


void setup() {
  pinMode(16, OUTPUT);
  pinMode(17, OUTPUT);
//  digitalWrite(LED_BUILTIN, LOW);   // turn the LED on (HIGH is the voltage level)
//
//  pinMode(LED_BUILTIN, OUTPUT);
//  digitalWrite(LED_BUILTIN, LOW);   // turn the LED on (HIGH is the voltage level)
}
 
void loop()
{
  digitalWrite(16, HIGH);
  tone(16, 31);
  digitalWrite(17, HIGH);
  tone(17, 31);
  delay(1000);
  noTone(16);
  digitalWrite(16, LOW);
  noTone(17);
  digitalWrite(17, LOW);
  delay(1000);
}
