//#include <Arduino.h>
#include <M5StickC.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include "BLE2902.h"

#define CMD_TOAST         0x0c
#define CMD_SENSOR_MASK   0x0e
#define CMD_TEXT          0x30

#define RSP_ACK           0x00
#define RSP_GYROSCOPE     0x14
#define RSP_ACCELEROMETER 0x15
#define RSP_BUTTON_EVENT  0x18

#define BTNID_FN_BASE   0x20

bool isGyroscope = false;
bool isAccelerometer = false;

void sendBuffer(uint8_t *p_value, uint16_t len);
void processCommaind(void);

#define UUID_SERVICE "08030900-7d3b-4ebf-94e9-18abc4cebede"
#define UUID_WRITE "08030901-7d3b-4ebf-94e9-18abc4cebede"
#define UUID_READ "08030902-7d3b-4ebf-94e9-18abc4cebede"
#define UUID_NOTIFY "08030903-7d3b-4ebf-94e9-18abc4cebede"

#define CAP_GYROSCOPE     0x00000002
#define CAP_ACCELEROMETER 0x00000004
#define CAP_BUTTON        0x00000040

BLECharacteristic *pCharacteristic_write;
BLECharacteristic *pCharacteristic_read;
BLECharacteristic *pCharacteristic_notify;

bool connected = false;

class MyCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer){
    connected = true;
    Serial.println("Connected\n");
    M5.Lcd.fillScreen(BLACK);
    M5.Lcd.setTextSize(2);
    M5.Lcd.setCursor(0, 0);
    M5.Lcd.print("Connected");
  }

  void onDisconnect(BLEServer* pServer){
    connected = false;
    isGyroscope = false;
    isAccelerometer = false;

    BLE2902* desc = (BLE2902*)pCharacteristic_notify->getDescriptorByUUID(BLEUUID((uint16_t)0x2902));
    desc->setNotifications(false);

    Serial.println("Disconnected\n");
    M5.Lcd.fillScreen(BLACK);
    M5.Lcd.setTextSize(2);
    M5.Lcd.setCursor(0, 0);
    M5.Lcd.print("Disconnected");
  }
};

unsigned short recv_len = 0;
unsigned short expected_len = 0;
unsigned char expected_slot = 0;
unsigned char recv_buffer[1024];

class MyCharacteristicCallbacks : public BLECharacteristicCallbacks{
  void onWrite(BLECharacteristic* pCharacteristic){
    Serial.println("onWrite");
    uint8_t* value = pCharacteristic->getData();
    std::string str = pCharacteristic->getValue(); 

    if( expected_len > 0 && value[0] != expected_slot )
        expected_len = 0;

    if( expected_len == 0 ){
      if( value[0] != 0x83 )
          return;
      recv_len = 0;
      expected_len = (value[1] << 8) | value[2];
      memmove(&recv_buffer[recv_len], &value[3], str.length() - 3);
      recv_len += str.length() - 3;
      expected_slot = 0;
      if( recv_len < expected_len )
        return;
    }else{
      memmove(&recv_buffer[recv_len], &value[1], str.length() - 1);
      recv_len += str.length() - 1;
      expected_slot++;
      if( recv_len < expected_len )
        return;
    }
    expected_len = 0;

    processCommaind();
  }
/*
  void onStatus(BLECharacteristic* pCharacteristic, Status s, uint32_t code){
  }
  void BLECharacteristicCallbacks::onRead(BLECharacteristic *pCharacteristic){
  };
*/
}

int str_split(char *p_data, char delimiter, char **pp_array, int max_num){
    int index = 0;
    int datalength = strlen(p_data);
    pp_array[0] = &p_data[0];
    for (int i = 0; i < datalength; i++) {
        char tmp = p_data[i];
        if ( tmp == delimiter ) {
          if( (index + 1) >= max_num )
            break;
          p_data[i] = '\0';
          index++;
          pp_array[index] = &p_data[i + 1];
        }
    }
    return (index + 1);
}

void processCommaind(void){
  switch(recv_buffer[0]){
    case CMD_SENSOR_MASK:{
      isGyroscope = (recv_buffer[1] & 0x02);
      isAccelerometer = (recv_buffer[1] & 0x04);
      break;
    }
    case CMD_TEXT:{
      recv_buffer[recv_len + 1] = '\0';
      char *arry[4];
      int num = str_split((char*)&recv_buffer[1], ',', arry, 4);
      int font = atoi(arry[0]);
      if( font <= 0 ){
        M5.Lcd.fillScreen(BLACK);
      }else{
        Serial.println(num);
        if( num < 4 )
          break;
        int x = atoi(arry[1]);
        int y = atoi(arry[2]);
        Serial.println(x);
        Serial.println(y);
        Serial.println(arry[3]);
        M5.Lcd.setTextSize(font);
        M5.Lcd.setCursor(x, y);
        M5.Lcd.print(arry[3]);
      }
      break;
    }
  }
  uint8_t ack = RSP_ACK;
  sendBuffer(&ack, 1);
}


 
#define UUID_VALUE_SIZE 20
uint8_t value_write[UUID_VALUE_SIZE];
uint32_t capability = CAP_BUTTON | CAP_GYROSCOPE | CAP_ACCELEROMETER;
uint8_t value_read[] = { (uint8_t)((UUID_VALUE_SIZE >> 8) & 0xff), (uint8_t)(UUID_VALUE_SIZE & 0xff), 
                      (uint8_t)((capability >> 24) & 0xff), (uint8_t)((capability >> 16) & 0xff), (uint8_t)((capability >> 8) & 0xff), (uint8_t)(capability & 0xff) };

