#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <WiFi.h>
#include <Preferences.h>
#include "freertos/FreeRTOS.h"
#include <ArduinoJson.h>
#include <SoftwareSerial.h>
#include <esp_camera.h>
#include <PubSubClient.h>
// #include <esp_core_dump.h>

// // BLE 服务 UUID 和特征 UUID
// #define SERVICE_UUID        "32000001"
// #define CHARACTERISTIC_UUID_SSID "320100001"
// #define CHARACTERISTIC_UUID_PASS "320100002"

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID_SSID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CHARACTERISTIC_UUID_PASS "d85093ce-0c8f-4ba6-b977-9b0c6d0f6e95"

BLEServer *pServer;
BLEService *pService;
BLECharacteristic *pCharacteristicSSID;
BLECharacteristic *pCharacteristicPASS;

String ssid = "";
String password = "";
bool wifiCredentialsReceived = false;
bool deviceConnected = false;
bool wifiConnected = false;
static int wifi_try_count = 0;
static int mqtt_try_count = 0;

// 物理UART已被全部占用，创建软窗口选择两个空闲的GPIO引脚作为 RX 和 TX 433
#define SOFT_RX_PIN 42
#define SOFT_TX_PIN 41

// 创建软件串口对象
SoftwareSerial mySoftSerial(SOFT_RX_PIN, SOFT_TX_PIN); //输出433信号

#define SOFT_315_RX_PIN 21
#define SOFT_315_TX_PIN 20

// 创建软件串口对象
SoftwareSerial mySoftSerial2(SOFT_315_RX_PIN, SOFT_315_TX_PIN); //输出315信号


//摄像头引脚定义
#define PWDN_GPIO_NUM -1
#define RESET_GPIO_NUM -1
#define XCLK_GPIO_NUM 15
#define SIOD_GPIO_NUM 4
#define SIOC_GPIO_NUM 5
#define Y9_GPIO_NUM 16
#define Y8_GPIO_NUM 17
#define Y7_GPIO_NUM 18
#define Y6_GPIO_NUM 12
#define Y5_GPIO_NUM 10
#define Y4_GPIO_NUM 8
#define Y3_GPIO_NUM 9
#define Y2_GPIO_NUM 11
#define VSYNC_GPIO_NUM 6
#define HREF_GPIO_NUM 7
#define PCLK_GPIO_NUM 13
// camera_fb_t *pic = NULL;

// 卷帘门三个模式数据，预设的7字节数据：FD + 5个数据字节 + DF
byte iron_upData[7] = {0xFD, 0x03, 0x71, 0x01, 0x03, 0x43, 0xDF};
byte iron_downData[7] = {0xFD, 0x03, 0x71, 0x01, 0xC0, 0x41, 0xDF};
byte iron_stopData[7] = {0xFD, 0x03, 0x71, 0x01, 0x30, 0x41, 0xDF};
byte glass_only_out[7] = {0xFD, 0x03, 0xC1, 0x11, 0x30, 0x74, 0xDF};//B
byte glass_in_out[7] = {0xFD, 0x03, 0xC1, 0x11, 0x03, 0x74, 0xDF};//d
byte glass_close[7] = {0xFD, 0x03, 0xC1, 0x11, 0xC0, 0x74, 0xDF};//A
byte glass_open[7] = {0xFD, 0x03, 0xC1, 0x11, 0x0C, 0x74, 0xDF};//C
byte light_left[7] = {0xFD, 0x04, 0x06, 0x05, 0xD8, 0x62, 0xDF};//left
byte light_right[7] = {0xFD, 0x04, 0x06, 0x05, 0xD1, 0x62, 0xDF};//right

//MQTT配置
//本地服务器
// const char* mqtt_server = "192.168.10.55";
//尚源服务器
const char* mqtt_server = "47.122.129.16";
const int mqtt_port = 1883;
const char* mqtt_user = "esp32-002";
const char* mqtt_password = "esp32-device-002";
const char* topic_video = "esp32/camera/video";
const char* topic_video_control = "esp32/camera/control";
//第二个设备的视频主题
// const char* topic_video_control = "esp32-002/camera/control";
const char* topic_door_control = "esp32/door/control";
const char* topic_light_control = "esp32/light/control";

WiFiClient espClient;
PubSubClient mqtt_client(espClient);
bool streamEnabled = false;
unsigned long lastFrameTime = 0;
const int frameInterval = 300; //帧间隔(ms)

