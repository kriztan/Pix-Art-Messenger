package de.pixart.messenger.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import de.pixart.messenger.Config;
import de.pixart.messenger.R;
import de.pixart.messenger.crypto.axolotl.SQLiteAxolotlStore;
import de.pixart.messenger.entities.Account;
import de.pixart.messenger.entities.Conversation;
import de.pixart.messenger.entities.Message;
import de.pixart.messenger.persistance.DatabaseBackend;
import de.pixart.messenger.persistance.FileBackend;
import de.pixart.messenger.utils.BackupFileHeader;
import de.pixart.messenger.utils.Compatibility;
import de.pixart.messenger.utils.WakeLockHelper;
import rocks.xmpp.addr.Jid;

public class ExportBackupService extends Service {

    private PowerManager.WakeLock wakeLock;
    private PowerManager pm;

    public static final String KEYTYPE = "AES";
    public static final String CIPHERMODE = "AES/GCM/NoPadding";
    public static final String PROVIDER = "BC";

    boolean ReadableLogsEnabled = false;
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final String DIRECTORY_STRING_FORMAT = FileBackend.getAppLogsDirectory() + "%s";
    private static final String MESSAGE_STRING_FORMAT = "(%s) %s: %s\n";

    private static final int NOTIFICATION_ID = 19;
    private static final int PAGE_SIZE = 20;
    private static AtomicBoolean running = new AtomicBoolean(false);
    private DatabaseBackend mDatabaseBackend;
    private List<Account> mAccounts;
    private NotificationManager notificationManager;

    private static List<Intent> getPossibleFileOpenIntents(final Context context, final String path) {

        //http://www.openintents.org/action/android-intent-action-view/file-directory
        //do not use 'vnd.android.document/directory' since this will trigger system file manager
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.addCategory(Intent.CATEGORY_DEFAULT);
        if (Compatibility.runsAndTargetsTwentyFour(context)) {
            openIntent.setType("resource/folder");
        } else {
            openIntent.setDataAndType(Uri.parse("file://" + path), "resource/folder");
        }
        openIntent.putExtra("org.openintents.extra.ABSOLUTE_PATH", path);

        Intent amazeIntent = new Intent(Intent.ACTION_VIEW);
        amazeIntent.setDataAndType(Uri.parse("com.amaze.filemanager:" + path), "resource/folder");

        //will open a file manager at root and user can navigate themselves
        Intent systemFallBack = new Intent(Intent.ACTION_VIEW);
        systemFallBack.addCategory(Intent.CATEGORY_DEFAULT);
        systemFallBack.setData(Uri.parse("content://com.android.externalstorage.documents/root/primary"));

        return Arrays.asList(openIntent, amazeIntent, systemFallBack);
    }

    private static void accountExport(SQLiteDatabase db, String uuid, PrintWriter writer) {
        final StringBuilder builder = new StringBuilder();
        final Cursor accountCursor = db.query(Account.TABLENAME, null, Account.UUID + "=?", new String[]{uuid}, null, null, null);
        while (accountCursor != null && accountCursor.moveToNext()) {
            builder.append("INSERT INTO ").append(Account.TABLENAME).append("(");
            for (int i = 0; i < accountCursor.getColumnCount(); ++i) {
                if (i != 0) {
                    builder.append(',');
                }
                builder.append(accountCursor.getColumnName(i));
            }
            builder.append(") VALUES(");
            for (int i = 0; i < accountCursor.getColumnCount(); ++i) {
                if (i != 0) {
                    builder.append(',');
                }
                final String value = accountCursor.getString(i);
                if (value == null || Account.ROSTERVERSION.equals(accountCursor.getColumnName(i))) {
                    builder.append("NULL");
                } else if (value.matches("\\d+")) {
                    int intValue = Integer.parseInt(value);
                    if (Account.OPTIONS.equals(accountCursor.getColumnName(i))) {
                        intValue |= 1 << Account.OPTION_DISABLED;
                    }
                    builder.append(intValue);
                } else {
                    DatabaseUtils.appendEscapedSQLString(builder, value);
                }
            }
            builder.append(")");
            builder.append(';');
            builder.append('\n');
        }
        if (accountCursor != null) {
            accountCursor.close();
        }
        writer.append(builder.toString());
    }

