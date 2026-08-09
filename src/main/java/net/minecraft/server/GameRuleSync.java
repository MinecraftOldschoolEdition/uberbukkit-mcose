package net.minecraft.server;

/** Syncs client-visible gamerules that affect inventory prediction. */
public final class GameRuleSync {
    public static final int FOOD_STACKING_ENABLED = 23;
    public static final int FOOD_STACKING_DISABLED = 24;

    private GameRuleSync() {
    }

    public static void send(EntityPlayer player) {
        if (player == null || player.netServerHandler == null || player.world == null) {
            return;
        }
        WorldData worldData = player.world.worldData;
        boolean enabled = worldData != null && worldData.getToggleFoodStacking();
        player.netServerHandler.sendPacket(new Packet70Bed(enabled ? FOOD_STACKING_ENABLED : FOOD_STACKING_DISABLED));
    }

    public static void broadcast(MinecraftServer server, WorldData worldData) {
        if (server == null || server.serverConfigurationManager == null || worldData == null) {
            return;
        }
        for (Object value : server.serverConfigurationManager.players) {
            if (value instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer)value;
                if (player.world != null && player.world.worldData == worldData) {
                    send(player);
                }
            }
        }
    }
}
