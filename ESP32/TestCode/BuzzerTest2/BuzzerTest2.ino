//const int buzzer = 16;  // Buzzer pin


void setup() {
  pinMode(16, OUTPUT);
  pinMode(17, OUTPUT);
  pinMode(32, OUTPUT);
  pinMode(33, OUTPUT);

  digitalWrite(16, HIGH);
  digitalWrite(17, HIGH);
  digitalWrite(32, HIGH);
  digitalWrite(33, HIGH);
}
 
void loop()
{
  tone(16, 100, 600);
  tone(17, 100, 600);
  tone(32, 100, 600);
  tone(33, 100, 600);
}
