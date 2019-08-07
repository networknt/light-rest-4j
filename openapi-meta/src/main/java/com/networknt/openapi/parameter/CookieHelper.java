package com.networknt.openapi.parameter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;

/**
 * Adapted from io.undertow.util.Cookies to support comma delimited values.
 * 
 * @author Daniel Zhao
 *
 */

public class CookieHelper {

    public static final String DOMAIN = "$Domain";
    public static final String VERSION = "$Version";
    public static final String PATH = "$Path";
    
    private static final char[] HTTP_SEPARATORS;
    private static final boolean[] HTTP_SEPARATOR_FLAGS = new boolean[128];
    
    /**
     * If set to true, the <code>/</code> character will be treated as a
     * separator. Default is false.
     */
    private static final boolean FWD_SLASH_IS_SEPARATOR = Boolean.getBoolean("io.undertow.legacy.cookie.FWD_SLASH_IS_SEPARATOR");

    static {
        /*
        Excluding the '/' char by default violates the RFC, but
        it looks like a lot of people put '/'
        in unquoted values: '/': ; //47
        '\t':9 ' ':32 '\"':34 '(':40 ')':41 ',':44 ':':58 ';':59 '<':60
        '=':61 '>':62 '?':63 '@':64 '[':91 '\\':92 ']':93 '{':123 '}':125
        */
        if (FWD_SLASH_IS_SEPARATOR) {
            HTTP_SEPARATORS = new char[]{'\t', ' ', '\"', '(', ')', ',', '/',
                    ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '{', '}'};
        } else {
            HTTP_SEPARATORS = new char[]{'\t', ' ', '\"', '(', ')', ',',
                    ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '{', '}'};
        }
        for (int i = 0; i < 128; i++) {
            HTTP_SEPARATOR_FLAGS[i] = false;
        }
        for (char HTTP_SEPARATOR : HTTP_SEPARATORS) {
            HTTP_SEPARATOR_FLAGS[HTTP_SEPARATOR] = true;
        }
    }