    private static void simpleExport(SQLiteDatabase db, String table, String column, String uuid, PrintWriter writer) {
        final Cursor cursor = db.query(table, null, column + "=?", new String[]{uuid}, null, null, null);
        while (cursor != null && cursor.moveToNext()) {
            writer.write(cursorToString(table, cursor, PAGE_SIZE));
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    public static byte[] getKey(String password, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return factory.generateSecret(new PBEKeySpec(password.toCharArray(), salt, 1024, 128)).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new AssertionError(e);
        }
    }

    private static String cursorToString(String tablename, Cursor cursor, int max) {
        return cursorToString(tablename, cursor, max, false);
    }

    private static String cursorToString(final String tablename, final Cursor cursor, int max, boolean ignore) {
        final boolean identities = SQLiteAxolotlStore.IDENTITIES_TABLENAME.equals(tablename);
        StringBuilder builder = new StringBuilder();
        builder.append("INSERT ");
        if (ignore) {
            builder.append("OR IGNORE ");
        }
        builder.append("INTO ").append(tablename).append("(");
        int skipColumn = -1;
        for (int i = 0; i < cursor.getColumnCount(); ++i) {
            final String name = cursor.getColumnName(i);
            if (identities && SQLiteAxolotlStore.TRUSTED.equals(name)) {
                skipColumn = i;
                continue;
            }
            if (i != 0) {
                builder.append(',');
            }
            builder.append(name);
        }
        builder.append(") VALUES");
        for (int i = 0; i < max; ++i) {
            if (i != 0) {
                builder.append(',');
            }
            appendValues(cursor, builder, skipColumn);
            if (i < max - 1 && !cursor.moveToNext()) {
                break;
            }
        }
        builder.append(';');
        builder.append('\n');
        return builder.toString();
    }

    private static void appendValues(final Cursor cursor, final StringBuilder builder, final int skipColumn) {
        builder.append("(");
        for (int i = 0; i < cursor.getColumnCount(); ++i) {
            if (i == skipColumn) {
                continue;
            }
            if (i != 0) {
                builder.append(',');
            }
            final String value = cursor.getString(i);
            if (value == null) {
                builder.append("NULL");
            } else if (value.matches("[0-9]+")) {
                builder.append(value);
            } else {
                DatabaseUtils.appendEscapedSQLString(builder, value);
            }
        }
        builder.append(")");
    }

    @Override
    public void onCreate() {
        mDatabaseBackend = DatabaseBackend.getInstance(getBaseContext());
        mAccounts = mDatabaseBackend.getAccounts();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final SharedPreferences ReadableLogs = PreferenceManager.getDefaultSharedPreferences(this);
        ReadableLogsEnabled = ReadableLogs.getBoolean("export_plain_text_logs", getResources().getBoolean(R.bool.plain_text_logs));
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Config.LOGTAG + ": ExportLogsService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running.compareAndSet(false, true)) {
            new Thread(() -> {
                if (intent == null) {
                    return;
                }
                Bundle extras = null;
                if (intent != null && intent.getExtras() != null) {
                    extras = intent.getExtras();
                }
                boolean notify = false;
                if (extras != null && extras.containsKey("NOTIFY_ON_BACKUP_COMPLETE")) {
                    notify = extras.getBoolean("NOTIFY_ON_BACKUP_COMPLETE");
                }
                final boolean success = export();
                stopForeground(true);
                running.set(false);
                if (success) {
                    notifySuccess(notify);
                } else {
                    notifyError();
                }
                WakeLockHelper.release(wakeLock);
                stopSelf();
            }).start();
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void messageExport(SQLiteDatabase db, String uuid, PrintWriter writer, Progress progress) {
        // not select and create column Message.FILE_DELETED to be compareable with conversations
        Cursor cursor = db.rawQuery("select messages." + String.join(", messages.", new String[] {
                Message.UUID, Message.CONVERSATION, Message.TIME_SENT, Message.COUNTERPART, Message.TRUE_COUNTERPART,
                Message.BODY, Message.ENCRYPTION, Message.STATUS, Message.TYPE, Message.RELATIVE_FILE_PATH,
                Message.SERVER_MSG_ID, Message.FINGERPRINT, Message.CARBON, Message.EDITED, Message.READ,
                Message.DELETED, Message.OOB, Message.ERROR_MESSAGE, Message.READ_BY_MARKERS, Message.MARKABLE,
                Message.REMOTE_MSG_ID, Message.CONVERSATION
            }) + " from messages join conversations on conversations.uuid=messages.conversationUuid where conversations.accountUuid=?", new String[]{uuid});

        int size = cursor != null ? cursor.getCount() : 0;
        Log.d(Config.LOGTAG, "exporting " + size + " messages");
        int i = 0;
        int p = 0;
        while (cursor != null && cursor.moveToNext()) {
            writer.write(cursorToString(Message.TABLENAME, cursor, PAGE_SIZE, false));
            if (i + PAGE_SIZE > size) {
                i = size;
            } else {
                i += PAGE_SIZE;
            }
            final int percentage = i * 100 / size;
            if (p < percentage) {
                p = percentage;
                notificationManager.notify(NOTIFICATION_ID, progress.build(p));
            }
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    private boolean export() {
        wakeLock.acquire(15 * 60 * 1000L /*15 minutes*/);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle(getString(R.string.notification_create_backup_title))
                .setSmallIcon(R.drawable.ic_archive_white_24dp)
                .setProgress(1, 0, false);
        startForeground(NOTIFICATION_ID, mBuilder.build());
        try {
            int count = 0;
            final int max = this.mAccounts.size();
            final SecureRandom secureRandom = new SecureRandom();
            if (mAccounts.size() >= 1) {
                if (ReadableLogsEnabled) {
                    List<Conversation> conversations = mDatabaseBackend.getConversations(Conversation.STATUS_AVAILABLE);
                    conversations.addAll(mDatabaseBackend.getConversations(Conversation.STATUS_ARCHIVED));
                    for (Conversation conversation : conversations) {
                        writeToFile(conversation);
                    }
                }
            }
            for (Account account : this.mAccounts) {
                final byte[] IV = new byte[12];
                final byte[] salt = new byte[16];
                secureRandom.nextBytes(IV);
                secureRandom.nextBytes(salt);
                final BackupFileHeader backupFileHeader = new BackupFileHeader(getString(R.string.app_name), account.getJid(), System.currentTimeMillis(), IV, salt);
                final Progress progress = new Progress(mBuilder, max, count);
                final File file = new File(FileBackend.getBackupDirectory() + account.getJid().asBareJid().toEscapedString() + ".ceb");
                if (file.getParentFile().mkdirs()) {
                    Log.d(Config.LOGTAG, "created backup directory " + file.getParentFile().getAbsolutePath());
                }
                final FileOutputStream fileOutputStream = new FileOutputStream(file);
                final DataOutputStream dataOutputStream = new DataOutputStream(fileOutputStream);
                backupFileHeader.write(dataOutputStream);
                dataOutputStream.flush();

                final Cipher cipher = Compatibility.twentyEight() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
                byte[] key = getKey(account.getPassword(), salt);
                Log.d(Config.LOGTAG, backupFileHeader.toString());
                SecretKeySpec keySpec = new SecretKeySpec(key, KEYTYPE);
                IvParameterSpec ivSpec = new IvParameterSpec(IV);
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
                CipherOutputStream cipherOutputStream = new CipherOutputStream(fileOutputStream, cipher);

                GZIPOutputStream gzipOutputStream = new GZIPOutputStream(cipherOutputStream);
                PrintWriter writer = new PrintWriter(gzipOutputStream);
                SQLiteDatabase db = this.mDatabaseBackend.getReadableDatabase();
                final String uuid = account.getUuid();
                accountExport(db, uuid, writer);
                simpleExport(db, Conversation.TABLENAME, Conversation.ACCOUNT, uuid, writer);
                messageExport(db, uuid, writer, progress);
                for (String table : Arrays.asList(SQLiteAxolotlStore.PREKEY_TABLENAME, SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, SQLiteAxolotlStore.SESSION_TABLENAME, SQLiteAxolotlStore.IDENTITIES_TABLENAME)) {
                    simpleExport(db, table, SQLiteAxolotlStore.ACCOUNT, uuid, writer);
                }
                writer.flush();
                writer.close();
                Log.d(Config.LOGTAG, "written backup to " + file.getAbsoluteFile());
                count++;
            }
            return true;
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "unable to create backup ", e);
            return false;
        }
    }

    private void notifySuccess(final boolean notify) {
        if (!notify) {
            return;
        }
        final String path = FileBackend.getBackupDirectory();
        PendingIntent pendingIntent = null;
        for (Intent intent : getPossibleFileOpenIntents(this, path)) {
            if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
                pendingIntent = PendingIntent.getActivity(this, 189, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                break;
            }
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle(getString(R.string.notification_backup_created_title))
                .setContentText(getString(R.string.notification_backup_created_subtitle, path))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_backup_created_subtitle, FileBackend.getBackupDirectory())))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_archive_white_24dp);
        notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private void notifyError() {
        final String path = FileBackend.getBackupDirectory();

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle(getString(R.string.notification_backup_failed_title))
                .setContentText(getString(R.string.notification_backup_failed_subtitle, path))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_backup_failed_subtitle, FileBackend.getBackupDirectory())))
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_warning_white_24dp);
        notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private void writeToFile(Conversation conversation) {
        Jid accountJid = resolveAccountUuid(conversation.getAccountUuid());
        Jid contactJid = conversation.getJid();

        File dir = new File(String.format(DIRECTORY_STRING_FORMAT, accountJid.asBareJid().toString()));
        dir.mkdirs();

        BufferedWriter bw = null;
        try {
            for (Message message : mDatabaseBackend.getMessagesIterable(conversation)) {
                if (message == null)
                    continue;
                if (message.getType() == Message.TYPE_TEXT || message.hasFileOnRemoteHost()) {
                    String date = simpleDateFormat.format(new Date(message.getTimeSent()));
                    if (bw == null) {
                        bw = new BufferedWriter(new FileWriter(
                                new File(dir, contactJid.asBareJid().toString() + ".txt")));
                    }
                    String jid = null;
                    switch (message.getStatus()) {
                        case Message.STATUS_RECEIVED:
                            jid = getMessageCounterpart(message);
                            break;
                        case Message.STATUS_SEND:
                        case Message.STATUS_SEND_RECEIVED:
                        case Message.STATUS_SEND_DISPLAYED:
                            jid = accountJid.asBareJid().toString();
                            break;
                    }
                    if (jid != null) {
                        String body = message.hasFileOnRemoteHost() ? message.getFileParams().url.toString() : message.getBody();
                        bw.write(String.format(MESSAGE_STRING_FORMAT, date, jid,
                                body.replace("\\\n", "\\ \n").replace("\n", "\\ \n")));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private String getMessageCounterpart(Message message) {
        String trueCounterpart = (String) message.getContentValues().get(Message.TRUE_COUNTERPART);
        if (trueCounterpart != null) {
            return trueCounterpart;
        } else {
            return message.getCounterpart().toString();
        }
    }

    private Jid resolveAccountUuid(String accountUuid) {
        for (Account account : mAccounts) {
            if (account.getUuid().equals(accountUuid)) {
                return account.getJid();
            }
        }
        return null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class Progress {
        private final NotificationCompat.Builder builder;
        private final int max;
        private final int count;

        private Progress(NotificationCompat.Builder builder, int max, int count) {
            this.builder = builder;
            this.max = max;
            this.count = count;
        }

        private Notification build(int percentage) {
            builder.setProgress(max * 100, count * 100 + percentage, false);
            return builder.build();
        }
    }
}
