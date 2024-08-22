#include <driver/adc.h>
int static_variable = 1024;
void setup() {
  // put your setup code here, to run once:
  Serial.begin(115200);

  adc1_config_width(ADC_WIDTH_12Bit); // configure the analogue to digital converter
  adc1_config_channel_atten(ADC1_CHANNEL_7, ADC_ATTEN_0db); // connects the ADC 1 with channel 0 (GPIO 36)
}

void loop() {
  // put your main code here, to run repeatedly:
  
  Serial.print("ADC Signal:");

  Serial.print(adc1_get_raw(ADC1_CHANNEL_7), DEC);

  Serial.print(",");

  Serial.print("Static Variable:");

  Serial.println(static_variable, DEC);
}
