package eu.siacs.conversations.services;

import android.content.Context;
import android.os.Build;

import androidx.emoji.bundled.BundledEmojiCompatConfig;
import androidx.emoji.text.EmojiCompat;

public class EmojiService {

    private final Context context;

    public EmojiService(Context context) {
        this.context = context;
    }

    public void init(boolean useBundledEmoji) {
        BundledEmojiCompatConfig config = new BundledEmojiCompatConfig(context);
        //On recent Androids we assume to have the latest emojis
        //there are some annoying bugs with emoji compat that make it a safer choice not to use it when possible
        // a) the text preview has annoying glitches when the cut of text contains emojis (the emoji will be half visible)
        // b) can trigger a hardware rendering bug https://issuetracker.google.com/issues/67102093
        config.setReplaceAll(useBundledEmoji && Build.VERSION.SDK_INT < Build.VERSION_CODES.O);
        EmojiCompat.init(config);
    }
}