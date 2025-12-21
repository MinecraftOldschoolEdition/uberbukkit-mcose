/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.util;

/**
 * Represents a three state permission value.
 */
public enum Tristate {
    /**
     * Permission is explicitly set to true
     */
    TRUE(true),
    
    /**
     * Permission is explicitly set to false
     */
    FALSE(false),
    
    /**
     * Permission is not set (undefined)
     */
    UNDEFINED(false);
    
    private final boolean booleanValue;
    
    Tristate(boolean booleanValue) {
        this.booleanValue = booleanValue;
    }
    
    /**
     * Returns the boolean value of this tristate.
     * UNDEFINED returns false.
     */
    public boolean asBoolean() {
        return this.booleanValue;
    }
    
    /**
     * Creates a Tristate from a boolean.
     */
    public static Tristate of(boolean value) {
        return value ? TRUE : FALSE;
    }
    
    /**
     * Creates a Tristate from a nullable Boolean.
     */
    public static Tristate of(Boolean value) {
        if (value == null) {
            return UNDEFINED;
        }
        return value ? TRUE : FALSE;
    }
}

