package de.pixart.messenger.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import java.util.Calendar;

import de.pixart.messenger.R;
import de.pixart.messenger.utils.ThemeHelper;

public class AboutActivity extends XmppActivity {

    private Button privacyButton;
    private Button termsOfUseButton;
    private TextView aboutmessage;

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(ThemeHelper.find(this));
        setContentView(R.layout.activity_about);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());

        aboutmessage = findViewById(R.id.aboutmessage);
        String year = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        aboutmessage.setText(getString(R.string.pref_about_message, year));

        privacyButton = findViewById(R.id.show_privacy_policy);
        privacyButton.setOnClickListener(view -> {
            final Uri uri = Uri.parse("https://jabber.pix-art.de/privacy/");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(browserIntent);
        });
        termsOfUseButton = findViewById(R.id.show_terms_of_use);
        termsOfUseButton.setOnClickListener(view -> {
            final Uri uri = Uri.parse("https://jabber.pix-art.de/termsofuse/");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(browserIntent);
        });
    }
}