Preferences preferences;
// String ori_wifi_uuid = "hjsx_2.4G";
// String ori_wifi_passwd = "hjsx@888888";

String current_wifi_uuid = "";
String current_wifi_passwd = "";
void mqtt_callback(char* topic,byte* payload,unsigned int length){
  String message = "";
  for(int i=0;i<length;i++){
    message += (char)payload[i];
  }
  if(String(topic) == topic_video_control){
    streamEnabled = (message == "on");
    Serial.println("Stream:"+String(streamEnabled ? "ON":"OFF"));
    //发送确认消息
    mqtt_client.publish("esp32/camera/status",streamEnabled ?"on":"off");
  }
  //如果收到的订阅消息类型是门控操作命令，根据收到的卷帘门操作行为，控制无线信号发送

  if(String(topic) == topic_door_control){
    if(message == "iron_up"){
      mySoftSerial.write(iron_upData, 7);
      Serial.println("卷帘门打开");
    }else if(message == "iron_down"){
      mySoftSerial.write(iron_downData, 7);
      Serial.println("卷帘门关闭");
    }else if(message == "iron_stop"){
      mySoftSerial.write(iron_stopData, 7);
      Serial.println("卷帘门暂停");
    }else if(message == "glass_in_out"){
      mySoftSerial2.write(glass_in_out, 7);
      Serial.println("玻璃门双向");
    }else if(message == "glass_only_out"){
      mySoftSerial2.write(glass_only_out, 7);
      Serial.println("玻璃门只出不进");
    }else if(message == "glass_close"){
      mySoftSerial2.write(glass_close, 7);
      Serial.println("玻璃门关闭");
    }else if(message == "glass_open"){
      mySoftSerial2.write(glass_open, 7);
      Serial.println("玻璃门全开");
    }else{
      return;
    }
  }
  if(String(topic) == topic_light_control){
    if(message == "light_left"){
      mySoftSerial.write(light_left, 7);
      Serial.println("灯控左键");
    }else if(message == "light_right"){
      mySoftSerial.write(light_right, 7);
      Serial.println("灯控右键");
    }
    else{
      return;
    }
  }
}
void mqtt_reconnect(){
  while(!mqtt_client.connected()){
    Serial.print("Attempting MQTT connection...");
    mqtt_client.setKeepAlive(30);
    if(mqtt_client.connect("ESP32CameraClient",mqtt_user,mqtt_password)){
      Serial.println("connected");
      mqtt_client.subscribe(topic_video_control);
      mqtt_client.publish("esp32/camera/status","connected");
      mqtt_client.subscribe(topic_door_control);
      mqtt_client.subscribe(topic_light_control);
    }else{
      Serial.print("MQTT connection failed,rc=");
      Serial.print(mqtt_client.state());
      Serial.println("try again in 5 secondes");
      delay(5000);
    }
  }
}


class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("设备已连接");
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("设备断开连接");
      BLEDevice::startAdvertising();
    }
};

class CharacteristicCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      // std::string value = pCharacteristic->getValue();
      String value = pCharacteristic->getValue();
      
      if (value.length() > 0) {
        // Serial.println(pCharacteristic->getUUID().toString());
        if (pCharacteristic->getUUID().toString() == CHARACTERISTIC_UUID_SSID) {
          ssid = value.c_str();
          Serial.println("收到SSID: " + ssid);
        } 
        else if (pCharacteristic->getUUID().toString() == CHARACTERISTIC_UUID_PASS) {
          password = value.c_str();
          Serial.println("收到密码");
          wifiCredentialsReceived = true;
        }
      }
    }
};
void cameraImageUpload(){
  unsigned long currentTime = millis();
  //控制图像上传帧率
  if(currentTime - lastFrameTime < frameInterval){
    return;
  }
  lastFrameTime = currentTime;
  if(streamEnabled){
    camera_fb_t *fb = esp_camera_fb_get();
        if (fb){
          if(mqtt_client.beginPublish(topic_video,fb->len,false)){
            mqtt_client.write(fb->buf,fb->len);
            mqtt_client.endPublish();
            Serial.printf("Published frame:%d bytes\n",fb->len);
          }else{
            Serial.println("Publish failed");
          }
          esp_camera_fb_return(fb);
        }else{
          Serial.println("Camera capture failed");
        }
  }
}

