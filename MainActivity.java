package com.rajnandanpatil100.lifitext;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // UI component declarations
    private EditText inputText;
    private Button btnSend;
    private TextView tvStatus;

    // Camera and Torch management variables
    private CameraManager cameraManager;
    private String cameraId;

    // Handler to post updates from background threads to the main UI thread
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Morse Timing Constants in milliseconds for signal processing
    private final int DOT_TIME = 150;     // Duration of a short light pulse
    private final int DASH_TIME = 350;    // Duration of a long light pulse
    private final int SYMBOL_GAP = 120;   // Delay between dots and dashes within a letter
    private final int LETTER_GAP = 300;   // Delay between complete letters
    private final int WORD_GAP = 700;     // Delay between words

    private static final int PERMISSION_CODE = 101;

    // Map to store character to Morse code associations
    private static final Map<Character, String> MORSE_MAP = new HashMap<>();

    static {
        String[][] pairs = {
                {"A", ".-"}, {"B", "-..."}, {"C", "-.-."}, {"D", "-.."}, {"E", "."},
                {"F", "..-."}, {"G", "--."}, {"H", "...."}, {"I", ".."}, {"J", ".---"},
                {"K", "-.-"}, {"L", ".-.."}, {"M", "--"}, {"N", "-."}, {"O", "---"},
                {"P", ".--."}, {"Q", "--.-"}, {"R", ".-."}, {"S", "..."}, {"T", "-"},
                {"U", "..-"}, {"V", "...-"}, {"W", ".--"}, {"X", "-..-"}, {"Y", "-.--"},
                {"Z", "--.."},

                // ✅ Numbers
                {"1", ".----"}, {"2", "..---"}, {"3", "...--"}, {"4", "....-"},
                {"5", "....."}, {"6", "-...."}, {"7", "--..."}, {"8", "---.."},
                {"9", "----."}, {"0", "-----"},

                // ✅ Special Characters
                {"?", "..--.."}, {".", ".-.-.-"}, {",", "--..--"},
                {"!", "-.-.--"}, {"@", ".--.-."}, {"+", ".-.-."}, {"-", "-....-"}
        };
        for (String[] p : pairs) MORSE_MAP.put(p[0].charAt(0), p[1]);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind UI elements to variables
        inputText = findViewById(R.id.input_text);
        btnSend = findViewById(R.id.btn_send);
        tvStatus = findViewById(R.id.tv_status);

        // Check for necessary permissions and initialize camera hardware
        checkPermission();
        setupCamera();

        // Handle the transmit button click event
        btnSend.setOnClickListener(v -> {
            String text = inputText.getText().toString().trim().toUpperCase();

            // Validate input text
            if (text.isEmpty()) {
                Toast.makeText(this, "Enter text", Toast.LENGTH_SHORT).show();
                return;
            }

            // Ensure camera permission is granted before proceeding
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                return;
            }

            // Disable UI during transmission and update status
            btnSend.setEnabled(false);
            tvStatus.setText("Status: Transmitting...");

            // Run transmission on a background thread to prevent UI freezing
            new Thread(() -> {
                transmitMorse(textToMorse(text));

                // Return to main thread to re-enable UI
                handler.post(() -> {
                    btnSend.setEnabled(true);
                    tvStatus.setText("Status: Done");
                });
            }).start();
        });
    }

    // Check and request runtime camera permissions
    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, PERMISSION_CODE);
        }
    }

    // Identify the first camera on the device that supports a flashlight
    private void setupCamera() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            for (String id : cameraManager.getCameraIdList()) {
                Boolean flash = cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);

                if (flash != null && flash) {
                    cameraId = id;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Convert plain text string into a string of Morse symbols
    private String textToMorse(String text) {
        StringBuilder sb = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (c == ' ') {
                sb.append("/ "); // Use forward slash to denote word separation
            } else if (MORSE_MAP.containsKey(c)) {
                sb.append(MORSE_MAP.get(c)).append(" "); // Append space after each letter
            }
        }
        return sb.toString();
    }

    // Iterate through Morse string and control torch timing
    private void transmitMorse(String morse) {
        try {
            for (char c : morse.toCharArray()) {

                if (c == '.') {
                    flash(DOT_TIME);
                    Thread.sleep(SYMBOL_GAP); // Wait before next symbol

                } else if (c == '-') {
                    flash(DASH_TIME);
                    Thread.sleep(SYMBOL_GAP); // Wait before next symbol

                } else if (c == ' ') {
                    Thread.sleep(LETTER_GAP); // Pause for letter separation

                } else if (c == '/') {
                    Thread.sleep(WORD_GAP);   // Pause for word separation
                }
            }

            // Ensure the torch is turned off after sequence completes
            setTorch(false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Turn torch on for a specific duration, then turn it off
    private void flash(int duration) throws InterruptedException {
        setTorch(true);
        Thread.sleep(duration);
        setTorch(false);
    }

    // Low-level hardware call to switch torch state
    private void setTorch(boolean state) {
        try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, state);
                Thread.sleep(10); // Stability delay to prevent rapid API crashes
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up: turn off torch if the app is closed during transmission
        setTorch(false);
    }
}