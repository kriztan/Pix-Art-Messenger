package eu.siacs.conversations.ui;

import android.app.FragmentManager;
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
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.ExportBackupService;
import eu.siacs.conversations.services.MemorizingTrustManager;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.utils.TimeframeUtils;
import me.drakeet.support.toast.ToastCompat;
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
    public static final String HIDE_YOU_ARE_NOT_PARTICIPATING = "hide_you_are_not_participating";
    public static final String THEME = "theme";
    public static final String THEME_COLOR = "theme_color";
    public static final String SHOW_DYNAMIC_TAGS = "show_dynamic_tags";
    public static final String OMEMO_SETTING = "omemo";
    public static final String SHOW_FOREGROUND_SERVICE = "show_foreground_service";
    public static final String USE_BUNDLED_EMOJIS = "use_bundled_emoji";
    public static final String ENABLE_MULTI_ACCOUNTS = "enable_multi_accounts";
    public static final String SHOW_OWN_ACCOUNTS = "show_own_accounts";
    public static final String QUICK_SHARE_ATTACHMENT_CHOICE = "quick_share_attachment_choice";
    public static final String NUMBER_OF_ACCOUNTS = "number_of_accounts";
    public static final String PLAY_GIF_INSIDE = "play_gif_inside";
    public static final String USE_INTERNAL_UPDATER = "use_internal_updater";
    public static final String SHOW_LINKS_INSIDE = "show_links_inside";
    public static final String SHOW_MAPS_INSIDE = "show_maps_inside";
    public static final String PREFER_XMPP_AVATAR = "prefer_xmpp_avatar";
    public static final String CHAT_STATES = "chat_states";
    public static final String FORBID_SCREENSHOTS = "screen_security";
    public static final String CONFIRM_MESSAGES = "confirm_messages";
    public static final String INDICATE_RECEIVED = "indicate_received";
    public static final String USE_INVIDIOUS = "use_invidious";
    public static final String ALLOW_MESSAGE_CORRECTION = "allow_message_correction";
    public static final String ENABLE_OTR_ENCRYPTION = "enable_otr_encryption";
    public static final String USE_UNICOLORED_CHATBG = "unicolored_chatbg";

    public static final int REQUEST_CREATE_BACKUP = 0xbf8701;
    Preference multiAccountPreference;
    Preference BundledEmojiPreference;
    Preference QuickShareAttachmentChoicePreference;
    boolean isMultiAccountChecked = false;
    boolean isBundledEmojiChecked;
    boolean isQuickShareAttachmentChoiceChecked = false;
    private SettingsFragment mSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mTheme = findTheme();
        setTheme(this.mTheme);
        updateTheme();
        setContentView(R.layout.activity_settings);
        FragmentManager fm = getFragmentManager();
        mSettingsFragment = (SettingsFragment) fm.findFragmentById(R.id.settings_content);
        if (mSettingsFragment == null || !mSettingsFragment.getClass().equals(SettingsFragment.class)) {
            mSettingsFragment = new SettingsFragment();
            fm.beginTransaction().replace(R.id.settings_content, mSettingsFragment).commit();
        }
        mSettingsFragment.setActivityIntent(getIntent());
        getWindow().getDecorView().setBackgroundColor(StyledAttributes.getColor(this, R.attr.color_background_secondary));
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
    }

    @Override
    void onBackendConnected() {

    }

    @Override
    public void onStart() {
        super.onStart();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        multiAccountPreference = mSettingsFragment.findPreference("enable_multi_accounts");
        if (multiAccountPreference != null) {
            isMultiAccountChecked = ((CheckBoxPreference) multiAccountPreference).isChecked();
            //handleMultiAccountChanges();
        }

        BundledEmojiPreference = mSettingsFragment.findPreference("use_bundled_emoji");
        if (BundledEmojiPreference != null) {
            isBundledEmojiChecked = ((CheckBoxPreference) BundledEmojiPreference).isChecked();
        }

        QuickShareAttachmentChoicePreference = mSettingsFragment.findPreference("quick_share_attachment_choice");
        if (QuickShareAttachmentChoicePreference != null) {
            QuickShareAttachmentChoicePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                refreshUiReal();
                return true;
            });
            isQuickShareAttachmentChoiceChecked = ((CheckBoxPreference) QuickShareAttachmentChoicePreference).isChecked();
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
        PreferenceScreen UIPreferenceScreen = (PreferenceScreen) mSettingsFragment.findPreference("userinterface");

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

        if (isQuickShareAttachmentChoiceChecked) {
            if (UIPreferenceScreen != null && quickAction != null) {
                UIPreferenceScreen.removePreference(quickAction);
            }
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
            updateTheme();
        }

        final Preference createBackupPreference = mSettingsFragment.findPreference("create_backup");
        if (createBackupPreference != null) {
            createBackupPreference.setSummary(getString(R.string.pref_create_backup_summary, FileBackend.getBackupDirectory()));
            createBackupPreference.setOnPreferenceClickListener(preference -> {
                if (hasStoragePermission(REQUEST_CREATE_BACKUP)) {
                    createBackup(true);
                }
                return true;
            });
        }

        final Preference prefereXmppAvatarPreference = mSettingsFragment.findPreference(PREFER_XMPP_AVATAR);
        if (prefereXmppAvatarPreference != null) {
            prefereXmppAvatarPreference.setOnPreferenceClickListener(preference -> {
                new Thread(() -> xmppConnectionService.getBitmapCache().evictAll()).start();
                return true;
            });
        }

        final Preference showIntroAgainPreference = mSettingsFragment.findPreference("show_intro");
        if (showIntroAgainPreference != null) {
            showIntroAgainPreference.setSummary(getString(R.string.pref_show_intro_summary));
            showIntroAgainPreference.setOnPreferenceClickListener(preference -> {
                showIntroAgain();
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isBundledEmojiChecked) {
                    ((CheckBoxPreference) BundledEmojiPreference).setChecked(false);
                    useBundledEmojis.setEnabled(false);
                }
                PreferenceCategory UICatergory = (PreferenceCategory) mSettingsFragment.findPreference("UI");
                UICatergory.removePreference(useBundledEmojis);
                if (UICatergory.getPreferenceCount() == 0) {
                    if (mainPreferenceScreen != null) {
                        mainPreferenceScreen.removePreference(UICatergory);
                    }
                }
            }
        }

        final Preference enableMultiAccountsPreference = mSettingsFragment.findPreference("enable_multi_accounts");
        if (enableMultiAccountsPreference != null) {
            Log.d(Config.LOGTAG, "Multi account checkbox checked: " + isMultiAccountChecked);
            if (isMultiAccountChecked) {
                enableMultiAccountsPreference.setEnabled(false);
                int accounts = getNumberOfAccounts();
                Log.d(Config.LOGTAG, "Disable multi account: Number of accounts " + accounts);
                if (accounts > 1) {
                    Log.d(Config.LOGTAG, "Disabling multi account not possible because you have more than one account");
                    enableMultiAccountsPreference.setEnabled(false);
                } else {
                    Log.d(Config.LOGTAG, "Disabling multi account possible because you have only one account");
                    enableMultiAccountsPreference.setEnabled(true);
                    enableMultiAccountsPreference.setOnPreferenceClickListener(preference -> {
                        refreshUiReal();
                        return true;
                    });
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
            refreshUiReal();
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
                case "always_off":
                    omemoPreference.setSummary(R.string.pref_omemo_setting_summary_always_off);
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
        for (String type : Arrays.asList("Images", "Videos", "Files", "Audios")) {
            cleanPrivateFiles(type);
        }
        return true;
    }

    private void cleanPrivateFiles(final String type) {
        try {
            File dir = new File(getFilesDir().getAbsolutePath(), File.separator + type + File.separator);
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
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        return true;
    }

    private void enableMultiAccounts() {
        if (!isMultiAccountChecked) {
            multiAccountPreference.setEnabled(true);
        }
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
                CONFIRM_MESSAGES,
                DND_ON_SILENT_MODE,
                AWAY_WHEN_SCREEN_IS_OFF,
                ALLOW_MESSAGE_CORRECTION,
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
            xmppConnectionService.reinitializeMuclumbusService();
        } else if (name.equals(AUTOMATIC_MESSAGE_DELETION)) {
            xmppConnectionService.expireOldMessages(true);
        } else if (name.equals(THEME) || name.equals(THEME_COLOR)) {
            updateTheme();
        } else if (name.equals(USE_UNICOLORED_CHATBG)) {
            xmppConnectionService.updateConversationUi();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == REQUEST_CREATE_BACKUP) {
                    createBackup(true);
                }
            } else {
                ToastCompat.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void createBackup(boolean notify) {
        final Intent intent = new Intent(this, ExportBackupService.class);
        intent.putExtra("NOTIFY_ON_BACKUP_COMPLETE", notify);
        ContextCompat.startForegroundService(this, intent);
    }

    private void displayToast(final String msg) {
        runOnUiThread(() -> ToastCompat.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    private void reconnectAccounts() {
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.isEnabled()) {
                xmppConnectionService.reconnectAccountInBackground(account);
            }
        }
    }

    public void refreshUiReal() {
        recreate();
    }

    private int getNumberOfAccounts() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int NumberOfAccounts = preferences.getInt(NUMBER_OF_ACCOUNTS, 0);
        Log.d(Config.LOGTAG, "Get number of accounts from file: " + NumberOfAccounts);
        return NumberOfAccounts;
    }

    private void showIntroAgain() {
        SharedPreferences getPrefs = PreferenceManager.getDefaultSharedPreferences(this.getBaseContext());
        Map<String, ?> allEntries = getPrefs.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().contains("intro_shown_on_activity")) {
                SharedPreferences.Editor e = getPrefs.edit();
                e.putBoolean(entry.getKey(), true);
                if (e.commit()) {
                    ToastCompat.makeText(this, R.string.show_intro_again, ToastCompat.LENGTH_SHORT).show();
                } else {
                    ToastCompat.makeText(this, R.string.show_intro_again_failed, ToastCompat.LENGTH_SHORT).show();
                }

            }
        }
    }
}
