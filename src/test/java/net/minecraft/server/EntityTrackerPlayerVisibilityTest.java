package net.minecraft.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.Test;
import sun.misc.Unsafe;
import uk.betacraft.uberbukkit.protocol.Protocol;

public class EntityTrackerPlayerVisibilityTest {

    @Test
    public void teleportVisibilityUsesThePlayersCurrentPosition() throws Exception {
        EntityPlayer trackedPlayer = allocatePlayer();
        EntityPlayer observer = allocatePlayer();
        trackedPlayer.locX = 0.0D;
        trackedPlayer.locZ = 0.0D;
        EntityTrackerEntry entry = new EntityTrackerEntry(trackedPlayer, 160, 2, false);

        trackedPlayer.locX = 1024.0D;
        trackedPlayer.locZ = -512.0D;
        observer.locX = 1024.0D;
        observer.locZ = -512.0D;

        assertTrue("A player beside the teleport destination must receive the player spawn",
                entry.isWithinTrackingRange(observer, 160));

        observer.locX = 0.0D;
        observer.locZ = 0.0D;
        assertFalse("The old encoded position must not retain a stale viewer",
                entry.isWithinTrackingRange(observer, 160));
    }

    @Test
    public void teleportDestinationObserverReceivesNamedPlayerSpawn() throws Exception {
        WorldServer world = (WorldServer) unsafe().allocateInstance(WorldServer.class);
        world.manager = new AlwaysLoadedPlayerManager();

        EntityPlayer trackedPlayer = allocatePlayer();
        initialisePlayer(trackedPlayer, world, "Tracked", 41);
        trackedPlayer.locX = 0.0D;
        trackedPlayer.locZ = 0.0D;
        EntityTrackerEntry entry = new EntityTrackerEntry(trackedPlayer, 160, 2, false);

        EntityPlayer observer = allocatePlayer();
        initialisePlayer(observer, world, "Observer", 42);
        CapturingHandler handler = (CapturingHandler) unsafe().allocateInstance(CapturingHandler.class);
        handler.packets = new ArrayList();
        handler.networkManager = (NetworkManager) unsafe().allocateInstance(NetworkManager.class);
        handler.networkManager.pvn = 14;
        handler.player = observer;
        observer.netServerHandler = handler;

        trackedPlayer.locX = 1024.0D;
        trackedPlayer.locZ = -512.0D;
        observer.locX = 1024.0D;
        observer.locZ = -512.0D;

        entry.b(observer);

        assertTrue("The destination observer must enter the tracked-player visibility set",
                entry.trackedPlayers.contains(observer));
        assertTrue("Entering visibility must emit Packet20NamedEntitySpawn",
                handler.contains(Packet20NamedEntitySpawn.class));
    }

    private static void initialisePlayer(EntityPlayer player, WorldServer world, String name, int id)
            throws Exception {
        player.world = world;
        player.name = name;
        player.id = id;
        player.inventory = new InventoryPlayer(player);
        player.protocol = new LegacyTestProtocol();
        player.bukkitEntity = (org.bukkit.entity.Entity) java.lang.reflect.Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class[] { Player.class }, (proxy, method, args) -> {
                    if (method.getName().equals("canSee")) {
                        return Boolean.TRUE;
                    }
                    Class returnType = method.getReturnType();
                    if (returnType == Boolean.TYPE) return Boolean.FALSE;
                    if (returnType == Integer.TYPE) return Integer.valueOf(0);
                    if (returnType == Long.TYPE) return Long.valueOf(0L);
                    if (returnType == Float.TYPE) return Float.valueOf(0.0F);
                    if (returnType == Double.TYPE) return Double.valueOf(0.0D);
                    return null;
                });
        setFinalField(player, EntityPlayer.class.getDeclaredField("removeQueue"), new LinkedList());
    }

    private static void setFinalField(Object target, Field field, Object value) throws Exception {
        unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
    }

    private static EntityPlayer allocatePlayer() throws Exception {
        return (EntityPlayer) unsafe().allocateInstance(EntityPlayer.class);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class AlwaysLoadedPlayerManager extends PlayerManager {
        private AlwaysLoadedPlayerManager() {
            super(null, 0, 3);
        }

        @Override
        public boolean a(EntityPlayer player, int chunkX, int chunkZ) {
            return true;
        }
    }

    private static final class CapturingHandler extends NetServerHandler {
        private List packets;

        private CapturingHandler() {
            super(null, null, null);
        }

        @Override
        public void sendPacket(Packet packet) {
            this.packets.add(packet);
        }

        private boolean contains(Class packetType) {
            for (int i = 0; i < this.packets.size(); ++i) {
                if (packetType.isInstance(this.packets.get(i))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class LegacyTestProtocol implements Protocol {
        public boolean canReceiveBlockItem(int id) {
            return true;
        }

        public boolean canReceivePacket(int id) {
            return false;
        }

        public boolean canSeeMob(Class type) {
            return true;
        }
    }
}
