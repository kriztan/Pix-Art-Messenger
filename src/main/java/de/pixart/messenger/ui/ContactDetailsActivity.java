package de.pixart.messenger.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import de.pixart.messenger.Config;
import de.pixart.messenger.R;
import de.pixart.messenger.crypto.axolotl.AxolotlService;
import de.pixart.messenger.crypto.axolotl.FingerprintStatus;
import de.pixart.messenger.crypto.axolotl.XmppAxolotlSession;
import de.pixart.messenger.databinding.ActivityContactDetailsBinding;
import de.pixart.messenger.entities.Account;
import de.pixart.messenger.entities.Contact;
import de.pixart.messenger.entities.Conversation;
import de.pixart.messenger.entities.ListItem;
import de.pixart.messenger.services.XmppConnectionService.OnAccountUpdate;
import de.pixart.messenger.services.XmppConnectionService.OnRosterUpdate;
import de.pixart.messenger.utils.CryptoHelper;
import de.pixart.messenger.utils.IrregularUnicodeDetector;
import de.pixart.messenger.utils.MenuDoubleTabUtil;
import de.pixart.messenger.utils.Namespace;
import de.pixart.messenger.utils.TimeframeUtils;
import de.pixart.messenger.utils.UIHelper;
import de.pixart.messenger.utils.XmppUri;
import de.pixart.messenger.xmpp.OnKeyStatusUpdated;
import de.pixart.messenger.xmpp.OnUpdateBlocklist;
import de.pixart.messenger.xmpp.XmppConnection;
import rocks.xmpp.addr.Jid;

public class ContactDetailsActivity extends OmemoActivity implements OnAccountUpdate, OnRosterUpdate, OnUpdateBlocklist, OnKeyStatusUpdated {
    public static final String ACTION_VIEW_CONTACT = "view_contact";

