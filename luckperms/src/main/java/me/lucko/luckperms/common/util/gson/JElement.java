/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.util.gson;

import com.google.gson.JsonElement;

/**
 * Stupidly simple fluent gson wrappers
 */
public interface JElement {

    JsonElement toJson();

}

