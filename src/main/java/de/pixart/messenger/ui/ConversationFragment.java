package de.pixart.messenger.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.media.ExifInterface;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import net.java.otr4j.session.SessionStatus;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import de.pixart.messenger.Config;
import de.pixart.messenger.R;
import de.pixart.messenger.crypto.axolotl.AxolotlService;
import de.pixart.messenger.crypto.axolotl.FingerprintStatus;
import de.pixart.messenger.databinding.FragmentConversationBinding;
import de.pixart.messenger.entities.Account;
import de.pixart.messenger.entities.Blockable;
import de.pixart.messenger.entities.Contact;
import de.pixart.messenger.entities.Conversation;
import de.pixart.messenger.entities.DownloadableFile;
import de.pixart.messenger.entities.Message;
import de.pixart.messenger.entities.MucOptions;
import de.pixart.messenger.entities.Presence;
import de.pixart.messenger.entities.ReadByMarker;
import de.pixart.messenger.entities.Transferable;
import de.pixart.messenger.entities.TransferablePlaceholder;
import de.pixart.messenger.http.HttpDownloadConnection;
import de.pixart.messenger.persistance.FileBackend;
import de.pixart.messenger.services.MessageArchiveService;
import de.pixart.messenger.services.XmppConnectionService;
import de.pixart.messenger.ui.adapter.MessageAdapter;
import de.pixart.messenger.ui.util.ActivityResult;
import de.pixart.messenger.ui.util.AttachmentTool;
import de.pixart.messenger.ui.util.ConversationMenuConfigurator;
import de.pixart.messenger.ui.util.DateSeparator;
import de.pixart.messenger.ui.util.ListViewUtils;
import de.pixart.messenger.ui.util.PendingItem;
import de.pixart.messenger.ui.util.PresenceSelector;
import de.pixart.messenger.ui.util.ScrollState;
import de.pixart.messenger.ui.util.SendButtonAction;
import de.pixart.messenger.ui.util.SendButtonTool;
import de.pixart.messenger.ui.util.ShareUtil;
import de.pixart.messenger.ui.widget.EditMessage;
import de.pixart.messenger.utils.FileUtils;
import de.pixart.messenger.utils.MenuDoubleTabUtil;
import de.pixart.messenger.utils.MessageUtils;
import de.pixart.messenger.utils.NickValidityChecker;
import de.pixart.messenger.utils.Patterns;
import de.pixart.messenger.utils.QuickLoader;
import de.pixart.messenger.utils.StylingHelper;
import de.pixart.messenger.utils.UIHelper;
import de.pixart.messenger.xmpp.XmppConnection;
import de.pixart.messenger.xmpp.chatstate.ChatState;
import de.pixart.messenger.xmpp.jingle.JingleConnection;
import in.championswimmer.sfg.lib.SimpleFingerGestures;
import rocks.xmpp.addr.Jid;

import static de.pixart.messenger.ui.XmppActivity.EXTRA_ACCOUNT;
import static de.pixart.messenger.ui.XmppActivity.REQUEST_INVITE_TO_CONVERSATION;
import static de.pixart.messenger.ui.util.SoftKeyboardUtils.hideSoftKeyboard;
import static de.pixart.messenger.xmpp.Patches.ENCRYPTION_EXCEPTIONS;

public class ConversationFragment extends XmppFragment implements EditMessage.KeyboardListener {

    public static final int REQUEST_SEND_MESSAGE = 0x0201;
    public static final int REQUEST_DECRYPT_PGP = 0x0202;
    public static final int REQUEST_ENCRYPT_MESSAGE = 0x0207;
    public static final int REQUEST_TRUST_KEYS_TEXT = 0x0208;
    public static final int REQUEST_TRUST_KEYS_MENU = 0x0209;
    public static final int REQUEST_START_DOWNLOAD = 0x0210;
    public static final int REQUEST_ADD_EDITOR_CONTENT = 0x0211;
    public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x0301;
    public static final int ATTACHMENT_CHOICE_TAKE_FROM_CAMERA = 0x0302;
    public static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 0x0303;
    public static final int ATTACHMENT_CHOICE_RECORD_VOICE = 0x0304;
    public static final int ATTACHMENT_CHOICE_LOCATION = 0x0305;
    public static final int ATTACHMENT_CHOICE_CHOOSE_VIDEO = 0x0306;
    public static final int ATTACHMENT_CHOICE_INVALID = 0x0399;

    public static final String RECENTLY_USED_QUICK_ACTION = "recently_used_quick_action";
    public static final String STATE_CONVERSATION_UUID = ConversationFragment.class.getName() + ".uuid";
	public static final String STATE_SCROLL_POSITION = ConversationFragment.class.getName() + ".scroll_position";
    public static final String STATE_PHOTO_URI = ConversationFragment.class.getName() + ".take_photo_uri";
    public static final String STATE_VIDEO_URI = ConversationFragment.class.getName() + ".take_video_uri";

    private static final String STATE_LAST_MESSAGE_UUID = "state_last_message_uuid";

    private final List<Message> messageList = new ArrayList<>();
    final private List<Uri> mPendingImageUris = new ArrayList<>();
    private String lastMessageUuid = null;
    public Uri mPendingEditorContent = null;
    private final PendingItem<ActivityResult> postponedActivityResult = new PendingItem<>();
	private final PendingItem<String> pendingConversationsUuid = new PendingItem<>();
    private final PendingItem<Bundle> pendingExtras = new PendingItem<>();
    private final PendingItem<Uri> pendingTakePhotoUri = new PendingItem<>();
    private final PendingItem<Uri> pendingTakeVideoUri = new PendingItem<>();
    private final PendingItem<ScrollState> pendingScrollState = new PendingItem<>();
    private final PendingItem<String> pendingLastMessageUuid = new PendingItem<>();
    private final PendingItem<Message> pendingMessage = new PendingItem<>();
    protected MessageAdapter messageListAdapter;
    private Conversation conversation;
	public FragmentConversationBinding binding;
    protected Message lastHistoryMessage = null;
    SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd. MMM yyyy", Locale.getDefault());
    private Toast messageLoaderToast;
    private ConversationsActivity activity;
    private boolean reInitRequiredOnStart = true;

    private SimpleFingerGestures gesturesDetector = new SimpleFingerGestures();

