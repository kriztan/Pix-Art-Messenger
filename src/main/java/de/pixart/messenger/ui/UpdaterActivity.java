package de.pixart.messenger.ui;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import de.pixart.messenger.Config;
import de.pixart.messenger.R;
import de.pixart.messenger.persistance.FileBackend;
import de.pixart.messenger.services.XmppConnectionService;
import de.pixart.messenger.utils.WakeLockHelper;
import me.drakeet.support.toast.ToastCompat;

import static de.pixart.messenger.http.HttpConnectionManager.getProxy;
import static de.pixart.messenger.services.XmppConnectionService.FDroid;
import static de.pixart.messenger.services.XmppConnectionService.PlayStore;

public class UpdaterActivity extends XmppActivity {
    static final private String FileName = "update.apk";
    String appURI = "";
    String changelog = "";
    Integer filesize = 0;
    String store;
    ProgressDialog mProgressDialog;
    DownloadTask downloadTask;
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //set activity
        setContentView(R.layout.activity_updater);
        this.mTheme = findTheme();
        setTheme(this.mTheme);

        textView = findViewById(R.id.updater);

        mProgressDialog = new ProgressDialog(UpdaterActivity.this) {
            //show warning on back pressed
            @Override
            public void onBackPressed() {
                showCancelDialog();
            }
        };
        mProgressDialog.setMessage(getString(R.string.download_started));
        mProgressDialog.setProgressNumberFormat(null);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setCanceledOnTouchOutside(false);
    }

    @Override
    protected void refreshUiReal() {
        //ignored
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.mTheme = findTheme();
        setTheme(this.mTheme);
        setTitle(getString(R.string.update_service));
        textView.setText(R.string.update_info);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
        if (getIntent() != null && getIntent().getStringExtra("update").equals("PixArtMessenger_UpdateService")) {
            try {
                appURI = getIntent().getStringExtra("url");
            } catch (Exception e) {
                ToastCompat.makeText(getApplicationContext(), getText(R.string.failed), Toast.LENGTH_LONG).show();
                UpdaterActivity.this.finish();
            }
            try {
                changelog = getIntent().getStringExtra("changelog");
            } catch (Exception e) {
                ToastCompat.makeText(getApplicationContext(), getText(R.string.failed), Toast.LENGTH_LONG).show();
                UpdaterActivity.this.finish();
            }
            try {
                store = getIntent().getStringExtra("store");
            } catch (Exception e) {
                store = null;
            }
            //delete old downloaded localVersion files
            File dir = new File(FileBackend.getAppUpdateDirectory());
            if (dir.isDirectory()) {
                String[] children = dir.list();
                for (String aChildren : children) {
                    Log.d(Config.LOGTAG, "AppUpdater: delete old update files " + aChildren + " in " + dir);
                    new File(dir, aChildren).delete();
                }
            }

            //oh yeah we do need an upgrade, let the user know send an alert message
            AlertDialog.Builder builder = new AlertDialog.Builder(UpdaterActivity.this);
            builder.setCancelable(false);
            //open link to changelog
            //if the user agrees to upgrade
            builder.setMessage(getString(R.string.install_update))
                    .setPositiveButton(R.string.update, (dialog, id) -> {
                        Log.d(Config.LOGTAG, "AppUpdater: downloading " + FileName + " from " + appURI);
                        //ask for permissions on devices >= SDK 23
                        if (isStoragePermissionGranted() && isNetworkAvailable(getApplicationContext())) {
                            //start downloading the file using the download manager
                            if (store != null && store.equalsIgnoreCase(PlayStore)) {
                                Uri uri = Uri.parse("market://details?id=de.pixart.messenger");
                                Intent marketIntent = new Intent(Intent.ACTION_VIEW, uri);
                                PackageManager manager = getApplicationContext().getPackageManager();
                                List<ResolveInfo> infos = manager.queryIntentActivities(marketIntent, 0);
                                if (infos.size() > 0) {
                                    startActivity(marketIntent);
                                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                                } else {
                                    uri = Uri.parse("https://jabber.pix-art.de/");
                                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                                    startActivity(browserIntent);
                                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                                }
                            } else if (store != null && store.equalsIgnoreCase(FDroid)) {
                                Uri uri = Uri.parse("https://f-droid.org/de/packages/de.pixart.messenger/");
                                Intent marketIntent = new Intent(Intent.ACTION_VIEW, uri);
                                PackageManager manager = getApplicationContext().getPackageManager();
                                List<ResolveInfo> infos = manager.queryIntentActivities(marketIntent, 0);
                                if (infos.size() > 0) {
                                    startActivity(marketIntent);
                                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                                } else {
                                    uri = Uri.parse("https://f-droid.org/de/packages/de.pixart.messenger/");
                                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                                    startActivity(browserIntent);
                                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                                }
                            } else {
                                ToastCompat.makeText(getApplicationContext(), getText(R.string.download_started), Toast.LENGTH_LONG).show();
                                downloadTask = new DownloadTask(UpdaterActivity.this);
                                downloadTask.execute(appURI);
                            }
                        } else {
                            Log.d(Config.LOGTAG, "AppUpdater: failed - has storage permissions " + isStoragePermissionGranted() + " and internet " + isNetworkAvailable(getApplicationContext()));
                        }
                    })
                    .setNeutralButton(R.string.changelog, (dialog, id) -> {
                        Uri uri = Uri.parse(Config.CHANGELOG_URL); // missing 'http://' will cause crash
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            //restart updater to show dialog again after coming back after opening changelog
                            recreate();
                        }
                    })
                    .setNegativeButton(R.string.remind_later, (dialog, id) -> {
                        // User cancelled the dialog
                        UpdaterActivity.this.finish();
                    });
            //show the alert message
            builder.create().show();
        } else {
            ToastCompat.makeText(getApplicationContext(), getText(R.string.failed), Toast.LENGTH_LONG).show();
            UpdaterActivity.this.finish();
        }
    }

    @Override
    void onBackendConnected() {
        //ignored
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    //check for internet connection
    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null) {
                for (NetworkInfo anInfo : info) {
                    if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            return true;
        }
    }

    //show warning on back pressed
    @Override
    public void onBackPressed() {
        showCancelDialog();
    }

    private void showCancelDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.cancel_update)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, (dialog, id) -> {
                    if (downloadTask != null && !downloadTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
                        downloadTask.cancel(true);
                    }
                    if (mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                    }
                    UpdaterActivity.this.finish();
                })
                .setNegativeButton(R.string.no, (dialog, id) -> dialog.cancel());
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (downloadTask != null && !downloadTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
            downloadTask.cancel(true);
        }
        UpdaterActivity.this.finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (downloadTask != null && !downloadTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
            downloadTask.cancel(true);
        }
        UpdaterActivity.this.finish();
    }

    private class DownloadTask extends AsyncTask<String, Integer, String> {

        File dir = new File(FileBackend.getAppUpdateDirectory());
        File file = new File(dir, FileName);
        XmppConnectionService xmppConnectionService;
        private Context context;
        private PowerManager.WakeLock mWakeLock;
        private long startTime = 0;
        private boolean mUseTor;

        DownloadTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            startTime = System.currentTimeMillis();
            // take CPU lock to prevent CPU from going off if the user
            // presses the power button during download
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, getClass().getName());
                mWakeLock.acquire();
                mUseTor = xmppConnectionService != null && xmppConnectionService.useTorToConnect();
            }
            mProgressDialog.show();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setProgress(progress[0]);
        }

        @Override
        protected String doInBackground(String... sUrl) {
            InputStream is = null;
            OutputStream os = null;
            HttpURLConnection connection = null;
            try {
                Log.d(Config.LOGTAG, "AppUpdater: save file to " + file.toString());
                Log.d(Config.LOGTAG, "AppUpdater: download update from url: " + sUrl[0] + " to file name: " + file.toString());

                URL url = new URL(sUrl[0]);

                if (mUseTor) {
                    connection = (HttpURLConnection) url.openConnection(getProxy());
                } else {
                    connection = (HttpURLConnection) url.openConnection();
                }
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    ToastCompat.makeText(getApplicationContext(), getText(R.string.failed), Toast.LENGTH_LONG).show();
                    return connection.getResponseCode() + ": " + connection.getResponseMessage();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();

                // create folders
                File parentDirectory = file.getParentFile();
                if (parentDirectory.mkdirs()) {
                    Log.d(Config.LOGTAG, "created " + parentDirectory.getAbsolutePath());
                }
                
                // download the file
                is = connection.getInputStream();
                os = new FileOutputStream(file);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = is.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        is.close();
                        return "canceled";
                    }
                    total += count;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    os.write(data, 0, count);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return e.toString();
            } finally {
                try {
                    if (os != null)
                        os.close();
                    if (is != null)
                        is.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            WakeLockHelper.release(mWakeLock);
            mProgressDialog.dismiss();
            if (result != null) {
                ToastCompat.makeText(getApplicationContext(), getString(R.string.failed), Toast.LENGTH_LONG).show();
                Log.d(Config.LOGTAG, "AppUpdater: failed with " + result);
                UpdaterActivity.this.finish();
            } else {
                Log.d(Config.LOGTAG, "AppUpdater: download ready in " + ((System.currentTimeMillis() - startTime) / 1000) + " sec");

                //start the installation of the latest localVersion
                Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                installIntent.setDataAndType(FileBackend.getUriForFile(UpdaterActivity.this, file), "application/vnd.android.package-archive");
                installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                installIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(installIntent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                UpdaterActivity.this.finish();
            }
        }
    }
}
