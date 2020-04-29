package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityEnterNameBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.FirstStartManager;

public class EnterNameActivity extends XmppActivity implements XmppConnectionService.OnAccountUpdate {

    private ActivityEnterNameBinding binding;
    private Account account;
    private AtomicBoolean setNick = new AtomicBoolean(false);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_enter_name);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        this.binding.next.setOnClickListener(this::next);
        updateNextButton();
        this.setNick.set(savedInstanceState != null && savedInstanceState.getBoolean("set_nick", false));
    }

    private void updateNextButton() {
        if (account != null && (account.getStatus() == Account.State.CONNECTING || account.getStatus() == Account.State.REGISTRATION_SUCCESSFUL)) {
            this.binding.next.setEnabled(false);
            this.binding.next.setText(R.string.account_status_connecting);
        } else if (account != null && (account.getStatus() == Account.State.ONLINE)) {
            this.binding.next.setEnabled(true);
            this.binding.next.setText(R.string.next);
        }
    }

    private void next(View view) {
        FirstStartManager firstStartManager = new FirstStartManager(this);
        if (account != null) {
            String name = this.binding.name.getText().toString().trim();
            account.setDisplayName(name);
            xmppConnectionService.publishDisplayName(account);
            if (firstStartManager.isFirstTimeLaunch()) {
                Intent intent = new Intent(this, SetSettingsActivity.class);
                intent.putExtra("setup", true);
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            } else {
                Intent intent = new Intent(this, PublishProfilePictureActivity.class);
                intent.putExtra(PublishProfilePictureActivity.EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
                intent.putExtra("setup", true);
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            }
        }
        finish();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("set_nick", this.setNick.get());
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void refreshUiReal() {
        checkSuggestPreviousNick();
        updateNextButton();
    }

    @Override
    void onBackendConnected() {
        this.account = extractAccount(getIntent());
        if (this.account != null) {
            checkSuggestPreviousNick();
        }
        updateNextButton();
    }

    private void checkSuggestPreviousNick() {
        String displayName = this.account == null ? null : this.account.getDisplayName();
        if (displayName != null) {
            if (setNick.compareAndSet(false, true) && this.binding.name.getText().length() == 0) {
                this.binding.name.getText().append(displayName);
            }
        }
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }
}