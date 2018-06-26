package de.pixart.messenger.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.pixart.messenger.Config;
import de.pixart.messenger.entities.Contact;
import de.pixart.messenger.entities.Conversation;
import de.pixart.messenger.entities.Conversational;
import de.pixart.messenger.entities.Message;
import de.pixart.messenger.ui.ShowLocationActivity;

public class GeoHelper {

    public static Pattern GEO_URI = Pattern.compile("geo:([\\-0-9.]+),([\\-0-9.]+)(?:,([\\-0-9.]+))?(?:\\?(.*))?", Pattern.CASE_INSENSITIVE);

    public static String MapPreviewUri(Message message) {
        Matcher matcher = GEO_URI.matcher(message.getBody());
        if (!matcher.matches()) {
            return null;
        }
        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(matcher.group(1));
            if (latitude > 90.0 || latitude < -90.0) {
                return null;
            }
            longitude = Double.parseDouble(matcher.group(2));
            if (longitude > 180.0 || longitude < -180.0) {
                return null;
            }
        } catch (NumberFormatException nfe) {
            return null;
        }
        return "https://xmpp.pix-art.de/staticmap/staticmap.php?center=" + latitude + "," + longitude + "&size=500x500&markers=" + latitude + "," + longitude + "&zoom= " + Config.DEFAULT_ZOOM;
    }

    public static ArrayList<Intent> createGeoIntentsFromMessage(Message message, Context context) {
        final ArrayList<Intent> intents = new ArrayList<>();
        Matcher matcher = GEO_URI.matcher(message.getBody());
        if (!matcher.matches()) {
            return intents;
        }
        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(matcher.group(1));
            if (latitude > 90.0 || latitude < -90.0) {
                return intents;
            }
            longitude = Double.parseDouble(matcher.group(2));
            if (longitude > 180.0 || longitude < -180.0) {
                return intents;
            }
        } catch (NumberFormatException nfe) {
            return intents;
        }
        final Conversational conversation = message.getConversation();
        String label;
        if (conversation instanceof Conversation && conversation.getMode() == Conversation.MODE_SINGLE && message.getStatus() == Message.STATUS_RECEIVED) {
            try {
                label = "(" + URLEncoder.encode(((Conversation) conversation).getName().toString(), "UTF-8") + ")";
            } catch (UnsupportedEncodingException e) {
                label = "";
            }
        } else {
            label = "";
        }

        Intent locationPluginIntent = new Intent(context, ShowLocationActivity.class);
        locationPluginIntent.putExtra("latitude", latitude);
        locationPluginIntent.putExtra("longitude", longitude);
        if (message.getStatus() != Message.STATUS_RECEIVED) {
            locationPluginIntent.putExtra("jid", conversation.getAccount().getJid().toString());
            locationPluginIntent.putExtra("name", conversation.getAccount().getJid().getLocal());
        } else {
            Contact contact = message.getContact();
            if (contact != null) {
                locationPluginIntent.putExtra("name", contact.getDisplayName());
                locationPluginIntent.putExtra("jid", contact.getJid().toString());
            } else {
                locationPluginIntent.putExtra("name", UIHelper.getDisplayedMucCounterpart(message.getCounterpart()));
            }
        }
        intents.add(locationPluginIntent);

        Intent geoIntent = new Intent(Intent.ACTION_VIEW);
        geoIntent.setData(Uri.parse("geo:" + String.valueOf(latitude) + "," + String.valueOf(longitude) + "?q=" + String.valueOf(latitude) + "," + String.valueOf(longitude) + label));
        intents.add(geoIntent);
        return intents;
    }
}