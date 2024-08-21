const int buzzer = 16;  // Buzzer pin


void setup() {
  pinMode(buzzer, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);   // turn the LED on (HIGH is the voltage level)

  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);   // turn the LED on (HIGH is the voltage level)
}
 
void loop()
{
  digitalWrite(16, HIGH);
  tone(16, 31);
//  digitalWrite(17, HIGH);
//  tone(17, 31);
//  delay(5000);
//  noTone(buzzer);
//  digitalWrite(buzzer, LOW);
}
