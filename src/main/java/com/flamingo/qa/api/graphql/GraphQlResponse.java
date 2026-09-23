package com.flamingo.qa.api.graphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.flamingo.qa.api.Json;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * A GraphQL response envelope.
 *
 * <p>Hygraph varies the envelope by failure class, so nothing here assumes a key
 * is present:
 *
 * <ul>
 *   <li>missing entity: 200, the field is JSON {@code null}, no {@code errors} key;</li>
 *   <li>execution-phase denial: 200, the field is {@code null} <em>and</em>
 *       {@code errors} is present;</li>
 *   <li>parse or validation failure: 400, {@code data} is {@code null},
 *       {@code errors} is present.</li>
 * </ul>
 *
 * <p>{@link #node(String)} distinguishes a JSON {@code null} (a {@code NullNode})
 * from an absent key (a {@code MissingNode}); the negative tests rely on that.
 */
public final class GraphQlResponse {

    private final int statusCode;
    private final JsonNode root;
    private final String rawBody;

    private GraphQlResponse(int statusCode, JsonNode root, String rawBody) {
        this.statusCode = statusCode;
        this.root = root;
        this.rawBody = rawBody;
    }

    static GraphQlResponse from(Response response) {
        String raw = response.asString();
        try {
            return new GraphQlResponse(response.statusCode(), Json.mapper().readTree(raw), raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "GraphQL response was not JSON (HTTP " + response.statusCode() + "): " + raw, e);
        }
    }

    public int statusCode() {
        return statusCode;
    }

    public String rawBody() {
        return rawBody;
    }

    /** The node at a JSON Pointer such as {@code /data/movies/0/id}. Never null. */
    public JsonNode node(String jsonPointer) {
        return root.at(jsonPointer);
    }

    /** Maps the node at {@code jsonPointer}; returns null for JSON null, fails if absent. */
    public <T> T get(String jsonPointer, Class<T> type) {
        JsonNode node = present(jsonPointer);
        if (node.isNull()) {
            return null;
        }
        try {
            return Json.mapper().treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Could not map " + jsonPointer + " to " + type.getSimpleName() + ": " + node, e);
        }
    }

    /** Maps the array at {@code jsonPointer} element by element. */
    public <T> List<T> getList(String jsonPointer, Class<T> elementType) {
        JsonNode node = present(jsonPointer);
        if (!node.isArray()) {
            throw new IllegalStateException(
                    "Expected an array at " + jsonPointer + " but found: " + node + "\nBody: " + rawBody);
        }
        List<T> items = new ArrayList<>();
        for (JsonNode element : node) {
            try {
                items.add(Json.mapper().treeToValue(element, elementType));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(
                        "Could not map element of " + jsonPointer + " to " + elementType.getSimpleName(), e);
            }
        }
        return items;
    }

    public boolean hasErrors() {
        JsonNode errors = root.path("errors");
        return errors.isArray() && !errors.isEmpty();
    }

    /** The {@code errors} array, or an empty list when the key is absent. */
    public List<GraphQlError> errors() {
        return root.has("errors") ? getList("/errors", GraphQlError.class) : List.of();
    }

    private JsonNode present(String jsonPointer) {
        JsonNode node = root.at(jsonPointer);
        if (node.isMissingNode()) {
            throw new IllegalStateException("Nothing at " + jsonPointer + " in response: " + rawBody);
        }
        return node;
    }
}
