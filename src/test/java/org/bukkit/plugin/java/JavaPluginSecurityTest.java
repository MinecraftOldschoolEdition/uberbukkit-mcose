package org.bukkit.plugin.java;

import com.avaje.ebean.config.GlobalProperties;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JavaPluginSecurityTest {

    @Test
    public void databaseInitializationDisablesUnsafeSocketClustering() {
        GlobalProperties.put("ebean.cluster.type", "socket");
        GlobalProperties.put("ebean.cluster.local", "127.0.0.1:9999");
        GlobalProperties.put("ebean.cluster.members", "127.0.0.1:9998");
        GlobalProperties.put("ebean.cluster.lucene.masterHostPort", "127.0.0.1:9997");

        JavaPlugin.disableUnsafeEbeanClustering();

        assertEquals("", GlobalProperties.get("ebean.cluster.type", "missing"));
        assertEquals("", GlobalProperties.get("ebean.cluster.local", "missing"));
        assertEquals("", GlobalProperties.get("ebean.cluster.members", "missing"));
        assertEquals("", GlobalProperties.get("ebean.cluster.lucene.masterHostPort", "missing"));
    }
}