void taskServer(void*) {
  BLEDevice::init("M5Stick-C");

  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyCallbacks());

  BLEService *pService = pServer->createService(UUID_SERVICE);

  pCharacteristic_write = pService->createCharacteristic( UUID_WRITE, BLECharacteristic::PROPERTY_WRITE );
  pCharacteristic_write->setAccessPermissions(ESP_GATT_PERM_WRITE);
  pCharacteristic_write->setValue(value_write, sizeof(value_write));
  pCharacteristic_write->setCallbacks(new MyCharacteristicCallbacks());

  pCharacteristic_read = pService->createCharacteristic( UUID_READ, BLECharacteristic::PROPERTY_READ );
  pCharacteristic_read->setAccessPermissions(ESP_GATT_PERM_READ);
  pCharacteristic_read->setValue(value_read, sizeof(value_read));

  pCharacteristic_notify = pService->createCharacteristic( UUID_NOTIFY, BLECharacteristic::PROPERTY_NOTIFY );
  pCharacteristic_notify->addDescriptor(new BLE2902());

  pService->start();

  BLEAdvertising *pAdvertising = pServer->getAdvertising();
  pAdvertising->addServiceUUID(UUID_SERVICE);
  pAdvertising->start();

  vTaskDelay(portMAX_DELAY); //delay(portMAX_DELAY);
}

void setFloatBytes(float f, uint8_t *p_bin){
  uint8_t *p_ptr = (uint8_t*)&f;

  p_bin[0] = p_ptr[3];
  p_bin[1] = p_ptr[2];
  p_bin[2] = p_ptr[1];
  p_bin[3] = p_ptr[0];
}

void sendBuffer(uint8_t *p_value, uint16_t len){
  Serial.println("SendBuffer");
  
  BLE2902* desc = (BLE2902*)pCharacteristic_notify->getDescriptorByUUID(BLEUUID((uint16_t)0x2902));
  if( !desc->getNotifications() )
    return;

  int offset = 0;
  int slot = 0;
  int packet_size = 0;
  do{
    if( offset == 0){
      value_write[0] = 0x83;
      value_write[1] = (len >> 8) & 0xff;
      value_write[2] = len & 0xff;
      packet_size = len - offset;
      if( packet_size > (UUID_VALUE_SIZE - 3) )
        packet_size = UUID_VALUE_SIZE - 3;
      memmove(&value_write[3], &p_value[offset], packet_size);

      offset += packet_size;
      packet_size += 3;

    }else{
      value_write[0] = slot++;
      packet_size = len - offset;
      if( packet_size > (UUID_VALUE_SIZE - 1) )
        packet_size = UUID_VALUE_SIZE - 1;
      memmove(&value_write[1], &p_value[offset], packet_size);

      offset += packet_size;
      packet_size += 1;
    }
    
    pCharacteristic_notify->setValue(value_write, packet_size);
    pCharacteristic_notify->notify();

  }while(packet_size >= UUID_VALUE_SIZE);  
}

void sendButton(uint8_t btn){
  uint8_t send_buffer[2];
  send_buffer[0] = RSP_BUTTON_EVENT;
  send_buffer[1] = btn;
  sendBuffer(send_buffer, sizeof(send_buffer));
}

void sendIMU(){
  float gyroX, gyroY, gyroZ;
  float acclX, acclY, acclZ;

  M5.IMU.getGyroData(&gyroX, &gyroY, &gyroZ);
  M5.IMU.getAccelData(&acclX, &acclY, &acclZ);

  M5.Lcd.fillScreen(BLACK);
  M5.Lcd.setTextSize(1);
  M5.Lcd.setCursor(0, 0);
  M5.Lcd.printf("g:%+4.2f %+4.2f %+4.2f\n", gyroX, gyroY, gyroZ);
  M5.Lcd.printf("a:%+4.2f %+4.2f %+4.2f\n", acclX, acclY, acclZ);

  uint8_t sensor_buffer[13];

  if( isGyroscope ){
    sensor_buffer[0] = RSP_GYROSCOPE;
    setFloatBytes(gyroX, &sensor_buffer[1]);
    setFloatBytes(gyroY, &sensor_buffer[5]);
    setFloatBytes(gyroZ, &sensor_buffer[9]);
    sendBuffer(sensor_buffer, sizeof(sensor_buffer));
  }
  if( isAccelerometer ){
    sensor_buffer[0] = RSP_ACCELEROMETER;
    setFloatBytes(acclX, &sensor_buffer[1]);
    setFloatBytes(acclY, &sensor_buffer[5]);
    setFloatBytes(acclZ, &sensor_buffer[9]);
    sendBuffer(sensor_buffer, sizeof(sensor_buffer));
  }
}

void setup() {
  M5.begin();

  M5.IMU.Init();
  M5.Axp.ScreenBreath(9);

  M5.Lcd.setRotation(3);
  M5.Lcd.fillScreen(BLACK);
  M5.Lcd.setTextSize(2);
  M5.Lcd.println("General Button");
  delay(1000);

//  M5.Lcd.println("start Serial");
  Serial.begin(9600);
  Serial.println("setup");

  xTaskCreate(taskServer, "server", 20000, NULL, 5, NULL);
}

uint32_t prevTime = 0;
#define SENSOR_DELAY  100

void loop() {
  M5.update();

  if ( M5.BtnA.wasPressed() )
    sendButton(BTNID_FN_BASE + 0);

  if ( M5.BtnB.wasPressed() )
    sendButton(BTNID_FN_BASE + 1);

  if( isGyroscope || isAccelerometer ){
    long time = millis();
    if( time - prevTime > SENSOR_DELAY ){
      sendIMU();
      prevTime = time;
    }
  }
}
