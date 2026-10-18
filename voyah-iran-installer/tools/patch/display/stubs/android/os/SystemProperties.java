package android.os;
public class SystemProperties { public static String value; public static String get(String key, String fallback) { return value == null ? fallback : value; } }
