package de.pixart.messenger.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.pixart.messenger.Config;
import de.pixart.messenger.R;
import de.pixart.messenger.databinding.ActivityMagicCreateBinding;
import de.pixart.messenger.entities.Account;
import de.pixart.messenger.utils.CryptoHelper;
import de.pixart.messenger.utils.InstallReferrerUtils;
import rocks.xmpp.addr.Jid;

public class MagicCreateActivity extends XmppActivity implements TextWatcher, AdapterView.OnItemSelectedListener, CompoundButton.OnCheckedChangeListener {

    private boolean useOwnProvider = false;
    public static final String EXTRA_DOMAIN = "domain";
    public static final String EXTRA_PRE_AUTH = "pre_auth";
    public static final String EXTRA_USERNAME = "username";

    private ActivityMagicCreateBinding binding;
    private String domain;
    private String username;
    private String preAuth;

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {

    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        final Intent data = getIntent();
        this.domain = data == null ? null : data.getStringExtra(EXTRA_DOMAIN);
        this.preAuth = data == null ? null : data.getStringExtra(EXTRA_PRE_AUTH);
        this.username = data == null ? null : data.getStringExtra(EXTRA_USERNAME);
        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_magic_create);
        final List<String> domains = Arrays.asList(getResources().getStringArray(R.array.domains));
        Collections.sort(domains, String::compareToIgnoreCase);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_selectable_list_item, domains);
        int defaultServer = adapter.getPosition("blabber.im");
        binding.useOwn.setOnCheckedChangeListener(this);
        binding.server.setAdapter(adapter);
        binding.server.setSelection(defaultServer);
        binding.server.setOnItemSelectedListener(this);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        configureActionBar(getSupportActionBar(), this.domain == null);
        if (username != null && domain != null) {
            binding.title.setText(R.string.your_server_invitation);
            binding.instructions.setText(getString(R.string.magic_create_text_fixed, domain));
            binding.username.setEnabled(false);
            binding.username.setText(this.username);
            updateFullJidInformation(this.username);
        } else if (domain != null) {
            binding.instructions.setText(getString(R.string.magic_create_text_on_x, domain));
        }
        binding.createAccount.setOnClickListener(v -> {
            try {
                final String username = binding.username.getText().toString();
                final boolean fixedUsername;
                final Jid jid;
                if (this.domain != null && this.username != null) {
                    fixedUsername = true;
                    jid = Jid.ofLocalAndDomain(this.username, this.domain);
                } else if (this.domain != null) {
                    fixedUsername = false;
                    jid = Jid.ofLocalAndDomain(username, this.domain);
                } else {
                    fixedUsername = false;
                    if (domain == null && !useOwnProvider) {
                        domain = Config.MAGIC_CREATE_DOMAIN;
                    }
                    if (useOwnProvider) {
                        domain = "your-domain.com";
                    }
                    jid = Jid.ofLocalAndDomain(username, domain);
                }
                if (!jid.getEscapedLocal().equals(jid.getLocal()) || (this.username == null && username.length() < 3)) {
                    binding.username.setError(getString(R.string.invalid_username));
                    binding.username.requestFocus();
                } else {
                    binding.username.setError(null);
                    Account account = xmppConnectionService.findAccountByJid(jid);
                    String password = CryptoHelper.createPassword(new SecureRandom());
                    if (account == null) {
                        account = new Account(jid, password);
                        account.setOption(Account.OPTION_REGISTER, true);
                        account.setOption(Account.OPTION_DISABLED, true);
                        account.setOption(Account.OPTION_MAGIC_CREATE, true);
                        account.setOption(Account.OPTION_FIXED_USERNAME, fixedUsername);
                        if (this.preAuth != null) {
                            account.setKey(Account.PRE_AUTH_REGISTRATION_TOKEN, this.preAuth);
                        }
                        xmppConnectionService.createAccount(account);
                    }
                    Intent intent = new Intent(MagicCreateActivity.this, EditAccountActivity.class);
                    intent.putExtra("jid", account.getJid().asBareJid().toString());
                    intent.putExtra("init", true);
                    intent.putExtra("existing", false);
                    intent.putExtra("useownprovider", useOwnProvider);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.create_account));
                    builder.setCancelable(false);
                    StringBuilder messasge = new StringBuilder();
                    messasge.append(getString(R.string.secure_password_generated));
                    messasge.append("\n\n");
                    messasge.append(getString(R.string.password));
                    messasge.append(": ");
                    messasge.append(password);
                    messasge.append("\n\n");
                    messasge.append(getString(R.string.change_password_in_next_step));
                    builder.setMessage(messasge);
                    builder.setPositiveButton(getString(R.string.copy_to_clipboard), (dialogInterface, i) -> {
                        if (copyTextToClipboard(password, R.string.create_account)) {
                            StartConversationActivity.addInviteUri(intent, getIntent());
                            startActivity(intent);
                            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                            finish();
                            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                        }
                    });
                    builder.create().show();
                }
            } catch (IllegalArgumentException e) {
                binding.username.setError(getString(R.string.invalid_username));
                binding.username.requestFocus();
            }
        });
        binding.username.addTextChangedListener(this);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        updateFullJidInformation(s.toString());
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        updateFullJidInformation(binding.username.getText().toString());
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        updateFullJidInformation(binding.username.getText().toString());
    }

    private void updateFullJidInformation(String username) {
        this.domain = binding.server.getSelectedItem().toString();
        if (username.trim().isEmpty()) {
            binding.fullJid.setVisibility(View.INVISIBLE);
        } else {
            try {
                binding.fullJid.setVisibility(View.VISIBLE);
                final Jid jid;
                if (this.domain == null) {
                    jid = Jid.ofLocalAndDomain(username, Config.MAGIC_CREATE_DOMAIN);
                } else {
                    jid = Jid.ofLocalAndDomain(username, this.domain);
                }
                binding.fullJid.setText(getString(R.string.your_full_jid_will_be, jid.toEscapedString()));
            } catch (IllegalArgumentException e) {
                binding.fullJid.setVisibility(View.INVISIBLE);
            }

        }
    }

    @Override
    public void onDestroy() {
        InstallReferrerUtils.markInstallReferrerExecuted(this);
        super.onDestroy();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (binding.useOwn.isChecked()) {
            binding.server.setEnabled(false);
            binding.fullJid.setVisibility(View.GONE);
            useOwnProvider = true;
        } else {
            binding.server.setEnabled(true);
            binding.fullJid.setVisibility(View.VISIBLE);
            useOwnProvider = false;
        }
    }
}