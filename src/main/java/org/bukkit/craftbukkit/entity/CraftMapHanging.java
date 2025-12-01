package org.bukkit.craftbukkit.entity;

import net.minecraft.server.EntityMapHanging;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.MapHanging;

public class CraftMapHanging extends CraftEntity implements MapHanging {

    public CraftMapHanging(CraftServer server, EntityMapHanging entity) {
        super(server, entity);
    }

    @Override
    public EntityMapHanging getHandle() {
        return (EntityMapHanging) entity;
    }

    @Override
    public int getMapId() {
        return getHandle().mapId;
    }

    @Override
    public void setMapId(int mapId) {
        getHandle().mapId = mapId;
    }

    @Override
    public String toString() {
        return "CraftMapHanging{mapId=" + getMapId() + "}";
    }
}