    private Contact contact;
    private Conversation mConversation;
    ActivityContactDetailsBinding binding;
    private DialogInterface.OnClickListener removeFromRoster = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.deleteContactOnServer(contact);
        }
    };
    private OnCheckedChangeListener mOnSendCheckedChange = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView,
                                     boolean isChecked) {
            if (isChecked) {
                if (contact
                        .getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                    xmppConnectionService.sendPresencePacket(contact
                                    .getAccount(),
                            xmppConnectionService.getPresenceGenerator()
                                    .sendPresenceUpdatesTo(contact));
                } else {
                    contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
                }
            } else {
                contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        xmppConnectionService.getPresenceGenerator()
                                .stopPresenceUpdatesTo(contact));
            }
        }
    };
    private OnCheckedChangeListener mOnReceiveCheckedChange = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView,
                                     boolean isChecked) {
            if (isChecked) {
                xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        xmppConnectionService.getPresenceGenerator()
                                .requestPresenceUpdatesFrom(contact));
            } else {
                xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        xmppConnectionService.getPresenceGenerator()
                                .stopPresenceUpdatesFrom(contact));
            }
        }
    };
    private Jid accountJid;
    private Jid contactJid;
    private boolean showDynamicTags = false;
    private boolean showLastSeen = false;
    private boolean showInactiveOmemo = false;
    private String messageFingerprint;
    private TextView mNotifyStatusText;
    private ImageButton mNotifyStatusButton;

    private DialogInterface.OnClickListener addToPhonebook = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(Intents.Insert.IM_HANDLE, contact.getJid().toString());
            intent.putExtra(Intents.Insert.IM_PROTOCOL,
                    CommonDataKinds.Im.PROTOCOL_JABBER);
            intent.putExtra("finishActivityOnSaveCompleted", true);
            ContactDetailsActivity.this.startActivityForResult(intent, 0);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        }
    };

    private OnClickListener onBadgeClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            Uri systemAccount = contact.getSystemAccount();
            if (systemAccount == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        ContactDetailsActivity.this);
                builder.setTitle(getString(R.string.action_add_phone_book));
                builder.setMessage(getString(R.string.add_phone_book_text, contact.getJid().toString()));
                builder.setNegativeButton(getString(R.string.cancel), null);
                builder.setPositiveButton(getString(R.string.add), addToPhonebook);
                builder.create().show();
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(systemAccount);
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            }
        }
    };

    private OnClickListener mNotifyStatusClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
            builder.setTitle(R.string.pref_notification_settings);
            String[] choices = {
                    getString(R.string.notify_on_all_messages),
                    getString(R.string.notify_never)
            };
            final AtomicInteger choice;
            if (mConversation.alwaysNotify()) {
                choice = new AtomicInteger(0);
            } else {
                choice = new AtomicInteger(1);
            }
            builder.setSingleChoiceItems(choices, choice.get(), (dialog, which) -> choice.set(which));
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(R.string.ok, (DialogInterface.OnClickListener) (dialog, which) -> {
                if (choice.get() == 1) {
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(ContactDetailsActivity.this);
                    builder1.setTitle(R.string.disable_notifications);
                    final int[] durations = getResources().getIntArray(R.array.mute_options_durations);
                    final CharSequence[] labels = new CharSequence[durations.length];
                    for (int i = 0; i < durations.length; ++i) {
                        if (durations[i] == -1) {
                            labels[i] = getString(R.string.until_further_notice);
                        } else {
                            labels[i] = TimeframeUtils.resolve(ContactDetailsActivity.this, 1000L * durations[i]);
                        }
                    }
                    builder1.setItems(labels, (dialog1, which1) -> {
                                final long till;
                                if (durations[which1] == -1) {
                                    till = Long.MAX_VALUE;
                                } else {
                                    till = System.currentTimeMillis() + (durations[which1] * 1000);
                                }
                                mConversation.setMutedTill(till);
                                xmppConnectionService.updateConversation(mConversation);
                                populateView();
                            });
                    builder1.create().show();
                } else {
                    mConversation.setMutedTill(0);
                    mConversation.setAttribute(Conversation.ATTRIBUTE_ALWAYS_NOTIFY, String.valueOf(choice.get() == 0));
                }
                xmppConnectionService.updateConversation(mConversation);
                populateView();
            });
            builder.create().show();
        }
    };

    @Override
    public void onRosterUpdate() {
        refreshUi();
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(final Status status) {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        populateView();
    }

    @Override
    protected String getShareableUri(boolean http) {
        if (http) {
            return Config.inviteUserURL + XmppUri.lameUrlEncode(contact.getJid().asBareJid().toEscapedString());
        } else {
            return "xmpp:" + contact.getJid().asBareJid().toEscapedString();
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showInactiveOmemo = savedInstanceState != null && savedInstanceState.getBoolean("show_inactive_omemo", false);
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            try {
                this.accountJid = Jid.of(getIntent().getExtras().getString(EXTRA_ACCOUNT));
            } catch (final IllegalArgumentException ignored) {
            }
            try {
                this.contactJid = Jid.of(getIntent().getExtras().getString("contact"));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        this.messageFingerprint = getIntent().getStringExtra("fingerprint");

        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_contact_details);
        setSupportActionBar((Toolbar) binding.toolbar);
        configureActionBar(getSupportActionBar());
        binding.showInactiveDevices.setOnClickListener(v -> {
            showInactiveOmemo = !showInactiveOmemo;
            populateView();
        });
        binding.addContactButton.setOnClickListener(v -> showAddToRosterDialog(contact));
        this.mNotifyStatusButton = findViewById(R.id.notification_status_button);
        this.mNotifyStatusButton.setOnClickListener(this.mNotifyStatusClickListener);
        this.mNotifyStatusText = findViewById(R.id.notification_status_text);
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        savedInstanceState.putBoolean("show_inactive_omemo", showInactiveOmemo);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        } else {
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            this.showDynamicTags = preferences.getBoolean(SettingsActivity.SHOW_DYNAMIC_TAGS, false);
            this.showLastSeen = preferences.getBoolean("last_activity", false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(getString(R.string.cancel), null);
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_share_http:
                shareLink(true);
                break;
            case R.id.action_share_uri:
                shareLink(false);
                break;
            case R.id.action_edit_contact:
                Uri systemAccount = contact.getSystemAccount();
                if (systemAccount == null) {
                    quickEdit(contact.getDisplayName(), 0, new OnValueEdited() {

                        @Override
                        public String onValueEdited(String value) {
                            contact.setServerName(value);
                            ContactDetailsActivity.this.xmppConnectionService.pushContactToServer(contact);
                            populateView();
                            return null;
                        }
                    });
                } else {
                    Intent intent = new Intent(Intent.ACTION_EDIT);
                    intent.setDataAndType(systemAccount, Contacts.CONTENT_ITEM_TYPE);
                    intent.putExtra("finishActivityOnSaveCompleted", true);
                    startActivity(intent);
                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                }
                break;
            case R.id.action_block:
                BlockContactDialog.show(this, contact);
                break;
            case R.id.action_unblock:
                BlockContactDialog.show(this, contact);
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        MenuItem block = menu.findItem(R.id.action_block);
        MenuItem unblock = menu.findItem(R.id.action_unblock);
        MenuItem edit = menu.findItem(R.id.action_edit_contact);
        if (contact == null) {
            return true;
        }
        final XmppConnection connection = contact.getAccount().getXmppConnection();
        if (connection != null && connection.getFeatures().blocking()) {
            if (this.contact.isBlocked()) {
                block.setVisible(false);
            } else {
                unblock.setVisible(false);
            }
        } else {
            unblock.setVisible(false);
            block.setVisible(false);
        }
        if (!contact.showInRoster()) {
            edit.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    private void populateView() {
        if (contact == null) {
            return;
        }
        int ic_notifications = getThemeResource(R.attr.icon_notifications, R.drawable.ic_notifications_black_24dp);
        int ic_notifications_off = getThemeResource(R.attr.icon_notifications_off, R.drawable.ic_notifications_off_black_24dp);
        int ic_notifications_paused = getThemeResource(R.attr.icon_notifications_paused, R.drawable.ic_notifications_paused_black_24dp);
        long mutedTill = mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
        if (mutedTill == Long.MAX_VALUE) {
            mNotifyStatusText.setText(R.string.notify_never);
            mNotifyStatusButton.setImageResource(ic_notifications_off);
        } else if (System.currentTimeMillis() < mutedTill) {
            mNotifyStatusText.setText(R.string.notify_paused);
            mNotifyStatusButton.setImageResource(ic_notifications_paused);
        } else {
            mNotifyStatusButton.setImageResource(ic_notifications);
            mNotifyStatusText.setText(R.string.notify_on_all_messages);
        }
        if (getSupportActionBar() != null) {
            final ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setCustomView(R.layout.ab_title);
                ab.setDisplayShowCustomEnabled(true);
                TextView abtitle = findViewById(android.R.id.text1);
                TextView absubtitle = findViewById(android.R.id.text2);
                abtitle.setText(contact.getDisplayName());
                abtitle.setSelected(true);
                abtitle.setClickable(false);
                absubtitle.setVisibility(View.GONE);
                absubtitle.setClickable(false);
            }
        }

        invalidateOptionsMenu();
        setTitle(contact.getDisplayName());
        if (contact.getServer().toString().toLowerCase().equals(accountJid.getDomain().toLowerCase())) {
            binding.contactDisplayName.setText(contact.getDisplayName());
        } else {
            binding.contactDisplayName.setText(contact.getJid().toString());
        }
        if (contact.showInRoster()) {
            binding.detailsSendPresence.setVisibility(View.VISIBLE);
            binding.detailsReceivePresence.setVisibility(View.VISIBLE);
            binding.addContactButton.setVisibility(View.VISIBLE);
            binding.addContactButton.setText(getString(R.string.action_delete_contact));
            binding.addContactButton.getBackground().setColorFilter(getWarningButtonColor(), PorterDuff.Mode.MULTIPLY);
            binding.detailsSendPresence.setOnCheckedChangeListener(null);
            binding.detailsReceivePresence.setOnCheckedChangeListener(null);

            binding.addContactButton.setOnClickListener(view -> {
                final AlertDialog.Builder deleteFromRosterDialog = new AlertDialog.Builder(ContactDetailsActivity.this);
                deleteFromRosterDialog.setNegativeButton(getString(R.string.cancel), null);
                deleteFromRosterDialog.setTitle(getString(R.string.action_delete_contact));
                deleteFromRosterDialog.setMessage(getString(R.string.remove_contact_text, contact.getJid().toString()));
                deleteFromRosterDialog.setPositiveButton(getString(R.string.delete), removeFromRoster).create().show();
            });
            binding.detailsSendPresence.setOnCheckedChangeListener(null);
            binding.detailsReceivePresence.setOnCheckedChangeListener(null);

            List<String> statusMessages = contact.getPresences().getStatusMessages();
            if (statusMessages.size() == 0) {
                binding.statusMessage.setVisibility(View.GONE);
            } else {
                StringBuilder builder = new StringBuilder();
                binding.statusMessage.setVisibility(View.VISIBLE);
                int s = statusMessages.size();
                for (int i = 0; i < s; ++i) {
                    if (s > 1) {
                        builder.append("• ");
                    }
                    builder.append(statusMessages.get(i));
                    if (i < s - 1) {
                        builder.append("\n");
                    }
                }
                binding.statusMessage.setText(builder);
            }

            String resources = contact.getPresences().getMostAvailableResource();
            if (resources.length() == 0) {
                binding.resource.setVisibility(View.GONE);
            } else {
                binding.resource.setVisibility(View.VISIBLE);
                binding.resource.setText(resources);
            }

            if (contact.getOption(Contact.Options.FROM)) {
                binding.detailsSendPresence.setText(R.string.send_presence_updates);
                binding.detailsSendPresence.setChecked(true);
            } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                binding.detailsSendPresence.setChecked(false);
                binding.detailsSendPresence.setText(R.string.send_presence_updates);
            } else {
                binding.detailsSendPresence.setText(R.string.preemptively_grant);
                if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                    binding.detailsSendPresence.setChecked(true);
                } else {
                    binding.detailsSendPresence.setChecked(false);
                }
            }
            if (contact.getOption(Contact.Options.TO)) {
                binding.detailsReceivePresence.setText(R.string.receive_presence_updates);
                binding.detailsReceivePresence.setChecked(true);
            } else {
                binding.detailsReceivePresence.setText(R.string.ask_for_presence_updates);
                if (contact.getOption(Contact.Options.ASKING)) {
                    binding.detailsReceivePresence.setChecked(true);
                } else {
                    binding.detailsReceivePresence.setChecked(false);
                }
            }
            if (contact.getAccount().isOnlineAndConnected()) {
                binding.detailsReceivePresence.setEnabled(true);
                binding.detailsSendPresence.setEnabled(true);
            } else {
                binding.detailsReceivePresence.setEnabled(false);
                binding.detailsSendPresence.setEnabled(false);
            }
            binding.detailsSendPresence.setOnCheckedChangeListener(this.mOnSendCheckedChange);
            binding.detailsReceivePresence.setOnCheckedChangeListener(this.mOnReceiveCheckedChange);
        } else {
            binding.addContactButton.setVisibility(View.VISIBLE);
            binding.addContactButton.setText(getString(R.string.add_contact));
            binding.addContactButton.getBackground().clearColorFilter();
            binding.addContactButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    showAddToRosterDialog(contact);
                }
            });
            binding.detailsSendPresence.setVisibility(View.GONE);
            binding.detailsReceivePresence.setVisibility(View.GONE);
            binding.statusMessage.setVisibility(View.GONE);
        }

        if (contact.isBlocked() && !this.showDynamicTags) {
            binding.detailsLastseen.setVisibility(View.VISIBLE);
            binding.detailsLastseen.setText(R.string.contact_blocked);
        } else {
            if (showLastSeen
                    && contact.getLastseen() > 0
                    && contact.getPresences().allOrNonSupport(Namespace.IDLE)) {
                binding.detailsLastseen.setVisibility(View.VISIBLE);
                binding.detailsLastseen.setText(UIHelper.lastseen(getApplicationContext(), contact.isActive(), contact.getLastseen()));
            } else {
                binding.detailsLastseen.setVisibility(View.GONE);
            }
        }

        binding.detailsContactjid.setText(IrregularUnicodeDetector.style(this, contact.getJid()));
        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = contact.getAccount().getJid().getLocal();
        } else {
            account = contact.getAccount().getJid().asBareJid().toString();
        }
        binding.detailsAccount.setText(getString(R.string.using_account, account));
        binding.detailsContactBadge.setImageBitmap(avatarService().get(contact, getPixel(Config.AVATAR_SIZE)));
        binding.detailsContactBadge.setOnClickListener(this.onBadgeClick);

        if (xmppConnectionService.multipleAccounts()) {
            binding.detailsAccount.setVisibility(View.VISIBLE);
        } else {
            binding.detailsAccount.setVisibility(View.GONE);
        }

        binding.detailsContactKeys.removeAllViews();
        boolean hasKeys = false;
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (Config.supportOtr()) {
            for (final String otrFingerprint : contact.getOtrFingerprints()) {
                hasKeys = true;
                View view = inflater.inflate(R.layout.contact_key, binding.detailsContactKeys, false);
                TextView key = view.findViewById(R.id.key);
                TextView keyType = view.findViewById(R.id.key_type);
                ImageButton removeButton = view
                        .findViewById(R.id.button_remove);
                removeButton.setVisibility(View.VISIBLE);
                key.setText(CryptoHelper.prettifyFingerprint(otrFingerprint));
                if (otrFingerprint != null && otrFingerprint.equalsIgnoreCase(messageFingerprint)) {
                    keyType.setText(R.string.otr_fingerprint_selected_message);
                    keyType.setTextColor(ContextCompat.getColor(this, R.color.accent));
                } else {
                    keyType.setText(R.string.otr_fingerprint);
                }
                binding.detailsContactKeys.addView(view);
                removeButton.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        confirmToDeleteFingerprint(otrFingerprint);
                    }
                });
            }
        }
        final AxolotlService axolotlService = contact.getAccount().getAxolotlService();
        if (Config.supportOmemo() && axolotlService != null) {
            boolean skippedInactive = false;
            boolean showsInactive = false;
            for (final XmppAxolotlSession session : axolotlService.findSessionsForContact(contact)) {
                final FingerprintStatus trust = session.getTrust();
                hasKeys |= !trust.isCompromised();
                if (!trust.isActive()) {
                    if (showInactiveOmemo) {
                        showsInactive = true;
                    } else {
                        skippedInactive = true;
                        continue;
                    }
                }
                if (!trust.isCompromised()) {
                    boolean highlight = session.getFingerprint().equals(messageFingerprint);
                    addFingerprintRow(binding.detailsContactKeys, session, highlight);
                }
            }
            if (showsInactive || skippedInactive) {
                binding.showInactiveDevices.setText(showsInactive ? R.string.hide_inactive_devices : R.string.show_inactive_devices);
                binding.showInactiveDevices.setVisibility(View.VISIBLE);
            } else {
                binding.showInactiveDevices.setVisibility(View.GONE);
            }
        } else {
            binding.showInactiveDevices.setVisibility(View.GONE);
        }
        binding.scanButton.setVisibility(hasKeys && isCameraFeatureAvailable() ? View.VISIBLE : View.GONE);
        if (hasKeys) {
            binding.scanButton.setOnClickListener((v) -> ScanActivity.scan(this));
        }
        if (Config.supportOpenPgp() && contact.getPgpKeyId() != 0) {
            hasKeys = true;
            View view = inflater.inflate(R.layout.contact_key, binding.detailsContactKeys, false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            keyType.setText(R.string.openpgp_key_id);
            if ("pgp".equals(messageFingerprint)) {
                keyType.setTextColor(ContextCompat.getColor(this, R.color.accent));
            }
            key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
            final OnClickListener openKey = new OnClickListener() {

                @Override
                public void onClick(View v) {
                    launchOpenKeyChain(contact.getPgpKeyId());
                }
            };
            view.setOnClickListener(openKey);
            key.setOnClickListener(openKey);
            keyType.setOnClickListener(openKey);
            binding.detailsContactKeys.addView(view);
        }
        binding.keysWrapper.setVisibility(hasKeys ? View.VISIBLE : View.GONE);

        List<ListItem.Tag> tagList = contact.getTags(this);
        if (tagList.size() == 0 || !this.showDynamicTags) {
            binding.tags.setVisibility(View.GONE);
        } else {
            binding.tags.setVisibility(View.VISIBLE);
            binding.tags.removeAllViewsInLayout();
            for (final ListItem.Tag tag : tagList) {
                final TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag, binding.tags, false);
                tv.setText(tag.getName());
                tv.setBackgroundColor(tag.getColor());
                binding.tags.addView(tv);
            }
        }
    }

    protected void confirmToDeleteFingerprint(final String fingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_fingerprint);
        builder.setMessage(R.string.sure_delete_fingerprint);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.delete,
                new android.content.DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (contact.deleteOtrFingerprint(fingerprint)) {
                            populateView();
                            xmppConnectionService.syncRosterToDisk(contact.getAccount());
                        }
                    }

                });
        builder.create().show();
    }

    public void onBackendConnected() {
        if (accountJid != null && contactJid != null) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.mConversation = xmppConnectionService.findConversation(account, contactJid, false);
            if (this.mConversation != null) {
                populateView();
            }
            this.contact = account.getRoster().getContact(contactJid);
            if (mPendingFingerprintVerificationUri != null) {
                processFingerprintVerification(mPendingFingerprintVerificationUri);
                mPendingFingerprintVerificationUri = null;
            }
            populateView();
        }
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    @Override
    protected void processFingerprintVerification(XmppUri uri) {
        if (contact != null && contact.getJid().asBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(contact, uri.getFingerprints())) {
                Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
        }
    }
}
