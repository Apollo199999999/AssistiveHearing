/* The true ESP32 chip ID is essentially its MAC address.
This sketch provides an alternate chip ID that matches 
the output of the ESP.getChipId() function on ESP8266 
(i.e. a 32-bit integer matching the last 3 bytes of 
the MAC address. This is less unique than the 
MAC address chip ID, but is helpful when you need 
an identifier that can be no more than a 32-bit integer 
(like for switch...case).

created 2020-06-07 by cweinhofer
with help from Cicicok */
	
uint32_t chipId = 0;

void setup() {
  delay(500);
  Serial.begin(115200);
  delay(500);
  Serial.println("\n\n================================");
  Serial.printf("Chip Model: %s\n", ESP.getChipModel());
  Serial.println("================================");

#ifdef EXTERNAL_NUM_INTERRUPTS
  Serial.printf("EXTERNAL_NUM_INTERRUPTS = %d\n", EXTERNAL_NUM_INTERRUPTS);
#endif
#ifdef NUM_DIGITAL_PINS
  Serial.printf("NUM_DIGITAL_PINS = %d\n", NUM_DIGITAL_PINS);
#endif
#ifdef NUM_ANALOG_INPUTS
  Serial.printf("NUM_ANALOG_INPUTS = %d\n", NUM_ANALOG_INPUTS);
#endif
  Serial.println();
  Serial.printf("Default TX:   %d\n", TX);
  Serial.printf("Default RX:   %d\n", RX);
  Serial.println();
  Serial.printf("Default SDA:  %d\n", SDA);
  Serial.printf("Default SCL:  %d\n", SCL);
  Serial.println();
  Serial.printf("Default SS:   %d\n", SS);
  Serial.printf("Default MOSI: %d\n", MOSI);
  Serial.printf("Default MISO: %d\n", MISO);
  Serial.printf("Default SCK:  %d\n", SCK);
  Serial.println();
  Serial.printf("Default A0:   %d\n", A0);
/*  Serial.printf("Default A1:   %d\n", A1); // No in ESP32-WROVER-B
  Serial.printf("Default A2:   %d\n", A2);
  Serial.printf("Default A3:   %d\n", A3);
  Serial.printf("Default A4:   %d\n", A4);
  Serial.printf("Default A5:   %d\n", A5); */
  Serial.println("================================");  
}

void loop() {
	for(int i=0; i<17; i=i+8) {
	  chipId |= ((ESP.getEfuseMac() >> (40 - i)) & 0xff) << i;
	}

	Serial.printf("ESP32 Chip model = %s Rev %d\n", ESP.getChipModel(), ESP.getChipRevision());
	Serial.printf("This chip has %d cores\n", ESP.getChipCores());
  Serial.print("Chip ID: "); Serial.println(chipId);
  
	delay(3000);

}
