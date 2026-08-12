package de.robv.android.xposed;

public final class XposedHelpers {
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) { return null; }
    public static Object getObjectField(Object obj, String fieldName) { return null; }
    public static Object getStaticObjectField(Class<?> clazz, String fieldName) { return null; }
    public static boolean getBooleanField(Object obj, String fieldName) { return false; }
    public static int getIntField(Object obj, String fieldName) { return 0; }
    public static long getLongField(Object obj, String fieldName) { return 0; }
    public static String getStringField(Object obj, String fieldName) { return null; }
    public static double getDoubleField(Object obj, String fieldName) { return 0; }
    public static int getStaticIntField(Class<?> clazz, String fieldName) { return 0; }
    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }
    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) { return null; }
}
