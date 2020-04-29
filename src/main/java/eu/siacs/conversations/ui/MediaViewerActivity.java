package eu.siacs.conversations.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.leinardi.android.speeddial.SpeedDialActionItem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMediaViewerBinding;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.ExifHelper;
import eu.siacs.conversations.utils.MimeUtils;
import me.drakeet.support.toast.ToastCompat;

import static eu.siacs.conversations.persistance.FileBackend.close;

public class MediaViewerActivity extends XmppActivity implements AudioManager.OnAudioFocusChangeListener {

    Integer oldOrientation;
    SimpleExoPlayer player;
    Uri mFileUri;
    File mFile;
    int height = 0;
    int width = 0;
    int rotation = 0;
    boolean isImage = false;
    boolean isVideo = false;
    private ActivityMediaViewerBinding binding;
    private GestureDetector gestureDetector;

    public static String getMimeType(String path) {
        try {
            String type = null;
            String extension = path.substring(path.lastIndexOf(".") + 1, path.length());
            if (extension != null) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            return type;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_media_viewer);
        this.mTheme = findTheme();
        setTheme(this.mTheme);
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                showFab();
                return super.onDown(e);
            }
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null && actionBar.isShowing()) {
            actionBar.hide();
        }

        oldOrientation = getRequestedOrientation();

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        if (useMaxBrightness()) {
            layout.screenBrightness = 1;
        }
        getWindow().setAttributes(layout);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //binding.speedDial.inflate(R.menu.media_viewer);
    }

    private void share() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(getMimeType(mFile.toString()));
        share.putExtra(Intent.EXTRA_STREAM, FileBackend.getUriForFile(this, mFile));
        try {
            startActivity(Intent.createChooser(share, getText(R.string.share_with)));
        } catch (ActivityNotFoundException e) {
            //This should happen only on faulty androids because normally chooser is always available
            ToastCompat.makeText(this, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setTitle(R.string.delete_file_dialog);
        builder.setMessage(R.string.delete_file_dialog_msg);
        builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
            if (this.xmppConnectionService.getFileBackend().deleteFile(mFile)) {
                finish();
            }
        });
        builder.create().show();
    }

    private void open() {
        Uri uri;
        try {
            uri = FileBackend.getUriForFile(this, mFile);
        } catch (SecurityException e) {
            Log.d(Config.LOGTAG, "No permission to access " + mFile.getAbsolutePath(), e);
            ToastCompat.makeText(this, this.getString(R.string.no_permission_to_access_x, mFile.getAbsolutePath()), Toast.LENGTH_SHORT).show();
            return;
        }
        String mime = MimeUtils.guessMimeTypeFromUri(this, uri);
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(uri, mime);
        openIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PackageManager manager = this.getPackageManager();
        List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
        if (info.size() == 0) {
            openIntent.setDataAndType(uri, "*/*");
        }
        try {
            this.startActivity(openIntent);
            finish();
        } catch (ActivityNotFoundException e) {
            ToastCompat.makeText(this, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra("image")) {
                mFileUri = intent.getParcelableExtra("image");
                mFile = new File(mFileUri.getPath());
                if (mFileUri != null && mFile.exists() && mFile.length() > 0) {
                    try {
                        isImage = true;
                        DisplayImage(mFile, mFileUri);
                    } catch (Exception e) {
                        isImage = false;
                        Log.d(Config.LOGTAG, "Illegal exeption :" + e);
                        ToastCompat.makeText(MediaViewerActivity.this, getString(R.string.error_file_corrupt), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    ToastCompat.makeText(MediaViewerActivity.this, getString(R.string.file_deleted), Toast.LENGTH_SHORT).show();
                }
            } else if (intent.hasExtra("video")) {
                mFileUri = intent.getParcelableExtra("video");
                mFile = new File(mFileUri.getPath());
                if (mFileUri != null && mFile.exists() && mFile.length() > 0) {
                    try {
                        isVideo = true;
                        DisplayVideo(mFileUri);
                    } catch (Exception e) {
                        isVideo = false;
                        Log.d(Config.LOGTAG, "Illegal exeption :" + e);
                        ToastCompat.makeText(MediaViewerActivity.this, getString(R.string.error_file_corrupt), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    ToastCompat.makeText(MediaViewerActivity.this, getString(R.string.file_deleted), Toast.LENGTH_SHORT).show();
                }
            }
        }
        if (isDeletableFile(mFile)) {
            binding.speedDial.addActionItem(new SpeedDialActionItem.Builder(R.id.action_delete, R.drawable.ic_delete_white_24dp)
                    .setLabel(R.string.action_delete)
                    .create()
            );
        }
        binding.speedDial.addActionItem(new SpeedDialActionItem.Builder(R.id.action_open, R.drawable.ic_open_in_new_white_24dp)
                .setLabel(R.string.open_with)
                .create()
        );
        binding.speedDial.addActionItem(new SpeedDialActionItem.Builder(R.id.action_share, R.drawable.ic_share_white_24dp)
                .setLabel(R.string.share)
                .create()
        );

        if (isDeletableFile(mFile)) {
            binding.speedDial.setOnActionSelectedListener(actionItem -> {
                switch (actionItem.getId()) {
                    case R.id.action_share:
                        share();
                        break;
                    case R.id.action_open:
                        open();
                        break;
                    case R.id.action_delete:
                        deleteFile();
                        break;
                    default:
                        return false;
                }
                return false;
            });
        } else {
            binding.speedDial.setOnActionSelectedListener(actionItem -> {
                switch (actionItem.getId()) {
                    case R.id.action_share:
                        share();
                        break;
                    case R.id.action_open:
                        open();
                        break;
                    default:
                        return false;
                }
                return false;
            });
        }
        showFab();
    }

    private void DisplayImage(final File file, final Uri uri) {
        final boolean gif = "image/gif".equalsIgnoreCase(getMimeType(file.toString()));
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(new File(file.getPath()).getAbsolutePath(), options);
        height = options.outHeight;
        width = options.outWidth;
        rotation = getRotation(Uri.parse("file://" + file.getAbsolutePath()));
        Log.d(Config.LOGTAG, "Image height: " + height + ", width: " + width + ", rotation: " + rotation);
        if (useAutoRotateScreen()) {
            rotateScreen(width, height, rotation);
        }
        try {
            if (gif) {
                binding.messageGifView.setVisibility(View.VISIBLE);
                binding.messageGifView.setImageURI(uri);
                binding.messageGifView.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
            } else {
                binding.messageImageView.setVisibility(View.VISIBLE);
                binding.messageImageView.setImage(ImageSource.uri(uri));
                binding.messageImageView.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
            }
        } catch (Exception e) {
            ToastCompat.makeText(this, getString(R.string.error_file_corrupt), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void DisplayVideo(final Uri uri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(uri.getPath());
            Bitmap bitmap = null;
            try {
                bitmap = retriever.getFrameAtTime(0);
                height = bitmap.getHeight();
                width = bitmap.getWidth();
            } catch (Exception e) {
                height = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                width = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
            try {
                rotation = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            } catch (Exception e) {
                rotation = 0;
            }
            Log.d(Config.LOGTAG, "Video height: " + height + ", width: " + width + ", rotation: " + rotation);
            if (useAutoRotateScreen()) {
                rotateScreen(width, height, rotation);
            }
            binding.messageVideoView.setVisibility(View.VISIBLE);
            player = new SimpleExoPlayer.Builder(this).build();
            player.setPlayWhenReady(true);
            player.addListener(new SimpleExoPlayer.EventListener() {
                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    open();
                }
            });
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
            binding.messageVideoView.setPlayer(player);
            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, BuildConfig.APPLICATION_ID));
            MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            player.prepare(videoSource);
            requestAudioFocus();
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            binding.messageVideoView.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
        } catch (Exception e) {
            e.printStackTrace();
            open();
        }
    }

    private void releaseAudiFocus() {
        AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.abandonAudioFocus(this);
        }
    }

    private void requestAudioFocus() {
        AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.requestAudioFocus(this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private int getRotation(Uri image) {
        InputStream is = null;
        try {
            is = this.getContentResolver().openInputStream(image);
            return ExifHelper.getOrientation(is);
        } catch (FileNotFoundException e) {
            return 0;
        } finally {
            close(is);
        }
    }

    private void rotateScreen(final int width, final int height, final int rotation) {
        if (width > height) {
            if (rotation == 0 || rotation == 180) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        } else if (width <= height) {
            if (rotation == 90 || rotation == 270) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        }
    }

    private void pausePlayer() {
        if (player != null && isVideo && isPlaying()) {
            player.setPlayWhenReady(false);
            player.getPlaybackState();
        }
    }

    private void startPlayer() {
        if (player != null && isVideo && !isPlaying()) {
            player.setPlayWhenReady(true);
            player.getPlaybackState();
        }
    }

    private void stopPlayer() {
        if (player != null && isVideo) {
            if (isPlaying()) {
                player.stop(true);
            }
            player.release();
        }
    }

    private boolean isPlaying() {
        return player != null
                && player.getPlaybackState() != Player.STATE_ENDED
                && player.getPlaybackState() != Player.STATE_IDLE
                && player.getPlayWhenReady();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onResume() {
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        if (useMaxBrightness()) {
            layout.screenBrightness = 1;
        }
        getWindow().setAttributes(layout);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startPlayer();
        super.onResume();
    }

    @Override
    public void onPause() {
        pausePlayer();
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        if (useMaxBrightness()) {
            layout.screenBrightness = -1;
        }
        getWindow().setAttributes(layout);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(oldOrientation);
        super.onPause();
    }

    @Override
    public void onStop() {
        stopPlayer();
        releaseAudiFocus();
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        if (useMaxBrightness()) {
            layout.screenBrightness = -1;
        }
        getWindow().setAttributes(layout);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(oldOrientation);
        super.onStop();
    }

    @Override
    void onBackendConnected() {

    }

    public boolean useMaxBrightness() {
        return getPreferences().getBoolean("use_max_brightness", getResources().getBoolean(R.bool.use_max_brightness));
    }

    public boolean useAutoRotateScreen() {
        return getPreferences().getBoolean("use_auto_rotate", getResources().getBoolean(R.bool.auto_rotate));
    }

    public SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    private void showFab() {
        binding.speedDial.show();
        hideFab();
    }

    private void hideFab() {
        new Handler().postDelayed(() -> {
            if (binding.speedDial.isOpen()) {
                hideFab();
            } else {
                binding.speedDial.hide();
            }
        }, 3000);
    }


    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.i(Config.LOGTAG, "Audio focus granted.");
        } else if (focusChange == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.i(Config.LOGTAG, "Audio focus failed.");
        }
    }

    private boolean isDeletableFile(File file) {
        return (file == null || !file.toString().startsWith("/") || file.toString().contains(FileBackend.getConversationsDirectory("null")));
    }
}