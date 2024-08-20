const int buzzer = 16;  // Buzzer pin


void setup() {
  pinMode(buzzer, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);   // turn the LED on (HIGH is the voltage level)

  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);   // turn the LED on (HIGH is the voltage level)
}
 
void loop()
{
  digitalWrite(buzzer, HIGH);
  tone(buzzer, 1);
  delay(5000);
  noTone(buzzer);
  digitalWrite(buzzer, LOW);
}
