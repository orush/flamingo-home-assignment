package com.flamingo.qa.config;

/** Typed access to configuration. Tests never reference raw string keys. */
public final class Config {

    private Config() {
    }

    public static String bookerBaseUrl() {
        return ConfigLoader.get("booker.base.url");
    }

    public static String bookerUsername() {
        return ConfigLoader.get("booker.username");
    }

    public static String bookerPassword() {
        return ConfigLoader.get("booker.password");
    }

    public static String graphQlEndpoint() {
        return ConfigLoader.get("graphql.endpoint");
    }

    public static String uiBaseUrl() {
        return ConfigLoader.get("ui.base.url");
    }

    public static String browser() {
        return ConfigLoader.get("ui.browser");
    }

    public static boolean headless() {
        return Boolean.parseBoolean(ConfigLoader.get("ui.headless"));
    }

    public static int timeoutMillis() {
        return Integer.parseInt(ConfigLoader.get("http.timeout.ms"));
    }
}
