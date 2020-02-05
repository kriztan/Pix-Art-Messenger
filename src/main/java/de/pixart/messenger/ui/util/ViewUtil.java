package de.pixart.messenger.ui.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.List;

import de.pixart.messenger.Config;
import de.pixart.messenger.R;
import de.pixart.messenger.entities.DownloadableFile;
import de.pixart.messenger.persistance.FileBackend;
import de.pixart.messenger.ui.MediaViewerActivity;
import me.drakeet.support.toast.ToastCompat;

public class ViewUtil {

    public static void view(Context context, Attachment attachment) {
        File file = new File(attachment.getUri().getPath());
        final String mime = attachment.getMime() == null ? "*/*" : attachment.getMime();
        view(context, file, mime);
    }

    public static void view(Context context, DownloadableFile file) {
        if (!file.exists()) {
            ToastCompat.makeText(context, R.string.file_deleted, Toast.LENGTH_SHORT).show();
            return;
        }
        String mime = file.getMimeType();
        if (mime == null) {
            mime = "*/*";
        }
        view(context, file, mime);
    }

    public static void view(Context context, File file, String mime) {
        Uri uri;
        try {
            uri = FileBackend.getUriForFile(context, file);
        } catch (SecurityException e) {
            Log.d(Config.LOGTAG, "No permission to access " + file.getAbsolutePath(), e);
            ToastCompat.makeText(context, context.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();
            return;
        }
        // use internal viewer for images and videos
        if (mime.startsWith("image/")) {
            Intent intent = new Intent(context, MediaViewerActivity.class);
            intent.putExtra("image", Uri.fromFile(file));
            try {
                context.startActivity(intent);
                return;
            } catch (ActivityNotFoundException e) {
                //ignored
            }
        } else if (mime.startsWith("video/")) {
            Intent intent = new Intent(context, MediaViewerActivity.class);
            intent.putExtra("video", Uri.fromFile(file));
            try {
                context.startActivity(intent);
                return;
            } catch (ActivityNotFoundException e) {
                //ignored
            }
        } else {
            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(uri, mime);
            openIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            PackageManager manager = context.getPackageManager();
            List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
            if (info.size() == 0) {
                openIntent.setDataAndType(uri, "*/*");
            }
            try {
                context.startActivity(openIntent);
            } catch (ActivityNotFoundException e) {
                ToastCompat.makeText(context, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
            }
        }
    }
}