package com.dicar.vehicle.data.source.bydauto

import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Reflection wrapper for one BYDAuto device class or BYDAutoDeviceManager.
 *
 * - Class and instance load lazily; failures are not retried in the same process and are recorded in [initError];
 * - Methods are cached by name and argument count; missing methods are recorded;
 * - Failures are logged once and returned as [CallResult.Missing] or [CallResult.Error], never thrown upstream.
 *
 * BYDAuto getter/setter arguments are mostly ints (zones, positions, levels), so this uses Int arguments
 * and matches only numeric or Boolean overloads, avoiding methods such as get(int[], Class).
 */
class BydDevice(private val context: Context, val className: String) {

    sealed interface CallResult {
        data class Value(val value: Any?) : CallResult
        data object Missing : CallResult
        data class Error(val error: Throwable) : CallResult
    }

    private var loaded = false
    private var instance: Any? = null
    private val methodCache = HashMap<String, Method?>()
    private val reported = HashSet<String>()

    /** Reason getInstance failed, or null when the class does not exist. */
    var initError: Throwable? = null
        private set

    val isAvailable: Boolean get() = obtain() != null

    /** Get the singleton, preferring getInstance(Context) over getInstance(). */
    @Synchronized
    fun obtain(): Any? {
        if (loaded) return instance
        loaded = true
        instance = try {
            val cls = Class.forName(className)
            val byContext = cls.methods.firstOrNull {
                it.name == "getInstance" && Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == Context::class.java
            }
            val noArg = cls.methods.firstOrNull {
                it.name == "getInstance" && Modifier.isStatic(it.modifiers) && it.parameterTypes.isEmpty()
            }
            when {
                byContext != null -> byContext.invoke(null, context)
                noArg != null -> noArg.invoke(null)
                else -> null
            }
        } catch (e: ClassNotFoundException) {
            null // Non-BYD systems or models without this device are normal; do not log a stack trace.
        } catch (e: Throwable) {
            initError = unwrap(e)
            Log.w(TAG, "getInstance failed: $className", initError)
            null
        }
        if (instance == null) Log.i(TAG, "device unavailable: $className")
        return instance
    }

    fun call(method: String, vararg args: Int): CallResult {
        val target = obtain() ?: return CallResult.Missing
        val m = findMethod(target.javaClass, method, args.size) ?: run {
            logOnce("missing:$method/${args.size}") { "method not found: ${shortName()}.$method(${args.size} args)" }
            return CallResult.Missing
        }
        return try {
            CallResult.Value(m.invoke(target, *convertArgs(m, args)))
        } catch (e: Throwable) {
            val cause = unwrap(e)
            logOnce("error:$method/${args.size}:${cause.javaClass.name}") {
                "call failed: ${shortName()}.$method${args.contentToString()} -> $cause"
            }
            CallResult.Error(cause)
        }
    }

    /** Read a public static constant such as a zone ID, or null if unavailable. */
    fun constant(name: String): Int? = try {
        (Class.forName(className).getField(name).get(null) as? Number)?.toInt()
    } catch (e: Throwable) {
        null
    }

    private fun findMethod(cls: Class<*>, name: String, argCount: Int): Method? {
        val key = "$name/$argCount"
        if (methodCache.containsKey(key)) return methodCache[key]
        val found = cls.methods
            .filter { m -> m.name == name && m.parameterTypes.size == argCount && m.parameterTypes.all { it in INT_CONVERTIBLE } }
            // Prefer all-int parameters when overloads exist.
            .sortedByDescending { m -> m.parameterTypes.count { it == Int::class.javaPrimitiveType } }
            .firstOrNull()
        methodCache[key] = found
        return found
    }

    private fun convertArgs(m: Method, args: IntArray): Array<Any> =
        Array(args.size) { i ->
            when (m.parameterTypes[i]) {
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> args[i].toLong()
                Double::class.javaPrimitiveType, Double::class.javaObjectType -> args[i].toDouble()
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> args[i].toFloat()
                Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> args[i] != 0
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> args[i].toShort()
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> args[i].toByte()
                else -> args[i]
            }
        }

    private fun shortName() = className.substringAfterLast('.')

    private inline fun logOnce(key: String, message: () -> String) {
        if (reported.add(key)) Log.w(TAG, message())
    }

    companion object {
        private const val TAG = "BydDevice"

        private val INT_CONVERTIBLE: Set<Class<*>?> = setOf(
            Int::class.javaPrimitiveType, Int::class.javaObjectType,
            Long::class.javaPrimitiveType, Long::class.javaObjectType,
            Double::class.javaPrimitiveType, Double::class.javaObjectType,
            Float::class.javaPrimitiveType, Float::class.javaObjectType,
            Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType,
            Short::class.javaPrimitiveType, Short::class.javaObjectType,
            Byte::class.javaPrimitiveType, Byte::class.javaObjectType,
        )

        fun unwrap(e: Throwable): Throwable =
            if (e is InvocationTargetException && e.targetException != null) e.targetException else e
    }
}
