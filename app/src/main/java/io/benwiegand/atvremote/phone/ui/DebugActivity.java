package io.benwiegand.atvremote.phone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import io.benwiegand.atvremote.phone.R;

public class DebugActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        findViewById(R.id.connect_button).setOnClickListener(v -> {
            EditText ipAddressText = findViewById(R.id.ip_address_text);
            EditText portText = findViewById(R.id.port_text);

            String ipAddress = ipAddressText.getText().toString();
            int port = Integer.parseInt(portText.getText().toString());

            Intent intent = new Intent(this, RemoteActivity.class);
            intent.putExtra(RemoteActivity.EXTRA_HOSTNAME, ipAddress);
            intent.putExtra(RemoteActivity.EXTRA_PORT_NUMBER, port);

            startActivity(intent);

        });
    }




}