    protected OnClickListener clickToVerify = new OnClickListener() {
        @Override
        public void onClick(View v) {
            activity.verifyOtrSessionDialog(conversation, v);
        }
    };
    private OnClickListener clickToMuc = new OnClickListener() {

        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getActivity(), ConferenceDetailsActivity.class);
            intent.setAction(ConferenceDetailsActivity.ACTION_VIEW_MUC);
            intent.putExtra("uuid", conversation.getUuid());
            startActivity(intent);
            activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        }
    };
    private OnClickListener leaveMuc = new OnClickListener() {

        @Override
        public void onClick(View v) {
            activity.xmppConnectionService.archiveConversation(conversation);
        }
    };
    private OnClickListener joinMuc = new OnClickListener() {

        @Override
        public void onClick(View v) {
            activity.xmppConnectionService.joinMuc(conversation);
        }
    };
    private OnClickListener enterPassword = new OnClickListener() {

        @Override
        public void onClick(View v) {
            MucOptions muc = conversation.getMucOptions();
            String password = muc.getPassword();
            if (password == null) {
                password = "";
            }
            activity.quickPasswordEdit(password, value -> {
                activity.xmppConnectionService.providePasswordForMuc(conversation, value);
                return null;
            });
        }
    };

    private OnScrollListener mOnScrollListener = new OnScrollListener() {

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (AbsListView.OnScrollListener.SCROLL_STATE_IDLE == scrollState) {
                fireReadEvent();
            }
        }

        @Override
        public void onScroll(final AbsListView view, int firstVisibleItem, int visibleItemCount, final int totalItemCount) {
            toggleScrollDownButton(view);
            synchronized (ConversationFragment.this.messageList) {
                if (firstVisibleItem < 25 && conversation != null && conversation.messagesLoaded.compareAndSet(true, false) && messageList.size() > 0) {
                    long timestamp;
                    if (messageList.get(0).getType() == Message.TYPE_STATUS && messageList.size() >= 2) {
                        timestamp = messageList.get(1).getTimeSent();
                    } else {
                        timestamp = messageList.get(0).getTimeSent();
                    }
                    activity.xmppConnectionService.loadMoreMessages(conversation, timestamp, new XmppConnectionService.OnMoreMessagesLoaded() {
                        @Override
                        public void onMoreMessagesLoaded(final int c, final Conversation conversation) {
                            if (ConversationFragment.this.conversation != conversation) {
                                conversation.messagesLoaded.set(true);
                                return;
                            }
                            runOnUiThread(() -> {
                                synchronized (messageList) {
                                    final int oldPosition = binding.messagesView.getFirstVisiblePosition();
                                    Message message = null;
                                    int childPos;
                                    for (childPos = 0; childPos + oldPosition < messageList.size(); ++childPos) {
                                        message = messageList.get(oldPosition + childPos);
                                        if (message.getType() != Message.TYPE_STATUS) {
                                            break;
                                        }
                                    }
                                    final String uuid = message != null ? message.getUuid() : null;
                                    View v = binding.messagesView.getChildAt(childPos);
                                    final int pxOffset = (v == null) ? 0 : v.getTop();
                                    ConversationFragment.this.conversation.populateWithMessages(ConversationFragment.this.messageList);
                                    try {
                                        updateStatusMessages();
                                    } catch (IllegalStateException e) {
                                        Log.d(Config.LOGTAG, "caught illegal state exception while updating status messages");
                                    }
                                    messageListAdapter.notifyDataSetChanged();
                                    int pos = Math.max(getIndexOf(uuid, messageList), 0);
                                    binding.messagesView.setSelectionFromTop(pos, pxOffset);
                                    if (messageLoaderToast != null) {
                                        messageLoaderToast.cancel();
                                    }
                                    conversation.messagesLoaded.set(true);
                                }
                            });
                        }

                        @Override
                        public void informUser(final int resId) {

                            runOnUiThread(() -> {
                                if (messageLoaderToast != null) {
                                    messageLoaderToast.cancel();
                                }
                                if (ConversationFragment.this.conversation != conversation) {
                                    return;
                                }
                                messageLoaderToast = Toast.makeText(view.getContext(), resId, Toast.LENGTH_LONG);
                                messageLoaderToast.show();
                            });
                        }
                    });
                }
            }
        }
    };

    private EditMessage.OnCommitContentListener mEditorContentListener = new EditMessage.OnCommitContentListener() {
        @Override
        public boolean onCommitContent(InputContentInfoCompat inputContentInfo, int flags, Bundle opts, String[] contentMimeTypes) {
            // try to get permission to read the image, if applicable
            if ((flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                try {
                    inputContentInfo.requestPermission();
                } catch (Exception e) {
                    Log.e(Config.LOGTAG, "InputContentInfoCompat#requestPermission() failed.", e);
                    Toast.makeText(getActivity(), activity.getString(R.string.no_permission_to_access_x, inputContentInfo.getDescription()), Toast.LENGTH_LONG
                    ).show();
                    return false;
                }
            }
            if (hasPermissions(REQUEST_ADD_EDITOR_CONTENT, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                attachImageToConversation(inputContentInfo.getContentUri());
            } else {
                mPendingEditorContent = inputContentInfo.getContentUri();
            }
            return true;
        }
    };
    private Message selectedMessage;
    private OnClickListener mEnableAccountListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Account account = conversation == null ? null : conversation.getAccount();
            if (account != null) {
                account.setOption(Account.OPTION_DISABLED, false);
                activity.xmppConnectionService.updateAccount(account);
            }
        }
    };
    private OnClickListener mUnblockClickListener = new OnClickListener() {
        @Override
        public void onClick(final View v) {
            v.post(() -> v.setVisibility(View.INVISIBLE));
            if (conversation.isDomainBlocked()) {
                BlockContactDialog.show(activity, conversation);
            } else {
                unblockConversation(conversation);
            }
        }
    };
    private OnClickListener mBlockClickListener = this::showBlockSubmenu;

    private OnClickListener mAddBackClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Contact contact = conversation == null ? null : conversation.getContact();
            if (contact != null) {
                activity.xmppConnectionService.createContact(contact, true);
                activity.switchToContactDetails(contact);
            }
        }
    };
    private View.OnLongClickListener mLongPressBlockListener = v -> {
        showBlockSubmenu(v);
        return true;
    };

    private OnClickListener mHideUnencryptionHint = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (Config.supportOmemo() && conversation.getAccount().getAxolotlService().isConversationAxolotlCapable(conversation)) {
                conversation.setNextEncryption(Message.ENCRYPTION_AXOLOTL);
                activity.xmppConnectionService.updateConversation(conversation);
                activity.refreshUi();
            }
            hideSnackbar();
        }
    };
    private OnClickListener mAllowPresenceSubscription = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Contact contact = conversation == null ? null : conversation.getContact();
            if (contact != null) {
                activity.xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        activity.xmppConnectionService.getPresenceGenerator()
                                .sendPresenceUpdatesTo(contact));
                hideSnackbar();
            }
        }
    };
    private OnClickListener mAnswerSmpClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent(activity, VerifyOTRActivity.class);
            intent.setAction(VerifyOTRActivity.ACTION_VERIFY_CONTACT);
            intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
            intent.putExtra(VerifyOTRActivity.EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
            intent.putExtra("mode", VerifyOTRActivity.MODE_ANSWER_QUESTION);
            startActivity(intent);
            activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        }
    };

    protected OnClickListener clickToDecryptListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            PendingIntent pendingIntent = conversation.getAccount().getPgpDecryptionService().getPendingIntent();
            if (pendingIntent != null) {
                try {
                    getActivity().startIntentSenderForResult(pendingIntent.getIntentSender(),
                            REQUEST_DECRYPT_PGP,
                            null,
                            0,
                            0,
                            0);
                } catch (SendIntentException e) {
                    Toast.makeText(getActivity(), R.string.unable_to_connect_to_keychain, Toast.LENGTH_SHORT).show();
                    conversation.getAccount().getPgpDecryptionService().continueDecryption(true);
                }
            }
            updateSnackBar(conversation);
        }
    };
    private AtomicBoolean mSendingPgpMessage = new AtomicBoolean(false);
    private OnEditorActionListener mEditorActionListener = (v, actionId, event) -> {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && imm.isFullscreenMode()) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            sendMessage();
            return true;
        } else {
            return false;
        }
    };

    private OnClickListener mScrollButtonListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            stopScrolling();
            setSelection(binding.messagesView.getCount() - 1, true);
        }
    };

    private OnClickListener mSendButtonListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            Object tag = v.getTag();
            if (tag instanceof SendButtonAction) {
                SendButtonAction action = (SendButtonAction) tag;
                switch (action) {
                    case TAKE_FROM_CAMERA:
                    case SEND_LOCATION:
                    case RECORD_VOICE:
                    case CHOOSE_PICTURE:
                        attachFile(action.toChoice());
                        break;
                    case CANCEL:
                        if (conversation != null) {
                            if (conversation.setCorrectingMessage(null)) {
                                binding.textinput.setText("");
                                binding.textinput.append(conversation.getDraftMessage());
                                conversation.setDraftMessage(null);
                            } else if (conversation.getMode() == Conversation.MODE_MULTI) {
                                conversation.setNextCounterpart(null);
                            }
                            updateChatMsgHint();
                            updateSendButton();
                            updateEditablity();
                        }
                        break;
                    default:
                        sendMessage();
                }
            } else {
                sendMessage();
            }
        }
    };
    private View.OnLongClickListener mSendButtonLongListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            final String body = binding.textinput.getText().toString();
            if (body.length() == 0) {
                binding.textinput.getText().insert(0, Message.ME_COMMAND + " ");
            }
            return true;
        }
    };
    private int completionIndex = 0;
    private int lastCompletionLength = 0;
    private String incomplete;
    private int lastCompletionCursor;
    private boolean firstWord = false;
    private Message mPendingDownloadableMessage;

    private static ConversationFragment findConversationFragment(Activity activity) {
        Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment != null && fragment instanceof ConversationFragment) {
            return (ConversationFragment) fragment;
        }
        fragment = activity.getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (fragment != null && fragment instanceof ConversationFragment) {
            return (ConversationFragment) fragment;
        }
        return null;
    }

    public static void startStopPending(Activity activity) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.messageListAdapter.startStopPending();
        }
    }

    public static void downloadFile(Activity activity, Message message) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.startDownloadable(message);
        }
    }

    public static void registerPendingMessage(Activity activity, Message message) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.pendingMessage.push(message);
        }
    }

    public static void openPendingMessage(Activity activity) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            Message message = fragment.pendingMessage.pop();
            if (message != null) {
                fragment.messageListAdapter.openDownloadable(message);
            }
        }
    }

    public static Conversation getConversation(Activity activity) {
        return getConversation(activity, R.id.secondary_fragment);
    }

    private static Conversation getConversation(Activity activity, @IdRes int res) {
        final Fragment fragment = activity.getFragmentManager().findFragmentById(res);
        if (fragment != null && fragment instanceof ConversationFragment) {
            return ((ConversationFragment) fragment).getConversation();
        } else {
            return null;
        }
    }

    public static Conversation getConversationReliable(Activity activity) {
        final Conversation conversation = getConversation(activity, R.id.secondary_fragment);
        if (conversation != null) {
            return conversation;
        }
        return getConversation(activity, R.id.main_fragment);
    }

    private static boolean allGranted(int[] grantResults) {
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private static String getFirstDenied(int[] grantResults, String[] permissions) {
        for (int i = 0; i < grantResults.length; ++i) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                return permissions[i];
            }
        }
        return null;
    }

    private static boolean scrolledToBottom(AbsListView listView) {
        final int count = listView.getCount();
        if (count == 0) {
            return true;
        } else if (listView.getLastVisiblePosition() == count - 1) {
            final View lastChild = listView.getChildAt(listView.getChildCount() - 1);
            return lastChild != null && lastChild.getBottom() <= listView.getHeight();
        } else {
            return false;
        }
    }

    private void toggleScrollDownButton() {
        toggleScrollDownButton(binding.messagesView);
    }

    private void toggleScrollDownButton(AbsListView listView) {
        if (conversation == null) {
            return;
        }
        if (scrolledToBottom(listView)) {
            lastMessageUuid = null;
            hideUnreadMessagesCount();
        } else {
            binding.scrollToBottomButton.setEnabled(true);
            binding.scrollToBottomButton.setVisibility(View.VISIBLE);
            if (lastMessageUuid == null) {
                lastMessageUuid = conversation.getLatestMessage().getUuid();
            }
            if (conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid) > 0) {
                binding.unreadCountCustomView.setVisibility(View.VISIBLE);
            }
        }
    }

    private int getIndexOf(String uuid, List<Message> messages) {
        if (uuid == null) {
            return messages.size() - 1;
        }
        for (int i = 0; i < messages.size(); ++i) {
            if (uuid.equals(messages.get(i).getUuid())) {
                return i;
            } else {
                Message next = messages.get(i);
                while (next != null && next.wasMergedIntoPrevious()) {
                    if (uuid.equals(next.getUuid())) {
                        return i;
                    }
                    next = next.next();
                }

            }
        }
        return -1;
    }

    private ScrollState getScrollPosition(int pos, View view) {
        final ListView listView = this.binding.messagesView;
        if (listView.getCount() == 0 || listView.getLastVisiblePosition() == listView.getCount() - 1) {
            return null;
        } else {
            //final int pos = listView.getFirstVisiblePosition();
            //final View view = listView.getChildAt(0);
            if (view == null) {
                return null;
            } else {
                return new ScrollState(pos, view.getTop());
            }
        }
    }

    private ScrollState getScrollPosition() {
        final ListView listView = this.binding.messagesView;
        if (listView.getCount() == 0 || listView.getLastVisiblePosition() == listView.getCount() - 1) {
            return null;
        } else {
            final int pos = listView.getFirstVisiblePosition();
            final View view = listView.getChildAt(0);
            if (view == null) {
                return null;
            } else {
                return new ScrollState(pos, view.getTop());
            }
        }
    }

    private void setScrollPosition(ScrollState scrollPosition, String lastMessageUuid) {
        if (scrollPosition != null) {
            this.lastMessageUuid = lastMessageUuid;
            if (lastMessageUuid != null) {
                binding.unreadCountCustomView.setUnreadCount(conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid));
            }
            //TODO maybe this needs a 'post'
            this.binding.messagesView.setSelectionFromTop(scrollPosition.position, scrollPosition.offset);
            toggleScrollDownButton();
        }
    }

    private void attachLocationToConversation(Conversation conversation, Uri uri) {
        if (conversation == null) {
            return;
        }
        activity.xmppConnectionService.attachLocationToConversation(conversation, uri, new UiCallback<Message>() {

            @Override
            public void success(Message message) {

            }

            @Override
            public void error(int errorCode, Message object) {
                //TODO show possible pgp error
            }

            @Override
            public void userInputRequried(PendingIntent pi, Message object) {

            }
        });
    }

    private void attachFileToConversation(Conversation conversation, Uri uri, String type) {
        if (conversation == null) {
            return;
        }
        final Toast prepareFileToast = Toast.makeText(getActivity(), getText(R.string.preparing_file), Toast.LENGTH_LONG);
        prepareFileToast.show();
        activity.delegateUriPermissionsToService(uri);
        activity.xmppConnectionService.attachFileToConversation(conversation, uri, type, new UiInformableCallback<Message>() {
            @Override
            public void inform(final String text) {
                hidePrepareFileToast(prepareFileToast);
                runOnUiThread(() -> activity.replaceToast(text));
            }

            @Override
            public void success(Message message) {
                runOnUiThread(() -> activity.hideToast());
                hidePrepareFileToast(prepareFileToast);
            }

            @Override
            public void error(final int errorCode, Message message) {
                hidePrepareFileToast(prepareFileToast);
                runOnUiThread(() -> activity.replaceToast(getString(errorCode)));

            }

            @Override
            public void userInputRequried(PendingIntent pi, Message message) {
                hidePrepareFileToast(prepareFileToast);
            }
        });
    }

    private void attachPhotoToConversation(Conversation conversation, Uri uri) {
        if (conversation == null) {
            return;
        }
        final Toast prepareFileToast = Toast.makeText(getActivity(), getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareFileToast.show();
        activity.delegateUriPermissionsToService(uri);
        activity.xmppConnectionService.attachImageToConversation(conversation, uri,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi, Message object) {
                        hidePrepareFileToast(prepareFileToast);
                    }

                    @Override
                    public void success(Message message) {
                        hidePrepareFileToast(prepareFileToast);
                    }

                    @Override
                    public void error(final int error, Message message) {
                        hidePrepareFileToast(prepareFileToast);
                        activity.runOnUiThread(() -> activity.replaceToast(getString(error)));
                    }
                });
    }

    private void attachImagesToConversation(Conversation conversation, Uri uri) {
        if (conversation == null) {
            return;
        }
        final Toast prepareFileToast = Toast.makeText(getActivity(), getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareFileToast.show();
        activity.delegateUriPermissionsToService(uri);
        activity.xmppConnectionService.attachImageToConversation(conversation, uri,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi, Message object) {
                        hidePrepareFileToast(prepareFileToast);
                    }

                    @Override
                    public void success(Message message) {
                        hidePrepareFileToast(prepareFileToast);
                    }

                    @Override
                    public void error(final int error, Message message) {
                        hidePrepareFileToast(prepareFileToast);
                        activity.runOnUiThread(() -> activity.replaceToast(getString(error)));
                    }
                });
    }

    public void attachImageToConversation(Uri uri) {
        this.attachImageToConversation(conversation, uri, true);
    }

    private void attachImageToConversation(Conversation conversation, Uri uri, boolean sendAsIs) {
        if (conversation == null) {
            return;
        }
        if (sendAsIs) {
            sendImage(conversation, uri);
            return;
        }
        final Conversation conversation_preview = conversation;
        final Uri uri_preview = uri;
        Bitmap bitmap = BitmapFactory.decodeFile(FileUtils.getPath(activity, uri));
        File file = null;
        ExifInterface exif = null;
        int orientation = 0;
        try {
            file = new File(FileUtils.getPath(activity, uri));
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (file != null) {
            try {
                exif = new ExifInterface(file.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        }
        Log.d(Config.LOGTAG, "EXIF: " + orientation);
        Bitmap rotated_image = null;
        Log.d(Config.LOGTAG, "Rotate image");
        rotated_image = FileBackend.rotateBitmap(file, bitmap, orientation);
        if (rotated_image != null) {
            int scaleSize = 600;
            int originalWidth = rotated_image.getWidth();
            int originalHeight = rotated_image.getHeight();
            int newWidth = -1;
            int newHeight = -1;
            float multFactor;
            if (originalHeight > originalWidth) {
                newHeight = scaleSize;
                multFactor = (float) originalWidth / (float) originalHeight;
                newWidth = (int) (newHeight * multFactor);
            } else if (originalWidth > originalHeight) {
                newWidth = scaleSize;
                multFactor = (float) originalHeight / (float) originalWidth;
                newHeight = (int) (newWidth * multFactor);
            } else if (originalHeight == originalWidth) {
                newHeight = scaleSize;
                newWidth = scaleSize;
            }
            Log.d(Config.LOGTAG, "Scaling preview image from " + originalHeight + "px x " + originalWidth + "px to " + newHeight + "px x " + newWidth + "px");
            Bitmap preview = Bitmap.createScaledBitmap(rotated_image, newWidth, newHeight, false);
            ImageView ImagePreview = new ImageView(activity);
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            ImagePreview.setLayoutParams(vp);
            ImagePreview.setMaxWidth(newWidth);
            ImagePreview.setMaxHeight(newHeight);
            //ImagePreview.setScaleType(ImageView.ScaleType.FIT_XY);
            //ImagePreview.setAdjustViewBounds(true);
            ImagePreview.setPadding(5, 5, 5, 5);
            ImagePreview.setImageBitmap(preview);
            getActivity().runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setView(ImagePreview);
                builder.setTitle(R.string.send_image);
                builder.setPositiveButton(R.string.ok, (dialog, which) -> sendImage(conversation_preview, uri_preview));
                builder.setOnCancelListener(dialog -> mPendingImageUris.clear());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    builder.setOnDismissListener(dialog -> mPendingImageUris.clear());
                }
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            });
        } else {
            getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), getText(R.string.error_file_not_found), Toast.LENGTH_LONG).show());
        }
    }

    private void sendImage(Conversation conversation, Uri uri) {
        final Toast prepareFileToast = Toast.makeText(getActivity(), getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareFileToast.show();
        activity.delegateUriPermissionsToService(uri);
        activity.xmppConnectionService.attachImageToConversation(conversation, uri,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi, Message object) {
                        hidePrepareFileToast(prepareFileToast);
                    }

                    @Override
                    public void success(Message message) {
                        hidePrepareFileToast(prepareFileToast);
                    }

                    @Override
                    public void error(final int error, Message message) {
                        hidePrepareFileToast(prepareFileToast);
                        activity.runOnUiThread(() -> activity.replaceToast(getString(error)));
                    }
                });
    }

    private void hidePrepareFileToast(final Toast prepareFileToast) {
        if (prepareFileToast != null && activity != null) {
            activity.runOnUiThread(prepareFileToast::cancel);
        }
    }

    private void sendMessage() {
        final String body = binding.textinput.getText().toString();
        final Conversation conversation = this.conversation;
        if (body.length() == 0 || conversation == null) {
            return;
        }
        final Message message;
        if (conversation.getCorrectingMessage() == null) {
            message = new Message(conversation, body, conversation.getNextEncryption());
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                final Jid nextCounterpart = conversation.getNextCounterpart();
                if (nextCounterpart != null) {
                    message.setCounterpart(nextCounterpart);
                    message.setTrueCounterpart(conversation.getMucOptions().getTrueCounterpart(nextCounterpart));
                    message.setType(Message.TYPE_PRIVATE);
                }
            }
        } else {
            message = conversation.getCorrectingMessage();
            message.setBody(body);
            message.setEdited(message.getUuid());
            message.setUuid(UUID.randomUUID().toString());
        }
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_AXOLOTL:
                if (!trustKeysIfNeeded(REQUEST_TRUST_KEYS_TEXT)) {
                    sendMessage(message);
                }
                break;
            default:
                sendMessage(message);
        }
    }

    protected boolean trustKeysIfNeeded(int requestCode) {
        return trustKeysIfNeeded(requestCode, ATTACHMENT_CHOICE_INVALID);
    }

    protected boolean trustKeysIfNeeded(int requestCode, int attachmentChoice) {
        AxolotlService axolotlService = conversation.getAccount().getAxolotlService();
        final List<Jid> targets = axolotlService.getCryptoTargets(conversation);
        boolean hasUnaccepted = !conversation.getAcceptedCryptoTargets().containsAll(targets);
        boolean hasUndecidedOwn = !axolotlService.getKeysWithTrust(FingerprintStatus.createActiveUndecided()).isEmpty();
        boolean hasUndecidedContacts = !axolotlService.getKeysWithTrust(FingerprintStatus.createActiveUndecided(), targets).isEmpty();
        boolean hasPendingKeys = !axolotlService.findDevicesWithoutSession(conversation).isEmpty();
        boolean hasNoTrustedKeys = axolotlService.anyTargetHasNoTrustedKeys(targets);
        if (hasUndecidedOwn || hasUndecidedContacts || hasPendingKeys || hasNoTrustedKeys || hasUnaccepted) {
            axolotlService.createSessionsIfNeeded(conversation);
            Intent intent = new Intent(getActivity(), TrustKeysActivity.class);
            String[] contacts = new String[targets.size()];
            for (int i = 0; i < contacts.length; ++i) {
                contacts[i] = targets.get(i).toString();
            }
            intent.putExtra("contacts", contacts);
            intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
            intent.putExtra("choice", attachmentChoice);
            intent.putExtra("conversation", conversation.getUuid());
            startActivityForResult(intent, requestCode);
            return true;
        } else {
            return false;
        }
    }

    public void updateChatMsgHint() {
        final boolean multi = conversation.getMode() == Conversation.MODE_MULTI;
        if (conversation.getCorrectingMessage() != null) {
            this.binding.textinput.setHint(R.string.send_corrected_message);
            hideMessageHint();
        } else if (multi && conversation.getNextCounterpart() != null) {
            this.binding.textinput.setHint(getString(
                    R.string.send_private_message_to,
                    conversation.getNextCounterpart().getResource()));
            showMessageHint(getString(
                    R.string.send_private_message_to,
                    conversation.getNextCounterpart().getResource()));
        } else if (multi && !conversation.getMucOptions().participating()) {
            this.binding.textinput.setHint(R.string.you_are_not_participating);
            hideMessageHint();
        } else {
            this.binding.textinput.setHint(UIHelper.getMessageHint(getActivity(), conversation));
            hideMessageHint();
            getActivity().invalidateOptionsMenu();
        }
    }

    public void setupIme() {
        this.binding.textinput.refreshIme();
    }

    private void handleActivityResult(ActivityResult activityResult) {
        if (activityResult.resultCode == Activity.RESULT_OK) {
            handlePositiveActivityResult(activityResult.requestCode, activityResult.data);
        } else {
            handleNegativeActivityResult(activityResult.requestCode);
        }
    }

    private void handlePositiveActivityResult(int requestCode, final Intent data) {
        final String type = data == null ? null : data.getType();
        switch (requestCode) {
            case REQUEST_TRUST_KEYS_TEXT:
                final String body = binding.textinput.getText().toString();
                Message message = new Message(conversation, body, conversation.getNextEncryption());
                sendMessage(message);
                break;
            case REQUEST_TRUST_KEYS_MENU:
                int choice = data.getIntExtra("choice", ATTACHMENT_CHOICE_INVALID);
                selectPresenceToAttachFile(choice);
                break;
            case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                final List<Uri> imageUris = AttachmentTool.extractUriFromIntent(data);
                final int ImageUrisCount = imageUris.size();
                Log.d(Config.LOGTAG, "ConversationsActivity.onActivityResult() - attaching image - number of uris: " + ImageUrisCount);
                if (ImageUrisCount == 1) {
                    Uri uri = imageUris.get(0);
                    Log.d(Config.LOGTAG, "ConversationsActivity.onActivityResult() - attaching image to conversations. CHOOSE_IMAGE");
                    attachImageToConversation(conversation, uri, false);
                } else {
                    for (Iterator<Uri> i = imageUris.iterator(); i.hasNext(); i.remove()) {
                        Log.d(Config.LOGTAG, "ConversationsActivity.onActivityResult() - attaching images to conversations. CHOOSE_IMAGES");
                        attachImagesToConversation(conversation, i.next());
                    }
                }
                break;
            case ATTACHMENT_CHOICE_TAKE_FROM_CAMERA:
                final Uri takePhotoUri = pendingTakePhotoUri.pop();
                final Uri takeVideoUri = pendingTakeVideoUri.pop();
                if (takePhotoUri != null) {
                    attachPhotoToConversation(conversation, takePhotoUri);
                } else if (takeVideoUri != null) {
                    attachFileToConversation(conversation, takeVideoUri, type);
                } else {
                    Log.d(Config.LOGTAG, "lost take uri. unable to to attach");
                }
                break;
            case ATTACHMENT_CHOICE_CHOOSE_FILE:
            case ATTACHMENT_CHOICE_RECORD_VOICE:
                final List<Uri> fileUris = AttachmentTool.extractUriFromIntent(data);
                final PresenceSelector.OnPresenceSelected callback = () -> {
                    for (Iterator<Uri> i = fileUris.iterator(); i.hasNext(); i.remove()) {
                        Log.d(Config.LOGTAG, "ConversationsActivity.onActivityResult() - attaching file to conversations. CHOOSE_FILE/RECORD_VOICE");
                        attachFileToConversation(conversation, i.next(), type);
                    }
                };
                if (conversation == null || conversation.getMode() == Conversation.MODE_MULTI || FileBackend.allFilesUnderSize(getActivity(), fileUris, getMaxHttpUploadSize(conversation))) {
                    callback.onPresenceSelected();
                } else {
                    activity.selectPresence(conversation, callback);
                }
                break;
            case ATTACHMENT_CHOICE_LOCATION:
                double latitude = data.getDoubleExtra("latitude", 0);
                double longitude = data.getDoubleExtra("longitude", 0);
                Uri geo = Uri.parse("geo:" + String.valueOf(latitude) + "," + String.valueOf(longitude));
                attachLocationToConversation(conversation, geo);
                break;
            case REQUEST_INVITE_TO_CONVERSATION:
                XmppActivity.ConferenceInvite invite = XmppActivity.ConferenceInvite.parse(data);
                if (invite != null) {
                    if (invite.execute(activity)) {
                        activity.mToast = Toast.makeText(activity, R.string.creating_conference, Toast.LENGTH_LONG);
                        activity.mToast.show();
                    }
                }
                break;
        }
    }

    private void handleNegativeActivityResult(int requestCode) {
        switch (requestCode) {
            //nothing to do for now
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ActivityResult activityResult = ActivityResult.of(requestCode, resultCode, data);
        if (activity != null && activity.xmppConnectionService != null) {
            handleActivityResult(activityResult);
        } else {
            this.postponedActivityResult.push(activityResult);
        }
    }

    public void unblockConversation(final Blockable conversation) {
        activity.xmppConnectionService.sendUnblockRequest(conversation);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(Config.LOGTAG, "ConversationFragment.onAttach()");
        if (activity instanceof ConversationsActivity) {
            this.activity = (ConversationsActivity) activity;
        } else {
            throw new IllegalStateException("Trying to attach fragment to activity that is not the ConversationsActivity");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.activity = null; //TODO maybe not a good idea since some callbacks really need it
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.fragment_conversation, menu);
        final MenuItem menuInviteContact = menu.findItem(R.id.action_invite);
        final MenuItem menuNeedHelp = menu.findItem(R.id.action_create_issue);
        final MenuItem menuSearchUpdates = menu.findItem(R.id.action_check_updates);
        final MenuItem menuArchiveChat = menu.findItem(R.id.action_archive_chat);
        final MenuItem menuEditProfiles = menu.findItem(R.id.action_accounts);

        if (conversation != null) {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                menuInviteContact.setVisible(true);
                menuArchiveChat.setTitle(R.string.action_end_conversation_muc);
            } else {
                menuInviteContact.setVisible(false);
                menuArchiveChat.setTitle(R.string.action_end_conversation);
            }
            menuEditProfiles.setVisible(false);
            menuNeedHelp.setVisible(true);
            menuSearchUpdates.setVisible(false);
            ConversationMenuConfigurator.configureAttachmentMenu(conversation, menu);
            ConversationMenuConfigurator.configureEncryptionMenu(conversation, menu);
        } else {
            menuNeedHelp.setVisible(false);
            menuSearchUpdates.setVisible(true);
        }
        super.onCreateOptionsMenu(menu, menuInflater);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment_conversation, container, false);
        binding.getRoot().setOnClickListener(null); //TODO why the fuck did we do this?

        binding.textinput.addTextChangedListener(new StylingHelper.MessageEditorStyler(binding.textinput));
        binding.textinput.setOnEditorActionListener(mEditorActionListener);
        binding.textinput.setRichContentListener(new String[]{"image/*"}, mEditorContentListener);
        binding.textinput.setBackgroundResource(activity.isDarkTheme() ? R.drawable.message_bubble_sent_blue_dark : R.drawable.message_bubble_sent_blue);

        binding.textSendButton.setOnClickListener(this.mSendButtonListener);
        binding.textSendButton.setOnLongClickListener(this.mSendButtonLongListener);
        binding.scrollToBottomButton.setOnClickListener(this.mScrollButtonListener);

        binding.messagesView.setOnScrollListener(mOnScrollListener);
        binding.messagesView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        messageListAdapter = new MessageAdapter((XmppActivity) getActivity(), this.messageList);
        messageListAdapter.setOnContactPictureClicked(message -> {
            String fingerprint;
            if (message.getEncryption() == Message.ENCRYPTION_PGP || message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
                fingerprint = "pgp";
            } else {
                fingerprint = message.getFingerprint();
            }
            final boolean received = message.getStatus() <= Message.STATUS_RECEIVED;
            if (received) {
                if (message.getConversation() instanceof Conversation && message.getConversation().getMode() == Conversation.MODE_MULTI) {
                    Jid user = message.getCounterpart();
                    if (user != null && !user.isBareJid()) {
                        final MucOptions mucOptions = ((Conversation) message.getConversation()).getMucOptions();
                        if (mucOptions.participating() || ((Conversation) message.getConversation()).getNextCounterpart() != null) {
                            if (!mucOptions.isUserInRoom(user)) {
                                Toast.makeText(getActivity(), activity.getString(R.string.user_has_left_conference, user.getResource()), Toast.LENGTH_SHORT).show();
                            }
                            highlightInConference(user.getResource());
                        } else {
                            Toast.makeText(getActivity(), R.string.you_are_not_participating, Toast.LENGTH_SHORT).show();
                        }
                    }
                    return;
                } else {
                    if (!message.getContact().isSelf()) {
                        activity.switchToContactDetails(message.getContact(), fingerprint);
                        return;
                    }
                }
            }
            activity.switchToAccount(message.getConversation().getAccount(), fingerprint);
        });
        messageListAdapter.setOnContactPictureLongClicked(message -> {
            if (message.getStatus() <= Message.STATUS_RECEIVED) {
                if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
                    final MucOptions mucOptions = conversation.getMucOptions();
                    if (!mucOptions.allowPm()) {
                        Toast.makeText(getActivity(), R.string.private_messages_are_disabled, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Jid user = message.getCounterpart();
                    if (user != null && !user.isBareJid()) {
                        if (mucOptions.isUserInRoom(user)) {
                            privateMessageWith(user);
                        } else {
                            Toast.makeText(getActivity(), activity.getString(R.string.user_has_left_conference, user.getResource()), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            } else {
                activity.showQrCode(conversation.getAccount().getShareableUri());
            }
        });
        messageListAdapter.setOnQuoteListener(this::quoteText);
        binding.messagesView.setAdapter(messageListAdapter);

        registerForContextMenu(binding.messagesView);
        return binding.getRoot();
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int animator = enter ? R.animator.fade_right_in : R.animator.fade_right_out;
            return AnimatorInflater.loadAnimator(getActivity(), animator);
        } else {
            return null;
        }
    }

    private void quoteText(String text) {
        if (binding.textinput.isEnabled()) {
            text = text.replaceAll("(\n *){2,}", "\n").replaceAll("(^|\n)", "$1> ").replaceAll("\n$", "");
            Editable editable = binding.textinput.getEditableText();
            int position = binding.textinput.getSelectionEnd();
            if (position == -1) position = editable.length();
            if (position > 0 && editable.charAt(position - 1) != '\n') {
                editable.insert(position++, "\n");
            }
            editable.insert(position, text);
            position += text.length();
            editable.insert(position++, "\n");
            if (position < editable.length() && editable.charAt(position) != '\n') {
                editable.insert(position, "\n");
            }
            binding.textinput.setSelection(position);
            binding.textinput.requestFocus();
            InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(binding.textinput, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void quoteMessage(Message message) {
        quoteText(MessageUtils.prepareQuote(message));
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        synchronized (this.messageList) {
            super.onCreateContextMenu(menu, v, menuInfo);
            AdapterView.AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) menuInfo;
            this.selectedMessage = this.messageList.get(acmi.position);
            populateContextMenu(menu);
        }
    }

    private void populateContextMenu(ContextMenu menu) {
        final Message m = this.selectedMessage;
        final Transferable t = m.getTransferable();
        Message relevantForCorrection = m;
        while (relevantForCorrection.mergeable(relevantForCorrection.next())) {
            relevantForCorrection = relevantForCorrection.next();
        }
        if (m.getType() != Message.TYPE_STATUS) {

            if (m.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE) {
                return;
            }

            final boolean deleted = t != null && t instanceof TransferablePlaceholder;
            final boolean encrypted = m.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED
                    || m.getEncryption() == Message.ENCRYPTION_PGP;
            final boolean receiving = m.getStatus() == Message.STATUS_RECEIVED && (t instanceof JingleConnection || t instanceof HttpDownloadConnection);
            activity.getMenuInflater().inflate(R.menu.message_context, menu);
            menu.setHeaderTitle(R.string.message_options);
            MenuItem copyMessage = menu.findItem(R.id.copy_message);
            MenuItem copyLink = menu.findItem(R.id.copy_link);
            MenuItem quoteMessage = menu.findItem(R.id.quote_message);
            MenuItem retryDecryption = menu.findItem(R.id.retry_decryption);
            MenuItem correctMessage = menu.findItem(R.id.correct_message);
            MenuItem deleteMessage = menu.findItem(R.id.delete_message);
            MenuItem shareWith = menu.findItem(R.id.share_with);
            MenuItem sendAgain = menu.findItem(R.id.send_again);
            MenuItem copyUrl = menu.findItem(R.id.copy_url);
            MenuItem cancelTransmission = menu.findItem(R.id.cancel_transmission);
            MenuItem downloadFile = menu.findItem(R.id.download_file);
            MenuItem deleteFile = menu.findItem(R.id.delete_file);
            MenuItem showErrorMessage = menu.findItem(R.id.show_error_message);
            if (!m.isFileOrImage() && !encrypted && !m.isGeoUri() && !m.treatAsDownloadable()) {
                copyMessage.setVisible(true);
                quoteMessage.setVisible(MessageUtils.prepareQuote(m).length() > 0);
                deleteMessage.setVisible(true);
                String body = m.getMergedBody().toString();
                if (ShareUtil.containsXmppUri(body)) {
                    copyLink.setTitle(R.string.copy_jabber_id);
                    copyLink.setVisible(true);
                } else if (Patterns.AUTOLINK_WEB_URL.matcher(body).find()) {
                    copyLink.setVisible(true);
                }
            }
            if (m.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
                retryDecryption.setVisible(true);
            }
            if (relevantForCorrection.getType() == Message.TYPE_TEXT
                    && relevantForCorrection.isLastCorrectableMessage()
                    && m.getConversation() instanceof Conversation
                    && (((Conversation) m.getConversation()).getMucOptions().nonanonymous() || m.getConversation().getMode() == Conversation.MODE_SINGLE)) {
                correctMessage.setVisible(true);
            }
            if ((m.isFileOrImage() && !deleted && !receiving) || (m.getType() == Message.TYPE_TEXT && !m.treatAsDownloadable())) {
                shareWith.setVisible(true);

            }
            if (m.getStatus() == Message.STATUS_SEND_FAILED) {
                sendAgain.setVisible(true);
            }
            if (m.hasFileOnRemoteHost()
                    || m.isGeoUri()
                    || m.isXmppUri()
                    || m.treatAsDownloadable()
                    || (t != null && t instanceof HttpDownloadConnection)) {
                copyUrl.setVisible(true);
            }
            if (m.isFileOrImage() && deleted && m.hasFileOnRemoteHost()) {
                downloadFile.setVisible(true);
                downloadFile.setTitle(activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, m)));
            }
            boolean waitingOfferedSending = m.getStatus() == Message.STATUS_WAITING
                    || m.getStatus() == Message.STATUS_UNSEND
                    || m.getStatus() == Message.STATUS_OFFERED;
            if ((t != null && !deleted) || waitingOfferedSending && m.needsUploading()) {
                cancelTransmission.setVisible(true);
            }
            if (m.isFileOrImage() && !deleted) {
                String path = m.getRelativeFilePath();
                Log.d(Config.LOGTAG, "Path = " + path);
                if (path == null || !path.startsWith("/") || path.contains(FileBackend.getConversationsDirectory("null", false))) {
                    deleteFile.setVisible(true);
                    deleteFile.setTitle(activity.getString(R.string.delete_x_file, UIHelper.getFileDescriptionString(activity, m)));
                }
            }
            if (m.getStatus() == Message.STATUS_SEND_FAILED && m.getErrorMessage() != null) {
                showErrorMessage.setVisible(true);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.share_with:
                ShareUtil.share(activity, selectedMessage);
                return true;
            case R.id.correct_message:
                correctMessage(selectedMessage);
                return true;
            case R.id.copy_message:
                ShareUtil.copyToClipboard(activity, selectedMessage);
                return true;
            case R.id.copy_link:
                ShareUtil.copyLinkToClipboard(activity, selectedMessage);
                return true;
            case R.id.quote_message:
                quoteMessage(selectedMessage);
                return true;
            case R.id.send_again:
                resendMessage(selectedMessage);
                return true;
            case R.id.copy_url:
                ShareUtil.copyUrlToClipboard(activity, selectedMessage);
                return true;
            case R.id.download_file:
                startDownloadable(selectedMessage);
                return true;
            case R.id.cancel_transmission:
                cancelTransmission(selectedMessage);
                return true;
            case R.id.retry_decryption:
                retryDecryption(selectedMessage);
                return true;
            case R.id.delete_message:
                deleteMessage(selectedMessage);
                return true;
            case R.id.delete_file:
                deleteFile(selectedMessage);
                return true;
            case R.id.show_error_message:
                showErrorMessage(selectedMessage);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        } else if (conversation == null) {
            return super.onOptionsItemSelected(item);
        }
        switch (item.getItemId()) {
            case R.id.encryption_choice_axolotl:
            case R.id.encryption_choice_otr:
            case R.id.encryption_choice_pgp:
            case R.id.encryption_choice_none:
                handleEncryptionSelection(item);
                break;
            case R.id.attach_choose_picture:
            case R.id.attach_take_picture:
            case R.id.attach_choose_file:
            case R.id.attach_record_voice:
            case R.id.attach_location:
                handleAttachmentSelection(item);
                break;
            case R.id.action_archive_chat:
                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    activity.xmppConnectionService.archiveConversation(conversation);
                } else {
                    activity.runOnUiThread(() -> {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setTitle(getString(R.string.action_end_conversation_muc));
                        builder.setMessage(getString(R.string.leave_conference_warning));
                        builder.setNegativeButton(getString(R.string.cancel), null);
                        builder.setPositiveButton(getString(R.string.action_end_conversation_muc),
                                (dialog, which) -> {
                                    activity.xmppConnectionService.archiveConversation(conversation);
                                });
                        builder.create().show();
                    });
                }
                break;
            case R.id.action_invite:
                startActivityForResult(ChooseContactActivity.create(activity, conversation), REQUEST_INVITE_TO_CONVERSATION);
                break;
            case R.id.action_clear_history:
                clearHistoryDialog(conversation);
                break;
            case R.id.action_block:
            case R.id.action_unblock:
                final Activity activity = getActivity();
                if (activity instanceof XmppActivity) {
                    BlockContactDialog.show((XmppActivity) activity, conversation);
                }
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleAttachmentSelection(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.attach_choose_picture:
                attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
                break;
            case R.id.attach_take_picture:
                attachFile(ATTACHMENT_CHOICE_TAKE_FROM_CAMERA);
                break;
            case R.id.attach_choose_file:
                attachFile(ATTACHMENT_CHOICE_CHOOSE_FILE);
                break;
            case R.id.attach_record_voice:
                attachFile(ATTACHMENT_CHOICE_RECORD_VOICE);
                break;
            case R.id.attach_location:
                attachFile(ATTACHMENT_CHOICE_LOCATION);
                break;
        }
    }

    private void handleEncryptionSelection(MenuItem item) {
        if (conversation == null) {
            return;
        }
        switch (item.getItemId()) {
            case R.id.encryption_choice_none:
                conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                item.setChecked(true);
                break;
            case R.id.encryption_choice_otr:
                conversation.setNextEncryption(Message.ENCRYPTION_OTR);
                item.setChecked(true);
                break;
            case R.id.encryption_choice_pgp:
                if (activity.hasPgp()) {
                    if (conversation.getAccount().getPgpSignature() != null) {
                        conversation.setNextEncryption(Message.ENCRYPTION_PGP);
                        item.setChecked(true);
                    } else {
                        activity.announcePgp(conversation.getAccount(), conversation, null, activity.onOpenPGPKeyPublished);
                    }
                } else {
                    activity.showInstallPgpDialog();
                }
                break;
            case R.id.encryption_choice_axolotl:
                Log.d(Config.LOGTAG, AxolotlService.getLogprefix(conversation.getAccount())
                        + "Enabled axolotl for Contact " + conversation.getContact().getJid());
                conversation.setNextEncryption(Message.ENCRYPTION_AXOLOTL);
                item.setChecked(true);
                break;
            default:
                conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                break;
        }
        activity.xmppConnectionService.updateConversation(conversation);
        updateChatMsgHint();
        getActivity().invalidateOptionsMenu();
        activity.refreshUi();
    }

    public void attachFile(final int attachmentChoice) {
        if (attachmentChoice == ATTACHMENT_CHOICE_RECORD_VOICE) {
            if (!hasPermissions(attachmentChoice, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO)) {
                return;
            }
        } else if (attachmentChoice == ATTACHMENT_CHOICE_TAKE_FROM_CAMERA) {
            if (!hasPermissions(attachmentChoice, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)) {
                return;
            }
        } else if (attachmentChoice == ATTACHMENT_CHOICE_LOCATION) {
            if (!hasPermissions(attachmentChoice, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                return;
            }
        } else if (attachmentChoice != ATTACHMENT_CHOICE_LOCATION) {
            if (!hasPermissions(attachmentChoice, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                return;
            }
        }
        try {
            activity.getPreferences().edit()
                    .putString(RECENTLY_USED_QUICK_ACTION, SendButtonAction.of(attachmentChoice).toString())
                    .apply();
        } catch (IllegalArgumentException e) {
            //just do not save
        }
        final int encryption = conversation.getNextEncryption();
        final int mode = conversation.getMode();
        if (encryption == Message.ENCRYPTION_PGP) {
            if (activity.hasPgp()) {
                if (mode == Conversation.MODE_SINGLE && conversation.getContact().getPgpKeyId() != 0) {
                    activity.xmppConnectionService.getPgpEngine().hasKey(
                            conversation.getContact(),
                            new UiCallback<Contact>() {

                                @Override
                                public void userInputRequried(PendingIntent pi, Contact contact) {
                                    startPendingIntent(pi, attachmentChoice);
                                }

                                @Override
                                public void success(Contact contact) {
                                    selectPresenceToAttachFile(attachmentChoice);
                                }

                                @Override
                                public void error(int error, Contact contact) {
                                    activity.replaceToast(getString(error));
                                }
                            });
                } else if (mode == Conversation.MODE_MULTI && conversation.getMucOptions().pgpKeysInUse()) {
                    if (!conversation.getMucOptions().everybodyHasKeys()) {
                        getActivity().runOnUiThread(() -> {
                            Toast warning = Toast.makeText(activity, R.string.missing_public_keys, Toast.LENGTH_LONG);
                            warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                            warning.show();
                        });
                    }
                    selectPresenceToAttachFile(attachmentChoice);
                } else {
                    showNoPGPKeyDialog(false, (dialog, which) -> {
                        conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                        activity.xmppConnectionService.updateConversation(conversation);
                        selectPresenceToAttachFile(attachmentChoice);
                    });
                }
            } else {
                activity.showInstallPgpDialog();
            }
        } else {
            if (encryption != Message.ENCRYPTION_AXOLOTL || !trustKeysIfNeeded(REQUEST_TRUST_KEYS_MENU, attachmentChoice)) {
                selectPresenceToAttachFile(attachmentChoice);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (grantResults.length > 0) {
            if (allGranted(grantResults)) {
                if (requestCode == REQUEST_START_DOWNLOAD) {
                    if (this.mPendingDownloadableMessage != null) {
                        startDownloadable(this.mPendingDownloadableMessage);
                    }
                } else if (requestCode == REQUEST_ADD_EDITOR_CONTENT) {
                    if (this.mPendingEditorContent != null) {
                        attachImageToConversation(this.mPendingEditorContent);
                    }
                } else {
                    attachFile(requestCode);
                }
            } else {
                @StringRes int res;
                String firstDenied = getFirstDenied(grantResults, permissions);
                if (Manifest.permission.RECORD_AUDIO.equals(firstDenied)) {
                    res = R.string.no_microphone_permission;
                } else if (Manifest.permission.CAMERA.equals(firstDenied)) {
                    res = R.string.no_camera_permission;
                } else {
                    res = R.string.no_storage_permission;
                }
                Toast.makeText(getActivity(), res, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void startDownloadable(Message message) {
        if (!hasPermissions(REQUEST_START_DOWNLOAD, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            this.mPendingDownloadableMessage = message;
            return;
        }
        Transferable transferable = message.getTransferable();
        if (transferable != null) {
            if (transferable instanceof TransferablePlaceholder && message.hasFileOnRemoteHost()) {
                createNewConnection(message);
                return;
            }
            if (!transferable.start()) {
                Log.d(Config.LOGTAG, "type: " + transferable.getClass().getName());
                Toast.makeText(getActivity(), R.string.not_connected_try_again, Toast.LENGTH_SHORT).show();
            }
        } else if (message.treatAsDownloadable()) {
            createNewConnection(message);
        }
    }

    private void createNewConnection(final Message message) {
        if (!activity.xmppConnectionService.getHttpConnectionManager().checkConnection(message)) {
            Toast.makeText(getActivity(), R.string.not_connected_try_again, Toast.LENGTH_SHORT).show();
            return;
        }
        activity.xmppConnectionService.getHttpConnectionManager().createNewDownloadConnection(message, true);
    }

    @SuppressLint("InflateParams")
    protected void clearHistoryDialog(final Conversation conversation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(R.string.clear_conversation_history));
        final View dialogView = getActivity().getLayoutInflater().inflate(R.layout.dialog_clear_history, null);
        final CheckBox endConversationCheckBox = dialogView.findViewById(R.id.end_conversation_checkbox);
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            endConversationCheckBox.setVisibility(View.VISIBLE);
            endConversationCheckBox.setChecked(true);
        }
        builder.setView(dialogView);
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.delete_messages), (dialog, which) -> {
            this.activity.xmppConnectionService.clearConversationHistory(conversation);
            if (endConversationCheckBox.isChecked()) {
                this.activity.xmppConnectionService.archiveConversation(conversation);
            } else {
                activity.onConversationsListItemUpdated();
                refresh();
            }
        });
        builder.create().show();
    }

    private boolean hasPermissions(int requestCode, String... permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final List<String> missingPermissions = new ArrayList<>();
            for (String permission : permissions) {
                if (Config.ONLY_INTERNAL_STORAGE && permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    continue;
                }
                if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission);
                }
            }
            if (missingPermissions.size() == 0) {
                return true;
            } else {
                requestPermissions(missingPermissions.toArray(new String[missingPermissions.size()]), requestCode);
                return false;
            }
        } else {
            return true;
        }
    }

    protected void selectPresenceToAttachFile(final int attachmentChoice) {
        final int encryption = conversation.getNextEncryption();
        final Account account = conversation.getAccount();
        final PresenceSelector.OnPresenceSelected callback = () -> {
            final Intent intent = new Intent();
            boolean chooser = false;
            switch (attachmentChoice) {
                case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                    intent.setType("image/*");
                    chooser = true;
                    break;
                case ATTACHMENT_CHOICE_CHOOSE_VIDEO:
                    chooser = true;
                    intent.setType("video/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    break;
                case ATTACHMENT_CHOICE_TAKE_FROM_CAMERA:
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle(getString(R.string.attach_take_from_camera));
                    builder.setNegativeButton(getString(R.string.action_take_photo),
                            (dialog, which) -> {
                                final Uri uri = activity.xmppConnectionService.getFileBackend().getTakePhotoUri();
                                pendingTakePhotoUri.push(uri);
                                intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                                startActivityForResult(intent, attachmentChoice);
                                activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                            });
                    builder.setPositiveButton(getString(R.string.action_take_video),
                            (dialog, which) -> {
                                final Uri uri = activity.xmppConnectionService.getFileBackend().getTakeVideoUri();
                                pendingTakeVideoUri.push(uri);
                                intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.setAction(MediaStore.ACTION_VIDEO_CAPTURE);
                                startActivityForResult(intent, attachmentChoice);
                                activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                            });
                    builder.create().show();
                    break;
                case ATTACHMENT_CHOICE_CHOOSE_FILE:
                    chooser = true;
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    break;
                case ATTACHMENT_CHOICE_RECORD_VOICE:
                    startActivityForResult(new Intent(getActivity(), RecordingActivity.class), attachmentChoice);
                    activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                    break;
                case ATTACHMENT_CHOICE_LOCATION:
                    startActivityForResult(new Intent(getActivity(), ShareLocationActivity.class), attachmentChoice);
                    activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                    break;
            }
            if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                Log.d(Config.LOGTAG, "Attachment: " + attachmentChoice);
                if (chooser) {
                    startActivityForResult(
                            Intent.createChooser(intent, getString(R.string.perform_action_with)),
                            attachmentChoice);
                } else {
                    startActivityForResult(intent, attachmentChoice);
                }
            }
        };
        if (account.httpUploadAvailable() || attachmentChoice == ATTACHMENT_CHOICE_LOCATION) {
        if ((account.httpUploadAvailable() || attachmentChoice == ATTACHMENT_CHOICE_LOCATION) && encryption != Message.ENCRYPTION_OTR) {
                conversation.setNextCounterpart(null);
                callback.onPresenceSelected();
            } else {
                activity.selectPresence(conversation, callback);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.messagesView.post(this::fireReadEvent);
    }

    private void fireReadEvent() {
        if (activity != null && this.conversation != null) {
            String uuid = getLastVisibleMessageUuid();
            if (uuid != null) {
                activity.onConversationRead(this.conversation, uuid);
            }
        }
    }

    private String getLastVisibleMessageUuid() {
        if (binding == null) {
            return null;
        }
        synchronized (this.messageList) {
            int pos = binding.messagesView.getLastVisiblePosition();
            if (pos >= 0) {
                Message message = null;
                for (int i = pos; i >= 0; --i) {
                    try {
                        message = (Message) binding.messagesView.getItemAtPosition(i);
                    } catch (IndexOutOfBoundsException e) {
                        //should not happen if we synchronize properly. however if that fails we just gonna try item -1
                        continue;
                    }
                    if (message.getType() != Message.TYPE_STATUS) {
                        break;
                    }
                }
                if (message != null) {
                    while (message.next() != null && message.next().wasMergedIntoPrevious()) {
                        message = message.next();
                    }
                    return message.getUuid();
                }
            }
        }
        return null;
    }

    private void showErrorMessage(final Message message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.error_message);
        builder.setMessage(message.getErrorMessage());
        builder.setPositiveButton(R.string.ok, null);
        builder.create().show();
    }

    private void deleteFile(Message message) {
        if (activity.xmppConnectionService.getFileBackend().deleteFile(message)) {
            message.setTransferable(new TransferablePlaceholder(Transferable.STATUS_DELETED));
            activity.onConversationsListItemUpdated();
            refresh();
        }
    }

    public void resendMessage(final Message message) {
        if (message != null && message.isFileOrImage()) {
            if (!(message.getConversation() instanceof Conversation)) {
                return;
            }
            final Conversation conversation = (Conversation) message.getConversation();
            DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
            if (file.exists()) {
                final XmppConnection xmppConnection = conversation.getAccount().getXmppConnection();
                if (!message.hasFileOnRemoteHost()
                        && xmppConnection != null
                        && !xmppConnection.getFeatures().httpUpload(message.getFileParams().size)) {
                    activity.selectPresence(conversation, () -> {
                        message.setCounterpart(conversation.getNextCounterpart());
                        activity.xmppConnectionService.resendFailedMessages(message);
                        new Handler().post(() -> {
                            int size = messageList.size();
                            this.binding.messagesView.setSelection(size - 1);
                        });
                    });
                    return;
                }
            } else {
                Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
                message.setTransferable(new TransferablePlaceholder(Transferable.STATUS_DELETED));
                activity.onConversationsListItemUpdated();
                refresh();
                return;
            }
        }
        activity.xmppConnectionService.resendFailedMessages(message);
        new Handler().post(() -> {
            int size = messageList.size();
            this.binding.messagesView.setSelection(size - 1);
        });
    }

    private void copyUrl(Message message) {
        final String url;
        final int resId;
        if (message.isGeoUri()) {
            resId = R.string.location;
            url = message.getBody();
        } else if (message.isXmppUri()) {
            resId = R.string.contact;
            url = message.getBody();
        } else if (message.hasFileOnRemoteHost()) {
            resId = R.string.file_url;
            url = message.getFileParams().url.toString();
        } else {
            url = message.getBody().trim();
            resId = R.string.file_url;
        }
        if (activity.copyTextToClipboard(url, resId)) {
            Toast.makeText(getActivity(), R.string.url_copied_to_clipboard,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelTransmission(Message message) {
        Transferable transferable = message.getTransferable();
        if (transferable != null) {
            transferable.cancel();
        } else if (message.getStatus() != Message.STATUS_RECEIVED) {
            activity.xmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
        }
    }

    private void retryDecryption(Message message) {
        message.setEncryption(Message.ENCRYPTION_PGP);
        activity.onConversationsListItemUpdated();
        refresh();
        conversation.getAccount().getPgpDecryptionService().decrypt(message, false);
    }

    private void privateMessageWith(final Jid counterpart) {
        if (conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
            activity.xmppConnectionService.sendChatState(conversation);
        }
        this.binding.textinput.setText("");
        this.conversation.setNextCounterpart(counterpart);
        updateChatMsgHint();
        updateSendButton();
        updateEditablity();
    }

    private void correctMessage(Message message) {
        while (message.mergeable(message.next())) {
            message = message.next();
        }
        this.conversation.setCorrectingMessage(message);
        final Editable editable = binding.textinput.getText();
        this.conversation.setDraftMessage(editable.toString());
        this.binding.textinput.setText("");
        this.binding.textinput.append(message.getBody());

    }

    private void highlightInConference(String nick) {
        final Editable editable = this.binding.textinput.getText();
        String oldString = editable.toString().trim();
        final int pos = this.binding.textinput.getSelectionStart();
        if (oldString.isEmpty() || pos == 0) {
            editable.insert(0, nick + ": ");
        } else {
            final char before = editable.charAt(pos - 1);
            final char after = editable.length() > pos ? editable.charAt(pos) : '\0';
            if (before == '\n') {
                editable.insert(pos, nick + ": ");
            } else {
                if (pos > 2 && editable.subSequence(pos - 2, pos).toString().equals(": ")) {
                    if (NickValidityChecker.check(conversation, Arrays.asList(editable.subSequence(0, pos - 2).toString().split(", ")))) {
                        editable.insert(pos - 2, ", " + nick);
                        return;
                    }
                }
                editable.insert(pos, (Character.isWhitespace(before) ? "" : " ") + nick + (Character.isWhitespace(after) ? "" : " "));
                if (Character.isWhitespace(after)) {
                    this.binding.textinput.setSelection(this.binding.textinput.getSelectionStart() + 1);
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (conversation != null) {
            outState.putString(STATE_CONVERSATION_UUID, conversation.getUuid());
            outState.putString(STATE_LAST_MESSAGE_UUID, lastMessageUuid);
            final Uri PhotoUri = pendingTakePhotoUri.peek();
            final Uri VideoUri = pendingTakeVideoUri.peek();
            if (PhotoUri != null) {
                outState.putString(STATE_PHOTO_URI, PhotoUri.toString());
            }
            if (VideoUri != null) {
                outState.putString(STATE_VIDEO_URI, VideoUri.toString());
            }
            final ScrollState scrollState = getScrollPosition();
            if (scrollState != null) {
                outState.putParcelable(STATE_SCROLL_POSITION, scrollState);
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }
        String uuid = savedInstanceState.getString(STATE_CONVERSATION_UUID);
        pendingLastMessageUuid.push(savedInstanceState.getString(STATE_LAST_MESSAGE_UUID, null));
        if (uuid != null) {
            QuickLoader.set(uuid);
            this.pendingConversationsUuid.push(uuid);
            String takePhotoUri = savedInstanceState.getString(STATE_PHOTO_URI);
            if (takePhotoUri != null) {
                pendingTakePhotoUri.push(Uri.parse(takePhotoUri));
            }
            String takeVideoUri = savedInstanceState.getString(STATE_VIDEO_URI);
            if (takeVideoUri != null) {
                pendingTakeVideoUri.push(Uri.parse(takeVideoUri));
            }
            pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (this.reInitRequiredOnStart && this.conversation != null) {
            final Bundle extras = pendingExtras.pop();
            reInit(this.conversation, extras != null);
            if (extras != null) {
                processExtras(extras);
            }
        } else if (conversation == null && activity != null && activity.xmppConnectionService != null) {
            final String uuid = pendingConversationsUuid.pop();
            Log.d(Config.LOGTAG, "ConversationFragment.onStart() - activity was bound but no conversation loaded. uuid=" + uuid);
            if (uuid != null) {
                findAndReInitByUuidOrArchive(uuid);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        final Activity activity = getActivity();
        if (activity == null || !activity.isChangingConfigurations()) {
            hideSoftKeyboard(activity);
            messageListAdapter.stopAudioPlayer();
        }
        if (this.conversation != null) {
            final String msg = this.binding.textinput.getText().toString();
            if (this.conversation.getStatus() != Conversation.STATUS_ARCHIVED && this.conversation.setNextMessage(msg)) {
                this.activity.xmppConnectionService.updateConversation(this.conversation);
            }
            updateChatState(this.conversation, msg);
            this.activity.xmppConnectionService.getNotificationService().setOpenConversation(null);
        }
        this.reInitRequiredOnStart = true;
    }

    private void updateChatState(final Conversation conversation, final String msg) {
        ChatState state = msg.length() == 0 ? Config.DEFAULT_CHATSTATE : ChatState.PAUSED;
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(state)) {
            activity.xmppConnectionService.sendChatState(conversation);
        }
    }

    private void saveMessageDraftStopAudioPlayer() {
        final Conversation previousConversation = this.conversation;
        if (this.activity == null || this.binding == null || previousConversation == null) {
            return;
        }
        Log.d(Config.LOGTAG, "ConversationFragment.saveMessageDraftStopAudioPlayer()");
        final String msg = this.binding.textinput.getText().toString();
        if (previousConversation.setNextMessage(msg)) {
            activity.xmppConnectionService.updateConversation(previousConversation);
        }
        updateChatState(this.conversation, msg);
        messageListAdapter.stopAudioPlayer();
    }

    public void reInit(Conversation conversation, Bundle extras) {
        QuickLoader.set(conversation.getUuid());
        this.saveMessageDraftStopAudioPlayer();
        if (this.reInit(conversation, extras != null)) {
            if (extras != null) {
                processExtras(extras);
            }
            this.reInitRequiredOnStart = false;
        } else {
            this.reInitRequiredOnStart = true;
            pendingExtras.push(extras);
        }
        resetUnreadMessagesCount();
    }

    private void reInit(Conversation conversation) {
        reInit(conversation, false);
    }

    private boolean reInit(final Conversation conversation, final boolean hasExtras) {
        if (conversation == null) {
            return false;
        }
        this.conversation = conversation;
        //once we set the conversation all is good and it will automatically do the right thing in onStart()
        if (this.activity == null || this.binding == null) {
            return false;
        }

        if (!activity.xmppConnectionService.isConversationStillOpen(this.conversation)) {
            activity.onConversationArchived(this.conversation);
            return false;
        }

        stopScrolling();
        Log.d(Config.LOGTAG, "reInit(hasExtras=" + Boolean.toString(hasExtras) + ")");

        if (this.conversation.isRead() && hasExtras) {
            Log.d(Config.LOGTAG, "trimming conversation");
            this.conversation.trim();
        }

        setupIme();

        final boolean scrolledToBottomAndNoPending = this.scrolledToBottom() && pendingScrollState.peek() == null;

        this.binding.textSendButton.setContentDescription(activity.getString(R.string.send_message_to_x, conversation.getName()));
        this.binding.textinput.setKeyboardListener(null);
        this.binding.textinput.setText("");
        this.binding.textinput.append(this.conversation.getNextMessage());
        this.binding.textinput.setKeyboardListener(this);
        messageListAdapter.updatePreferences();
        refresh(false);
        this.conversation.messagesLoaded.set(true);

        Log.d(Config.LOGTAG, "scrolledToBottomAndNoPending=" + Boolean.toString(scrolledToBottomAndNoPending));

        if (hasExtras || scrolledToBottomAndNoPending) {
            resetUnreadMessagesCount();
            synchronized (this.messageList) {
                Log.d(Config.LOGTAG, "jump to first unread message");
                final Message first = conversation.getFirstUnreadMessage();
                final int bottom = Math.max(0, this.messageList.size() - 1);
                final int pos;
                final boolean jumpToBottom;
                if (first == null) {
                    pos = bottom;
                    jumpToBottom = true;
                } else {
                    int i = getIndexOf(first.getUuid(), this.messageList);
                    pos = i < 0 ? bottom : i;
                    jumpToBottom = false;
                }
                setSelection(pos, jumpToBottom);
            }
        }

        this.binding.messagesView.post(this::fireReadEvent);
        //TODO if we only do this when this fragment is running on main it won't *bing* in tablet layout which might be unnecessary since we can *see* it
        activity.xmppConnectionService.getNotificationService().setOpenConversation(this.conversation);

        // todo temporarly disable swipe gestures
        /*
        gesturesDetector.setOnFingerGestureListener(new SimpleFingerGestures.OnFingerGestureListener() {
            @Override
            public boolean onSwipeUp(int fingers, long gestureDuration, double gestureDistance) {
                return false;
            }

            @Override
            public boolean onSwipeDown(int fingers, long gestureDuration, double gestureDistance) {
                return false;
            }

            @Override
            public boolean onSwipeLeft(int fingers, long gestureDuration, double gestureDistance) {
                return false;
            }

            @Override
            public boolean onSwipeRight(int fingers, long gestureDuration, double gestureDistance) {
                activity.onBackPressed();
                return false;
            }

            @Override
            public boolean onPinch(int fingers, long gestureDuration, double gestureDistance) {
                return false;
            }

            @Override
            public boolean onUnpinch(int fingers, long gestureDuration, double gestureDistance) {
                return false;
            }

            @Override
            public boolean onDoubleTap(int fingers) {
                return false;
            }
        });
        this.binding.messagesView.setOnTouchListener(gesturesDetector);
        */
        return true;
    }

    private void resetUnreadMessagesCount() {
        lastMessageUuid = null;
        hideUnreadMessagesCount();
    }

    private void hideUnreadMessagesCount() {
        if (this.binding == null) {
            return;
        }
        this.binding.scrollToBottomButton.setEnabled(false);
        this.binding.scrollToBottomButton.setVisibility(View.GONE);
        this.binding.unreadCountCustomView.setVisibility(View.GONE);
    }

    private void setSelection(int pos, boolean jumpToBottom) {
        ListViewUtils.setSelection(this.binding.messagesView, pos, jumpToBottom);
        this.binding.messagesView.post(() -> ListViewUtils.setSelection(this.binding.messagesView, pos, jumpToBottom));
        this.binding.messagesView.post(this::fireReadEvent);
    }

    private boolean scrolledToBottom() {
        return this.binding != null && scrolledToBottom(this.binding.messagesView);
    }

    private void processExtras(Bundle extras) {
        final String downloadUuid = extras.getString(ConversationsActivity.EXTRA_DOWNLOAD_UUID);
        final String text = extras.getString(ConversationsActivity.EXTRA_TEXT);
        final String nick = extras.getString(ConversationsActivity.EXTRA_NICK);
        final boolean asQuote = extras.getBoolean(ConversationsActivity.EXTRA_AS_QUOTE);
        final boolean pm = extras.getBoolean(ConversationsActivity.EXTRA_IS_PRIVATE_MESSAGE, false);
        if (nick != null) {
            if (pm) {
                Jid jid = conversation.getJid();
                try {
                    Jid next = Jid.of(jid.getLocal(), jid.getDomain(), nick);
                    privateMessageWith(next);
                } catch (final IllegalArgumentException ignored) {
                    //do nothing
                }
            } else {
                final MucOptions mucOptions = conversation.getMucOptions();
                if (mucOptions.participating() || conversation.getNextCounterpart() != null) {
                    highlightInConference(nick);
                }
            }
        } else {
            if (text != null && asQuote) {
                quoteText(text);
            } else {
                appendText(text);
            }
        }
        final Message message = downloadUuid == null ? null : conversation.findMessageWithFileAndUuid(downloadUuid);
        if (message != null) {
            startDownloadable(message);
        }
    }

    private boolean showBlockSubmenu(View view) {
        final Jid jid = conversation.getJid();
        if (jid.getLocal() == null) {
            BlockContactDialog.show(activity, conversation);
        } else {
            PopupMenu popupMenu = new PopupMenu(getActivity(), view);
            popupMenu.inflate(R.menu.block);
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                Blockable blockable;
                switch (menuItem.getItemId()) {
                    case R.id.block_domain:
                        blockable = conversation.getAccount().getRoster().getContact(Jid.ofDomain(jid.getDomain()));
                        break;
                    default:
                        blockable = conversation;
                }
                BlockContactDialog.show(activity, blockable);
                return true;
            });
            popupMenu.show();
        }
        return true;
    }

    private void updateSnackBar(final Conversation conversation) {
        final Account account = conversation.getAccount();
        final XmppConnection connection = account.getXmppConnection();
        final int mode = conversation.getMode();
        final Contact contact = mode == Conversation.MODE_SINGLE ? conversation.getContact() : null;
        if (conversation.getStatus() == Conversation.STATUS_ARCHIVED) {
            return;
        }
        if (account.getStatus() == Account.State.DISABLED) {
            showSnackbar(R.string.this_account_is_disabled, R.string.enable, this.mEnableAccountListener);
        } else if (conversation.isBlocked()) {
            showSnackbar(R.string.contact_blocked, R.string.unblock, this.mUnblockClickListener);
        } else if (contact != null && !contact.showInRoster() && contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            showSnackbar(R.string.contact_added_you, R.string.add_back, this.mAddBackClickListener, this.mLongPressBlockListener);
        } else if (contact != null && contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            showSnackbar(R.string.contact_asks_for_presence_subscription, R.string.allow, this.mAllowPresenceSubscription, this.mLongPressBlockListener);
        } else if (mode == Conversation.MODE_MULTI
                && !conversation.getMucOptions().online()
                && account.getStatus() == Account.State.ONLINE) {
            switch (conversation.getMucOptions().getError()) {
                case NICK_IN_USE:
                    showSnackbar(R.string.nick_in_use, R.string.edit, clickToMuc);
                    break;
                case NO_RESPONSE:
                    showSnackbar(R.string.joining_conference, 0, null);
                    break;
                case SERVER_NOT_FOUND:
                    if (conversation.receivedMessagesCount() > 0) {
                        showSnackbar(R.string.remote_server_not_found, R.string.try_again, joinMuc);
                    } else {
                        showSnackbar(R.string.remote_server_not_found, R.string.leave, leaveMuc);
                    }
                    break;
                case PASSWORD_REQUIRED:
                    showSnackbar(R.string.conference_requires_password, R.string.enter_password, enterPassword);
                    break;
                case BANNED:
                    showSnackbar(R.string.conference_banned, R.string.leave, leaveMuc);
                    break;
                case MEMBERS_ONLY:
                    showSnackbar(R.string.conference_members_only, R.string.leave, leaveMuc);
                    break;
                case KICKED:
                    showSnackbar(R.string.conference_kicked, R.string.join, joinMuc);
                    break;
                case UNKNOWN:
                    showSnackbar(R.string.conference_unknown_error, R.string.leave, leaveMuc);
                    break;
                case INVALID_NICK:
                    showSnackbar(R.string.invalid_muc_nick, R.string.edit, clickToMuc);
                case SHUTDOWN:
                    showSnackbar(R.string.conference_shutdown, R.string.try_again, joinMuc);
                    break;
                default:
                    hideSnackbar();
                    break;
            }
        } else if ((mode == Conversation.MODE_MULTI
                && !conversation.getMucOptions().participating())) {
            showSnackbar(R.string.no_write_access_in_public_muc, R.string.ok, clickToMuc);
        } else if (account.hasPendingPgpIntent(conversation)) {
            showSnackbar(R.string.openpgp_messages_found, R.string.decrypt, clickToDecryptListener);
        } else if (mode == Conversation.MODE_SINGLE
                && conversation.smpRequested()) {
            showSnackbar(R.string.smp_requested, R.string.verify, this.mAnswerSmpClickListener);
        } else if (mode == Conversation.MODE_SINGLE
                && conversation.hasValidOtrSession()
                && (conversation.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED)
                && (!conversation.isOtrFingerprintVerified())) {
            showSnackbar(R.string.unknown_otr_fingerprint, R.string.verify, clickToVerify);
        } else if (connection != null
                && connection.getFeatures().blocking()
                && conversation.countMessages() != 0
                && !conversation.isBlocked()
                && conversation.isWithStranger()) {
            showSnackbar(R.string.received_message_from_stranger, R.string.block, mBlockClickListener);
        } else if (activity.xmppConnectionService.warnUnecryptedChat()) {
            AxolotlService axolotlService = account.getAxolotlService();
            if ((mode == Conversation.MODE_SINGLE) && (conversation.getNextEncryption() == Message.ENCRYPTION_NONE &&
                    ((Config.supportOmemo() && axolotlService != null && conversation.getAccount().getAxolotlService().isConversationAxolotlCapable(conversation)) ||
                            (Config.supportOpenPgp() && account.isPgpDecryptionServiceConnected()) ||
                            Config.supportOtr()))) {
                if (ENCRYPTION_EXCEPTIONS.contains(conversation.getJid().toString()) || conversation.getJid().toString().equals(account.getJid().getDomain())) {
                    hideSnackbar();
                } else {
                    showSnackbar(R.string.conversation_unencrypted_hint, R.string.ok, mHideUnencryptionHint, null);
                }
            } else if ((mode == Conversation.MODE_MULTI && conversation.getMucOptions().membersOnly() && conversation.getMucOptions().nonanonymous()) &&
                    (conversation.getNextEncryption() == Message.ENCRYPTION_NONE &&
                            ((Config.supportOmemo() && axolotlService != null && conversation.getAccount().getAxolotlService().isConversationAxolotlCapable(conversation)) ||
                                    (Config.supportOpenPgp() && account.isPgpDecryptionServiceConnected())))) {
                if (ENCRYPTION_EXCEPTIONS.contains(conversation.getJid().toString()) || conversation.getJid().toString().equals(account.getJid().getDomain())) {
                    Log.d(Config.LOGTAG, "Don't show unencrypted warning because " + conversation.getJid().toString() + " is on exception list");
                    hideSnackbar();
                } else {
                    showSnackbar(R.string.conversation_unencrypted_hint, R.string.ok, mHideUnencryptionHint, null);
                }
            } else {
                hideSnackbar();
            }
        } else {
            hideSnackbar();
        }
    }

    @Override
    public void refresh() {
        if (this.binding == null) {
            Log.d(Config.LOGTAG, "ConversationFragment.refresh() skipped updated because view binding was null");
            return;
        }
        if (this.conversation != null && this.activity != null && this.activity.xmppConnectionService != null) {
            if (!activity.xmppConnectionService.isConversationStillOpen(this.conversation)) {
                activity.onConversationArchived(this.conversation);
                return;
            }
        }
        this.refresh(true);
    }


    private void refresh(boolean notifyConversationRead) {
        synchronized (this.messageList) {
            if (this.conversation != null) {
                conversation.populateWithMessages(ConversationFragment.this.messageList);
                updateSnackBar(conversation);
                updateStatusMessages();
                if (conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid) != 0) {
                    binding.unreadCountCustomView.setVisibility(View.VISIBLE);
                    binding.unreadCountCustomView.setUnreadCount(conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid));
                }
                this.messageListAdapter.notifyDataSetChanged();
                updateChatMsgHint();
                if (notifyConversationRead && activity != null) {
                    binding.messagesView.post(this::fireReadEvent);
                }
                updateSendButton();
                updateEditablity();
            }
        }
    }

    protected void messageSent() {
        mSendingPgpMessage.set(false);
        this.binding.textinput.setText("");
        if (conversation.setCorrectingMessage(null)) {
            this.binding.textinput.append(conversation.getDraftMessage());
            conversation.setDraftMessage(null);
        }
        if (conversation.setNextMessage(this.binding.textinput.getText().toString())) {
            activity.xmppConnectionService.updateConversation(conversation);
        }
        updateChatMsgHint();
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean prefScrollToBottom = p.getBoolean("scroll_to_bottom", activity.getResources().getBoolean(R.bool.scroll_to_bottom));
        if (prefScrollToBottom || scrolledToBottom()) {
            new Handler().post(() -> {
                int size = messageList.size();
                this.binding.messagesView.setSelection(size - 1);
            });
        }
    }

    public void doneSendingPgpMessage() {
        mSendingPgpMessage.set(false);
    }

    public long getMaxHttpUploadSize(Conversation conversation) {
        final XmppConnection connection = conversation.getAccount().getXmppConnection();
        return connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
    }

    private void updateEditablity() {
        boolean canWrite = this.conversation.getMode() == Conversation.MODE_SINGLE || this.conversation.getMucOptions().participating() || this.conversation.getNextCounterpart() != null;
        this.binding.textinput.setFocusable(canWrite);
        this.binding.textinput.setFocusableInTouchMode(canWrite);
        this.binding.textSendButton.setEnabled(canWrite);
        this.binding.textinput.setCursorVisible(canWrite);
    }

    public void updateSendButton() {
        boolean useSendButtonToIndicateStatus = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("send_button_status", getResources().getBoolean(R.bool.send_button_status));
        final Conversation c = this.conversation;
        final Presence.Status status;
        final String text = this.binding.textinput == null ? "" : this.binding.textinput.getText().toString();
        final SendButtonAction action = SendButtonTool.getAction(getActivity(), c, text);
        if (useSendButtonToIndicateStatus && c.getAccount().getStatus() == Account.State.ONLINE) {
            if (activity.xmppConnectionService != null && activity.xmppConnectionService.getMessageArchiveService().isCatchingUp(c)) {
                status = Presence.Status.OFFLINE;
            } else if (c.getMode() == Conversation.MODE_SINGLE) {
                status = c.getContact().getShownStatus();
            } else {
                status = c.getMucOptions().online() ? Presence.Status.ONLINE : Presence.Status.OFFLINE;
            }
        } else {
            status = Presence.Status.OFFLINE;
        }
        this.binding.textSendButton.setTag(action);
        this.binding.textSendButton.setImageResource(SendButtonTool.getSendButtonImageResource(getActivity(), action, status));
    }

    protected void updateStatusMessages() {
        updateDateBubbles();
        synchronized (this.messageList) {
            if (showLoadMoreMessages(conversation)) {
                this.messageList.add(0, Message.createLoadMoreMessage(conversation));
            }
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                final MucOptions mucOptions = conversation.getMucOptions();
                final List<MucOptions.User> allUsers = mucOptions.getUsers();
                final Set<ReadByMarker> addedMarkers = new HashSet<>();
                ChatState state = ChatState.COMPOSING;
                List<MucOptions.User> users = conversation.getMucOptions().getUsersWithChatState(state, 5);
                if (users.size() == 0) {
                    state = ChatState.PAUSED;
                    users = conversation.getMucOptions().getUsersWithChatState(state, 5);
                }
                if (mucOptions.isPrivateAndNonAnonymous()) {
                    for (int i = this.messageList.size() - 1; i >= 0; --i) {
                        final Set<ReadByMarker> markersForMessage = messageList.get(i).getReadByMarkers();
                        final List<MucOptions.User> shownMarkers = new ArrayList<>();
                        for (ReadByMarker marker : markersForMessage) {
                            if (!ReadByMarker.contains(marker, addedMarkers)) {
                                addedMarkers.add(marker); //may be put outside this condition. set should do dedup anyway
                                MucOptions.User user = mucOptions.findUser(marker);
                                shownMarkers.add(user);
                            }
                        }
                        final ReadByMarker markerForSender = ReadByMarker.from(messageList.get(i));
                        final Message statusMessage;
                        final int size = shownMarkers.size();
                        if (size > 1) {
                            final String body;
                            if (size <= 4) {
                                body = getString(R.string.contacts_have_read_up_to_this_point, UIHelper.concatNames(shownMarkers));
                            } else {
                                body = getString(R.string.contacts_and_n_more_have_read_up_to_this_point, UIHelper.concatNames(shownMarkers, 3), size - 3);
                            }
                            statusMessage = Message.createStatusMessage(conversation, body);
                            statusMessage.setCounterparts(shownMarkers);
                        } else if (size == 1) {
                            statusMessage = Message.createStatusMessage(conversation, getString(R.string.contact_has_read_up_to_this_point, UIHelper.getDisplayName(shownMarkers.get(0))));
                            statusMessage.setCounterpart(shownMarkers.get(0).getFullJid());
                            statusMessage.setTrueCounterpart(shownMarkers.get(0).getRealJid());
                        } else {
                            statusMessage = null;
                        }
                        if (statusMessage != null) {
                            this.messageList.add(i + 1, statusMessage);
                        }
                        addedMarkers.add(markerForSender);
                        if (ReadByMarker.allUsersRepresented(allUsers, addedMarkers)) {
                            break;
                        }
                    }
                }
            }
        }
    }

    protected void updateDateBubbles() {
        synchronized (this.messageList) {
            DateSeparator.addAll(this.messageList);
        }
    }

    private void stopScrolling() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        binding.messagesView.dispatchTouchEvent(cancel);
    }

    private boolean showLoadMoreMessages(final Conversation c) {
        if (activity == null || activity.xmppConnectionService == null) {
            return false;
        }
        final boolean mam = hasMamSupport(c) && !c.getContact().isBlocked();
        final MessageArchiveService service = activity.xmppConnectionService.getMessageArchiveService();
        return mam && (c.getLastClearHistory().getTimestamp() != 0 || (c.countMessages() == 0 && c.messagesLoaded.get() && c.hasMessagesLeftOnServer() && !service.queryInProgress(c)));
    }

    private boolean hasMamSupport(final Conversation c) {
        if (c.getMode() == Conversation.MODE_SINGLE) {
            final XmppConnection connection = c.getAccount().getXmppConnection();
            return connection != null && connection.getFeatures().mam();
        } else {
            return c.getMucOptions().mamSupport();
        }
    }

    protected void showSnackbar(final int message, final int action, final OnClickListener clickListener) {
        showSnackbar(message, action, clickListener, null);
    }

    protected void showSnackbar(final int message, final int action, final OnClickListener clickListener, final View.OnLongClickListener longClickListener) {
        this.binding.snackbar.setVisibility(View.VISIBLE);
        this.binding.snackbar.setOnClickListener(null);
        this.binding.snackbarMessage.setText(message);
        this.binding.snackbarMessage.setOnClickListener(null);
        this.binding.snackbarAction.setVisibility(clickListener == null ? View.GONE : View.VISIBLE);
        if (action != 0) {
            this.binding.snackbarAction.setText(action);
        }
        this.binding.snackbarAction.setOnClickListener(clickListener);
        this.binding.snackbarAction.setOnLongClickListener(longClickListener);
    }

    protected void hideSnackbar() {
        this.binding.snackbar.setVisibility(View.GONE);
    }

    protected void showMessageHint(final String message) {
        this.binding.messagehint.setVisibility(View.VISIBLE);
        this.binding.messagehintMessage.setText(message);
    }

    protected void hideMessageHint() {
        this.binding.messagehint.setVisibility(View.GONE);
    }

    protected void sendMessage(Message message) {
        activity.xmppConnectionService.sendMessage(message);
        messageSent();
    }

    protected void sendPgpMessage(final Message message) {
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (!activity.hasPgp()) {
            activity.showInstallPgpDialog();
            return;
        }
        if (conversation.getAccount().getPgpSignature() == null) {
            activity.announcePgp(conversation.getAccount(), conversation, null, activity.onOpenPGPKeyPublished);
            return;
        }
        if (!mSendingPgpMessage.compareAndSet(false, true)) {
            Log.d(Config.LOGTAG, "sending pgp message already in progress");
        }
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (contact.getPgpKeyId() != 0) {
                xmppService.getPgpEngine().hasKey(contact,
                        new UiCallback<Contact>() {

                            @Override
                            public void userInputRequried(PendingIntent pi, Contact contact) {
                                startPendingIntent(pi, REQUEST_ENCRYPT_MESSAGE);
                            }

                            @Override
                            public void success(Contact contact) {
                                encryptTextMessage(message);
                            }

                            @Override
                            public void error(int error, Contact contact) {
                                activity.runOnUiThread(() -> Toast.makeText(activity,
                                        R.string.unable_to_connect_to_keychain,
                                        Toast.LENGTH_SHORT
                                ).show());
                                mSendingPgpMessage.set(false);
                            }
                        });

            } else {
                showNoPGPKeyDialog(false,
                        (dialog, which) -> {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            xmppService.updateConversation(conversation);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.sendMessage(message);
                            messageSent();
                        });
            }
        } else {
            if (conversation.getMucOptions().pgpKeysInUse()) {
                if (!conversation.getMucOptions().everybodyHasKeys()) {
                    Toast warning = Toast
                            .makeText(getActivity(),
                                    R.string.missing_public_keys,
                                    Toast.LENGTH_LONG);
                    warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                    warning.show();
                }
                encryptTextMessage(message);
            } else {
                showNoPGPKeyDialog(true,
                        (dialog, which) -> {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.updateConversation(conversation);
                            xmppService.sendMessage(message);
                            messageSent();
                        });
            }
        }
    }

    public void encryptTextMessage(Message message) {
        activity.xmppConnectionService.getPgpEngine().encrypt(message,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi, Message message) {
                        startPendingIntent(pi, REQUEST_SEND_MESSAGE);
                    }

                    @Override
                    public void success(Message message) {
                        //TODO the following two call can be made before the callback
                        getActivity().runOnUiThread(() -> messageSent());
                    }

                    @Override
                    public void error(final int error, Message message) {
                        getActivity().runOnUiThread(() -> {
                            doneSendingPgpMessage();
                            Toast.makeText(getActivity(), R.string.unable_to_connect_to_keychain, Toast.LENGTH_SHORT).show();
                        });

                    }
                });
    }

    public void showNoPGPKeyDialog(boolean plural, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        if (plural) {
            builder.setTitle(getString(R.string.no_pgp_keys));
            builder.setMessage(getText(R.string.contacts_have_no_pgp_keys));
        } else {
            builder.setTitle(getString(R.string.no_pgp_key));
            builder.setMessage(getText(R.string.contact_has_no_pgp_key));
        }
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.send_unencrypted), listener);
        builder.create().show();
    }

    protected void sendOtrMessage(final Message message) {
        final ConversationsActivity activity = (ConversationsActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        activity.selectPresence(conversation,
                () -> {
                    message.setCounterpart(conversation.getNextCounterpart());
                    xmppService.sendMessage(message);
                    messageSent();
                });
    }

    public void appendText(String text) {
        if (text == null) {
            return;
        }
        String previous = this.binding.textinput.getText().toString();
        if (UIHelper.isLastLineQuote(previous)) {
            text = '\n' + text;
        } else if (previous.length() != 0 && !Character.isWhitespace(previous.charAt(previous.length() - 1))) {
            text = " " + text;
        }
        this.binding.textinput.append(text);
    }

    @Override
    public boolean onEnterPressed() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final boolean enterIsSend = p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
        if (enterIsSend) {
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onTypingStarted() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        final boolean broadcastLastActivity = activity.xmppConnectionService.broadcastLastActivity();
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
            service.sendChatState(conversation);
        }
        if (broadcastLastActivity) {
            service.sendPresence(conversation.getAccount(), false); //send new presence but don't include idle because we are not
        }
        updateSendButton();
    }

    @Override
    public void onTypingStopped() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) {
            service.sendChatState(conversation);
        }
    }

    @Override
    public void onTextDeleted() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
            service.sendChatState(conversation);
        }
        updateSendButton();
    }

    @Override
    public void onTextChanged() {
        if (conversation != null && conversation.getCorrectingMessage() != null) {
            updateSendButton();
        }
    }

    @Override
    public boolean onTabPressed(boolean repeated) {
        if (conversation == null || conversation.getMode() == Conversation.MODE_SINGLE) {
            return false;
        }
        if (repeated) {
            completionIndex++;
        } else {
            lastCompletionLength = 0;
            completionIndex = 0;
            final String content = this.binding.textinput.getText().toString();
            lastCompletionCursor = this.binding.textinput.getSelectionEnd();
            int start = lastCompletionCursor > 0 ? content.lastIndexOf(" ", lastCompletionCursor - 1) + 1 : 0;
            firstWord = start == 0;
            incomplete = content.substring(start, lastCompletionCursor);
        }
        List<String> completions = new ArrayList<>();
        for (MucOptions.User user : conversation.getMucOptions().getUsers()) {
            String name = user.getName();
            if (name != null && name.startsWith(incomplete)) {
                completions.add(name + (firstWord ? ": " : " "));
            }
        }
        Collections.sort(completions);
        if (completions.size() > completionIndex) {
            String completion = completions.get(completionIndex).substring(incomplete.length());
            this.binding.textinput.getEditableText().delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            this.binding.textinput.getEditableText().insert(lastCompletionCursor, completion);
            lastCompletionLength = completion.length();
        } else {
            completionIndex = -1;
            this.binding.textinput.getEditableText().delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            lastCompletionLength = 0;
        }
        return true;
    }

    public Message searchHistory(String query) {
        return searchHistory(query, null);
    }

    public Message searchHistory(String query, Boolean ascendingSearch) {
        return searchHistory(query, lastHistoryMessage, ascendingSearch);
    }

    /**
     * Search through history from message basis either ascending or descending
     *
     * @param query           search term
     * @param basis           message to start from. If null, start from last recent message
     * @param ascendingSearch do we want to ascend or descend in our search?
     *                        If this is null, ascend to first match and return.
     * @return match or null
     */
    public Message searchHistory(String query, Message basis, Boolean ascendingSearch) {
        int entryIndex;
        Message message;
        lastHistoryMessage = basis;
        if (messageList.size() == 0) {
            return null;
        }
        if (basis == null) {
            entryIndex = messageList.size() - 1;
        } else {
            int in = getIndexOf(basis.getUuid(), messageList);
            entryIndex = (in != -1 ? in : messageList.size() - 1);
        }

        int firstMatchIndex = entryIndex;
        boolean entryIndexWasMatch = true;
        do {
            message = messageList.get(firstMatchIndex);
            if (message.getType() == Message.TYPE_TEXT && messageContainsQuery(message, query)) {
                lastHistoryMessage = message;
                break;
            }
            entryIndexWasMatch = false;
            firstMatchIndex = (messageList.size() + firstMatchIndex - 1) % messageList.size();
        } while (entryIndex != firstMatchIndex);

        if (!entryIndexWasMatch && entryIndex == firstMatchIndex) {
            //No matches
            return null;
        }

        if (ascendingSearch != null) {
            int direction = ascendingSearch ? -1 : 1;
            int nextMatchIndex = firstMatchIndex;
            do {
                nextMatchIndex = (messageList.size() + nextMatchIndex + direction) % messageList.size();
                message = messageList.get(nextMatchIndex);
                if (message.getType() == Message.TYPE_TEXT && messageContainsQuery(message, query)) {
                    lastHistoryMessage = message;
                    break;
                }
            } while (nextMatchIndex != entryIndex);
        }

        if (lastHistoryMessage != null) {
            int pos = getIndexOf(lastHistoryMessage.getUuid(), messageList);
            setScrollPosition(getScrollPosition(pos, getView()), null);
            this.binding.messagesView.setSelection(pos);
        }
        return lastHistoryMessage;
    }

    private boolean messageContainsQuery(Message m, String q) {
        return m != null && m.getMergedBody().toString().toLowerCase().contains(q.toLowerCase());
    }

    private void startPendingIntent(PendingIntent pendingIntent, int requestCode) {
        try {
            getActivity().startIntentSenderForResult(pendingIntent.getIntentSender(), requestCode, null, 0, 0, 0);
        } catch (final SendIntentException ignored) {
        }
    }

    @Override
    public void onBackendConnected() {
        Log.d(Config.LOGTAG, "ConversationFragment.onBackendConnected()");
        String uuid = pendingConversationsUuid.pop();
        if (uuid != null) {
            if (!findAndReInitByUuidOrArchive(uuid)) {
                return;
            }
        } else {
            if (!activity.xmppConnectionService.isConversationStillOpen(conversation)) {
                clearPending();
                activity.onConversationArchived(conversation);
                return;
            }
        }
        ActivityResult activityResult = postponedActivityResult.pop();
        if (activityResult != null) {
            handleActivityResult(activityResult);
        }
        clearPending();
    }

    private boolean findAndReInitByUuidOrArchive(@NonNull final String uuid) {
        Conversation conversation = activity.xmppConnectionService.findConversationByUuid(uuid);
        if (conversation == null) {
            clearPending();
            activity.onConversationArchived(null);
            return false;
        }
        reInit(conversation);
        ScrollState scrollState = pendingScrollState.pop();
        String lastMessageUuid = pendingLastMessageUuid.pop();
        if (scrollState != null) {
            setScrollPosition(scrollState, lastMessageUuid);
        }
        return true;
    }

    private void clearPending() {
        if (postponedActivityResult.pop() != null) {
            Log.e(Config.LOGTAG, "cleared pending intent with unhandled result left");
        }
        pendingScrollState.pop();
        if (pendingTakePhotoUri.pop() != null) {
            Log.e(Config.LOGTAG, "cleared pending photo uri");
        }
    }



    public Conversation getConversation() {
        return conversation;
    }
}
