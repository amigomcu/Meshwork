package org.meshwork.app.zeroconf.l3.node;

import org.meshwork.core.AbstractMessage;
import org.meshwork.core.AbstractMessageTransport;
import org.meshwork.core.MessageData;
import org.meshwork.core.TransportTimeoutException;
import org.meshwork.core.host.l3.MOK;
import org.meshwork.core.host.l3.MessageAdapter;
import org.meshwork.core.zeroconf.l3.*;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Sinisha Djukic on 14-2-13.
 */
public class MessageDispatcherImpl implements MessageDispatcher {

    public static final int MAX_READ_MSG_COUNT_PER_CALL = 100;
    protected AbstractMessageTransport transport;
    protected PrintWriter writer;
    protected MessageAdapter adapter;
    protected ZeroConfiguration config;
    protected boolean running;
    protected SimpleDateFormat dateFormatter;
    protected byte seq;
    protected int consoleReadTimeout;


    public MessageDispatcherImpl(MessageAdapter adapter, AbstractMessageTransport transport,
                                 ZeroConfiguration config, PrintWriter writer) {
        this.adapter = adapter;
        this.transport = transport;
        this.config = config;
        this.writer = writer;
        consoleReadTimeout = config.getConsoleReadTimeout();
        dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    }

    protected byte nextSeq() {
        return ++seq;
    }

    @Override
    public void run() {
        synchronized (this) {
            running = true;
            this.notifyAll();
        }
        AbstractMessage message = null;
        if (running) {
            try {
                writer.print("\n----------- ");
                writer.println(dateFormatter.format(new Date(System.currentTimeMillis())));
                doMZCInit();
                doMZCID();
                doMZCNwkID();
                doMZCCfgNwk();
                doMZCCfgRep();
                doMZCDeinit();
                writer.println("[MessageDispatcher] Device configured!");
                writer.print("\n----------- ");
            } catch (Throwable t) {
                writer.println("[MessageDispatcher] Error: " + t.getMessage());
                t.printStackTrace(writer);
                writer.flush();
//                readMessagesAndDiscardAll();
//                t.printStackTrace(writer);
            }
        }
        writer.println("[MessageDispatcher] Run complete.");
    }

    @Override
    public void init() throws Exception {
        readMessagesAndDiscardAll();
    }

    @Override
    public void deinit() throws Exception {
        running = false;
    }

    protected void doMZCInit() throws Exception {
        MZCInit msg = new MZCInit(nextSeq());
        AbstractMessage result = sendMessageAndReceive(msg);
        writer.print("[doMZCInit] Response: ");
        result.toString(writer, "\t\t", null, null);
        if ( result == null || !(result instanceof MOK) )
            throw new Exception("Error sending MZCInit");
    }

    protected void doMZCDeinit() throws Exception {
        MZCDeinit msg = new MZCDeinit(nextSeq());
        AbstractMessage result = sendMessageAndReceive(msg);
        writer.print("[MZCDeinit] Response: ");
        if ( result != null )
            result.toString(writer, "\t\t", null, null);
        if ( result == null || !(result instanceof MOK) )
            throw new Exception("Error sending MZCDeinit");
    }

    protected void doMZCID() throws Exception {
        MZCID msg = new MZCID(nextSeq());
        AbstractMessage result = sendMessageAndReceive(msg);
        writer.print("[doMZCID] Response: ");
        if ( result != null )
            result.toString(writer, "\t\t", null, null);
        if ( result == null || !(result instanceof MZCIDRes) )
            throw new Exception("Error sending MZCID");
    }

    protected void doMZCNwkID() throws Exception {
        MZCNwkID msg = new MZCNwkID(nextSeq());
        AbstractMessage result = sendMessageAndReceive(msg);
        writer.print("[doMZCNwkID] Response: ");
        if ( result != null )
            result.toString(writer, "\t\t", null, null);
        if ( result == null || !(result instanceof MZCNwkIDRes) )
            throw new Exception("Error sending MZCNwkID");
    }

