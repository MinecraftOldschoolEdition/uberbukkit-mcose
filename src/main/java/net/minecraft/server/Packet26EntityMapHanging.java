package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Packet for spawning a map hanging entity (wall-mounted map).
 * Similar to Packet25EntityPainting but for maps.
 */
public class Packet26EntityMapHanging extends Packet {

    public int entityId;
    public int mapId;
    public int xPosition;
    public int yPosition;
    public int zPosition;
    public int direction;

    public Packet26EntityMapHanging() {
    }

    public Packet26EntityMapHanging(EntityMapHanging entity) {
        this.entityId = entity.id;
        this.mapId = entity.mapId;
        this.xPosition = entity.xPosition;
        this.yPosition = entity.yPosition;
        this.zPosition = entity.zPosition;
        this.direction = entity.direction;
    }

    public void a(DataInputStream input) throws IOException {
        this.entityId = input.readInt();
        this.mapId = input.readInt();  // Use int for extended map ID support
        this.xPosition = input.readInt();
        this.yPosition = input.readInt();
        this.zPosition = input.readInt();
        this.direction = input.readInt();
    }

    public void a(DataOutputStream output) throws IOException {
        output.writeInt(this.entityId);
        output.writeInt(this.mapId);  // Use int for extended map ID support
        output.writeInt(this.xPosition);
        output.writeInt(this.yPosition);
        output.writeInt(this.zPosition);
        output.writeInt(this.direction);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 24; // 6 ints * 4 bytes each
    }
}

