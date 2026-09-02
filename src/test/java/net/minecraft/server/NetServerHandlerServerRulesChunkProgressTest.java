package net.minecraft.server;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import org.junit.Test;
import sun.misc.Unsafe;

public class NetServerHandlerServerRulesChunkProgressTest {

    @Test
    public void pendingConsentStillPumpsInitialChunkDelivery() throws Exception {
        NetServerHandler handler = (NetServerHandler) unsafe().allocateInstance(NetServerHandler.class);
        ChunkPumpingPlayer player = (ChunkPumpingPlayer) unsafe().allocateInstance(ChunkPumpingPlayer.class);
        handler.player = player;
        player.setServerRulesAccepted(false);

        handler.a(new Packet10Flying());

        assertEquals("Consent gating must not deadlock the first full chunk", 1, player.chunkPumpCalls);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class ChunkPumpingPlayer extends EntityPlayer {
        int chunkPumpCalls;

        private ChunkPumpingPlayer() {
            super(null, null, null, null, 0);
        }

        @Override
        public void a(boolean sendChunks) {
            if (sendChunks) {
                ++this.chunkPumpCalls;
            }
        }
    }
}
