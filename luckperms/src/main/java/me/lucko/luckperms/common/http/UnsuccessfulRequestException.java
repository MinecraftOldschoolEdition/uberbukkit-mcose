/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.http;

import okhttp3.Response;

public class UnsuccessfulRequestException extends Exception {
    private final Response response;

    public UnsuccessfulRequestException(Response response) {
        super("Request was unsuccessful: " + response.code() + " - " + response.message());
        this.response = response;
    }

    public Response getResponse() {
        return this.response;
    }
}

