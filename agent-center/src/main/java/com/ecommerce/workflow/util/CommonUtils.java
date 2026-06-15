package com.ecommerce.workflow.util;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CommonUtils {

    private CommonUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String generateShortId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String generateId(String prefix, int length) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, Math.min(32, length));
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }

    public static String truncateWithEllipsis(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    public static String safeSubstring(String str, int maxLength) {
        return truncate(str, maxLength);
    }

    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static String defaultIfEmpty(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }

    public static String defaultIfBlank(String str, String defaultValue) {
        return isBlank(str) ? defaultValue : str;
    }

    public static <T> boolean isEmpty(T[] array) {
        return array == null || array.length == 0;
    }

    public static <T> boolean isNotEmpty(T[] array) {
        return !isEmpty(array);
    }

    public static String maskSensitiveInfo(String str, int keepStart, int keepEnd) {
        if (str == null || str.length() <= keepStart + keepEnd) {
            return str;
        }
        
        StringBuilder masked = new StringBuilder();
        if (keepStart > 0) {
            masked.append(str.substring(0, keepStart));
        }
        
        int maskLength = str.length() - keepStart - keepEnd;
        for (int i = 0; i < maskLength; i++) {
            masked.append("*");
        }
        
        if (keepEnd > 0) {
            masked.append(str.substring(str.length() - keepEnd));
        }
        
        return masked.toString();
    }

    public static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            long minutes = millis / 60000;
            long seconds = (millis % 60000) / 1000;
            return minutes + "m" + seconds + "s";
        }
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    public static String formatPercentage(double value) {
        return String.format("%.2f%%", value * 100);
    }

    public static boolean isValidEmail(String email) {
        if (isEmpty(email)) {
            return false;
        }
        Pattern pattern = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
        return pattern.matcher(email).matches();
    }

    public static boolean isValidPhone(String phone) {
        if (isEmpty(phone)) {
            return false;
        }
        Pattern pattern = Pattern.compile("^1[3-9]\\d{9}$");
        return pattern.matcher(phone).matches();
    }

    public static boolean containsIgnoreCase(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        return str.toLowerCase().contains(searchStr.toLowerCase());
    }

    public static String capitalizeFirst(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static String toLowerCase(String str) {
        return str == null ? null : str.toLowerCase();
    }

    public static String toUpperCase(String str) {
        return str == null ? null : str.toUpperCase();
    }

    public static String repeat(String str, int count) {
        if (str == null || count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    public static String padLeft(String str, char padChar, int totalLength) {
        if (str == null) {
            str = "";
        }
        if (str.length() >= totalLength) {
            return str;
        }
        return repeat(String.valueOf(padChar), totalLength - str.length()) + str;
    }

    public static String padRight(String str, char padChar, int totalLength) {
        if (str == null) {
            str = "";
        }
        if (str.length() >= totalLength) {
            return str;
        }
        return str + repeat(String.valueOf(padChar), totalLength - str.length());
    }

    public static String removeExtraWhitespace(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.replaceAll("\\s+", " ").trim();
    }

    public static String toCamelCase(String str) {
        if (isEmpty(str)) {
            return str;
        }
        
        String[] parts = str.split("_");
        StringBuilder result = new StringBuilder(parts[0].toLowerCase());
        
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result.append(Character.toUpperCase(parts[i].charAt(0)))
                      .append(parts[i].substring(1).toLowerCase());
            }
        }
        
        return result.toString();
    }

    public static String toSnakeCase(String str) {
        if (isEmpty(str)) {
            return str;
        }
        
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    public static Integer parseInteger(String str, Integer defaultValue) {
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Long parseLong(String str, Long defaultValue) {
        try {
            return Long.parseLong(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Double parseDouble(String str, Double defaultValue) {
        try {
            return Double.parseDouble(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Boolean parseBoolean(String str, Boolean defaultValue) {
        if (isEmpty(str)) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(str) || "1".equals(str) || "yes".equalsIgnoreCase(str)) {
            return true;
        }
        if ("false".equalsIgnoreCase(str) || "0".equals(str) || "no".equalsIgnoreCase(str)) {
            return false;
        }
        return defaultValue;
    }

    public static String join(String delimiter, String... elements) {
        if (elements == null || elements.length == 0) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(elements[i]);
        }
        
        return sb.toString();
    }

    public static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        java.util.Random random = new java.util.Random();
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }

    public static String extractDomainFromUrl(String url) {
        if (isEmpty(url)) {
            return null;
        }
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getHost();
        } catch (Exception e) {
            return url;
        }
    }

    public static String sanitizeForLog(Object obj) {
        if (obj == null) {
            return "null";
        }
        
        String str = obj.toString();
        if (str.length() > 500) {
            return str.substring(0, 500) + "... [truncated]";
        }
        
        return str;
    }

    public static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static <T> T getOrDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    public static RuntimeException runtimeException(String message, Throwable cause) {
        return new RuntimeException(message, cause);
    }

    public static IllegalArgumentException illegalArgument(String message) {
        return new IllegalArgumentException(message);
    }

    public static IllegalStateException illegalState(String message) {
        return new IllegalStateException(message);
    }

    public static UnsupportedOperationException unsupportedOperation(String message) {
        return new UnsupportedOperationException(message);
    }
}