    /**
    /**
     * Parses the cookies from a list of "Cookie:" header values. The cookie header values are parsed according to RFC2109 that
     * defines the following syntax:
     *
     * <pre>
     * <code>
     * cookie          =  "Cookie:" cookie-version
     *                    1*((";" | ",") cookie-value)
     * cookie-value    =  NAME "=" VALUE [";" path] [";" domain]
     * cookie-version  =  "$Version" "=" value
     * NAME            =  attr
     * VALUE           =  value
     * path            =  "$Path" "=" value
     * domain          =  "$Domain" "=" value
     * </code>
     * </pre>
     *
     * @param maxCookies The maximum number of cookies. Used to prevent hash collision attacks
     * @param allowEqualInValue if true equal characters are allowed in cookie values
     * @param cookies The cookie values to parse
     * @return A pared cookie map
     *
     * @see Cookie
     * @see <a href="http://tools.ietf.org/search/rfc2109">rfc2109</a>
     */
    public static Map<String, Cookie> parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies) {
        return parseRequestCookies(maxCookies, allowEqualInValue, cookies, false);
    }

    static Map<String, Cookie> parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, boolean commaIsSeperator) {
        return parseRequestCookies(maxCookies, allowEqualInValue, cookies, commaIsSeperator, true);
    }

    static Map<String, Cookie> parseRequestCookies(int maxCookies, boolean allowEqualInValue, List<String> cookies, boolean commaIsSeperator, boolean allowHttpSepartorsV0) {
        if (cookies == null) {
            return new TreeMap<>();
        }
        final Map<String, Cookie> parsedCookies = new TreeMap<>();

        for (String cookie : cookies) {
            parseCookie(cookie, parsedCookies, maxCookies, allowEqualInValue, commaIsSeperator, allowHttpSepartorsV0);
        }
        return parsedCookies;
    }

    private static void parseCookie(final String cookie, final Map<String, Cookie> parsedCookies, int maxCookies, boolean allowEqualInValue, boolean commaIsSeperator, boolean allowHttpSepartorsV0) {
        int state = 0;
        String name = null;
        int start = 0;
        boolean containsEscapedQuotes = false;
        int cookieCount = parsedCookies.size();
        final Map<String, String> cookies = new HashMap<>();
        final Map<String, String> additional = new HashMap<>();
        for (int i = 0; i < cookie.length(); ++i) {
            char c = cookie.charAt(i);
            switch (state) {
                case 0: {
                    //eat leading whitespace
                    if (c == ' ' || c == '\t' || c == ';') {
                        start = i + 1;
                        break;
                    }
                    state = 1;
                    //fall through
                }
                case 1: {
                    //extract key
                    if (c == '=') {
                        name = cookie.substring(start, i);
                        start = i + 1;
                        state = 2;
                    } else if (c == ';' || (commaIsSeperator && c == ',')) {
                        if(name != null) {
                            cookieCount = createCookie(name, cookie.substring(start, i), maxCookies, cookieCount, cookies, additional);
                        } else if(UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.trace("Ignoring invalid cookies in header " + cookie);
                        }
                        state = 0;
                        start = i + 1;
                    }
                    break;
                }
                case 2: {
                    //extract value
                    if (c == ';' || (commaIsSeperator && c == ',')) {
                        cookieCount = createCookie(name, cookie.substring(start, i), maxCookies, cookieCount, cookies, additional);
                        state = 0;
                        start = i + 1;
                    } else if (c == '"' && start == i) { //only process the " if it is the first character
                        containsEscapedQuotes = false;
                        state = 3;
                        start = i + 1;
                    } else if (c == '=') {
                        if (!allowEqualInValue && !allowHttpSepartorsV0) {
                            cookieCount = createCookie(name, cookie.substring(start, i), maxCookies, cookieCount, cookies, additional);
                            state = 4;
                            start = i + 1;
                        }
                    } else if (!allowHttpSepartorsV0 && isHttpSeparator(c)) {
                        cookieCount = createCookie(name, cookie.substring(start, i), maxCookies, cookieCount, cookies, additional);
                        state = 4;
                        start = i + 1;
                    }
                    break;
                }
                case 3: {
                    //extract quoted value
                    if (c == '"') {
                        cookieCount = createCookie(name, containsEscapedQuotes ? unescapeDoubleQuotes(cookie.substring(start, i)) : cookie.substring(start, i), maxCookies, cookieCount, cookies, additional);
                        state = 0;
                        start = i + 1;
                    }
                    // Skip the next double quote char '"' when it is escaped by backslash '\' (i.e. \") inside the quoted value
                    if (c == '\\' && (i + 1 < cookie.length()) && cookie.charAt(i + 1) == '"') {
                        // But..., do not skip at the following conditions
                        if (i + 2 == cookie.length()) { // Cookie: key="\" or Cookie: key="...\"
                            break;
                        }
                        if (i + 2 < cookie.length() && (cookie.charAt(i + 2) == ';'      // Cookie: key="\"; key2=...
                                || (commaIsSeperator && cookie.charAt(i + 2) == ','))) { // Cookie: key="\", key2=...
                            break;
                        }
                        // Skip the next double quote char ('"' behind '\') in the cookie value
                        i++;
                        containsEscapedQuotes = true;
                    }
                    break;
                }
                case 4: {
                    //skip value portion behind '='
                    if (c == ';' || (commaIsSeperator && c == ',')) {
                        state = 0;
                    }
                    start = i + 1;
                    break;
                }
            }
        }
        if (state == 2) {
            createCookie(name, cookie.substring(start), maxCookies, cookieCount, cookies, additional);
        }

        for (final Map.Entry<String, String> entry : cookies.entrySet()) {
            Cookie c = new CookieImpl(entry.getKey(), entry.getValue());
            String domain = additional.get(DOMAIN);
            if (domain != null) {
                c.setDomain(domain);
            }
            String version = additional.get(VERSION);
            if (version != null) {
                c.setVersion(Integer.parseInt(version));
            }
            String path = additional.get(PATH);
            if (path != null) {
                c.setPath(path);
            }
            parsedCookies.put(c.getName(), c);
        }
    }

    private static int createCookie(final String name, final String value, int maxCookies, int cookieCount,
            final Map<String, String> cookies, final Map<String, String> additional) {
        if (!name.isEmpty() && name.charAt(0) == '$') {
            if(additional.containsKey(name)) {
                return cookieCount;
            }
            additional.put(name, value);
            return cookieCount;
        } else {
            if (cookieCount == maxCookies) {
                throw UndertowMessages.MESSAGES.tooManyCookies(maxCookies);
            }
            if(cookies.containsKey(name)) {
                return cookieCount;
            }
            cookies.put(name, value);
            return ++cookieCount;
        }
    }

    private static String unescapeDoubleQuotes(final String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Replace all escaped double quote (\") to double quote (")
        char[] tmp = new char[value.length()];
        int dest = 0;
        for(int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\\' && (i + 1 < value.length()) && value.charAt(i + 1) == '"') {
                i++;
            }
            tmp[dest] = value.charAt(i);
            dest++;
        }
        return new String(tmp, 0, dest);
    }

    /**
     * Returns true if the byte is a separator as defined by V1 of the cookie
     * spec, RFC2109.
     * @throws IllegalArgumentException if a control character was supplied as
     *         input
     */
    static boolean isHttpSeparator(final char c) {
        if (c < 0x20 || c >= 0x7f) {
            if (c != 0x09) {
                throw UndertowMessages.MESSAGES.invalidControlCharacter(Integer.toString(c));
            }
        }

        return HTTP_SEPARATOR_FLAGS[c];
    }
}


