package com.smartportx.common.monitoring;

import java.net.InetAddress;
import java.util.List;

/**
 * Allow-list check for internal-only actuator endpoints such as
 * {@code /actuator/prometheus}.
 *
 * <p>The check uses the socket peer address ({@code HttpServletRequest.getRemoteAddr()} /
 * {@code InetSocketAddress}) and never {@code X-Forwarded-For}, so a client cannot grant
 * itself monitoring access by spoofing a header. Supported entries are exact IPv4/IPv6
 * addresses and CIDR blocks (for example {@code 127.0.0.1/32}, {@code ::1/128} or the
 * default Docker bridge range {@code 172.16.0.0/12}).</p>
 */
public final class MonitoringNetworkAccess {

    public static final String DEFAULT_NETWORKS = "127.0.0.1/32,::1/128,172.16.0.0/12";

    private MonitoringNetworkAccess() {
    }

    /** True only for a syntactically valid IPv4 or IPv6 literal (no hostnames). */
    public static boolean isValidIpAddress(String value) {
        String address = normalize(value);
        if (address == null) {
            return false;
        }
        return address.contains(":") ? parseIpv6(address) != null : parseIpv4(address) != null;
    }

    public static boolean isAllowed(String remoteAddress, List<String> networks) {
        if (remoteAddress == null || remoteAddress.isBlank() || networks == null || networks.isEmpty()) {
            return false;
        }
        String address = normalize(remoteAddress);
        if (address == null) {
            return false;
        }
        for (String entry : networks) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            if (matches(address, entry.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        String address = value.trim();
        int zone = address.indexOf('%');
        if (zone > 0) {
            address = address.substring(0, zone);
        }
        // IPv4-mapped IPv6 (::ffff:127.0.0.1) -> plain IPv4.
        String lower = address.toLowerCase();
        if (lower.startsWith("::ffff:")) {
            address = address.substring(7);
            if (address.contains(":")) {
                return null;
            }
        }
        return address;
    }

    private static boolean matches(String address, String network) {
        int slash = network.indexOf('/');
        if (slash < 0) {
            return address.equalsIgnoreCase(network);
        }
        String base = network.substring(0, slash);
        int prefix;
        try {
            prefix = Integer.parseInt(network.substring(slash + 1));
        } catch (NumberFormatException ex) {
            return false;
        }
        boolean ipv6 = address.contains(":") || base.contains(":");
        if (ipv6) {
            return matchesIpv6(address, base, prefix);
        }
        return matchesIpv4(address, base, prefix);
    }

    private static boolean matchesIpv4(String address, String base, int prefix) {
        if (prefix < 0 || prefix > 32) {
            return false;
        }
        Long ip = parseIpv4(address);
        Long network = parseIpv4(base);
        if (ip == null || network == null) {
            return false;
        }
        int mask = prefix == 0 ? 0 : (0xFFFFFFFF << (32 - prefix));
        return (ip & mask) == (network & mask);
    }

    private static Long parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        long result = 0;
        for (String part : parts) {
            try {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) {
                    return null;
                }
                result = (result << 8) | octet;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return result;
    }

    private static boolean matchesIpv6(String address, String base, int prefix) {
        if (prefix < 0 || prefix > 128) {
            return false;
        }
        byte[] ip = parseIpv6(address);
        byte[] network = parseIpv6(base);
        if (ip == null || network == null) {
            return false;
        }
        int wholeBytes = prefix / 8;
        for (int i = 0; i < wholeBytes; i++) {
            if (ip[i] != network[i]) {
                return false;
            }
        }
        int remainingBits = prefix % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (ip[wholeBytes] & mask) == (network[wholeBytes] & mask);
    }

    private static byte[] parseIpv6(String value) {
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (Exception ex) {
            return null;
        }
    }
}
