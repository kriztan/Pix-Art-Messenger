package de.pixart.messenger.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.textfield.TextInputLayout;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.pixart.messenger.Config;
import de.pixart.messenger.R;
import de.pixart.messenger.crypto.axolotl.AxolotlService;
import de.pixart.messenger.crypto.axolotl.XmppAxolotlSession;
import de.pixart.messenger.databinding.ActivityEditAccountBinding;
import de.pixart.messenger.databinding.DialogPresenceBinding;
import de.pixart.messenger.entities.Account;
import de.pixart.messenger.entities.Presence;
import de.pixart.messenger.entities.PresenceTemplate;
import de.pixart.messenger.services.BarcodeProvider;
import de.pixart.messenger.services.QuickConversationsService;
import de.pixart.messenger.services.XmppConnectionService;
import de.pixart.messenger.services.XmppConnectionService.OnAccountUpdate;
import de.pixart.messenger.services.XmppConnectionService.OnCaptchaRequested;
import de.pixart.messenger.ui.adapter.KnownHostsAdapter;
import de.pixart.messenger.ui.adapter.PresenceTemplateAdapter;
import de.pixart.messenger.ui.util.AvatarWorkerTask;
import de.pixart.messenger.ui.util.PendingItem;
import de.pixart.messenger.ui.util.SoftKeyboardUtils;
import de.pixart.messenger.utils.CryptoHelper;
import de.pixart.messenger.utils.MenuDoubleTabUtil;
import de.pixart.messenger.utils.Resolver;
import de.pixart.messenger.utils.SignupUtils;
import de.pixart.messenger.utils.TorServiceUtils;
import de.pixart.messenger.utils.UIHelper;
import de.pixart.messenger.utils.XmppUri;
import de.pixart.messenger.xml.Element;
import de.pixart.messenger.xmpp.OnKeyStatusUpdated;
import de.pixart.messenger.xmpp.OnUpdateBlocklist;
import de.pixart.messenger.xmpp.XmppConnection;
import de.pixart.messenger.xmpp.XmppConnection.Features;
import de.pixart.messenger.xmpp.forms.Data;
import de.pixart.messenger.xmpp.pep.Avatar;
import me.drakeet.support.toast.ToastCompat;
import rocks.xmpp.addr.Jid;

import static de.pixart.messenger.utils.PermissionUtils.allGranted;
import static de.pixart.messenger.utils.PermissionUtils.readGranted;

