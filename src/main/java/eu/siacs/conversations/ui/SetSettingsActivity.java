package eu.siacs.conversations.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivitySetSettingsBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.FirstStartManager;

import static eu.siacs.conversations.ui.SettingsActivity.BROADCAST_LAST_ACTIVITY;
import static eu.siacs.conversations.ui.SettingsActivity.CHAT_STATES;
import static eu.siacs.conversations.ui.SettingsActivity.CONFIRM_MESSAGES;
import static eu.siacs.conversations.ui.SettingsActivity.FORBID_SCREENSHOTS;
import static eu.siacs.conversations.ui.SettingsActivity.SHOW_LINKS_INSIDE;
import static eu.siacs.conversations.ui.SettingsActivity.SHOW_MAPS_INSIDE;
import static eu.siacs.conversations.ui.SettingsActivity.USE_INVIDIOUS;

public class SetSettingsActivity extends XmppActivity implements XmppConnectionService.OnAccountUpdate {
    ActivitySetSettingsBinding binding;
    Account account;
    static final int FORDBIDSCREENSHOTS = 1;
    static final int SHOWWEBLINKS = 2;
    static final int SHOWMAPPREVIEW = 3;
    static final int CHATSTATES = 4;
    static final int CONFIRMMESSAGES = 5;
    static final int LASTSEEN = 6;
    static final int INVIDIOUS = 7;

    @Override
    protected void refreshUiReal() {
        createInfoMenu();
    }

    @Override
    void onBackendConnected() {
        this.account = AccountUtils.getFirst(xmppConnectionService);
        refreshUi();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_set_settings);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        this.binding.next.setOnClickListener(this::next);
        createInfoMenu();
        getDefaults();
    }

    private void createInfoMenu() {
        this.binding.actionInfoForbidScreenshots.setOnClickListener(string -> showInfo(FORDBIDSCREENSHOTS));
        this.binding.actionInfoShowWeblinks.setOnClickListener(string -> showInfo(SHOWWEBLINKS));
        this.binding.actionInfoShowMapPreviews.setOnClickListener(string -> showInfo(SHOWMAPPREVIEW));
        this.binding.actionInfoChatStates.setOnClickListener(string -> showInfo(CHATSTATES));
        this.binding.actionInfoConfirmMessages.setOnClickListener(string -> showInfo(CONFIRMMESSAGES));
        this.binding.actionInfoLastSeen.setOnClickListener(string -> showInfo(LASTSEEN));
        this.binding.actionInfoInvidious.setOnClickListener(string -> showInfo(INVIDIOUS));
    }

    private void getDefaults() {
        this.binding.forbidScreenshots.setChecked(getResources().getBoolean(R.bool.screen_security));
        this.binding.showLinks.setChecked(getResources().getBoolean(R.bool.show_links_inside));
        this.binding.showMappreview.setChecked(getResources().getBoolean(R.bool.show_maps_inside));
        this.binding.chatStates.setChecked(getResources().getBoolean(R.bool.chat_states));
        this.binding.confirmMessages.setChecked(getResources().getBoolean(R.bool.confirm_messages));
        this.binding.lastSeen.setChecked(getResources().getBoolean(R.bool.last_activity));
        this.binding.invidious.setChecked(getResources().getBoolean(R.bool.use_invidious));
    }

    private void next(View view) {
        setSettings();
        FirstStartManager firstStartManager = new FirstStartManager(this);
        firstStartManager.setFirstTimeLaunch(false);
        if (account != null) {
            Intent intent = new Intent(this, PublishProfilePictureActivity.class);
            intent.putExtra(PublishProfilePictureActivity.EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
            intent.putExtra("setup", true);
            startActivity(intent);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        }
        finish();
    }

    private void showInfo(int setting) {
        Log.d(Config.LOGTAG, "STRING " + setting);
        String title;
        String message;
        switch (setting) {
            case FORDBIDSCREENSHOTS:
                title = getString(R.string.pref_screen_security);
                message = getString(R.string.pref_screen_security_summary);
                break;
            case SHOWWEBLINKS:
                title = getString(R.string.pref_show_links_inside);
                message = getString(R.string.pref_show_links_inside_summary);
                break;
            case SHOWMAPPREVIEW:
                title = getString(R.string.pref_show_mappreview_inside);
                message = getString(R.string.pref_show_mappreview_inside_summary);
                break;
            case CHATSTATES:
                title = getString(R.string.pref_chat_states);
                message = getString(R.string.pref_chat_states_summary);
                break;
            case CONFIRMMESSAGES:
                title = getString(R.string.pref_confirm_messages);
                message = getString(R.string.pref_confirm_messages_summary);
                break;
            case LASTSEEN:
                title = getString(R.string.pref_broadcast_last_activity);
                message = getString(R.string.pref_broadcast_last_activity_summary);
                break;
            case INVIDIOUS:
                title = getString(R.string.pref_use_invidious);
                message = getString(R.string.pref_use_invidious_summary);
                break;
            default:
                title = getString(R.string.error);
                message = getString(R.string.error);
        }
        Log.d(Config.LOGTAG, "STRING value " + title);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setNeutralButton(getString(R.string.ok), null);
        builder.create().show();
    }


    private void setSettings() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (this.binding.forbidScreenshots.isChecked()) {
            preferences.edit().putBoolean(FORBID_SCREENSHOTS, true).apply();
        } else {
            preferences.edit().putBoolean(FORBID_SCREENSHOTS, false).apply();
        }
        if (this.binding.showLinks.isChecked()) {
            preferences.edit().putBoolean(SHOW_LINKS_INSIDE, true).apply();
        } else {
            preferences.edit().putBoolean(SHOW_LINKS_INSIDE, false).apply();
        }
        if (this.binding.showMappreview.isChecked()) {
            preferences.edit().putBoolean(SHOW_MAPS_INSIDE, true).apply();
        } else {
            preferences.edit().putBoolean(SHOW_MAPS_INSIDE, false).apply();
        }
        if (this.binding.chatStates.isChecked()) {
            preferences.edit().putBoolean(CHAT_STATES, true).apply();
        } else {
            preferences.edit().putBoolean(CHAT_STATES, false).apply();
        }
        if (this.binding.confirmMessages.isChecked()) {
            preferences.edit().putBoolean(CONFIRM_MESSAGES, true).apply();
        } else {
            preferences.edit().putBoolean(CONFIRM_MESSAGES, false).apply();
        }
        if (this.binding.lastSeen.isChecked()) {
            preferences.edit().putBoolean(BROADCAST_LAST_ACTIVITY, true).apply();
        } else {
            preferences.edit().putBoolean(BROADCAST_LAST_ACTIVITY, false).apply();
        }
        if (this.binding.invidious.isChecked()) {
            preferences.edit().putBoolean(USE_INVIDIOUS, true).apply();
        } else {
            preferences.edit().putBoolean(USE_INVIDIOUS, false).apply();
        }
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }
}
