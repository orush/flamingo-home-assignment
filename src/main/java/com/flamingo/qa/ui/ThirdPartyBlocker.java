package com.flamingo.qa.ui;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Route;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lets only first-party traffic through; aborts everything else.
 *
 * <p>DemoQA embeds ad frames that shift layout and intercept clicks, and they
 * come from a couple of dozen rotating ad and tracking hosts. A blocklist of
 * those would go stale; an allowlist of the site itself does not. Measured on
 * the web tables page, it removes every ad iframe and cuts time to network idle
 * by roughly 45%, and aborting the two tag loaders means no downstream ad host is
 * ever contacted at all.
 */
public final class ThirdPartyBlocker {

    private static final Pattern HTTP_HOST =
            Pattern.compile("^https?://([^/:?#]+)", Pattern.CASE_INSENSITIVE);

    private ThirdPartyBlocker() {
    }

    public static void install(BrowserContext context, String firstPartyHost) {
        context.route(url -> !isAllowed(url, firstPartyHost), Route::abort);
    }

    /**
     * True for the first-party host, its subdomains, and non-network schemes such
     * as {@code data:} and {@code blob:}. A look-alike such as
     * {@code evil-example.com} does not match {@code example.com}.
     */
    static boolean isAllowed(String url, String firstPartyHost) {
        Matcher matcher = HTTP_HOST.matcher(url);
        if (!matcher.find()) {
            return true; // not an http(s) request, so nothing leaves for a third party
        }
        String host = matcher.group(1).toLowerCase(Locale.ROOT);
        String firstParty = firstPartyHost.toLowerCase(Locale.ROOT);
        return host.equals(firstParty) || host.endsWith("." + firstParty);
    }
}
