package net.minecraft.server;

public class ServerCommand {

    public final String command;
    public final ICommandListener b;
    public final long enqueueTimeMillis;

    public ServerCommand(String s, ICommandListener icommandlistener) {
        this(s, icommandlistener, System.currentTimeMillis());
    }

    public ServerCommand(String s, ICommandListener icommandlistener, long enqueueTimeMillis) {
        this.command = s;
        this.b = icommandlistener;
        this.enqueueTimeMillis = enqueueTimeMillis;
    }
}
