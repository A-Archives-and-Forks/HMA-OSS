package org.frknkrc44.hma_oss.zygote

import android.util.Log
import com.v7878.unsafe.Reflection
import com.v7878.unsafe.invoke.EmulatedStackFrame
import com.v7878.unsafe.invoke.EmulatedStackFrame.RETURN_VALUE_IDX
import com.v7878.unsafe.invoke.Transformers
import com.v7878.vmtools.HookTransformer
import com.v7878.vmtools.Hooks
import org.frknkrc44.hma_oss.common.BuildConfig
import org.frknkrc44.hma_oss.zygote.ZygoteEntry.TAG
import java.lang.invoke.MethodHandle
import java.lang.reflect.Executable
import java.util.regex.Pattern
import java.util.stream.Stream

class BulkHooker private constructor() {
    companion object {
        val instance: BulkHooker by lazy { BulkHooker() }
    }

    private val hooks: MutableMap<String, MutableList<HookElement>> =
        HashMap<String, MutableList<HookElement>>()

    private fun addPattern(clazz: String, pattern: String, hookFirst: Boolean, paramCount: Int, impl: HookTransformer) {
        hooks.computeIfAbsent(clazz) { _ -> mutableListOf() }
            .add(HookElement(
                impl = impl,
                pattern = pattern,
                hookFirst = hookFirst,
                paramCount = paramCount,
            ))
    }

    private fun addAll(clazz: String, methodName: String, hookFirst: Boolean, paramCount: Int, impl: HookTransformer) {
        addPattern(
            clazz,
            String.format("%s\\(.*\\).*", Pattern.quote(methodName)),
            hookFirst,
            paramCount,
            impl,
        )
    }

    internal fun hookBefore(
        clazz: String,
        methodName: String,
        autoApply: Boolean = true,
        hookFirst: Boolean = true,
        paramCount: Int = -1,
        hook: (param: HookParam) -> Unit,
    ) {
        addAll(clazz, methodName, hookFirst, paramCount) { original, frame ->
            val value = ReturnValue()

            runCatching {
                hook(HookParam(clazz, original, frame, methodName, value))
            }.onFailure {
                logE(TAG, it.message ?: "Unknown error on hook", it)
            }

            if (!value.replace) {
                Transformers.invokeExact(original, frame)
            }

            if (value.replace && frame.type().returnType() != Void::class.java) {
                frame.accessor().setValue(RETURN_VALUE_IDX, value.value)
            }
        }

        if (autoApply) {
            applyHooks()
        }
    }

    internal fun hookAfter(
        clazz: String,
        methodName: String,
        autoApply: Boolean = true,
        hookFirst: Boolean = true,
        paramCount: Int = -1,
        hook: (param: HookParam) -> Unit,
    ) {
        addAll(clazz, methodName, hookFirst, paramCount) { original, frame ->
            var throwable: Throwable? = null

            runCatching {
                Transformers.invokeExact(original, frame)
            }.onFailure {
                throwable = it
            }

            val value = ReturnValue(if (throwable == null) {
                frame.accessor().getValue(RETURN_VALUE_IDX)
            } else null)
            value.throwable = throwable

            runCatching {
                hook(HookParam(clazz, original, frame, methodName, value))
            }.onFailure {
                logE(TAG, it.message ?: "Unknown error on hook", it)
            }

            if (throwable != null) {
                throw throwable
            }

            if (value.replace && frame.type().returnType() != Void::class.java) {
                frame.accessor().setValue(RETURN_VALUE_IDX, value.value)
            }
        }

        if (autoApply) {
            applyHooks()
        }
    }

    internal fun applyHooks(loader: ClassLoader? = SystemServerHook.classLoader) {
        for (entry in hooks.entries) {
            var curClazz: Class<*>?
            try {
                curClazz = Class.forName(entry.key, true, loader)
            } catch (ex: ClassNotFoundException) {
                Log.e(TAG, java.lang.String.format("Class %s not found", entry.key), ex)
                continue
            }

            fun apply(clazz: Class<*>?) {
                val executables = Reflection.getHiddenExecutables(clazz).sortedWith { v1, v2 ->
                    v1.parameterCount.compareTo(v2.parameterCount)
                }.toTypedArray()
                for (element in entry.value) {
                    Stream.of<Executable>(*executables)
                        .filter(Utils4Zygote.filter(element.pattern))
                        .forEach { executable: Executable ->
                            if (element.paramCount != -1 && executable.parameterCount != element.paramCount) {
                                return@forEach
                            }

                            if (!element.hookFirst || element.applyCount < 1) {
                                if (BuildConfig.DEBUG) {
                                    logI(TAG, "Hooked: $executable")
                                }
                                Hooks.hook(
                                    executable, Hooks.EntryPointType.DIRECT,
                                    element.impl, Hooks.EntryPointType.DIRECT
                                )

                                element.applyCount++
                            }
                        }
                }
            }

            while (
                curClazz != null &&
                entry.value.any { it.applyCount < 1 } &&
                curClazz.javaClass.simpleName != "Object"
            ) {
                apply(curClazz)
                curClazz = curClazz.superclass
            }

            // remove invalid entries
            entry.value.removeIf {
                (it.applyCount < 1).apply {
                    if (this@apply) {
                        logI(TAG, "Invalid hook removed: ${it.pattern}")
                    }
                }
            }
        }
    }

    data class HookElement(
        val impl: HookTransformer,
        val pattern: String,
        val hookFirst: Boolean,
        val paramCount: Int = -1,
        var applyCount: Int = 0,
    )

    class ReturnValue(initialValue: Any? = null) {
        var replace: Boolean = false
            private set

        var value: Any? = initialValue
            set(value) {
                field = value
                replace = true
            }

        var throwable: Throwable? = null
    }

    data class HookParam(
        val clazz: String,
        val original: MethodHandle,
        val frame: EmulatedStackFrame,
        val methodName: String,
        val returnValue: ReturnValue,
    ) {
        var result: Any?
            get() = returnValue.value
            set(newValue) { returnValue.value = newValue }

        /**
         * Returns the first argument
         */
        val thisObject by lazy { Utils4Zygote.getArgument(frame, 0) }

        fun getArgument(index: Int) = Utils4Zygote.getArgument(frame, index)

        fun setArgument(index: Int, value: Any) = Utils4Zygote.setArgument(frame, index, value)

        /**
         * - `args[0] == thisObject`
         * - `args[1:] == function args`
         *
         * Note that this variable is null when `dumpArgs == false`
         */
        val args by lazy { Utils4Zygote.dumpArgs(frame) }

        var throwable: Throwable?
            get() = returnValue.throwable
            set(newValue) { returnValue.throwable = newValue }
    }
}
