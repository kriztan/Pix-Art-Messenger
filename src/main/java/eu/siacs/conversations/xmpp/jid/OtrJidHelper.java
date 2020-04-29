package eu.siacs.conversations.xmpp.jid;

import net.java.otr4j.session.SessionID;

import rocks.xmpp.addr.Jid;

public final class OtrJidHelper {

    public static Jid fromSessionID(final SessionID id) throws IllegalArgumentException {
        if (id.getUserID().isEmpty()) {
            return Jid.of(id.getAccountID());
        } else {
            return Jid.of(id.getAccountID() + "/" + id.getUserID());
        }
    }
}
