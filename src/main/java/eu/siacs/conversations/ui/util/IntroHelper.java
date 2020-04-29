package eu.siacs.conversations.ui.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.ui.IntroActivity;

import static eu.siacs.conversations.ui.IntroActivity.ACTIVITY;
import static eu.siacs.conversations.ui.IntroActivity.MULTICHAT;

public class IntroHelper {
    public static void showIntro(Activity activity, boolean mode_multi) {
        Thread t = new Thread(() -> {
            SharedPreferences getPrefs = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
            String activityname = activity.getClass().getSimpleName();
            String INTRO = "intro_shown_on_activity_" + activityname + "_MultiMode_" + mode_multi;
            boolean SHOW_INTRO = getPrefs.getBoolean(INTRO, true);

            if (SHOW_INTRO && Config.SHOW_INTRO) {
                final Intent i = new Intent(activity, IntroActivity.class);
                i.putExtra(ACTIVITY, activityname);
                i.putExtra(MULTICHAT, mode_multi);
                activity.runOnUiThread(() -> activity.startActivity(i));
            }
        });
        t.start();
    }

    public static void SaveIntroShown(Context context, String activity, boolean mode_multi) {
        SharedPreferences getPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String INTRO = "intro_shown_on_activity_" + activity + "_MultiMode_" + mode_multi;
        SharedPreferences.Editor e = getPrefs.edit();
        e.putBoolean(INTRO, false);
        e.apply();
    }
}
