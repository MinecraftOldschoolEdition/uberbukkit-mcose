package uk.betacraft.uberbukkit.protocol;

import uk.betacraft.uberbukkit.protocol.extension.Protocol2000;

public class Protocol14 extends Protocol2000 {

    @Override
    public boolean canReceivePacket(int id) {
        // Allow 63 (digging progress), but keep 62 (custom sound) blocked for vanilla-safe clients
        return id != 62;
    }
}
