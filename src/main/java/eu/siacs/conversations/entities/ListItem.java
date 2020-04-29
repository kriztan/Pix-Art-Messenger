package eu.siacs.conversations.entities;

import android.content.Context;

import java.util.List;

import eu.siacs.conversations.services.AvatarService;
import rocks.xmpp.addr.Jid;

public interface ListItem extends Comparable<ListItem>, AvatarService.Avatarable  {
    String getDisplayName();

    int getOffline();

    Jid getJid();

    Account getAccount();

    List<Tag> getTags(Context context);

    final class Tag {
        private final String name;
        private final int color;
        private final int offline;
        private final Account account;

        public Tag(final String name, final int color, final int offline, final Account account) {
            this.name = name;
            this.color = color;
            this.offline = offline;
            this.account = account;
        }

        public int getColor() {
            return this.color;
        }

        public String getName() {
            return this.name;
        }

        public int getOffline() {
            return this.offline;
        }

        public Account getAccount() {
            return this.account;
        }
    }

    boolean match(Context context, final String needle);
}