void initCamera(){
  //摄像头初始化
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  // config.pixel_format = PIXFORMAT_JPEG;
  // config.frame_size = FRAMESIZE_XGA;// 1024x768
  // config.frame_size = FRAMESIZE_HVGA;// 480x320
  config.frame_size = FRAMESIZE_VGA; // 640x480
  // config.frame_size = FRAMESIZE_SVGA; // 800x600
  // config.frame_size = FRAMESIZE_UXGA; // 1600x1200
  // config.frame_size = FRAMESIZE_QVGA;// 320x240
  config.jpeg_quality = 12;
  config.fb_count = 2;

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x", err);
    return;
  }
  //图像旋转180度
  sensor_t *sensor = esp_camera_sensor_get();
  sensor->set_hmirror(sensor,1);//启用水平镜像
  sensor->set_vflip(sensor,1);//启用垂直反转
}

void restartTask(void *params){
  vTaskDelay(10);
  while(1){
    if(wifi_try_count >= 500){
      ESP.restart();
    }
    if(mqtt_try_count >= 500){
      ESP.restart();
    }
    vTaskDelay(1000);
  }
}
void setup() {
  delay(1000);
  Serial.begin(115200);
  mySoftSerial.begin(9600); // 软件串口建议使用较低波特率ySoftSerial      // 初始化串口，波特率115200
  mySoftSerial2.begin(9600);
  // 初始化BLE
  BLEDevice::init("ESP32-WiFi配置器");
  // 创建服务器
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  // 创建服务
  pService = pServer->createService(SERVICE_UUID); 
  // 创建特征
  pCharacteristicSSID = pService->createCharacteristic(
                           CHARACTERISTIC_UUID_SSID,
                           BLECharacteristic::PROPERTY_WRITE
                         );
  pCharacteristicPASS = pService->createCharacteristic(
                           CHARACTERISTIC_UUID_PASS,
                           BLECharacteristic::PROPERTY_WRITE
                         );
  
  // 设置回调
  pCharacteristicSSID->setCallbacks(new CharacteristicCallbacks());
  pCharacteristicPASS->setCallbacks(new CharacteristicCallbacks());
  
  // 启动服务
  pService->start();
  
  // 开始广播
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);

  preferences.begin("wifi-config",false);
  // preferences.putString("uuid",ori_wifi_uuid);
  // preferences.putString("passwd",ori_wifi_passwd);

  current_wifi_uuid = preferences.getString("uuid","");
  current_wifi_passwd = preferences.getString("passwd","");

  if(current_wifi_uuid !="" && current_wifi_passwd !=""){
    WiFi.begin(current_wifi_uuid, current_wifi_passwd);
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 10) {
      delay(500);
      Serial.print(".");
      attempts++;
    }
  }
  
  //如果使用默认的wifi配置连接wifi失败，开启蓝牙连接
  if (WiFi.status() != WL_CONNECTED){
      BLEDevice::startAdvertising();
      Serial.println("等待手机连接并发送WiFi凭证...");
  }
  
    //初始化摄像头
  initCamera();
  xTaskCreate(restartTask, "restartTask", 2048, NULL, 3, NULL);

}

void loop() {
  
  if (wifiCredentialsReceived) {
    wifiCredentialsReceived = false;
    
    Serial.println("尝试连接WiFi...");
    Serial.println(ssid.c_str());
    Serial.println(password.c_str());
    
    WiFi.begin(ssid.c_str(), password.c_str());
    
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 20) {
      delay(500);
      Serial.print(".");
      wifi_try_count++;
      attempts++;
    }
    
    if (WiFi.status() == WL_CONNECTED) {
      preferences.putString("uuid",ssid.c_str());
      preferences.putString("passwd",password.c_str());
      Serial.println("\nWiFi连接成功!");
      Serial.print("IP地址: ");
      Serial.println(WiFi.localIP());
      BLEDevice::stopAdvertising();
      BLEDevice::deinit();
      Serial.println("BLE已关闭");
      
    } else {
      Serial.println("\nWiFi连接失败，请检查凭证");
      BLEDevice::startAdvertising();
    }
  }
  if((WiFi.status() == WL_CONNECTED) && (!mqtt_client.connected())){
    //设置MQTT
    mqtt_client.setServer(mqtt_server,mqtt_port);
    mqtt_client.setCallback(mqtt_callback);
    mqtt_reconnect();
    mqtt_try_count++;
    
  }else if((WiFi.status() == WL_CONNECTED) && (mqtt_client.connected())){
    mqtt_client.loop();
    cameraImageUpload();
  }
}