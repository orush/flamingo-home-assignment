package com.flamingo.qa.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flamingo.qa.api.Json;
import com.flamingo.qa.config.Secret;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Masks credentials before anything is written to a report.
 *
 * <p>Reports are uploaded as CI artifacts, so they are held to the same rule as
 * the repository: no password, token or session cookie may appear in them.
 */
public final class Redactor {

    public static final String MASK = Secret.MASK;

    private static final Set<String> SENSITIVE_NAMES = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        SENSITIVE_NAMES.addAll(Set.of("username", "password", "token", "authorization", "cookie", "set-cookie"));
    }

    private Redactor() {
    }

    /** True for a JSON key, header or cookie name whose value must never be reported. */
    public static boolean isSensitive(String name) {
        return name != null && SENSITIVE_NAMES.contains(name);
    }

    /**
     * Returns the body with every sensitive field's value masked, at any depth.
     * A body that is not JSON is returned unchanged: plain-text responses such
     * as {@code Forbidden} carry no credentials.
     */
    public static String redactJson(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        JsonNode root;
        try {
            root = Json.mapper().readTree(body);
        } catch (JsonProcessingException notJson) {
            return body;
        }
        if (root == null || !root.isContainerNode()) {
            return body;
        }
        mask(root);
        try {
            return Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not re-serialise redacted body", e);
        }
    }

    private static void mask(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> sensitiveKeys = new ArrayList<>();
            for (Map.Entry<String, JsonNode> field : object.properties()) {
                if (isSensitive(field.getKey()) && !field.getValue().isContainerNode()) {
                    sensitiveKeys.add(field.getKey());
                } else {
                    mask(field.getValue());
                }
            }
            sensitiveKeys.forEach(key -> object.put(key, MASK));
        } else if (node.isArray()) {
            node.forEach(Redactor::mask);
        }
    }
}
