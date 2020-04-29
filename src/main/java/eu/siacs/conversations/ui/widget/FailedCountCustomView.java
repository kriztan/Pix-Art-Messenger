package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import eu.siacs.conversations.R;

public class FailedCountCustomView extends View {

    private int count;
    private Paint paint, textPaint;
    private int backgroundColor = 0xffd50000;

    public FailedCountCustomView(Context context) {
        super(context);
        init();
    }

    public FailedCountCustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initXMLAttrs(context, attrs);
        init();
    }

    public FailedCountCustomView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initXMLAttrs(context, attrs);
        init();
    }

    private void initXMLAttrs(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.UnreadCountCustomView);
        //setBackgroundColor(a.getColor(a.getIndex(0), ContextCompat.getColor(context, R.color.accent)));
        setBackgroundColor(ContextCompat.getColor(context, R.color.red700));
        a.recycle();
    }

    void init() {
        paint = new Paint();
        paint.setColor(backgroundColor);
        paint.setAntiAlias(true);
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float midx = canvas.getWidth() / 2.0f;
        float midy = canvas.getHeight() / 2.0f;
        float radius = Math.min(canvas.getWidth(), canvas.getHeight()) / 2.0f;
        float textOffset = canvas.getWidth() / 6.0f;
        textPaint.setTextSize(0.95f * radius);
        canvas.drawCircle(midx, midy, radius * 0.94f, paint);
        canvas.drawText(count > 999 ? "\u221E" : String.valueOf(count), midx, midy + textOffset, textPaint);
    }

    public void setFailedCount(int count) {
        this.count = count;
        invalidate();
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }
}
