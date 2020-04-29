package eu.siacs.conversations.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import net.ypresto.androidtranscoder.MediaTranscoder;
import net.ypresto.androidtranscoder.format.MediaFormatStrategyPresets;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.utils.MimeUtils;

public class AttachFileToConversationRunnable implements Runnable, MediaTranscoder.Listener {

    private final XmppConnectionService mXmppConnectionService;
    private final Message message;
    private final Uri uri;
    private final String type;
    private final UiCallback<Message> callback;
    private final boolean isVideoMessage;
    private final long originalFileSize;
    private int currentProgress = -1;

    public AttachFileToConversationRunnable(XmppConnectionService xmppConnectionService, Uri uri, String type, Message message, UiCallback<Message> callback) {
        this.uri = uri;
        this.type = type;
        this.mXmppConnectionService = xmppConnectionService;
        this.message = message;
        this.callback = callback;
        final String mimeType = MimeUtils.guessMimeTypeFromUriAndMime(mXmppConnectionService, uri, type);
        final int autoAcceptFileSize = Config.FILE_SIZE;
        this.originalFileSize = FileBackend.getFileSize(mXmppConnectionService, uri);
        this.isVideoMessage = !getFileBackend().useFileAsIs(uri)
                && (mimeType != null && mimeType.startsWith("video/")
                && (mXmppConnectionService.getCompressVideoBitratePreference() != 0 && mXmppConnectionService.getCompressVideoResolutionPreference() != 0))
                && originalFileSize > autoAcceptFileSize;
    }

    boolean isVideoMessage() {
        return this.isVideoMessage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    }

    private void processAsFile() {
        final String path = mXmppConnectionService.getFileBackend().getOriginalPath(uri);
        if (path != null && !FileBackend.isPathBlacklisted(path)) {
            message.setRelativeFilePath(path);
            mXmppConnectionService.getFileBackend().updateFileParams(message);
            if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
                mXmppConnectionService.getPgpEngine().encrypt(message, callback);
            } else {
                mXmppConnectionService.sendMessage(message);
                callback.success(message);
            }
        } else {
            try {
                mXmppConnectionService.getFileBackend().copyFileToPrivateStorage(message, uri, type);
                mXmppConnectionService.getFileBackend().updateFileParams(message);
                if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
                    final PgpEngine pgpEngine = mXmppConnectionService.getPgpEngine();
                    if (pgpEngine != null) {
                        pgpEngine.encrypt(message, callback);
                    } else if (callback != null) {
                        callback.error(R.string.unable_to_connect_to_keychain, null);
                    }
                } else {
                    mXmppConnectionService.sendMessage(message);
                    callback.success(message);
                }
            } catch (FileBackend.FileCopyException e) {
                callback.error(e.getResId(), message);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void processAsVideo() throws FileNotFoundException {
        Log.d(Config.LOGTAG, "processing file as video");
        mXmppConnectionService.startForcingForegroundNotification();
        SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        message.setRelativeFilePath("Sent/" + fileDateFormat.format(new Date(message.getTimeSent())) + "_" + message.getUuid().substring(0, 4) + "_komp.mp4");
        final DownloadableFile file = mXmppConnectionService.getFileBackend().getFile(message);
        file.getParentFile().mkdirs();
        final ParcelFileDescriptor parcelFileDescriptor = mXmppConnectionService.getContentResolver().openFileDescriptor(uri, "r");
        if (parcelFileDescriptor == null) {
            throw new FileNotFoundException("Parcel File Descriptor was null");
        }
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Future<Void> future = MediaTranscoder.getInstance().transcodeVideo(fileDescriptor, file.getAbsolutePath(), MediaFormatStrategyPresets.createAndroidStandardStrategy(mXmppConnectionService.getCompressVideoBitratePreference(), mXmppConnectionService.getCompressVideoResolutionPreference()), this);
        try {
            future.get();
        } catch (InterruptedException e) {
            mXmppConnectionService.stopForcingForegroundNotification();
            throw new AssertionError(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error) {
                mXmppConnectionService.stopForcingForegroundNotification();
                processAsFile();
            } else {
                Log.d(Config.LOGTAG, "ignoring execution exception. Should get handled by onTranscodeFiled() instead", e);
            }
        }
    }

    @Override
    public void onTranscodeProgress(double progress) {
        final int p = (int) Math.round(progress * 100);
        if (p > currentProgress) {
            currentProgress = p;
            mXmppConnectionService.getNotificationService().updateFileAddingNotification(p, message);
        }
    }

    @Override
    public void onTranscodeCompleted() {
        mXmppConnectionService.stopForcingForegroundNotification();
        final File file = mXmppConnectionService.getFileBackend().getFile(message);
        long convertedFileSize = mXmppConnectionService.getFileBackend().getFile(message).getSize();
        Log.d(Config.LOGTAG, "originalFileSize = " + originalFileSize + " convertedFileSize = " + convertedFileSize);
        if (originalFileSize != 0 && convertedFileSize >= originalFileSize) {
            if (file.delete()) {
                Log.d(Config.LOGTAG, "original file size was smaller. Deleting and processing as file");
                processAsFile();
                return;
            } else {
                Log.d(Config.LOGTAG, "unable to delete converted file");
            }
        }
        mXmppConnectionService.getFileBackend().updateFileParams(message);
        if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
            mXmppConnectionService.getPgpEngine().encrypt(message, callback);
        } else {
            mXmppConnectionService.sendMessage(message);
            callback.success(message);
        }
    }

    @Override
    public void onTranscodeCanceled() {
        mXmppConnectionService.stopForcingForegroundNotification();
        processAsFile();
    }

    @Override
    public void onTranscodeFailed(Exception e) {
        mXmppConnectionService.stopForcingForegroundNotification();
        Log.d(Config.LOGTAG, "video transcoding failed", e);
        processAsFile();
    }

    @Override
    public void run() {
        if (this.isVideoMessage()) {
            try {
                processAsVideo();
            } catch (FileNotFoundException e) {
                processAsFile();
                e.printStackTrace();
            }
        } else {
            processAsFile();
        }
    }

    public static String getVideoCompression(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString("video_compression", context.getResources().getString(R.string.video_compression));
    }

    public FileBackend getFileBackend() {
        return mXmppConnectionService.fileBackend;
    }
}