public class EditAccountActivity extends OmemoActivity implements OnAccountUpdate, OnUpdateBlocklist,
        OnKeyStatusUpdated, OnCaptchaRequested, KeyChainAliasCallback, XmppConnectionService.OnShowErrorToast, XmppConnectionService.OnMamPreferencesFetched {

    public static final String EXTRA_OPENED_FROM_NOTIFICATION = "opened_from_notification";
    public static final String EXTRA_FORCE_REGISTER = "force_register";

    private static final int REQUEST_DATA_SAVER = 0xf244;
    private static final int REQUEST_CHANGE_STATUS = 0xee11;
    private static final int REQUEST_ORBOT = 0xff22;
    private static final int REQUEST_IMPORT_BACKUP = 0x63fb;

    private AlertDialog mCaptchaDialog = null;
    private final AtomicBoolean mPendingReconnect = new AtomicBoolean(false);
    private final AtomicBoolean redirectInProgress = new AtomicBoolean(false);
    private Jid jidToEdit;
    private boolean mInitMode = false;
    private boolean mExisting = false;
    private Boolean mForceRegister = null;
    private boolean mUsernameMode = Config.DOMAIN_LOCK != null;
    private boolean mShowOptions = false;
    private boolean useOwnProvider = false;
    private Account mAccount;
    private String messageFingerprint;

    private final PendingItem<PresenceTemplate> mPendingPresenceTemplate = new PendingItem<>();

    private boolean mFetchingAvatar = false;

    private final OnClickListener mSaveButtonClickListener = new OnClickListener() {

        @Override
        public void onClick(final View v) {
            final String password = binding.accountPassword.getText().toString();
            final boolean wasDisabled = mAccount != null && mAccount.getStatus() == Account.State.DISABLED;
            final boolean accountInfoEdited = accountInfoEdited();
            if (mInitMode && mAccount != null) {
                mAccount.setOption(Account.OPTION_DISABLED, false);
            }
            if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED && !accountInfoEdited) {
                mAccount.setOption(Account.OPTION_DISABLED, false);
                if (!xmppConnectionService.updateAccount(mAccount)) {
                    ToastCompat.makeText(EditAccountActivity.this, R.string.unable_to_update_account, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            final boolean registerNewAccount;
            if (mForceRegister != null) {
                registerNewAccount = mForceRegister;
            } else {
                registerNewAccount = binding.accountRegisterNew.isChecked() && !Config.DISALLOW_REGISTRATION_IN_UI;
            }
            if (mUsernameMode && binding.accountJid.getText().toString().contains("@")) {
                binding.accountJidLayout.setError(getString(R.string.invalid_username));
                removeErrorsOnAllBut(binding.accountJidLayout);
                binding.accountJid.requestFocus();
                return;
            }

            XmppConnection connection = mAccount == null ? null : mAccount.getXmppConnection();
            final boolean startOrbot = mAccount != null && mAccount.getStatus() == Account.State.TOR_NOT_AVAILABLE;
            if (startOrbot) {
                if (TorServiceUtils.isOrbotInstalled(EditAccountActivity.this)) {
                    TorServiceUtils.startOrbot(EditAccountActivity.this, REQUEST_ORBOT);
                } else {
                    TorServiceUtils.downloadOrbot(EditAccountActivity.this, REQUEST_ORBOT);
                }
                return;
            }
            if (inNeedOfSaslAccept()) {
                mAccount.setKey(Account.PINNED_MECHANISM_KEY, String.valueOf(-1));
                if (!xmppConnectionService.updateAccount(mAccount)) {
                    ToastCompat.makeText(EditAccountActivity.this, R.string.unable_to_update_account, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            final boolean openRegistrationUrl = registerNewAccount && !accountInfoEdited && mAccount != null && mAccount.getStatus() == Account.State.REGISTRATION_WEB;
            final boolean openPaymentUrl = mAccount != null && mAccount.getStatus() == Account.State.PAYMENT_REQUIRED;
            final boolean redirectionWorthyStatus = openPaymentUrl || openRegistrationUrl;
            URL url = connection != null && redirectionWorthyStatus ? connection.getRedirectionUrl() : null;
            if (url != null && !wasDisabled) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())));
                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                    return;
                } catch (ActivityNotFoundException e) {
                    ToastCompat.makeText(EditAccountActivity.this, R.string.application_found_to_open_website, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            final Jid jid;
            try {
                if (mUsernameMode) {
                    jid = Jid.of(binding.accountJid.getText().toString(), getUserModeDomain(), null);
                } else {
                    jid = Jid.of(binding.accountJid.getText().toString());
                }
            } catch (final NullPointerException e) {
                if (mUsernameMode) {
                    binding.accountJidLayout.setError(getString(R.string.invalid_username));
                } else {
                    binding.accountJidLayout.setError(getString(R.string.invalid_jid));
                }
                binding.accountJid.requestFocus();
                removeErrorsOnAllBut(binding.accountJidLayout);
                return;
            } catch (final IllegalArgumentException e) {
                if (mUsernameMode) {
                    binding.accountJidLayout.setError(getString(R.string.invalid_username));
                } else {
                    binding.accountJidLayout.setError(getString(R.string.invalid_jid));
                }
                binding.accountJid.requestFocus();
                removeErrorsOnAllBut(binding.accountJidLayout);
                return;
            }
            String hostname = null;
            int numericPort = 5222;
            if (mShowOptions) {
                hostname = binding.hostname.getText().toString().replaceAll("\\s", "");
                final String port = binding.port.getText().toString().replaceAll("\\s", "");
                if (hostname.contains(" ")) {
                    binding.hostnameLayout.setError(getString(R.string.not_valid_hostname));
                    binding.hostname.requestFocus();
                    removeErrorsOnAllBut(binding.hostnameLayout);
                    return;
                }
                if (!hostname.isEmpty()) {
                    try {
                        numericPort = Integer.parseInt(port);
                        if (numericPort < 0 || numericPort > 65535) {
                            binding.portLayout.setError(getString(R.string.not_a_valid_port));
                            removeErrorsOnAllBut(binding.portLayout);
                            binding.port.requestFocus();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        binding.portLayout.setError(getString(R.string.not_a_valid_port));
                        removeErrorsOnAllBut(binding.portLayout);
                        binding.port.requestFocus();
                        return;
                    }
                }
            }

            if (jid.getLocal() == null) {
                if (mUsernameMode) {
                    binding.accountJidLayout.setError(getString(R.string.invalid_username));
                } else {
                    binding.accountJidLayout.setError(getString(R.string.invalid_jid));
                }
                removeErrorsOnAllBut(binding.accountJidLayout);
                binding.accountJid.requestFocus();
                return;
            }
            if (registerNewAccount) {
                if (XmppConnection.errorMessage != null) {
                    ToastCompat.makeText(EditAccountActivity.this, XmppConnection.errorMessage, Toast.LENGTH_LONG).show();
                }
            }
            if (mAccount != null) {
                if (mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
                    mAccount.setOption(Account.OPTION_MAGIC_CREATE, mAccount.getPassword().contains(password));
                }
                mAccount.setJid(jid);
                mAccount.setPort(numericPort);
                mAccount.setHostname(hostname);
                if (XmppConnection.errorMessage != null) {
                    binding.accountJidLayout.setError(XmppConnection.errorMessage);
                } else {
                    binding.accountJidLayout.setError(null);
                }
                mAccount.setPassword(password);
                mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
                if (!xmppConnectionService.updateAccount(mAccount)) {
                    ToastCompat.makeText(EditAccountActivity.this, R.string.unable_to_update_account, Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                if (xmppConnectionService.findAccountByJid(jid) != null) {
                    binding.accountJidLayout.setError(getString(R.string.account_already_exists));
                    removeErrorsOnAllBut(binding.accountJidLayout);
                    binding.accountJid.requestFocus();
                    return;
                }
                mAccount = new Account(jid.asBareJid(), password);
                mAccount.setPort(numericPort);
                mAccount.setHostname(hostname);
                mAccount.setOption(Account.OPTION_USETLS, true);
                mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
                mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
                xmppConnectionService.createAccount(mAccount);
            }
            binding.hostnameLayout.setError(null);
            binding.portLayout.setError(null);
            if (mAccount.isEnabled()
                    && !registerNewAccount
                    && !mInitMode) {
                finish();
            } else {
                updateSaveButton();
                updateAccountInformation(true);
            }

        }
    };
    private final OnClickListener mCancelButtonClickListener = new OnClickListener() {

        @Override
        public void onClick(final View v) {
            deleteAccountAndReturnIfNecessary();
            finish();
        }
    };
    private Toast mFetchingMamPrefsToast;
    private String mSavedInstanceAccount;
    private boolean mSavedInstanceInit = false;
    private XmppUri pendingUri = null;
    private boolean mUseTor;
    private ActivityEditAccountBinding binding;

    public void refreshUiReal() {
        invalidateOptionsMenu();
        if (mAccount != null
                && mAccount.getStatus() != Account.State.ONLINE
                && mFetchingAvatar) {
            Intent intent = new Intent(this, StartConversationActivity.class);
            StartConversationActivity.addInviteUri(intent, getIntent());
            startActivity(intent);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            finish();
        } else if (mInitMode && mAccount != null && mAccount.getStatus() == Account.State.ONLINE) {
            runOnUiThread(this::next);
        }
        if (mAccount != null) {
            updateAccountInformation(false);
        }
        updateSaveButton();
    }

    private void next() {
        if (redirectInProgress.compareAndSet(false, true)) {
            Intent intent = new Intent(this, EnterNameActivity.class);
            intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().asBareJid().toString());
            startActivity(intent);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            finish();
        }
    }

    @Override
    public boolean onNavigateUp() {
        deleteAccountAndReturnIfNecessary();
        return super.onNavigateUp();
    }

    @Override
    public void onBackPressed() {
        deleteAccountAndReturnIfNecessary();
        super.onBackPressed();
    }

    private void deleteAccountAndReturnIfNecessary() {
        if (mInitMode && mAccount != null && !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY)) {
            xmppConnectionService.deleteAccount(mAccount);
        }

        final boolean magicCreate = mAccount != null && mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) && !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY);
        final Jid jid = mAccount == null ? null : mAccount.getJid();

        if (SignupUtils.isSupportTokenRegistry() && jid != null && magicCreate && !jid.getDomain().equals(Config.MAGIC_CREATE_DOMAIN)) {
            final Jid preset;
            if (mAccount.isOptionSet(Account.OPTION_FIXED_USERNAME)) {
                preset = jid.asBareJid();
            } else {
                preset = Jid.ofDomain(jid.getDomain());
            }
            final Intent intent = SignupUtils.getTokenRegistrationIntent(this, preset, mAccount.getKey(Account.PRE_AUTH_REGISTRATION_TOKEN));
            StartConversationActivity.addInviteUri(intent, getIntent());
            startActivity(intent);
            return;
        }

        if (xmppConnectionService.getAccounts().size() == 0 && Config.MAGIC_CREATE_DOMAIN != null) {
            Intent intent = SignupUtils.getSignUpIntent(this, mForceRegister != null && mForceRegister);
            StartConversationActivity.addInviteUri(intent, getIntent());
            startActivity(intent);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        }
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    private final UiCallback<Avatar> mAvatarFetchCallback = new UiCallback<Avatar>() {

        @Override
        public void userInputRequired(final PendingIntent pi, final Avatar avatar) {
            finishInitialSetup(avatar);
        }

        @Override
        public void success(final Avatar avatar) {
            finishInitialSetup(avatar);
        }

        @Override
        public void error(final int errorCode, final Avatar avatar) {
            finishInitialSetup(avatar);
        }
    };
    private final TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            updatePortLayout();
            updateSaveButton();
            updateInfoButtons();
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void afterTextChanged(final Editable s) {

        }
    };

    private View.OnFocusChangeListener mEditTextFocusListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean b) {
            EditText et = (EditText) view;
            if (b) {
                int resId = mUsernameMode ? R.string.username : R.string.account_settings_example_jabber_id;
                if (view.getId() == R.id.hostname) {
                    resId = mUseTor ? R.string.hostname_or_onion : R.string.hostname_example;
                }
                final int res = resId;
                new Handler().postDelayed(() -> et.setHint(res), 500);
            } else {
                et.setHint(null);
            }
        }
    };


    private final OnClickListener mAvatarClickListener = new OnClickListener() {
        @Override
        public void onClick(final View view) {
            if (mAccount != null) {
                final Intent intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
                intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().asBareJid().toString());
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            }
        }
    };

    protected void finishInitialSetup(final Avatar avatar) {
        runOnUiThread(() -> {
            SoftKeyboardUtils.hideSoftKeyboard(EditAccountActivity.this);
            final Intent intent;
            final XmppConnection connection = mAccount.getXmppConnection();
            final boolean wasFirstAccount = xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 1;
            if (avatar != null || (connection != null && !connection.getFeatures().pep())) {
                intent = new Intent(getApplicationContext(), StartConversationActivity.class);
                if (wasFirstAccount) {
                    intent.putExtra("init", true);
                }
                intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().asBareJid().toEscapedString());
            } else {
                intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
                intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().asBareJid().toString());
                intent.putExtra("setup", true);
            }
            if (wasFirstAccount) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }
            StartConversationActivity.addInviteUri(intent, getIntent());
            startActivity(intent);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            finish();
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BATTERY_OP || requestCode == REQUEST_DATA_SAVER) {
            updateAccountInformation(mAccount == null);
        }
        if (requestCode == REQUEST_CHANGE_STATUS) {
            PresenceTemplate template = mPendingPresenceTemplate.pop();
            if (template != null && resultCode == Activity.RESULT_OK) {
                generateSignature(data, template);
            } else {
                Log.d(Config.LOGTAG, "pgp result not ok");
            }
        }
        if (requestCode == REQUEST_ORBOT) {
            if (xmppConnectionService != null && mAccount != null) {
                xmppConnectionService.reconnectAccountInBackground(mAccount);
            } else {
                mPendingReconnect.set(true);
            }
        }
    }

    @Override
    protected void processFingerprintVerification(XmppUri uri) {
        processFingerprintVerification(uri, true);
    }


    protected void processFingerprintVerification(XmppUri uri, boolean showWarningToast) {
        if (mAccount != null && mAccount.getJid().asBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(mAccount, uri.getFingerprints())) {
                ToastCompat.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
                updateAccountInformation(false);
            }
        } else if (showWarningToast) {
            ToastCompat.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
        }
    }

    protected void updateInfoButtons() {
        if (this.binding.accountRegisterNew.isChecked() && this.binding.accountJid.getText().length() > 0 && !this.binding.accountJid.getText().toString().contains("@")) {
            try {
                final String jid = this.binding.accountJid.getText().toString();
                if (!mUsernameMode && Jid.of(jid).getDomain().toLowerCase().equals("pix-art.de")) {
                    this.binding.showPrivacyPolicy.setVisibility(View.VISIBLE);
                    this.binding.showTermsOfUse.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            this.binding.showPrivacyPolicy.setVisibility(View.GONE);
            this.binding.showTermsOfUse.setVisibility(View.GONE);
        }
    }

    private void updatePortLayout() {
        final String hostname = this.binding.hostname.getText().toString();
        if (TextUtils.isEmpty(hostname)) {
            this.binding.portLayout.setEnabled(false);
            this.binding.portLayout.setError(null);
        } else {
            this.binding.portLayout.setEnabled(true);
        }
    }

    protected void updateSaveButton() {
        boolean accountInfoEdited = accountInfoEdited();
        if (accountInfoEdited && !mInitMode) {
            this.binding.saveButton.setText(R.string.save);
            this.binding.saveButton.setEnabled(true);
        } else if (mAccount != null
                && (mAccount.getStatus() == Account.State.CONNECTING || mAccount.getStatus() == Account.State.REGISTRATION_SUCCESSFUL || mFetchingAvatar)) {
            this.binding.saveButton.setEnabled(false);
            this.binding.saveButton.setText(R.string.account_status_connecting);
        } else if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED && !mInitMode) {
            this.binding.saveButton.setEnabled(true);
            this.binding.saveButton.setText(R.string.enable);
        } else if (torNeedsInstall(mAccount)) {
            this.binding.saveButton.setEnabled(true);
            this.binding.saveButton.setText(R.string.install_orbot);
        } else if (torNeedsStart(mAccount)) {
            this.binding.saveButton.setEnabled(true);
            this.binding.saveButton.setText(R.string.start_orbot);
        } else {
            this.binding.saveButton.setEnabled(true);
            if (!mInitMode) {
                if (mAccount != null && mAccount.isOnlineAndConnected()) {
                    this.binding.yourStatusBox.setVisibility(View.VISIBLE);
                    this.binding.saveButton.setText(R.string.save);
                    if (!accountInfoEdited) {
                        this.binding.saveButton.setEnabled(false);
                    }
                    if (!mUsernameMode && Jid.of(mAccount.getJid()).getDomain().toLowerCase().equals("pix-art.de")) {
                        this.binding.showPrivacyPolicy.setVisibility(View.VISIBLE);
                        this.binding.showTermsOfUse.setVisibility(View.VISIBLE);
                    }
                } else {
                    this.binding.yourStatusBox.setVisibility(View.GONE);
                    this.binding.showPrivacyPolicy.setVisibility(View.GONE);
                    this.binding.showTermsOfUse.setVisibility(View.GONE);
                    XmppConnection connection = mAccount == null ? null : mAccount.getXmppConnection();
                    URL url = connection != null && mAccount.getStatus() == Account.State.PAYMENT_REQUIRED ? connection.getRedirectionUrl() : null;
                    if (url != null) {
                        this.binding.saveButton.setText(R.string.open_website);
                    } else if (inNeedOfSaslAccept()) {
                        this.binding.saveButton.setText(R.string.accept);
                    } else {
                        this.binding.saveButton.setText(R.string.connect);
                    }
                }
            } else {
                XmppConnection connection = mAccount == null ? null : mAccount.getXmppConnection();
                URL url = connection != null && mAccount.getStatus() == Account.State.REGISTRATION_WEB ? connection.getRedirectionUrl() : null;
                if (url != null && this.binding.accountRegisterNew.isChecked() && !accountInfoEdited) {
                    this.binding.saveButton.setText(R.string.open_website);
                } else {
                    this.binding.saveButton.setText(R.string.next);
                }
            }
        }
    }

    private boolean torNeedsInstall(final Account account) {
        return account != null && account.getStatus() == Account.State.TOR_NOT_AVAILABLE && !TorServiceUtils.isOrbotInstalled(this);
    }

    private boolean torNeedsStart(final Account account) {
        return account != null && account.getStatus() == Account.State.TOR_NOT_AVAILABLE;
    }

    protected boolean accountInfoEdited() {
        if (this.mAccount == null) {
            return false;
        }
        return jidEdited() ||
                !this.mAccount.getPassword().equals(binding.accountPassword.getText().toString()) ||
                !this.mAccount.getHostname().equals(this.binding.hostname.getText().toString()) ||
                !String.valueOf(this.mAccount.getPort()).equals(this.binding.port.getText().toString());
    }

    protected boolean jidEdited() {
        final String unmodified;
        if (mUsernameMode) {
            unmodified = this.mAccount.getJid().getLocal();
        } else {
            unmodified = this.mAccount.getJid().asBareJid().toString();
        }
        return !unmodified.equals(this.binding.accountJid.getText().toString());
    }

    @Override
    protected String getShareableUri(boolean http) {
        if (mAccount != null) {
            return http ? mAccount.getShareableLink() : mAccount.getShareableUri();
        } else {
            return null;
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            this.mSavedInstanceAccount = savedInstanceState.getString("account");
            this.mSavedInstanceInit = savedInstanceState.getBoolean("initMode", false);
        }
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_edit_account);
        setSupportActionBar((Toolbar) binding.toolbar);
        configureActionBar(getSupportActionBar());
        this.binding.accountJid.addTextChangedListener(this.mTextWatcher);
        this.binding.accountJid.setOnFocusChangeListener(this.mEditTextFocusListener);
        this.binding.accountPassword.addTextChangedListener(this.mTextWatcher);
        this.binding.avater.setOnClickListener(this.mAvatarClickListener);
        this.binding.hostname.addTextChangedListener(mTextWatcher);
        this.binding.hostname.setOnFocusChangeListener(mEditTextFocusListener);
        this.binding.clearDevices.setOnClickListener(v -> showWipePepDialog());
        this.binding.port.setText(String.valueOf(Resolver.DEFAULT_PORT_XMPP));
        this.binding.port.addTextChangedListener(mTextWatcher);
        this.binding.saveButton.setOnClickListener(this.mSaveButtonClickListener);
        this.binding.cancelButton.setOnClickListener(this.mCancelButtonClickListener);
        this.binding.actionEditYourName.setOnClickListener(this::onEditYourNameClicked);
        this.binding.actionEditYourStatus.setOnClickListener(this::onEditYourStatusClicked);
        if (savedInstanceState != null && savedInstanceState.getBoolean("showMoreTable")) {
            changeMoreTableVisibility(true);
        }
        final OnCheckedChangeListener OnCheckedShowConfirmPassword = (buttonView, isChecked) -> {
            updatePortLayout();
            updateSaveButton();
            updateInfoButtons();
        };
        this.binding.accountRegisterNew.setOnCheckedChangeListener(OnCheckedShowConfirmPassword);
        if (Config.DISALLOW_REGISTRATION_IN_UI) {
            this.binding.accountRegisterNew.setVisibility(View.GONE);
        }
        this.binding.showPrivacyPolicy.setOnClickListener(view -> {
            final Uri uri = Uri.parse("https://jabber.pix-art.de/privacy/");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(browserIntent);
        });
        this.binding.showTermsOfUse.setOnClickListener(view -> {
            final Uri uri = Uri.parse("https://jabber.pix-art.de/termsofuse/");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(browserIntent);
        });
    }

    private void onEditYourNameClicked(View view) {
        quickEdit(mAccount.getDisplayName(), R.string.your_name, value -> {
            final String displayName = value.trim();
            updateDisplayName(displayName);
            mAccount.setDisplayName(displayName);
            xmppConnectionService.publishDisplayName(mAccount);
            refreshAvatar();
            return null;
        }, true);
    }

    private void onEditYourStatusClicked(View view) {
        changePresence();
    }

    private void refreshAvatar() {
        AvatarWorkerTask.loadAvatar(mAccount, binding.avater, R.dimen.avatar_on_details_screen_size, true);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.editaccount, menu);
        final MenuItem showBlocklist = menu.findItem(R.id.action_show_block_list);
        final MenuItem reconnect = menu.findItem(R.id.mgmt_account_reconnect);
        final MenuItem showMoreInfo = menu.findItem(R.id.action_server_info_show_more);
        final MenuItem changePassword = menu.findItem(R.id.action_change_password_on_server);
        final MenuItem showPassword = menu.findItem(R.id.action_show_password);
        final MenuItem renewCertificate = menu.findItem(R.id.action_renew_certificate);
        final MenuItem mamPrefs = menu.findItem(R.id.action_mam_prefs);
        final MenuItem actionShare = menu.findItem(R.id.action_share);
        final MenuItem shareBarcode = menu.findItem(R.id.action_share_barcode);
        final MenuItem shareQRCode = menu.findItem(R.id.action_show_qr_code);
        final MenuItem announcePGP = menu.findItem(R.id.mgmt_account_announce_pgp);
        final MenuItem forgotPassword = menu.findItem(R.id.mgmt_account_password_forgotten);
        renewCertificate.setVisible(mAccount != null && mAccount.getPrivateKeyAlias() != null);

        if (mAccount != null && mAccount.isOnlineAndConnected()) {
            if (!mAccount.getXmppConnection().getFeatures().blocking()) {
                showBlocklist.setVisible(false);
            }
            if (!mAccount.getXmppConnection().getFeatures().register()) {
                changePassword.setVisible(false);
            }
            reconnect.setVisible(true);
            announcePGP.setVisible(true);
            forgotPassword.setVisible(true);
            mamPrefs.setVisible(mAccount.getXmppConnection().getFeatures().mam());
        } else {
            announcePGP.setVisible(false);
            forgotPassword.setVisible(false);
            reconnect.setVisible(false);
            showBlocklist.setVisible(false);
            showMoreInfo.setVisible(false);
            changePassword.setVisible(false);
            mamPrefs.setVisible(false);
            actionShare.setVisible(false);
            shareBarcode.setVisible(false);
            shareQRCode.setVisible(false);
        }

        if (mAccount != null) {
            showPassword.setVisible(mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)
                    && !mAccount.isOptionSet(Account.OPTION_REGISTER));
        } else {
            showPassword.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem showMoreInfo = menu.findItem(R.id.action_server_info_show_more);
        if (showMoreInfo.isVisible()) {
            showMoreInfo.setChecked(binding.serverInfoMore.getVisibility() == View.VISIBLE);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        super.onStart();
        final Intent intent = getIntent();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        } else if (intent != null) {
            try {
                this.jidToEdit = Jid.of(intent.getStringExtra("jid"));
            } catch (final IllegalArgumentException ignored) {
                this.jidToEdit = null;
            } catch (final NullPointerException ignored) {
                this.jidToEdit = null;
            }
            if (jidToEdit != null && intent.getData() != null && intent.getBooleanExtra("scanned", false)) {
                final XmppUri uri = new XmppUri(intent.getData());
                if (xmppConnectionServiceBound) {
                    processFingerprintVerification(uri, false);
                } else {
                    this.pendingUri = uri;
                }
            }
            boolean init = intent.getBooleanExtra("init", false);
            boolean existing = intent.getBooleanExtra("existing", false);
            useOwnProvider = intent.getBooleanExtra("useownprovider", false);
            boolean openedFromNotification = intent.getBooleanExtra(EXTRA_OPENED_FROM_NOTIFICATION, false);
            Log.d(Config.LOGTAG, "extras " + intent.getExtras());
            this.mForceRegister = intent.hasExtra(EXTRA_FORCE_REGISTER) ? intent.getBooleanExtra(EXTRA_FORCE_REGISTER, false) : null;
            Log.d(Config.LOGTAG, "force register=" + mForceRegister);
            this.mInitMode = init || this.jidToEdit == null;
            this.mExisting = existing;
            this.messageFingerprint = intent.getStringExtra("fingerprint");
            if (mExisting) {
                this.binding.accountRegisterNew.setVisibility(View.GONE);
            }
            if (!mInitMode) {
                this.binding.accountRegisterNew.setVisibility(View.GONE);
                setTitle(getString(R.string.account_details));
                configureActionBar(getSupportActionBar(), !openedFromNotification);
            } else {
                this.binding.yourNameBox.setVisibility(View.GONE);
                this.binding.yourStatusBox.setVisibility(View.GONE);
                this.binding.avater.setVisibility(View.GONE);
                configureActionBar(getSupportActionBar(), !(init && Config.MAGIC_CREATE_DOMAIN == null));
                if (mForceRegister != null) {
                    if (mForceRegister) {
                        setTitle(R.string.action_add_new_account);
                    } else {
                        setTitle(R.string.action_add_existing_account);
                    }
                }
            }
        }
        SharedPreferences preferences = getPreferences();
        mUseTor = QuickConversationsService.isConversations() && preferences.getBoolean("use_tor", getResources().getBoolean(R.bool.use_tor));
        this.mShowOptions = mUseTor || (QuickConversationsService.isConversations() && preferences.getBoolean("show_connection_options", getResources().getBoolean(R.bool.show_connection_options)));
        this.binding.namePort.setVisibility(mShowOptions ? View.VISIBLE : View.GONE);
        if (mForceRegister != null) {
            this.binding.accountRegisterNew.setVisibility(View.GONE);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            final XmppUri uri = new XmppUri(intent.getData());
            if (xmppConnectionServiceBound) {
                processFingerprintVerification(uri, false);
            } else {
                this.pendingUri = uri;
            }
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        if (mAccount != null) {
            savedInstanceState.putString("account", mAccount.getJid().asBareJid().toString());
            savedInstanceState.putBoolean("existing", mExisting);
            savedInstanceState.putBoolean("initMode", mInitMode);
            savedInstanceState.putBoolean("showMoreTable", binding.serverInfoMore.getVisibility() == View.VISIBLE);
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    protected void onBackendConnected() {
        boolean init = true;
        if (mSavedInstanceAccount != null) {
            try {
                this.mAccount = xmppConnectionService.findAccountByJid(Jid.of(mSavedInstanceAccount));
                this.mInitMode = mSavedInstanceInit;
                init = false;
            } catch (IllegalArgumentException e) {
                this.mAccount = null;
            }

        } else if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
        }

        if (mAccount != null) {
            if (mPendingReconnect.compareAndSet(true, false)) {
                xmppConnectionService.reconnectAccountInBackground(mAccount);
            }
            this.mInitMode |= this.mAccount.isOptionSet(Account.OPTION_REGISTER);
            this.mUsernameMode |= mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) && mAccount.isOptionSet(Account.OPTION_REGISTER) && !useOwnProvider;
            if (this.mAccount.getPrivateKeyAlias() != null) {
                binding.accountPassword.setHint(R.string.authenticate_with_certificate);
                if (this.mInitMode) {
                    binding.accountPassword.requestFocus();
                }
            }
            if (mPendingFingerprintVerificationUri != null) {
                processFingerprintVerification(mPendingFingerprintVerificationUri, false);
                mPendingFingerprintVerificationUri = null;
            }
            updateAccountInformation(init);
        }


        if (Config.MAGIC_CREATE_DOMAIN == null && this.xmppConnectionService.getAccounts().size() == 0) {
            this.binding.cancelButton.setEnabled(false);
        }
        if (mUsernameMode) {
            this.binding.accountJidLayout.setHint(getString(R.string.username_hint));
            this.binding.accountJid.setHint(R.string.username_hint);
        } else {
            final KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this,
                    R.layout.simple_list_item,
                    xmppConnectionService.getKnownHosts());
            this.binding.accountJid.setAdapter(mKnownHostsAdapter);
        }
        if (pendingUri != null) {
            processFingerprintVerification(pendingUri, false);
            pendingUri = null;
        }
        updateSaveButton();
        invalidateOptionsMenu();
    }

    private String getUserModeDomain() {
        if (mAccount != null && mAccount.getJid().getDomain() != null) {
            return mAccount.getJid().getDomain();
        } else {
            return Config.DOMAIN_LOCK;
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (item.getItemId()) {
            case android.R.id.home:
                deleteAccountAndReturnIfNecessary();
                break;
            case R.id.action_import_backup:
                if (hasStoragePermission(REQUEST_IMPORT_BACKUP)) {
                    startActivity(new Intent(this, ImportBackupActivity.class));
                }
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                break;
            case R.id.mgmt_account_reconnect:
                XmppConnection connection = mAccount.getXmppConnection();
                if (connection != null) {
                    connection.resetStreamId();
                }
                xmppConnectionService.reconnectAccountInBackground(mAccount);
                break;
            case R.id.action_show_block_list:
                final Intent showBlocklistIntent = new Intent(this, BlocklistActivity.class);
                showBlocklistIntent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toString());
                startActivity(showBlocklistIntent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                break;
            case R.id.action_server_info_show_more:
                changeMoreTableVisibility(!item.isChecked());
                break;
            case R.id.action_share_barcode:
                shareBarcode();
                break;
            case R.id.action_share_http:
                shareLink(true);
                break;
            case R.id.action_share_uri:
                shareLink(false);
                break;
            case R.id.action_change_password_on_server:
                gotoChangePassword(null);
                break;
            case R.id.action_mam_prefs:
                editMamPrefs();
                break;
            case R.id.action_renew_certificate:
                renewCertificate();
                break;
            case R.id.action_show_password:
                showPassword();
                break;
            case R.id.mgmt_account_announce_pgp:
                publishOpenPGPPublicKey(mAccount);
                return true;
            case R.id.mgmt_account_password_forgotten:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.password_forgotten_title);
                builder.setMessage(R.string.password_forgotten_text);
                builder.setNegativeButton(R.string.cancel, null);
                builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
                    try {
                        Uri uri = Uri.parse(getSupportSite(mAccount.getJid().getDomain()));
                        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                builder.create().show();
        }
        return super.onOptionsItemSelected(item);
    }

    private String getSupportSite(String domain) {
        int i = -1;
        for (String domains : getResources().getStringArray(R.array.support_domains)) {
            i++;
            if (domains.equals(domain)) {
                return getResources().getStringArray(R.array.support_site)[i];
            }
        }
        return domain;
    }

    private boolean inNeedOfSaslAccept() {
        return mAccount != null && mAccount.getLastErrorStatus() == Account.State.DOWNGRADE_ATTACK && mAccount.getKeyAsInt(Account.PINNED_MECHANISM_KEY, -1) >= 0 && !accountInfoEdited();
    }

    private void publishOpenPGPPublicKey(Account account) {
        if (EditAccountActivity.this.hasPgp()) {
            announcePgp(account, null, null, onOpenPGPKeyPublished);
        } else {
            this.showInstallPgpDialog();
        }
    }

    private void shareBarcode() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM, BarcodeProvider.getUriForAccount(this, mAccount));
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType("image/png");
        startActivity(Intent.createChooser(intent, getText(R.string.share_with)));
    }

    private void changeMoreTableVisibility(boolean visible) {
        binding.serverInfoMore.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void gotoChangePassword(String newPassword) {
        final Intent changePasswordIntent = new Intent(this, ChangePasswordActivity.class);
        changePasswordIntent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toString());
        if (newPassword != null) {
            changePasswordIntent.putExtra("password", newPassword);
        }
        startActivity(changePasswordIntent);
        overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
    }

    private void renewCertificate() {
        KeyChain.choosePrivateKeyAlias(this, this, null, null, null, -1, null);
    }

    private void changePresence() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean manualStatus = sharedPreferences.getBoolean(SettingsActivity.MANUALLY_CHANGE_PRESENCE, getResources().getBoolean(R.bool.manually_change_presence));
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final DialogPresenceBinding binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.dialog_presence, null, false);
        final String current = mAccount.getPresenceStatusMessage();
        if (current != null && !current.trim().isEmpty()) {
            binding.statusMessage.append(current);
        }
        setAvailabilityRadioButton(mAccount.getPresenceStatus(), binding);
        binding.show.setVisibility(manualStatus ? View.VISIBLE : View.GONE);
        List<PresenceTemplate> templates = xmppConnectionService.getPresenceTemplates(mAccount);
        PresenceTemplateAdapter presenceTemplateAdapter = new PresenceTemplateAdapter(this, R.layout.simple_list_item, templates);
        binding.statusMessage.setAdapter(presenceTemplateAdapter);
        binding.statusMessage.setOnItemClickListener((parent, view, position, id) -> {
            PresenceTemplate template = (PresenceTemplate) parent.getItemAtPosition(position);
            setAvailabilityRadioButton(template.getStatus(), binding);
        });
        builder.setTitle(R.string.edit_status_message_title);
        builder.setView(binding.getRoot());
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
            PresenceTemplate template = new PresenceTemplate(getAvailabilityRadioButton(binding), binding.statusMessage.getText().toString().trim());
            if (mAccount.getPgpId() != 0 && hasPgp()) {
                generateSignature(null, template);
            } else {
                xmppConnectionService.changeStatus(mAccount, template, null);
            }
            updatePresenceStatus(getPresenceStatus(getAvailabilityRadioButton(binding)), binding.statusMessage.getText().toString().trim());
        });
        builder.create().show();
    }

    private void generateSignature(Intent intent, PresenceTemplate template) {
        xmppConnectionService.getPgpEngine().generateSignature(intent, mAccount, template.getStatusMessage(), new UiCallback<String>() {
            @Override
            public void success(String signature) {
                xmppConnectionService.changeStatus(mAccount, template, signature);
            }

            @Override
            public void error(int errorCode, String object) {

            }

            @Override
            public void userInputRequired(PendingIntent pi, String object) {
                mPendingPresenceTemplate.push(template);
                try {
                    startIntentSenderForResult(pi.getIntentSender(), REQUEST_CHANGE_STATUS, null, 0, 0, 0);
                } catch (final IntentSender.SendIntentException ignored) {
                }
            }
        });
    }

    private static void setAvailabilityRadioButton(Presence.Status status, DialogPresenceBinding binding) {
        if (status == null) {
            binding.online.setChecked(true);
            return;
        }
        switch (status) {
            case DND:
                binding.dnd.setChecked(true);
                break;
            case XA:
                binding.xa.setChecked(true);
                break;
            case AWAY:
                binding.away.setChecked(true);
                break;
            default:
                binding.online.setChecked(true);
        }
    }

    private static Presence.Status getAvailabilityRadioButton(DialogPresenceBinding binding) {
        if (binding.dnd.isChecked()) {
            return Presence.Status.DND;
        } else if (binding.xa.isChecked()) {
            return Presence.Status.XA;
        } else if (binding.away.isChecked()) {
            return Presence.Status.AWAY;
        } else {
            return Presence.Status.ONLINE;
        }
    }

    private String getPresenceStatus(Presence.Status status) {
        if (status == null) {
            return getString(R.string.presence_online);
        }
        switch (status) {
            case DND:
                return getString(R.string.presence_dnd);
            case XA:
                return getString(R.string.presence_xa);
            case AWAY:
                return getString(R.string.presence_away);
            default:
                return getString(R.string.presence_online);
        }
    }

    @Override
    public void alias(String alias) {
        if (alias != null) {
            xmppConnectionService.updateKeyInAccount(mAccount, alias);
        }
    }

    private void updateAccountInformation(boolean init) {
        if (init) {
            this.binding.accountJid.getEditableText().clear();
            if (mUsernameMode) {
                this.binding.accountJid.getEditableText().append(this.mAccount.getJid().getLocal());
            } else {
                this.binding.accountJid.getEditableText().append(this.mAccount.getJid().asBareJid().toString());
            }
            binding.accountPassword.getEditableText().clear();
            binding.accountPassword.getEditableText().append(this.mAccount.getPassword());
            binding.accountPassword.setText(this.mAccount.getPassword());
            this.binding.hostname.setText("");
            this.binding.hostname.getEditableText().append(this.mAccount.getHostname());
            this.binding.port.setText("");
            this.binding.port.getEditableText().append(String.valueOf(this.mAccount.getPort()));
            this.binding.namePort.setVisibility(mShowOptions ? View.VISIBLE : View.GONE);

        }

        final boolean editable = !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY) && !mAccount.isOptionSet(Account.OPTION_FIXED_USERNAME) && QuickConversationsService.isConversations();
        this.binding.accountJid.setEnabled(editable);
        this.binding.accountJid.setFocusable(editable);
        this.binding.accountJid.setFocusableInTouchMode(editable);
        this.binding.accountJid.setCursorVisible(editable);

        final String displayName = mAccount.getDisplayName();
        updateDisplayName(displayName);
        final String presenceStatus = getPresenceStatus(mAccount.getPresenceStatus());
        final String presenceStatusMessage = mAccount.getPresenceStatusMessage();
        updatePresenceStatus(presenceStatus, presenceStatusMessage);
        final boolean tooglePassword = mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) || !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY);
        final boolean editPassword = !mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) || (!mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY) && QuickConversationsService.isConversations()) || mAccount.getLastErrorStatus() == Account.State.UNAUTHORIZED;
        this.binding.accountPasswordLayout.setPasswordVisibilityToggleEnabled(tooglePassword);
        this.binding.accountPassword.setFocusable(editPassword);
        this.binding.accountPassword.setFocusableInTouchMode(editPassword);
        this.binding.accountPassword.setCursorVisible(editPassword);
        this.binding.accountPassword.setEnabled(editPassword);

        if (!mInitMode) {
            binding.avater.setVisibility(View.VISIBLE);
            refreshAvatar();
            this.binding.accountJid.setEnabled(false);
        } else {
            binding.avater.setVisibility(View.GONE);
        }
        this.binding.accountRegisterNew.setChecked(this.mAccount.isOptionSet(Account.OPTION_REGISTER));
        if (this.mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
            if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    setTitle(R.string.action_add_new_account);
                }
            }
            this.binding.accountRegisterNew.setVisibility(View.GONE);
        } else if (this.mAccount.isOptionSet(Account.OPTION_REGISTER) && mForceRegister == null) {
            this.binding.accountRegisterNew.setVisibility(View.VISIBLE);
        } else if (mExisting) {
            this.binding.accountRegisterNew.setVisibility(View.GONE);
        } else {
            if (mInitMode) {
                this.binding.accountRegisterNew.setVisibility(View.VISIBLE);
            } else {
                this.binding.accountRegisterNew.setVisibility(View.GONE);
            }
        }
        this.binding.yourNameBox.setVisibility(mInitMode ? View.GONE : View.VISIBLE);
        this.binding.yourStatusBox.setVisibility(mInitMode ? View.GONE : View.VISIBLE);
        if (this.mAccount.isOnlineAndConnected() && !this.mFetchingAvatar) {
            Features features = this.mAccount.getXmppConnection().getFeatures();
            this.binding.stats.setVisibility(View.VISIBLE);
            boolean showBatteryWarning = !xmppConnectionService.getPushManagementService().available(mAccount) && isOptimizingBattery();
            boolean showDataSaverWarning = isAffectedByDataSaver();
            showOsOptimizationWarning(showBatteryWarning, showDataSaverWarning);
            this.binding.sessionEst.setText(UIHelper.readableTimeDifferenceFull(this, this.mAccount.getXmppConnection().getLastSessionEstablished()));
            if (features.rosterVersioning()) {
                this.binding.serverInfoRosterVersion.setText(R.string.server_info_available);
            } else {
                this.binding.serverInfoRosterVersion.setText(R.string.server_info_unavailable);
            }
            if (features.carbons()) {
                this.binding.serverInfoCarbons.setText(R.string.server_info_available);
            } else {
                this.binding.serverInfoCarbons.setText(R.string.server_info_unavailable);
            }
            if (features.mam()) {
                this.binding.serverInfoMam.setText(R.string.server_info_available);
            } else {
                this.binding.serverInfoMam.setText(R.string.server_info_unavailable);
            }
            if (features.csi()) {
                this.binding.serverInfoCsi.setText(R.string.server_info_available);
            } else {
                this.binding.serverInfoCsi.setText(R.string.server_info_unavailable);
            }
            if (features.blocking()) {
                this.binding.serverInfoBlocking.setText(R.string.server_info_available);
            } else {
                this.binding.serverInfoBlocking.setText(R.string.server_info_unavailable);
            }
            if (features.sm()) {
                this.binding.serverInfoSm.setText(R.string.server_info_available);
            } else {
                this.binding.serverInfoSm.setText(R.string.server_info_unavailable);
            }
            if (features.adhocinvite) {
                this.binding.serverInfoAdhocInvite.setText(R.string.server_info_available);
            } else {
                this.binding.serverInfoAdhocInvite.setText(R.string.server_info_unavailable);
            }
            if (features.pep()) {
                AxolotlService axolotlService = this.mAccount.getAxolotlService();
                if (axolotlService != null && axolotlService.isPepBroken()) {
                    this.binding.serverInfoPep.setText(R.string.server_info_broken);
                } else if (features.pepPublishOptions() || features.pepOmemoWhitelisted()) {
                    this.binding.serverInfoPep.setText(R.string.server_info_available);
                } else {
                    this.binding.serverInfoPep.setText(R.string.server_info_partial);
                }
            } else {
                this.binding.serverInfoPep.setText(R.string.server_info_unavailable);
            }
            if (features.httpUpload(0)) {
                final long maxFileSize = features.getMaxHttpUploadSize();
                if (maxFileSize > 0) {
                    this.binding.serverInfoHttpUpload.setText(UIHelper.filesizeToString(maxFileSize));
                } else {
                    this.binding.serverInfoHttpUpload.setText(R.string.server_info_available);
                }
            } else if (features.p1S3FileTransfer()) {
                this.binding.serverInfoHttpUploadDescription.setText(R.string.p1_s3_filetransfer);
                this.binding.serverInfoHttpUpload.setText(R.string.server_info_available);
            } else {
                this.binding.serverInfoHttpUpload.setText(R.string.server_info_unavailable);
            }

            this.binding.pushRow.setVisibility(xmppConnectionService.getPushManagementService().isStub() ? View.GONE : View.VISIBLE);

            if (xmppConnectionService.getPushManagementService().available(mAccount)) {
                this.binding.serverInfoPush.setText(R.string.server_info_available);
            } else {
                this.binding.serverInfoPush.setText(R.string.server_info_unavailable);
            }
            final long pgpKeyId = this.mAccount.getPgpId();
            if (pgpKeyId != 0 && Config.supportOpenPgp()) {
                OnClickListener openPgp = view -> launchOpenKeyChain(pgpKeyId);
                OnClickListener delete = view -> showDeletePgpDialog();
                this.binding.pgpFingerprintBox.setVisibility(View.VISIBLE);
                this.binding.pgpFingerprint.setText(OpenPgpUtils.convertKeyIdToHex(pgpKeyId));
                this.binding.pgpFingerprint.setOnClickListener(openPgp);
                if ("pgp".equals(messageFingerprint)) {
                    this.binding.pgpFingerprintDesc.setTextAppearance(this, R.style.TextAppearance_Conversations_Caption_Highlight);
                }
                this.binding.pgpFingerprintDesc.setOnClickListener(openPgp);
                this.binding.actionDeletePgp.setOnClickListener(delete);
            } else {
                this.binding.pgpFingerprintBox.setVisibility(View.GONE);
            }
            final String otrFingerprint = this.mAccount.getOtrFingerprint();
            if (otrFingerprint != null && Config.supportOtr()) {
                if ("otr".equals(messageFingerprint)) {
                    this.binding.otrFingerprintDesc.setTextColor(ContextCompat.getColor(this, R.color.accent));
                }
                this.binding.otrFingerprintBox.setVisibility(View.VISIBLE);
                this.binding.otrFingerprint.setText(CryptoHelper.prettifyFingerprint(otrFingerprint));
                this.binding.actionCopyToClipboard.setVisibility(View.VISIBLE);
                this.binding.actionCopyToClipboard.setOnClickListener(v -> {
                    if (copyTextToClipboard(CryptoHelper.prettifyFingerprint(otrFingerprint), R.string.otr_fingerprint)) {
                        ToastCompat.makeText(
                                EditAccountActivity.this,
                                R.string.toast_message_otr_fingerprint,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                this.binding.otrFingerprintBox.setVisibility(View.GONE);
            }
            final String ownAxolotlFingerprint = this.mAccount.getAxolotlService().getOwnFingerprint();
            if (ownAxolotlFingerprint != null && Config.supportOmemo()) {
                this.binding.axolotlFingerprintBox.setVisibility(View.VISIBLE);
                if (ownAxolotlFingerprint.equals(messageFingerprint)) {
                    this.binding.ownFingerprintDesc.setTextAppearance(this, R.style.TextAppearance_Conversations_Caption_Highlight);
                    this.binding.ownFingerprintDesc.setText(R.string.omemo_fingerprint_selected_message);
                } else {
                    this.binding.ownFingerprintDesc.setTextAppearance(this, R.style.TextAppearance_Conversations_Caption);
                    this.binding.ownFingerprintDesc.setText(R.string.omemo_fingerprint);
                }
                this.binding.axolotlFingerprint.setText(CryptoHelper.prettifyFingerprint(ownAxolotlFingerprint.substring(2)));
                this.binding.actionCopyAxolotlToClipboard.setVisibility(View.VISIBLE);
                this.binding.actionCopyAxolotlToClipboard.setOnClickListener(v -> copyOmemoFingerprint(ownAxolotlFingerprint));
            } else {
                this.binding.axolotlFingerprintBox.setVisibility(View.GONE);
            }
            boolean hasKeys = false;
            binding.otherDeviceKeys.removeAllViews();
            for (XmppAxolotlSession session : mAccount.getAxolotlService().findOwnSessions()) {
                if (!session.getTrust().isCompromised()) {
                    boolean highlight = session.getFingerprint().equals(messageFingerprint);
                    addFingerprintRow(binding.otherDeviceKeys, session, highlight);
                    hasKeys = true;
                }
            }
            if (hasKeys && Config.supportOmemo()) { //TODO: either the button should be visible if we print an active device or the device list should be fed with reactived devices
                this.binding.otherDeviceKeysCard.setVisibility(View.VISIBLE);
                Set<Integer> otherDevices = mAccount.getAxolotlService().getOwnDeviceIds();
                if (otherDevices == null || otherDevices.isEmpty()) {
                    binding.clearDevices.setVisibility(View.GONE);
                } else {
                    binding.clearDevices.setVisibility(View.VISIBLE);
                }
            } else {
                this.binding.otherDeviceKeysCard.setVisibility(View.GONE);
            }
        } else {
            final TextInputLayout errorLayout;
            if (this.mAccount.errorStatus()) {
                if (this.mAccount.getStatus() == Account.State.UNAUTHORIZED || this.mAccount.getStatus() == Account.State.DOWNGRADE_ATTACK) {
                    errorLayout = this.binding.accountPasswordLayout;
                } else if (mShowOptions
                        && this.mAccount.getStatus() == Account.State.SERVER_NOT_FOUND
                        && this.binding.hostname.getText().length() > 0) {
                    errorLayout = this.binding.hostnameLayout;
                } else {
                    errorLayout = this.binding.accountJidLayout;
                }
                errorLayout.setError(getString(this.mAccount.getStatus().getReadableId()));
                if (init || !accountInfoEdited()) {
                    errorLayout.requestFocus();
                }
            } else {
                errorLayout = null;
            }
            removeErrorsOnAllBut(errorLayout);
            this.binding.stats.setVisibility(View.GONE);
            this.binding.otherDeviceKeysCard.setVisibility(View.GONE);
        }
    }

    private void updateDisplayName(String displayName) {
        if (TextUtils.isEmpty(displayName)) {
            this.binding.yourName.setText(R.string.no_name_set_instructions);
            this.binding.yourName.setTextAppearance(this, R.style.TextAppearance_Conversations_Body1_Tertiary);
        } else {
            this.binding.yourName.setText(displayName);
            this.binding.yourName.setTextAppearance(this, R.style.TextAppearance_Conversations_Body1);
        }
    }

    private void updatePresenceStatus(String presenceStatus, String presenceStatusMessage) {
        String status = presenceStatus;
        if (!TextUtils.isEmpty(presenceStatusMessage)) {
            status = presenceStatus + ": " + presenceStatusMessage;
        }
        this.binding.yourStatus.setText(status);
        this.binding.yourStatus.setTextAppearance(this, R.style.TextAppearance_Conversations_Body1);
    }

    private void removeErrorsOnAllBut(TextInputLayout exception) {
        if (this.binding.accountJidLayout != exception) {
            this.binding.accountJidLayout.setErrorEnabled(false);
            this.binding.accountJidLayout.setError(null);
        }
        if (this.binding.accountPasswordLayout != exception) {
            this.binding.accountPasswordLayout.setErrorEnabled(false);
            this.binding.accountPasswordLayout.setError(null);
        }
        if (this.binding.hostnameLayout != exception) {
            this.binding.hostnameLayout.setErrorEnabled(false);
            this.binding.hostnameLayout.setError(null);
        }
        if (this.binding.portLayout != exception) {
            this.binding.portLayout.setErrorEnabled(false);
            this.binding.portLayout.setError(null);
        }
    }

    private void showDeletePgpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.unpublish_pgp);
        builder.setMessage(R.string.unpublish_pgp_message);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.confirm, (dialogInterface, i) -> {
            mAccount.setPgpSignId(0);
            mAccount.unsetPgpSignature();
            xmppConnectionService.databaseBackend.updateAccount(mAccount);
            xmppConnectionService.sendPresence(mAccount);
            refreshUiReal();
        });
        builder.create().show();
    }

    private void showOsOptimizationWarning(boolean showBatteryWarning, boolean showDataSaverWarning) {
        this.binding.osOptimization.setVisibility(showBatteryWarning || showDataSaverWarning ? View.VISIBLE : View.GONE);
        if (showDataSaverWarning && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            this.binding.osOptimizationHeadline.setText(R.string.data_saver_enabled);
            this.binding.osOptimizationBody.setText(R.string.data_saver_enabled_explained);
            this.binding.osOptimizationDisable.setText(R.string.allow);
            this.binding.osOptimizationDisable.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS);
                Uri uri = Uri.parse("package:" + getPackageName());
                intent.setData(uri);
                try {
                    startActivityForResult(intent, REQUEST_DATA_SAVER);
                } catch (ActivityNotFoundException e) {
                    ToastCompat.makeText(EditAccountActivity.this, R.string.device_does_not_support_data_saver, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (showBatteryWarning && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            this.binding.osOptimizationDisable.setText(R.string.disable);
            this.binding.osOptimizationHeadline.setText(R.string.battery_optimizations_enabled);
            this.binding.osOptimizationBody.setText(R.string.battery_optimizations_enabled_explained);
            this.binding.osOptimizationDisable.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                Uri uri = Uri.parse("package:" + getPackageName());
                intent.setData(uri);
                try {
                    startActivityForResult(intent, REQUEST_BATTERY_OP);
                } catch (ActivityNotFoundException e) {
                    ToastCompat.makeText(EditAccountActivity.this, R.string.device_does_not_support_battery_op, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public void showWipePepDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.clear_other_devices));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getString(R.string.clear_other_devices_desc));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.accept),
                (dialog, which) -> mAccount.getAxolotlService().wipeOtherPepDevices());
        builder.create().show();
    }

    private void editMamPrefs() {
        this.mFetchingMamPrefsToast = ToastCompat.makeText(this, R.string.fetching_mam_prefs, Toast.LENGTH_LONG);
        this.mFetchingMamPrefsToast.show();
        xmppConnectionService.fetchMamPreferences(mAccount, this);
    }

    private void showPassword() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_show_password, null);
        TextView password = view.findViewById(R.id.password);
        password.setText(mAccount.getPassword());
        builder.setTitle(R.string.password);
        builder.setView(view);
        builder.setPositiveButton(R.string.cancel, null);
        builder.create().show();
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    @Override
    public void onCaptchaRequested(final Account account, final String id, final Data data, final Bitmap captcha) {
        runOnUiThread(() -> {
            if ((mCaptchaDialog != null) && mCaptchaDialog.isShowing()) {
                mCaptchaDialog.dismiss();
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(EditAccountActivity.this);
            final View view = getLayoutInflater().inflate(R.layout.captcha, null);
            final ImageView imageView = view.findViewById(R.id.captcha);
            final EditText input = view.findViewById(R.id.input);
            imageView.setImageBitmap(captcha);

            builder.setTitle(getString(R.string.captcha_required));
            builder.setView(view);

            builder.setPositiveButton(getString(R.string.ok),
                    (dialog, which) -> {
                        String rc = input.getText().toString();
                        data.put("username", account.getUsername());
                        data.put("password", account.getPassword());
                        data.put("ocr", rc);
                        data.submit();

                        if (xmppConnectionServiceBound) {
                            xmppConnectionService.sendCreateAccountWithCaptchaPacket(
                                    account, id, data);
                        }
                    });
            builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                if (xmppConnectionService != null) {
                    xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
                }
            });

            builder.setOnCancelListener(dialog -> {
                if (xmppConnectionService != null) {
                    xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
                }
            });
            mCaptchaDialog = builder.create();
            mCaptchaDialog.show();
            input.requestFocus();
        });
    }

    public void onShowErrorToast(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToastCompat.makeText(EditAccountActivity.this, resId, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onPreferencesFetched(final Element prefs) {
        runOnUiThread(() -> {
            if (mFetchingMamPrefsToast != null) {
                mFetchingMamPrefsToast.cancel();
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(EditAccountActivity.this);
            builder.setTitle(R.string.server_side_mam_prefs);
            String defaultAttr = prefs.getAttribute("default");
            final List<String> defaults = Arrays.asList("never", "roster", "always");
            final AtomicInteger choice = new AtomicInteger(Math.max(0, defaults.indexOf(defaultAttr)));
            builder.setSingleChoiceItems(R.array.mam_prefs, choice.get(), (dialog, which) -> choice.set(which));
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(R.string.ok, (dialog, which) -> {
                prefs.setAttribute("default", defaults.get(choice.get()));
                xmppConnectionService.pushMamPreferences(mAccount, prefs);
            });
            builder.create().show();
        });
    }

    @Override
    public void onPreferencesFetchFailed() {
        runOnUiThread(() -> {
            if (mFetchingMamPrefsToast != null) {
                mFetchingMamPrefsToast.cancel();
            }
            ToastCompat.makeText(EditAccountActivity.this, R.string.unable_to_fetch_mam_prefs, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0) {
            if (allGranted(grantResults)) {
                switch (requestCode) {
                    case REQUEST_IMPORT_BACKUP:
                        startActivity(new Intent(this, ImportBackupActivity.class));
                        break;
                }
            } else {
                ToastCompat.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
            }
        }
        if (readGranted(grantResults, permissions)) {
            if (xmppConnectionService != null) {
                xmppConnectionService.restartFileObserver();
            }
        }
    }

    @Override
    public void OnUpdateBlocklist(Status status) {
        if (isFinishing()) {
            return;
        }
        refreshUi();
    }
}
