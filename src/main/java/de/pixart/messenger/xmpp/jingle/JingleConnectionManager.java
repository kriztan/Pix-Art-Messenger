package de.pixart.messenger.xmpp.jingle;

import android.annotation.SuppressLint;
import android.util.Log;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import de.pixart.messenger.Config;
import de.pixart.messenger.entities.Account;
import de.pixart.messenger.entities.Message;
import de.pixart.messenger.entities.Transferable;
import de.pixart.messenger.services.AbstractConnectionManager;
import de.pixart.messenger.services.XmppConnectionService;
import de.pixart.messenger.utils.Namespace;
import de.pixart.messenger.xml.Element;
import de.pixart.messenger.xmpp.OnIqPacketReceived;
import de.pixart.messenger.xmpp.jingle.stanzas.JinglePacket;
import de.pixart.messenger.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.Jid;

public class JingleConnectionManager extends AbstractConnectionManager {
    private List<JingleConnection> connections = new CopyOnWriteArrayList<>();

    private HashMap<Jid, JingleCandidate> primaryCandidates = new HashMap<>();

    @SuppressLint("TrulyRandom")
    private SecureRandom random = new SecureRandom();

    public JingleConnectionManager(XmppConnectionService service) {
        super(service);
    }

    public void deliverPacket(Account account, JinglePacket packet) {
        if (packet.isAction("session-initiate")) {
            JingleConnection connection = new JingleConnection(this);
            connection.init(account, packet);
            connections.add(connection);
        } else {
            for (JingleConnection connection : connections) {
                if (connection.getAccount() == account
                        && connection.getSessionId().equals(
                        packet.getSessionId())
                        && connection.getCounterPart().equals(packet.getFrom())) {
                    connection.deliverPacket(packet);
                    return;
                }
            }
            Log.d(Config.LOGTAG, "unable to route jingle packet: " + packet);
            IqPacket response = packet.generateResponse(IqPacket.TYPE.ERROR);
            Element error = response.addChild("error");
            error.setAttribute("type", "cancel");
            error.addChild("item-not-found",
                    "urn:ietf:params:xml:ns:xmpp-stanzas");
            error.addChild("unknown-session", "urn:xmpp:jingle:errors:1");
            account.getXmppConnection().sendIqPacket(response, null);
        }
    }

    public JingleConnection createNewConnection(Message message) {
        Transferable old = message.getTransferable();
        if (old != null) {
            old.cancel();
        }
        JingleConnection connection = new JingleConnection(this);
        mXmppConnectionService.markMessage(message, Message.STATUS_WAITING);
        connection.init(message);
        this.connections.add(connection);
        return connection;
    }

    public JingleConnection createNewConnection(final JinglePacket packet) {
        JingleConnection connection = new JingleConnection(this);
        this.connections.add(connection);
        return connection;
    }

    public void finishConnection(JingleConnection connection) {
        this.connections.remove(connection);
    }

    public void getPrimaryCandidate(final Account account, final boolean initiator, final OnPrimaryCandidateFound listener) {
        if (Config.DISABLE_PROXY_LOOKUP) {
            listener.onPrimaryCandidateFound(false, null);
            return;
        }
        if (!this.primaryCandidates.containsKey(account.getJid().asBareJid())) {
            final Jid proxy = account.getXmppConnection().findDiscoItemByFeature(Namespace.BYTE_STREAMS);
            if (proxy != null) {
                IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
                iq.setTo(proxy);
                iq.query(Namespace.BYTE_STREAMS);
                account.getXmppConnection().sendIqPacket(iq, new OnIqPacketReceived() {

                    @Override
                    public void onIqPacketReceived(Account account, IqPacket packet) {
                        Element streamhost = packet.query().findChild("streamhost", Namespace.BYTE_STREAMS);
                        final String host = streamhost == null ? null : streamhost.getAttribute("host");
                        final String port = streamhost == null ? null : streamhost.getAttribute("port");
                        if (host != null && port != null) {
                            try {
                                JingleCandidate candidate = new JingleCandidate(nextRandomId(), true);
                                candidate.setHost(host);
                                candidate.setPort(Integer.parseInt(port));
                                candidate.setType(JingleCandidate.TYPE_PROXY);
                                candidate.setJid(proxy);
                                candidate.setPriority(655360 + (initiator ? 30 : 0));
                                primaryCandidates.put(account.getJid().asBareJid(), candidate);
                                listener.onPrimaryCandidateFound(true, candidate);
                            } catch (final NumberFormatException e) {
                                listener.onPrimaryCandidateFound(false, null);
                                return;
                            }
                        } else {
                            listener.onPrimaryCandidateFound(false, null);
                        }
                    }
                });
            } else {
                listener.onPrimaryCandidateFound(false, null);
            }

        } else {
            listener.onPrimaryCandidateFound(true,
                    this.primaryCandidates.get(account.getJid().asBareJid()));
        }
    }

    public String nextRandomId() {
        return new BigInteger(50, random).toString(32);
    }

    public void deliverIbbPacket(Account account, IqPacket packet) {
        String sid = null;
        Element payload = null;
        if (packet.hasChild("open", "http://jabber.org/protocol/ibb")) {
            payload = packet.findChild("open", "http://jabber.org/protocol/ibb");
            sid = payload.getAttribute("sid");
        } else if (packet.hasChild("data", "http://jabber.org/protocol/ibb")) {
            payload = packet.findChild("data", "http://jabber.org/protocol/ibb");
            sid = payload.getAttribute("sid");
        } else if (packet.hasChild("close", "http://jabber.org/protocol/ibb")) {
            payload = packet.findChild("close", "http://jabber.org/protocol/ibb");
            sid = payload.getAttribute("sid");
        }
        if (sid != null) {
            for (JingleConnection connection : connections) {
                if (connection.getAccount() == account
                        && connection.hasTransportId(sid)) {
                    JingleTransport transport = connection.getTransport();
                    if (transport instanceof JingleInBandTransport) {
                        JingleInBandTransport inbandTransport = (JingleInBandTransport) transport;
                        inbandTransport.deliverPayload(packet, payload);
                        return;
                    }
                }
            }
        }
        Log.d(Config.LOGTAG, "unable to deliver ibb packet: " + packet.toString());
        account.getXmppConnection().sendIqPacket(packet.generateResponse(IqPacket.TYPE.ERROR), null);
    }

    public void cancelInTransmission() {
        for (JingleConnection connection : this.connections) {
            if (connection.getJingleStatus() == JingleConnection.JINGLE_STATUS_TRANSMITTING) {
                connection.abort("connectivity-error");
            }
        }
    }
}