#include <driver/adc.h>
#include "esp_adc_cal.h"
int static_variable = 1024;

esp_adc_cal_characteristics_t *adc_chars;
void setup() {
  // put your setup code here, to run once:
  Serial.begin(115200);

  // adc1_config_width(ADC_WIDTH_12Bit); // configure the analogue to digital converter
  // adc1_config_channel_atten(ADC1_CHANNEL_7, ADC_ATTEN_11db); 
}

void loop() {
  // put your main code here, to run repeatedly:
  
  Serial.print("ADC Signal:");

  Serial.print(analogRead(35), DEC);

  Serial.print(",");

  Serial.print("Static Variable:");

  Serial.println(static_variable, DEC);
}
