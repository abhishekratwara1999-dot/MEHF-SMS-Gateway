package com.mehf.smsgateway;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private TextView statusLog;
    private String selectedRole = ""; // "admin" या "school"
    private static final int SMS_PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ऐप का लेआउट (बिना ड्रैग-एंड-ड्रॉप के कोड से बना UI)
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("MEHF SMS Gateway");
        title.setTextSize(24f);
        title.setPadding(0, 0, 0, 50);
        layout.addView(title);

        // रोल चुनने के लिए रेडियो बटन
        RadioGroup roleGroup = new RadioGroup(this);
        RadioButton rdoAdmin = new RadioButton(this);
        rdoAdmin.setText("Admin (MEHF Updates)");
        RadioButton rdoSchool = new RadioButton(this);
        rdoSchool.setText("School Director (Students/Teachers)");
        roleGroup.addView(rdoAdmin);
        roleGroup.addView(rdoSchool);
        layout.addView(roleGroup);

        Button startBtn = new Button(this);
        startBtn.setText("सिस्टम चालू करें");
        layout.addView(startBtn);

        statusLog = new TextView(this);
        statusLog.setText("\nस्टेटस: इंतज़ार कर रहा है...");
        statusLog.setTextSize(16f);
        layout.addView(statusLog);

        setContentView(layout);

        // बटन दबाने पर क्या होगा
        startBtn.setOnClickListener(v -> {
            if (rdoAdmin.isChecked()) selectedRole = "admin";
            else if (rdoSchool.isChecked()) selectedRole = "school";
            else {
                Toast.makeText(this, "कृपया अपना रोल चुनें!", Toast.LENGTH_SHORT).show();
                return;
            }

            checkPermissionsAndStart();
        });
    }

    private void checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_CODE);
        } else {
            startListeningToFirebase();
        }
    }

    private void startListeningToFirebase() {
        db = FirebaseFirestore.getInstance();
        statusLog.setText("\n✅ " + selectedRole.toUpperCase() + " मोड सक्रिय है।\nनये SMS का इंतज़ार कर रहा है...");

        // यहाँ जादू है: सिर्फ वही मेसेज पकड़ेगा जो इस रोल (sender) के लिए हैं
        db.collection("pending_sms")
            .whereEqualTo("status", "pending")
            .whereEqualTo("sender", selectedRole) 
            .addSnapshotListener((snapshots, e) -> {
                if (e != null) return;

                if (snapshots != null) {
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            String docId = dc.getDocument().getId();
                            String phone = dc.getDocument().getString("phone");
                            String message = dc.getDocument().getString("message");

                            if (phone != null && message != null) {
                                sendRealSMS(phone, message, docId);
                            }
                        }
                    }
                }
            });
    }

    private void sendRealSMS(String phoneNo, String msg, String docId) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNo, null, msg, null, null);
            
            // मैसेज जाने के बाद Firebase में 'sent' कर दें
            db.collection("pending_sms").document(docId).update("status", "sent");
            statusLog.append("\n➜ SMS भेजा गया: " + phoneNo);
            
        } catch (Exception ex) {
            db.collection("pending_sms").document(docId).update("status", "failed");
            statusLog.append("\n❌ विफल: " + phoneNo);
        }
    }
}
