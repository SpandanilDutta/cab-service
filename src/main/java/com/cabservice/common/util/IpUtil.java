package com.cabservice.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;

public class IpUtil {

    /**
     * Extracts the client IP address from the request, taking into account
     * reverse proxies / load balancers headers like X-Forwarded-For and X-Real-IP.
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) return "";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            ip = ip.split(",")[0].trim();
        } else {
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                ip = ip.trim();
            } else {
                ip = request.getRemoteAddr();
            }
        }
        return normalizeToIpv4(ip);
    }

    public static String normalizeToIpv4(String ip) {
        if (ip == null) return "";
        String trimmed = ip.trim();
        if ("0:0:0:0:0:0:0:1".equals(trimmed) || "::1".equals(trimmed)) {
            return "127.0.0.1";
        }
        if (trimmed.startsWith("::ffff:")) {
            return trimmed.substring(7);
        }
        return trimmed;
    }

    public static boolean isIpAllowed(String rawClientIp, String allowedIpRangesStr) {
        if (allowedIpRangesStr == null || allowedIpRangesStr.isBlank()) {
            return true;
        }
        String clientIp = normalizeToIpv4(rawClientIp);
        if (clientIp.isBlank()) {
            return false;
        }

        String[] ranges = allowedIpRangesStr.split("[\n,;]+");
        for (String rawRange : ranges) {
            String range = rawRange.trim();
            if (range.isEmpty()) continue;

            if (matches(clientIp, range)) {
                return true;
            }
        }

        return false;
    }

    private static boolean matches(String ip, String pattern) {
        if (pattern.equals("*") || pattern.equalsIgnoreCase("any") || pattern.equals("0.0.0.0") || pattern.equals("0.0.0.0/0")) {
            return true;
        }
        if ("0:0:0:0:0:0:0:1".equals(pattern) || "::1".equals(pattern)) {
            pattern = "127.0.0.1";
        }

        // Exact match
        if (ip.equalsIgnoreCase(pattern)) {
            return true;
        }

        // Wildcard match (e.g., 192.168.1.*)
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return ip.matches(regex);
        }

        // CIDR notation (e.g., 192.168.1.0/24)
        if (pattern.contains("/")) {
            return isIpInCidr(ip, pattern);
        }

        // Range (e.g., 192.168.1.10-192.168.1.50 or 192.168.1.10-50)
        if (pattern.contains("-")) {
            return isIpInRange(ip, pattern);
        }

        return false;
    }

    private static boolean isIpInCidr(String ipStr, String cidrStr) {
        try {
            String[] parts = cidrStr.split("/");
            if (parts.length != 2) return false;
            long ip = ipToLong(InetAddress.getByName(ipStr));
            long cidrIp = ipToLong(InetAddress.getByName(parts[0].trim()));
            int prefix = Integer.parseInt(parts[1].trim());

            long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            return (ip & mask) == (cidrIp & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isIpInRange(String ipStr, String rangeStr) {
        try {
            String[] parts = rangeStr.split("-");
            if (parts.length != 2) return false;

            String startStr = parts[0].trim();
            String endStr = parts[1].trim();

            // Handle shorthand range like 192.168.1.10-50
            if (!endStr.contains(".")) {
                int lastDot = startStr.lastIndexOf('.');
                if (lastDot > 0) {
                    endStr = startStr.substring(0, lastDot + 1) + endStr;
                }
            }

            long target = ipToLong(InetAddress.getByName(ipStr));
            long start = ipToLong(InetAddress.getByName(startStr));
            long end = ipToLong(InetAddress.getByName(endStr));

            return target >= start && target <= end;
        } catch (Exception e) {
            return false;
        }
    }

    private static long ipToLong(InetAddress ip) {
        byte[] octets = ip.getAddress();
        long result = 0;
        for (byte octet : octets) {
            result = (result << 8) | (octet & 0xFF);
        }
        return result;
    }
}
