package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMediaBrowserBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.adapter.MediaAdapter;
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.GridManager;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import rocks.xmpp.addr.Jid;


public class MediaBrowserActivity extends XmppActivity implements OnMediaLoaded {

    private ActivityMediaBrowserBinding binding;
    private MediaAdapter mMediaAdapter;
    private boolean OnlyImagesVideos = false;
    ArrayList<Attachment> allAttachments = new ArrayList<>();
    ArrayList<Attachment> filteredAttachments = new ArrayList<>();
    private String mSavedInstanceAccount;
    private String mSavedInstanceJid;
    private String account;
    private String jid;

    @Override
    protected void onStart() {
        super.onStart();
        filter(OnlyImagesVideos);
        invalidateOptionsMenu();
        refreshUiReal();
    }

    public static void launch(Context context, Contact contact) {
        launch(context, contact.getAccount(), contact.getJid().asBareJid().toEscapedString());
    }

    public static void launch(Context context, Conversation conversation) {
        launch(context, conversation.getAccount(), conversation.getJid().asBareJid().toEscapedString());
    }

    private static void launch(Context context, Account account, String jid) {
        final Intent intent = new Intent(context, MediaBrowserActivity.class);
        intent.putExtra("account", account.getUuid());
        intent.putExtra("jid", jid);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            this.mSavedInstanceAccount = savedInstanceState.getString("account");
            this.mSavedInstanceJid = savedInstanceState.getString("jid");
        }
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_media_browser);
        setSupportActionBar((Toolbar) binding.toolbar);
        configureActionBar(getSupportActionBar());
        mMediaAdapter = new MediaAdapter(this, R.dimen.media_size);
        this.binding.media.setAdapter(mMediaAdapter);
        GridManager.setupLayoutManager(this, this.binding.media, R.dimen.browser_media_size);
        this.binding.noMedia.setVisibility(View.GONE);
        this.binding.progressbar.setVisibility(View.VISIBLE);
        this.OnlyImagesVideos = getPreferences().getBoolean("show_videos_images_only", this.getResources().getBoolean(R.bool.show_videos_images_only));
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        savedInstanceState.putString("account", account);
        savedInstanceState.putString("jid", jid);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void refreshUiReal() {
        mMediaAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        MenuItem showImagesVideosOnly = menu.findItem(R.id.show_videos_images_only);
        showImagesVideosOnly.setChecked(OnlyImagesVideos);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.media_browser, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.show_videos_images_only:
                this.OnlyImagesVideos = !menuItem.isChecked();
                menuItem.setChecked(this.OnlyImagesVideos);
                getPreferences().edit().putBoolean("show_videos_images_only", OnlyImagesVideos).apply();
                filter(OnlyImagesVideos);
                invalidateOptionsMenu();
                refreshUiReal();
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    void onBackendConnected() {
        final Intent intent = getIntent();
        if (mSavedInstanceAccount != null) {
            try {
                account = mSavedInstanceAccount;
            } catch (Exception e) {
                account = intent == null ? null : intent.getStringExtra("account");
            }
        } else {
            account = intent == null ? null : intent.getStringExtra("account");
        }
        if (mSavedInstanceJid != null) {
            try {
                jid = mSavedInstanceJid;
            } catch (Exception e) {
                jid = intent == null ? null : intent.getStringExtra("jid");
            }
        } else {
            jid = intent == null ? null : intent.getStringExtra("jid");
        }
        if (account != null && jid != null) {
            xmppConnectionService.getAttachments(account, Jid.of(jid), 0, this);
        }
    }

    @Override
    public void onMediaLoaded(List<Attachment> attachments) {
        allAttachments.clear();
        allAttachments.addAll(attachments);
        runOnUiThread(() -> {
            filter(OnlyImagesVideos);
        });
    }

    private void loadAttachments(List<Attachment> attachments) {
        if (attachments.size() > 0) {
            if (mMediaAdapter.getItemCount() != attachments.size()) {
                mMediaAdapter.setAttachments(attachments);
            }
            this.binding.noMedia.setVisibility(View.GONE);
            this.binding.progressbar.setVisibility(View.GONE);
        } else {
            this.binding.noMedia.setVisibility(View.VISIBLE);
            this.binding.progressbar.setVisibility(View.GONE);
        }
    }

    protected void filter(boolean needle) {
        if (xmppConnectionServiceBound) {
            filterAttachments(needle);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        filter(OnlyImagesVideos);
    }

    protected void filterAttachments(boolean needle) {
        if (allAttachments.size() > 0) {
            if (needle) {
                final ArrayList<Attachment> attachments = new ArrayList<>(allAttachments);
                filteredAttachments.clear();
                for (Attachment attachment : attachments) {
                    if (attachment.getMime() != null && (attachment.getMime().startsWith("image/") || attachment.getMime().startsWith("video/"))) {
                        filteredAttachments.add(attachment);
                    }
                }
                loadAttachments(filteredAttachments);
            } else {
                loadAttachments(allAttachments);
            }
        } else {
            loadAttachments(allAttachments);
        }
    }
}