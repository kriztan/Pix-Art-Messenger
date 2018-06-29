package de.pixart.messenger.ui.adapter;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.wefika.flowlayout.FlowLayout;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import de.pixart.messenger.R;
import de.pixart.messenger.databinding.ContactBinding;
import de.pixart.messenger.entities.ListItem;
import de.pixart.messenger.ui.SettingsActivity;
import de.pixart.messenger.ui.XmppActivity;
import de.pixart.messenger.ui.util.Color;
import de.pixart.messenger.utils.IrregularUnicodeDetector;
import de.pixart.messenger.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class ListItemAdapter extends ArrayAdapter<ListItem> {

    private static final float INACTIVE_ALPHA = 0.4684f;
    private static final float ACTIVE_ALPHA = 1.0f;
    protected XmppActivity activity;
    protected boolean showDynamicTags = false;
    private OnTagClickedListener mOnTagClickedListener = null;
    protected int color = 0;
    protected boolean offline = false;
    private View.OnClickListener onTagTvClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (view instanceof TextView && mOnTagClickedListener != null) {
                TextView tv = (TextView) view;
                final String tag = tv.getText().toString();
                mOnTagClickedListener.onTagClicked(tag);
            }
        }
    };

    public ListItemAdapter(XmppActivity activity, List<ListItem> objects) {
        super(activity, 0, objects);
        this.activity = activity;
    }

    public void refreshSettings() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.showDynamicTags = preferences.getBoolean(SettingsActivity.SHOW_DYNAMIC_TAGS, false);
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = activity.getLayoutInflater();
        ListItem item = getItem(position);
        ViewHolder viewHolder;
        if (view == null) {
            ContactBinding binding = DataBindingUtil.inflate(inflater, R.layout.contact, parent, false);
            viewHolder = ViewHolder.get(binding);
            view = binding.getRoot();
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        List<ListItem.Tag> tags = item.getTags(activity);

        if (tags.size() == 0 || !this.showDynamicTags) {
            viewHolder.tags.setVisibility(View.GONE);
        } else {
            viewHolder.tags.setVisibility(View.VISIBLE);
            viewHolder.tags.removeAllViewsInLayout();
            for (ListItem.Tag tag : tags) {
                TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag, viewHolder.tags, false);
                tv.setText(tag.getName());
                tv.setBackgroundColor(tag.getColor());
                tv.setOnClickListener(this.onTagTvClick);
                viewHolder.tags.addView(tv);
            }
        }
        final Jid jid = item.getJid();
        if (jid != null) {
            viewHolder.jid.setVisibility(View.VISIBLE);
            viewHolder.jid.setText(IrregularUnicodeDetector.style(activity, jid));
        } else {
            viewHolder.jid.setVisibility(View.GONE);
        }
        viewHolder.name.setText(item.getDisplayName());
        if (tags.size() != 0) {
            for (ListItem.Tag tag : tags) {
                offline = tag.getOffline() == 1;
                color = tag.getColor();
            }
        }
        if (offline) {
            viewHolder.name.setTextColor(Color.get(activity, R.attr.text_Color_Main));
            viewHolder.name.setAlpha(INACTIVE_ALPHA);
            viewHolder.jid.setAlpha(INACTIVE_ALPHA);
            viewHolder.avatar.setAlpha(INACTIVE_ALPHA);
            viewHolder.tags.setAlpha(INACTIVE_ALPHA);
        } else {
            if (ShowPresenceColoredNames()) {
                viewHolder.name.setTextColor(color != 0 ? color : Color.get(activity, R.attr.text_Color_Main));
            } else {
                viewHolder.name.setTextColor(Color.get(activity, R.attr.text_Color_Main));
            }
            viewHolder.name.setAlpha(ACTIVE_ALPHA);
            viewHolder.jid.setAlpha(ACTIVE_ALPHA);
            viewHolder.avatar.setAlpha(ACTIVE_ALPHA);
            viewHolder.tags.setAlpha(ACTIVE_ALPHA);
        }
        loadAvatar(item, viewHolder.avatar);
        return view;
    }

    public void setOnTagClickedListener(OnTagClickedListener listener) {
        this.mOnTagClickedListener = listener;
    }

    public interface OnTagClickedListener {
        void onTagClicked(String tag);
    }

    class BitmapWorkerTask extends AsyncTask<ListItem, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private ListItem item = null;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(ListItem... params) {
            this.item = params[0];
            return activity.avatarService().get(this.item, activity.getPixel(56), isCancelled());
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && !isCancelled()) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(0x00000000);
                }
            }
        }
    }

    public void loadAvatar(ListItem item, ImageView imageView) {
        if (cancelPotentialWork(item, imageView)) {
            final Bitmap bm = activity.avatarService().get(item, activity.getPixel(48), true);
            if (bm != null) {
                cancelPotentialWork(item, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                String seed = item.getJid() != null ? item.getJid().asBareJid().toString() : item.getDisplayName();
                imageView.setBackgroundColor(UIHelper.getColorForName(seed));
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.executeOnExecutor(BitmapWorkerTask.THREAD_POOL_EXECUTOR, item);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    public static boolean cancelPotentialWork(ListItem item, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final ListItem oldItem = bitmapWorkerTask.item;
            if (oldItem == null || item != oldItem) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    private static class ViewHolder {
        private TextView name;
        private TextView jid;
        private ImageView avatar;
        private FlowLayout tags;

        private ViewHolder() {
        }

        public static ViewHolder get(ContactBinding binding) {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.name = binding.contactDisplayName;
            viewHolder.jid = binding.contactJid;
            viewHolder.avatar = binding.contactPhoto;
            viewHolder.tags = binding.tags;
            binding.getRoot().setTag(viewHolder);
            return viewHolder;
        }
    }


    public boolean ShowPresenceColoredNames() {
        return getPreferences().getBoolean("presence_colored_names", activity.getResources().getBoolean(R.bool.presence_colored_names));
    }

    protected SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
    }
}