    protected void doMZCCfgNwk() throws Exception {
        MZCCfgNwk msg = new MZCCfgNwk(nextSeq());
        msg.channel = config.getChannel();
        msg.nodeid = config.getNodeid();
        msg.nwkid = config.getNwkid();
        String k = config.getNwkkey();
        msg.keylen = (byte) (k == null ? 0 : k.length());
        msg.key = msg.keylen == 0 ? null : k.getBytes();
        writer.print("[doMZCCfgNwk] Configuring network: ");
        msg.toString(writer, "\t\t", null, null);
        AbstractMessage result = sendMessageAndReceive(msg);
        writer.print("[doMZCCfgNwk] Response: ");
        if ( result != null )
            result.toString(writer, "\t\t", null, null);
        if ( result == null || !(result instanceof MOK) )
            throw new Exception("Error sending MZCCfgNwk");
    }

    protected void doMZCCfgRep() throws Exception {
        MZCCfgRep msg = new MZCCfgRep(nextSeq());
        msg.reportNodeid = config.getReportNodeid();
        msg.reportFlags = config.getReportFlags();
        writer.print("[doMZCCfgNwk] Configuring reporting: ");
        msg.toString(writer, "\t\t", null, null);
        AbstractMessage result = sendMessageAndReceive(msg);
        writer.print("[doMZCCfgRep] Response: ");
        if ( result != null )
            result.toString(writer, "\t\t", null, null);
        if ( result == null || !(result instanceof MOK) )
            throw new Exception("Error sending MZCCfgRep");
    }

    protected void readMessagesAndDiscardAll() {
        boolean hasMessages = true;
        int count = 0;//prevent endless loop for some unknown reason
        while ( hasMessages && count < MAX_READ_MSG_COUNT_PER_CALL) {
            try {
                count ++;
                transport.readMessage(10);//low timeout to catch only the buffered messages;
            } catch (TransportTimeoutException e) {
                hasMessages = false;
            } catch (Exception e) {

            }
        }
    }

    protected void sendMessage(AbstractMessage msg) throws Exception {
        writer.println("[sendMessage] Msg: "+msg);
        msg.toString(writer, "\t\t", null, null);
        writer.println();
        writer.flush();
        MessageData data = new MessageData();
        msg.serialize(data);
        transport.sendMessage(data);
        writer.println("[sendMessage][DONE] Msg: " + msg);
    }

    protected AbstractMessage sendMessageAndReceive(AbstractMessage msg) throws Exception {
        writer.println();
        writer.println("[sendMessageAndReceive] Entered");
        readMessagesAndDiscardAll();
        sendMessage(msg);
        writer.println("[sendMessageAndReceive] Receiving with timeout: " + consoleReadTimeout);
        MessageData data = readMessageUntil(consoleReadTimeout, msg.seq);
        writer.print("[sendMessageAndReceive] Received Data: ");
        if ( data != null )
            data.toString(writer, "\t\t", null, null);
        else
            writer.print("<Null or timeout>");
        writer.println();
        writer.flush();
        AbstractMessage result = data == null ? null : adapter.deserialize(data);
        writer.print("[sendMessageAndReceive] Received Message: ");
        if ( result != null )
            result.toString(writer, "\t\t", null, null);
        else
            writer.println("<Null or timeout>");
        writer.println();
        writer.println("[sendMessageAndReceive] Exited");
        writer.println();
        writer.flush();
        return result;
    }

    protected MessageData readMessageUntil(int readTimeout, byte seq) {
        MessageData result = null, temp = null;
        long start = System.currentTimeMillis();
        int count = 0;//prevent endless loop for some unknown reason... oh, sanity
        while (count < MAX_READ_MSG_COUNT_PER_CALL) {
            try {
                count++;
                temp = transport.readMessage(readTimeout);
                boolean breakout = false;
                if (temp != null) {//shouldn't happen?
                    if (temp.seq == seq) {
                        result = temp;
                        breakout = true;
                    } else {
                        AbstractMessage message = adapter.deserialize(temp);
                        writer.println("<Unexpected message> " + message);
                        if (message != null)
                            message.toString(writer, null, null, null);
                        writer.println();
                        writer.flush();
                    }
                    if ( breakout )
                        break;
                }
            } catch (TransportTimeoutException e) {
                writer.println("*** Transport timeout error: "+e.getMessage());
                writer.flush();
            } catch (Exception e) {
                writer.println("*** Read error: "+e.getMessage());
                e.printStackTrace(writer);
                writer.flush();
            }
            //invoked here to ensure at least one read in case readTimeout is really low or zero
            if (System.currentTimeMillis() - start >= readTimeout)
                break;
        }
        return result;
    }

}