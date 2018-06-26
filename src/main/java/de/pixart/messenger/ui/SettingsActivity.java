package de.pixart.messenger.ui;

import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.pixart.messenger.Config;
import de.pixart.messenger.R;
import de.pixart.messenger.crypto.OmemoSetting;
import de.pixart.messenger.entities.Account;
import de.pixart.messenger.services.ExportLogsService;
import de.pixart.messenger.services.MemorizingTrustManager;
import de.pixart.messenger.ui.util.Color;
import de.pixart.messenger.utils.TimeframeUtils;
import rocks.xmpp.addr.Jid;

public class SettingsActivity extends XmppActivity implements
        OnSharedPreferenceChangeListener {

    public static final String AWAY_WHEN_SCREEN_IS_OFF = "away_when_screen_off";
    public static final String TREAT_VIBRATE_AS_SILENT = "treat_vibrate_as_silent";
    public static final String DND_ON_SILENT_MODE = "dnd_on_silent_mode";
    public static final String MANUALLY_CHANGE_PRESENCE = "manually_change_presence";
    public static final String BLIND_TRUST_BEFORE_VERIFICATION = "btbv";
    public static final String AUTOMATIC_MESSAGE_DELETION = "automatic_message_deletion";
    public static final String BROADCAST_LAST_ACTIVITY = "last_activity";
    public static final String WARN_UNENCRYPTED_CHAT = "warn_unencrypted_chat";
    public static final String THEME = "theme";
    public static final String SHOW_DYNAMIC_TAGS = "show_dynamic_tags";
    public static final String OMEMO_SETTING = "omemo";
    public static final String SHOW_FOREGROUND_SERVICE = "show_foreground_service";
    public static final String USE_BUNDLED_EMOJIS = "use_bundled_emoji";
    public static final String USE_MULTI_ACCOUNTS = "use_multi_accounts";

    public static final int REQUEST_WRITE_LOGS = 0xbf8701;
    Preference multiAccountPreference;
    Preference BundledEmojiPreference;
    boolean isMultiAccountChecked = false;
    boolean isBundledEmojiChecked;
    private SettingsFragment mSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mTheme = findTheme();
        setTheme(this.mTheme);
        setContentView(R.layout.activity_settings);
        FragmentManager fm = getFragmentManager();
        mSettingsFragment = (SettingsFragment) fm.findFragmentById(R.id.settings_content);
        if (mSettingsFragment == null || !mSettingsFragment.getClass().equals(SettingsFragment.class)) {
            mSettingsFragment = new SettingsFragment();
            fm.beginTransaction().replace(R.id.settings_content, mSettingsFragment).commit();
        }
        mSettingsFragment.setActivityIntent(getIntent());
        getWindow().getDecorView().setBackgroundColor(Color.get(this, R.attr.color_background_secondary));
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
    }

    @Override
    void onBackendConnected() {

    }

    @Override
    public void onStart() {
        super.onStart();
        updateTheme();

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        multiAccountPreference = mSettingsFragment.findPreference("enable_multi_accounts");
        if (multiAccountPreference != null) {
            isMultiAccountChecked = ((CheckBoxPreference) multiAccountPreference).isChecked();
        }

        BundledEmojiPreference = mSettingsFragment.findPreference("use_bundled_emoji");
        if (BundledEmojiPreference != null) {
            isBundledEmojiChecked = ((CheckBoxPreference) BundledEmojiPreference).isChecked();
        }

        changeOmemoSettingSummary();

        if (Config.FORCE_ORBOT) {
            PreferenceCategory connectionOptions = (PreferenceCategory) mSettingsFragment.findPreference("connection_options");
            PreferenceScreen expert = (PreferenceScreen) mSettingsFragment.findPreference("expert");
            if (connectionOptions != null) {
                expert.removePreference(connectionOptions);
            }
        }

        PreferenceScreen mainPreferenceScreen = (PreferenceScreen) mSettingsFragment.findPreference("main_screen");

        //this feature is only available on Huawei Android 6.
        PreferenceScreen huaweiPreferenceScreen = (PreferenceScreen) mSettingsFragment.findPreference("huawei");
        if (huaweiPreferenceScreen != null) {
            Intent intent = huaweiPreferenceScreen.getIntent();
            //remove when Api version is above M (Version 6.0) or if the intent is not callable
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M || !isCallable(intent)) {
                PreferenceCategory generalCategory = (PreferenceCategory) mSettingsFragment.findPreference("general");
                generalCategory.removePreference(huaweiPreferenceScreen);
                if (generalCategory.getPreferenceCount() == 0) {
                    if (mainPreferenceScreen != null) {
                        mainPreferenceScreen.removePreference(generalCategory);
                    }
                }
            }
        }

        ListPreference automaticMessageDeletionList = (ListPreference) mSettingsFragment.findPreference(AUTOMATIC_MESSAGE_DELETION);
        if (automaticMessageDeletionList != null) {
            final int[] choices = getResources().getIntArray(R.array.automatic_message_deletion_values);
            CharSequence[] entries = new CharSequence[choices.length];
            CharSequence[] entryValues = new CharSequence[choices.length];
            for (int i = 0; i < choices.length; ++i) {
                entryValues[i] = String.valueOf(choices[i]);
                if (choices[i] == 0) {
                    entries[i] = getString(R.string.never);
                } else {
                    entries[i] = TimeframeUtils.resolve(this, 1000L * choices[i]);
                }
            }
            automaticMessageDeletionList.setEntries(entries);
            automaticMessageDeletionList.setEntryValues(entryValues);
        }

        boolean removeVoice = !getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        boolean removeLocation = !getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
                && !getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK);

        ListPreference quickAction = (ListPreference) mSettingsFragment.findPreference("quick_action");
        if (quickAction != null && (removeLocation || removeVoice)) {
            ArrayList<CharSequence> entries = new ArrayList<>(Arrays.asList(quickAction.getEntries()));
            ArrayList<CharSequence> entryValues = new ArrayList<>(Arrays.asList(quickAction.getEntryValues()));
            int index = entryValues.indexOf("location");
            if (index > 0 && removeLocation) {
                entries.remove(index);
                entryValues.remove(index);
            }
            index = entryValues.indexOf("voice");
            if (index > 0 && removeVoice) {
                entries.remove(index);
                entryValues.remove(index);
            }
            quickAction.setEntries(entries.toArray(new CharSequence[entries.size()]));
            quickAction.setEntryValues(entryValues.toArray(new CharSequence[entryValues.size()]));
        }

        final Preference removeCertsPreference = mSettingsFragment.findPreference("remove_trusted_certificates");
        if (removeCertsPreference != null) {
            removeCertsPreference.setOnPreferenceClickListener(preference -> {
                final MemorizingTrustManager mtm = xmppConnectionService.getMemorizingTrustManager();
                final ArrayList<String> aliases = Collections.list(mtm.getCertificates());
                if (aliases.size() == 0) {
                    displayToast(getString(R.string.toast_no_trusted_certs));
                    return true;
                }
                final ArrayList<Integer> selectedItems = new ArrayList<>();
                final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(SettingsActivity.this);
                dialogBuilder.setTitle(getResources().getString(R.string.dialog_manage_certs_title));
                dialogBuilder.setMultiChoiceItems(aliases.toArray(new CharSequence[aliases.size()]), null,
                        (dialog, indexSelected, isChecked) -> {
                            if (isChecked) {
                                selectedItems.add(indexSelected);
                            } else if (selectedItems.contains(indexSelected)) {
                                selectedItems.remove(Integer.valueOf(indexSelected));
                            }
                            if (selectedItems.size() > 0)
                                ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                            else {
                                ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                            }
                        });

                dialogBuilder.setPositiveButton(
                        getResources().getString(R.string.dialog_manage_certs_positivebutton), (dialog, which) -> {
                            int count = selectedItems.size();
                            if (count > 0) {
                                for (int i = 0; i < count; i++) {
                                    try {
                                        Integer item = Integer.valueOf(selectedItems.get(i).toString());
                                        String alias = aliases.get(item);
                                        mtm.deleteCertificate(alias);
                                    } catch (KeyStoreException e) {
                                        e.printStackTrace();
                                        displayToast("Error: " + e.getLocalizedMessage());
                                    }
                                }
                                if (xmppConnectionServiceBound) {
                                    reconnectAccounts();
                                }
                                displayToast(getResources().getQuantityString(R.plurals.toast_delete_certificates, count, count));
                            }
                        });
                dialogBuilder.setNegativeButton(getResources().getString(R.string.dialog_manage_certs_negativebutton), null);
                AlertDialog removeCertsDialog = dialogBuilder.create();
                removeCertsDialog.show();
                removeCertsDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                return true;
            });
        }

        final Preference exportLogsPreference = mSettingsFragment.findPreference("export_logs");
        if (exportLogsPreference != null) {
            exportLogsPreference.setOnPreferenceClickListener(preference -> {
                if (hasStoragePermission(REQUEST_WRITE_LOGS)) {
                    startExport();
                }
                return true;
            });
        }

        if (Config.ONLY_INTERNAL_STORAGE) {
            final Preference cleanCachePreference = mSettingsFragment.findPreference("clean_cache");
            if (cleanCachePreference != null) {
                cleanCachePreference.setOnPreferenceClickListener(preference -> cleanCache());
            }

            final Preference cleanPrivateStoragePreference = mSettingsFragment.findPreference("clean_private_storage");
            if (cleanPrivateStoragePreference != null) {
                cleanPrivateStoragePreference.setOnPreferenceClickListener(preference -> cleanPrivateStorage());
            }
        }

        final Preference deleteOmemoPreference = mSettingsFragment.findPreference("delete_omemo_identities");
        if (deleteOmemoPreference != null) {
            deleteOmemoPreference.setOnPreferenceClickListener(preference -> deleteOmemoIdentities());
        }

        final Preference useBundledEmojis = mSettingsFragment.findPreference("use_bundled_emoji");
        if (useBundledEmojis != null) {
            Log.d(Config.LOGTAG, "Bundled Emoji checkbox checked: " + isBundledEmojiChecked);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O && isBundledEmojiChecked) {
                ((CheckBoxPreference) BundledEmojiPreference).setChecked(false);
                useBundledEmojis.setEnabled(false);
            }
        }

        final Preference enableMultiAccountsPreference = mSettingsFragment.findPreference("enable_multi_accounts");
        if (enableMultiAccountsPreference != null) {
            Log.d(Config.LOGTAG, "Multi account checkbox checked: " + isMultiAccountChecked);
            if (isMultiAccountChecked) {
                enableMultiAccountsPreference.setEnabled(false);
            if (xmppConnectionService != null) {
                final List<Account> accounts = xmppConnectionService.getAccounts();
                Log.d(Config.LOGTAG, "Disabled multi account: Number of accounts " + accounts.size());
                if (accounts.size() > 1) {
                    Log.d(Config.LOGTAG, "Disabled multi account not possible because you have more than one account");
                    enableMultiAccountsPreference.setEnabled(false);
                } else {
                    Log.d(Config.LOGTAG, "Disabled multi account possible because you have one account");
                    enableMultiAccountsPreference.setEnabled(true);
                }
            } else {
                Log.d(Config.LOGTAG, "Disabled multi account not possible because XmppConnectionService == null");
                enableMultiAccountsPreference.setEnabled(false);
            }
            } else {
                enableMultiAccountsPreference.setEnabled(true);
                enableMultiAccountsPreference.setOnPreferenceClickListener(preference -> {
                    enableMultiAccounts();
                    return true;
                });
            }
        }
    }

    private void updateTheme() {
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
    }

    private void changeOmemoSettingSummary() {
        ListPreference omemoPreference = (ListPreference) mSettingsFragment.findPreference(OMEMO_SETTING);
        if (omemoPreference != null) {
            String value = omemoPreference.getValue();
            switch (value) {
                case "always":
                    omemoPreference.setSummary(R.string.pref_omemo_setting_summary_always);
                    break;
                case "default_on":
                    omemoPreference.setSummary(R.string.pref_omemo_setting_summary_default_on);
                    break;
                case "default_off":
                    omemoPreference.setSummary(R.string.pref_omemo_setting_summary_default_off);
                    break;
            }
        } else {
            Log.d(Config.LOGTAG, "unable to find preference named " + OMEMO_SETTING);
        }
    }

    private boolean isCallable(final Intent i) {
        return i != null && getPackageManager().queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY).size() > 0;
    }

    private boolean cleanCache() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
        return true;
    }

    private boolean cleanPrivateStorage() {
        cleanPrivatePictures();
        cleanPrivateFiles();
        return true;
    }

    private void cleanPrivatePictures() {
        try {
            File dir = new File(getFilesDir().getAbsolutePath(), "/Pictures/");
            File[] array = dir.listFiles();
            if (array != null) {
                for (int b = 0; b < array.length; b++) {
                    String name = array[b].getName().toLowerCase();
                    if (name.equals(".nomedia")) {
                        continue;
                    }
                    if (array[b].isFile()) {
                        array[b].delete();
                    }
                }
            }
        } catch (Throwable e) {
            Log.e("CleanCache", e.toString());
        }
    }

    private void cleanPrivateFiles() {
        try {
            File dir = new File(getFilesDir().getAbsolutePath(), "/Files/");
            File[] array = dir.listFiles();
            if (array != null) {
                for (int b = 0; b < array.length; b++) {
                    String name = array[b].getName().toLowerCase();
                    if (name.equals(".nomedia")) {
                        continue;
                    }
                    if (array[b].isFile()) {
                        array[b].delete();
                    }
                }
            }
        } catch (Throwable e) {
            Log.e("CleanCache", e.toString());
        }
    }

    private boolean deleteOmemoIdentities() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_delete_omemo_identities);
        final List<CharSequence> accounts = new ArrayList<>();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.isEnabled()) {
                accounts.add(account.getJid().asBareJid().toString());
            }
        }
        final boolean[] checkedItems = new boolean[accounts.size()];
        builder.setMultiChoiceItems(accounts.toArray(new CharSequence[accounts.size()]), checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
            final AlertDialog alertDialog = (AlertDialog) dialog;
            for (boolean item : checkedItems) {
                if (item) {
                    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                    return;
                }
            }
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.delete_selected_keys, (dialog, which) -> {
            for (int i = 0; i < checkedItems.length; ++i) {
                if (checkedItems[i]) {
                    try {
                        Jid jid = Jid.of(accounts.get(i).toString());
                        Account account = xmppConnectionService.findAccountByJid(jid);
                        if (account != null) {
                            account.getAxolotlService().regenerateKeys(true);
                        }
                    } catch (IllegalArgumentException e) {
                        //
                    }
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        return true;
    }

    private void enableMultiAccounts() {
        if (!isMultiAccountChecked) {
            multiAccountPreference.setEnabled(true);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);
            builder.setTitle(R.string.pref_enable_multi_accounts_title);
            builder.setMessage(R.string.pref_enable_multi_accounts_summary);
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
                ((CheckBoxPreference) multiAccountPreference).setChecked(false);
            });
            builder.setPositiveButton(R.string.enter_password, (dialog, which) -> {
                ((CheckBoxPreference) multiAccountPreference).setChecked(false);
                enterPasswordDialog();
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    public void enterPasswordDialog() {
        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.password, null);

        final Preference preference = mSettingsFragment.findPreference("enable_multi_accounts");

        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(promptsView);
        final EditText password = promptsView.findViewById(R.id.password);
        final EditText confirm_password = promptsView.findViewById(R.id.confirm_password);
        confirm_password.setVisibility(View.VISIBLE);
        alertDialogBuilder.setTitle(R.string.enter_password);
        alertDialogBuilder.setMessage(R.string.enter_password);
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        (dialog, id) -> {
                            final String pw1 = password.getText().toString();
                            final String pw2 = confirm_password.getText().toString();
                            if (!pw1.equals(pw2)) {
                                ((CheckBoxPreference) preference).setChecked(false);
                                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                                builder.setTitle(R.string.error);
                                builder.setMessage(R.string.passwords_do_not_match);
                                builder.setNegativeButton(R.string.cancel, null);
                                builder.setPositiveButton(R.string.try_again, (dialog12, id12) -> enterPasswordDialog());
                                builder.create().show();
                            } else if (pw1.trim().isEmpty()) {
                                ((CheckBoxPreference) preference).setChecked(false);
                                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                                builder.setTitle(R.string.error);
                                builder.setMessage(R.string.password_should_not_be_empty);
                                builder.setNegativeButton(R.string.cancel, null);
                                builder.setPositiveButton(R.string.try_again, (dialog1, id1) -> enterPasswordDialog());
                                builder.create().show();
                            } else {
                                ((CheckBoxPreference) preference).setChecked(true);
                                SharedPreferences multiaccount_prefs = getApplicationContext().getSharedPreferences(USE_MULTI_ACCOUNTS, Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = multiaccount_prefs.edit();
                                editor.putString("BackupPW", pw1);
                                editor.commit();
                            }
                        })
                .setNegativeButton(R.string.cancel, null);
        alertDialogBuilder.create().show();

    }


    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String name) {
        final List<String> resendPresence = Arrays.asList(
                "confirm_messages",
                DND_ON_SILENT_MODE,
                AWAY_WHEN_SCREEN_IS_OFF,
                "allow_message_correction",
                TREAT_VIBRATE_AS_SILENT,
                MANUALLY_CHANGE_PRESENCE,
                BROADCAST_LAST_ACTIVITY);
        if (name.equals(OMEMO_SETTING)) {
            OmemoSetting.load(this, preferences);
            changeOmemoSettingSummary();
        } else if (name.equals(SHOW_FOREGROUND_SERVICE)) {
            xmppConnectionService.toggleForegroundService();
        } else if (resendPresence.contains(name)) {
            if (xmppConnectionServiceBound) {
                if (name.equals(AWAY_WHEN_SCREEN_IS_OFF)
                        || name.equals(MANUALLY_CHANGE_PRESENCE)) {
                    xmppConnectionService.toggleScreenEventReceiver();
                }
                xmppConnectionService.refreshAllPresences();
            }
        } else if (name.equals("dont_trust_system_cas")) {
            xmppConnectionService.updateMemorizingTrustmanager();
            reconnectAccounts();
        } else if (name.equals("use_tor")) {
            reconnectAccounts();
        } else if (name.equals(AUTOMATIC_MESSAGE_DELETION)) {
            xmppConnectionService.expireOldMessages(true);
        } else if (name.equals(THEME)) {
            updateTheme();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0)
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == REQUEST_WRITE_LOGS) {
                    startExport();
                }
            } else {
                Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
            }
    }

    private void startExport() {
        startService(new Intent(getApplicationContext(), ExportLogsService.class));
    }

    private void displayToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    private void reconnectAccounts() {
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.isEnabled()) {
                xmppConnectionService.reconnectAccountInBackground(account);
            }
        }
    }

    public void refreshUiReal() {
        //nothing to do. This Activity doesn't implement any listeners
    }
}
