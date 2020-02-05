/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.pixart.messenger.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import de.pixart.messenger.R;
import de.pixart.messenger.ui.SettingsActivity;

public class ThemeHelper {

    public static int find(final Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        final boolean auto = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("auto");
        boolean dark;
        if (auto) {
            dark = nightMode(context);
        } else {
            dark = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("dark");
        }
        final String themeColor = sharedPreferences.getString("theme_color", resources.getString(R.string.theme_color));
        final String fontSize = sharedPreferences.getString("font_size", resources.getString(R.string.default_font_size));
        switch (themeColor) {
            case "blue":
                switch (fontSize) {
                    case "medium":
                        return dark ? R.style.ConversationsTheme_Dark_Medium : R.style.ConversationsTheme_Medium;
                    case "large":
                        return dark ? R.style.ConversationsTheme_Dark_Large : R.style.ConversationsTheme_Large;
                    default:
                        return dark ? R.style.ConversationsTheme_Dark : R.style.ConversationsTheme;
                }
            case "orange":
                switch (fontSize) {
                    case "medium":
                        return dark ? R.style.ConversationsTheme_Orange_Dark_Medium : R.style.ConversationsTheme_Orange_Medium;
                    case "large":
                        return dark ? R.style.ConversationsTheme_Orange_Dark_Large : R.style.ConversationsTheme_Orange_Large;
                    default:
                        return dark ? R.style.ConversationsTheme_Orange_Dark : R.style.ConversationsTheme_Orange;
                }
            default:
                return dark ? R.style.ConversationsTheme_Dark : R.style.ConversationsTheme;
        }
    }

    private static boolean nightMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_YES:
                return true;
            case Configuration.UI_MODE_NIGHT_NO:
                return false;
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
                return false;
            default:
                return false;
        }
    }

    public static int findDialog(Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        final boolean dark = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme)).equals("dark");
        final String fontSize = sharedPreferences.getString("font_size", resources.getString(R.string.default_font_size));
        final String themeColor = sharedPreferences.getString("theme_color", resources.getString(R.string.theme_color));
        switch (themeColor) {
            case "blue":
                switch (fontSize) {
                    case "medium":
                        return dark ? R.style.ConversationsTheme_Dark_Dialog_Medium : R.style.ConversationsTheme_Dialog_Medium;
                    case "large":
                        return dark ? R.style.ConversationsTheme_Dark_Dialog_Large : R.style.ConversationsTheme_Dialog_Large;
                    default:
                        return dark ? R.style.ConversationsTheme_Dark_Dialog : R.style.ConversationsTheme_Dialog;
                }
            case "orange":
                switch (fontSize) {
                    case "medium":
                        return dark ? R.style.ConversationsTheme_Orange_Dark_Dialog_Medium : R.style.ConversationsTheme_Orange_Dialog_Medium;
                    case "large":
                        return dark ? R.style.ConversationsTheme_Orange_Dark_Dialog_Large : R.style.ConversationsTheme_Orange_Dialog_Large;
                    default:
                        return dark ? R.style.ConversationsTheme_Orange_Dark_Dialog : R.style.ConversationsTheme_Orange_Dialog;
                }
            default:
                return dark ? R.style.ConversationsTheme_Dark_Dialog : R.style.ConversationsTheme_Dialog;
        }
    }

    public static boolean isDark(@StyleRes int id) {
        switch (id) {
            case R.style.ConversationsTheme_Dark:
            case R.style.ConversationsTheme_Dark_Large:
            case R.style.ConversationsTheme_Dark_Medium:
            case R.style.ConversationsTheme_Orange_Dark:
            case R.style.ConversationsTheme_Orange_Dark_Large:
            case R.style.ConversationsTheme_Orange_Dark_Medium:
                return true;
            default:
                return false;
        }
    }

    public static void fix(Snackbar snackbar) {
        final Context context = snackbar.getContext();
        TypedArray typedArray = context.obtainStyledAttributes(new int[]{R.attr.TextSizeBody1});
        final float size = typedArray.getDimension(0, 0f);
        typedArray.recycle();
        if (size != 0f) {
            final TextView text = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
            final TextView action = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_action);
            if (text != null && action != null) {
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                action.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                action.setTextColor(ContextCompat.getColor(context, R.color.deep_purple_a100));
            }
        }
    }
}