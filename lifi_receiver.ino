#include <Wire.h>
#include <LiquidCrystal_I2C.h>

LiquidCrystal_I2C lcd(0x27, 16, 2);

const int LDR_PIN = A0;
int threshold = 500; 

// Morse Timing Constants
const int DOT_MAX = 180;    
const int DASH_MAX = 400;
const int LETTER_GAP = 450;
const int WORD_GAP = 900;

// Auto Reset Timer
unsigned long lastCharTime = 0;
const int RESET_DELAY = 10000; // 10 seconds

unsigned long pulseStart = 0;
unsigned long offStart = 0;
bool isLightOn = false;

String currentMorse = "";
String decodedText = "";

void setup() {
  Serial.begin(9600);
  
  // Use internal pull-up
  pinMode(LDR_PIN, INPUT_PULLUP); 
  
  lcd.init();
  lcd.backlight();
  lcd.print("5S Inventors..!");
  delay(3000);
  lcd.clear();
  lcd.print("Li-Fi Ready...");

  // Calibration
  int ambient = analogRead(LDR_PIN);
  threshold = ambient - 200; 
  
  Serial.print("Ambient: "); Serial.println(ambient);
  Serial.print("Threshold: "); Serial.println(threshold);
}

void loop() {
  int ldrValue = analogRead(LDR_PIN);
  unsigned long now = millis();

  // LIGHT DETECTED
  if (ldrValue < threshold) {
    if (!isLightOn) {
      pulseStart = now;
      isLightOn = true;
      
      unsigned long offDuration = now - offStart;

      if (offDuration >= WORD_GAP) {
        if (decodedText.length() > 0 && decodedText.charAt(decodedText.length()-1) != ' ') {
          updateLCD(' ');
        }
      } 
      else if (offDuration >= LETTER_GAP) {
        decodeChar();
      }
    }
  } 
  // DARKNESS
  else {
    if (isLightOn) {
      unsigned long pulseDuration = now - pulseStart;
      isLightOn = false;
      offStart = now;

      if (pulseDuration > 40 && pulseDuration < DOT_MAX) {
        currentMorse += ".";
      } 
      else if (pulseDuration >= DOT_MAX && pulseDuration < DASH_MAX) {
        currentMorse += "-";
      }

      Serial.println("Current Morse: " + currentMorse);
    }
    
    // Auto decode after pause
    if (now - offStart > WORD_GAP && currentMorse != "") {
      decodeChar();
    }
  }

  // AUTO RESET AFTER 10 SECONDS
  if (decodedText != "" && (millis() - lastCharTime > RESET_DELAY)) {
    decodedText = "";
    currentMorse = "";

    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("Li-Fi Ready...");
    
    Serial.println("System Reset - Ready for new message");
  }
}

void decodeChar() {
  if (currentMorse == "") return;
  
  // ✅ Letters (0-25), Numbers (26-35)
  static const char* tokens[] = {
    ".-","-...","-.-.","-..",".","..-.","--.","....","..",".---", // A-J
    "-.-",".-..","--","-.","---",".--.","--.-",".-.","...","-", // K-T
    "..-","...-",".--","-..-","-.--","--..",                   // U-Z
    ".----","..---","...--","....-",".....","-....","--...","---..","----.","-----", // 1-0
    "..--..", ".-.-.-", "--..--" // ?, ., ,
  };

  static const char alphaNum[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890?. ,";

  char c = '?';
  
  // Array size is now 39
  for (int i = 0; i < 39; i++) {
    if (currentMorse == tokens[i]) {
      c = alphaNum[i];
      break;
    }
  }
  
  if (c != '?') {
    updateLCD(c);
  } else {
    Serial.println("Unknown Morse: " + currentMorse);
  }

  currentMorse = ""; 
}

void updateLCD(char c) {
  if (decodedText.length() >= 16) decodedText = ""; 
  
  decodedText += c;
  lastCharTime = millis();
  
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Message:");
  lcd.setCursor(0, 1);
  lcd.print(decodedText);

  Serial.print("Decoded: ");
  Serial.println(c);
}
