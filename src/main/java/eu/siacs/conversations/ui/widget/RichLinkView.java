package eu.siacs.conversations.ui.widget;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.squareup.picasso.Picasso;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.MetaData;
import eu.siacs.conversations.utils.RichPreview;
import me.drakeet.support.toast.ToastCompat;


/**
 * Created by ponna on 16-01-2018.
 */

public class RichLinkView extends RelativeLayout {

    private View view;
    Context context;
    private MetaData meta;

    LinearLayout linearLayout;
    ImageView imageView;
    TextView textViewTitle;
    TextView textViewDesp;

    private String main_url;

    private boolean isDefaultClick = true;

    private RichPreview.RichLinkListener richLinkListener;

    public RichLinkView(Context context) {
        super(context);
        this.context = context;
    }

    public RichLinkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public RichLinkView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public RichLinkView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.context = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }


    public void initView(final boolean dataSaverDisabled) {
        if (findLinearLayoutChild() != null) {
            this.view = findLinearLayoutChild();
        } else {
            this.view = this;
            inflate(context, R.layout.link_layout, this);
        }
        linearLayout = findViewById(R.id.rich_link_card);
        imageView = findViewById(R.id.rich_link_image);
        textViewTitle = findViewById(R.id.rich_link_title);
        textViewDesp = findViewById(R.id.rich_link_desp);
        imageView.setAdjustViewBounds(true);
        if (meta.getImageurl() != null && !meta.getImageurl().equals("") && !meta.getImageurl().isEmpty()) {
            if (!dataSaverDisabled) {
                Picasso.get()
                        .load(R.drawable.ic_web_grey600_48)
                        .into(imageView);
            } else {
                imageView.setVisibility(VISIBLE);
                Picasso.get()
                        .load(meta.getImageurl())
                        .resize(80, 80)
                        .centerInside()
                        .placeholder(R.drawable.ic_web_grey600_48)
                        .error(R.drawable.ic_web_grey600_48)
                        .into(imageView);
            }
        } else {
            imageView.setVisibility(VISIBLE);
            Picasso.get()
                    .load(R.drawable.ic_web_grey600_48)
                    .into(imageView);
        }
        if (meta.getTitle().isEmpty() || meta.getTitle().equals("")) {
            textViewTitle.setVisibility(VISIBLE);
            textViewTitle.setText(meta.getUrl());
        } else {
            textViewTitle.setVisibility(VISIBLE);
            textViewTitle.setText(meta.getTitle());
        }
        if (meta.getDescription().isEmpty() || meta.getDescription().equals("")) {
            textViewDesp.setVisibility(GONE);
        } else {
            textViewDesp.setVisibility(VISIBLE);
            textViewDesp.setText(meta.getDescription());
        }

        linearLayout.setOnClickListener(view -> {
            if (isDefaultClick) {
                richLinkClicked();
            } else {
                if (richLinkListener != null) {
                    richLinkListener.onClicked(view, meta);
                } else {
                    richLinkClicked();
                }
            }
        });
    }

    private void richLinkClicked() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(main_url));
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            ToastCompat.makeText(context, R.string.no_application_found_to_open_link, Toast.LENGTH_LONG).show();
        }
    }

    public void setDefaultClickListener(boolean isDefault) {
        isDefaultClick = isDefault;
    }

    public void setClickListener(RichPreview.RichLinkListener richLinkListener1) {
        richLinkListener = richLinkListener1;
    }

    protected LinearLayout findLinearLayoutChild() {
        if (getChildCount() > 0 && getChildAt(0) instanceof LinearLayout) {
            return (LinearLayout) getChildAt(0);
        }
        return null;
    }

    public void setLinkFromMeta(MetaData metaData) {
        meta = metaData;
        initView(true);
    }

    public MetaData getMetaData() {
        return meta;
    }

    public void setLink(final String url, final String filename, final boolean dataSaverDisabled, final XmppConnectionService mXmppConnectionService, final RichPreview.ViewListener viewListener) {
        main_url = url;
        RichPreview richPreview = new RichPreview(new RichPreview.ResponseListener() {
            @Override
            public void onData(MetaData metaData) {
                meta = metaData;
                if (!meta.getTitle().isEmpty() || !meta.getTitle().equals("")) {
                    viewListener.onSuccess(true);
                }
                initView(dataSaverDisabled);
            }

            @Override
            public void onError(Exception e) {
                viewListener.onError(e);
            }
        });
        richPreview.getPreview(url, filename, context, mXmppConnectionService);
    }
}
