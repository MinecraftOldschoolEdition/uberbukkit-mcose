package net.minecraft.server;

/** Syncs client-visible gamerules that affect inventory prediction. */
public final class GameRuleSync {
    public static final int FOOD_STACKING_ENABLED = 23;
    public static final int FOOD_STACKING_DISABLED = 24;
    public static final int ADVENTURE_MOVEMENT_ENABLED = 25;
    public static final int ADVENTURE_MOVEMENT_DISABLED = 26;
    public static final int ADVENTURE_COMBAT_ENABLED = 27;
    public static final int ADVENTURE_COMBAT_DISABLED = 28;

    private GameRuleSync() {
    }

    public static void send(EntityPlayer player) {
        if (player == null || player.netServerHandler == null || player.world == null) {
            return;
        }
        WorldData worldData = player.world.worldData;
        boolean enabled = worldData != null && worldData.getToggleFoodStacking();
        player.netServerHandler.sendPacket(new Packet70Bed(enabled ? FOOD_STACKING_ENABLED : FOOD_STACKING_DISABLED));
        boolean adventureMovement = worldData != null && worldData.getAdventureMovement();
        if(!adventureMovement) {
            player.setSprinting(false);
        }
        player.netServerHandler.sendPacket(new Packet70Bed(
            adventureMovement ? ADVENTURE_MOVEMENT_ENABLED : ADVENTURE_MOVEMENT_DISABLED
        ));
        boolean adventureCombat = worldData != null && worldData.getAdventureCombat();
        if(!adventureCombat) {
            player.cancelBowCharge();
        }
        player.netServerHandler.sendPacket(new Packet70Bed(
            adventureCombat ? ADVENTURE_COMBAT_ENABLED : ADVENTURE_COMBAT_DISABLED
        ));
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
