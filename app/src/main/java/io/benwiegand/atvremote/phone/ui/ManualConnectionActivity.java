package io.benwiegand.atvremote.phone.ui;

import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.benwiegand.atvremote.phone.R;

public class ManualConnectionActivity extends DynamicColorsCompatActivity {

    private static final boolean DEBUG_AUTOFILL = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_connection);

        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.connect_button).setOnClickListener(v -> submit());
        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        EditText portText = findViewById(R.id.port_text);
        portText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != IME_ACTION_DONE) return false;
            submit();
            return true;
        });

        if (DEBUG_AUTOFILL) {
            EditText hostnameText = findViewById(R.id.hostname_text);
            hostnameText.setText("127.0.0.1");
            portText.setText("6969");
        }
    }

    private void submit() {
        EditText hostText = findViewById(R.id.hostname_text);
        EditText portText = findViewById(R.id.port_text);

        String host = hostText.getText().toString();
        String portString = portText.getText().toString();

        if (host.isBlank()) {
            hostText.setError(getString(R.string.error_requires_hostname));
            return;
        }

        if (portString.isBlank()) {
            portText.setError(getString(R.string.error_requires_port));
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portString);
        } catch (NumberFormatException e) {
            portText.setError(getString(R.string.error_numeric_port));
            return;
        }

        if (port < 0 || port > 65535) {
            portText.setError(getString(R.string.error_range_port));
            return;
        }

        connect(host, port);
    }

    private void connect(String host, int port) {
        Intent intent = new Intent(this, RemoteActivity.class);
        intent.putExtra(RemoteActivity.EXTRA_HOSTNAME, host);
        intent.putExtra(RemoteActivity.EXTRA_PORT_NUMBER, port);
        startActivity(intent);
    }
}