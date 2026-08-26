package com.walnutt.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Shared Gson instance + small helpers for building the WS message envelope. */
public final class JsonSupport {
    /**
     * serializeNulls() matters here: API_CONTRACT.md's DTOs use "string | null" for
     * several fields (winnerName, playerTwoName, VfxEvent's nullable fields, ...),
     * meaning the key must always be present. Without this, Gson's default
     * (serializeNulls=false) silently *drops* null-valued keys from a JsonObject
     * tree (confirmed: JsonObject.addProperty("k", (String) null) followed by
     * gson.toJson(obj) produces "{}", even though obj.toString() shows the null) -
     * a real gotcha that would otherwise ship a contract violation.
     */
    public static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private JsonSupport() {
    }

    /** {"type": type, "payload": payload} */
    public static String envelope(String type, Object payload) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.add("payload", GSON.toJsonTree(payload));
        return GSON.toJson(obj);
    }

    /** {"type": "message", "text": text} */
    public static String messageEnvelope(String text) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "message");
        obj.addProperty("text", text);
        return GSON.toJson(obj);
    }

    public static JsonElement parse(String json) {
        return com.google.gson.JsonParser.parseString(json);
    }

    /** Null-safe string field lookup - missing key, JSON null, or a non-string value all yield null rather than throwing. */
    public static String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Null-safe int field lookup - missing key, JSON null, or a non-numeric value all yield null rather than throwing. */
    public static Integer optInt(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
