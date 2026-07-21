package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Tab completion packet for command auto-complete.
 * 
 * Client -> Server: Contains the partial command string to complete
 * Server -> Client: Contains the list of possible completions
 */
public class Packet203TabComplete extends Packet {

    /** The partial message/command to complete (client->server) */
    public String text;
    
    /** The list of completions (server->client), newline-separated */
    public String[] completions;

    public Packet203TabComplete() {}

    /**
     * Constructor for client request (text to complete)
     */
    public Packet203TabComplete(String text) {
        this.text = text;
        this.completions = null;
    }

    /**
     * Constructor for server response (list of completions)
     */
    public Packet203TabComplete(String[] completions) {
        this.text = null;
        this.completions = completions;
    }

    @Override
    public void a(DataInputStream in) throws IOException {
        // Read text (for request)
        this.text = PacketLimits.readUtf(in, PacketLimits.MAX_COMMAND_TEXT_CHARS, "tab-complete text");
        
        // A server only receives tab-complete requests. Response entries are
        // server-to-client data and accepting them here permits a client to
        // force hundreds of bounded strings to be allocated before dispatch.
        int count = in.readInt();
        if (count != 0) {
            throw new IOException("Client tab-complete request contained response entries: " + count);
        }
        this.completions = new String[0];
    }

    @Override
    public void a(DataOutputStream out) throws IOException {
        // Write text (empty string if null)
        PacketLimits.writeUtf(out, this.text, PacketLimits.MAX_COMMAND_TEXT_CHARS, "tab-complete text");
        
        // Write completions
        if (this.completions != null) {
            int count = Math.min(this.completions.length, PacketLimits.MAX_TAB_COMPLETIONS);
            out.writeInt(count);
            for (int i = 0; i < count; ++i) {
                PacketLimits.writeUtf(out, this.completions[i], PacketLimits.MAX_COMPLETION_CHARS, "tab-complete completion");
            }
        } else {
            out.writeInt(0);
        }
    }

    @Override
    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    @Override
    public int a() {
        int size = 2 + (this.text != null ? this.text.length() * 2 : 0) + 4;
        if (this.completions != null) {
            for (String s : this.completions) {
                size += 2 + (s != null ? s.length() * 2 : 0);
            }
        }
        return size;
    }
}
