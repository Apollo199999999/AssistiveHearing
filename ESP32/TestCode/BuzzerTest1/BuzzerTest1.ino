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
  tone(16, 100, 400);
  tone(17, 100, 400);
  tone(32, 100, 400);
  tone(33, 100, 400);
}
