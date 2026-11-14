package at.libmatti.mtztool.util

import android.util.Log

object MiuiApmHelper {
    private const val TAG = "MiuiApmHelper"
    private const val TARGET_CLASS = "com.miui.miapm.MiApmManager" // Replace with exact target class name from firmware dump

    fun isMiApmAvailable(): Boolean {
        return try {
            Class.forName(TARGET_CLASS)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    fun invokeSystemMethod(methodName: String, vararg args: Any?): Any? {
        if (isMiApmAvailable())
            return try {
                val clazz = Class.forName(TARGET_CLASS)
                // Adjust parameter types to match the exact signature in the decompiled framework
                val paramTypes = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
                val method = clazz.getDeclaredMethod(methodName, *paramTypes).apply {
                    isAccessible = true
                }

                // Pass 'null' as the first argument if invoking a static method,
                // or pass the retrieved singleton instance if it's an instance method.
                method.invoke(null, *args)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to invoke $methodName on $TARGET_CLASS", e)
                null
            }
        else
            return "Failed"
    }
}
