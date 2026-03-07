package org.frknkrc44.hma_oss.zygote

import com.v7878.unsafe.Reflection
import com.v7878.unsafe.invoke.EmulatedStackFrame
import com.v7878.unsafe.invoke.EmulatedStackFrame.RETURN_VALUE_IDX
import com.v7878.unsafe.invoke.Transformers
import com.v7878.vmtools.HookTransformer
import com.v7878.vmtools.Hooks
import org.frknkrc44.hma_oss.common.BuildConfig
import org.frknkrc44.hma_oss.zygote.ZygoteEntry.TAG
import java.lang.invoke.MethodHandle

class BulkHooker private constructor() {
    companion object {
        val instance: BulkHooker by lazy { BulkHooker() }
    }

    private val hooks: MutableMap<String, MutableList<HookElement>> =
        HashMap<String, MutableList<HookElement>>()

    private fun addAll(clazz: String, methodName: String, hookOnce: Boolean, paramCount: Int, impl: HookTransformer) {
        val element = HookElement(
            impl = impl,
            methodName = methodName,
            hookOnce = hookOnce,
            paramCount = paramCount,
        )

        if (applyHook(clazz, element)) {
            hooks.computeIfAbsent(clazz) { _ -> mutableListOf() }.add(element)
        } else {
            logI(TAG, "Invalid hook removed: $clazz -> $methodName($paramCount)")
        }
    }

    internal fun hookBefore(
        clazz: String,
        methodName: String,
        hookOnce: Boolean = true,
        paramCount: Int = -1,
        hook: (param: HookParam) -> Unit,
    ) {
        addAll(clazz, methodName, hookOnce, paramCount) { original, frame ->
            val value = ReturnValue()

            try {
                hook(HookParam(clazz, original, frame, methodName, value))
            } catch (it: Throwable) {
                logE(TAG, it.message ?: "Unknown error on hook", it)
            }

            if (!value.replace) {
                try {
                    Transformers.invokeExactPlain(original, frame)
                } catch (it: Throwable) {
                    logE(TAG, it.message ?: "Unknown error on original function", it)
                    value.throwable = it
                }
            }

            value.throwable?.let { throw it }

            if (value.replace) {
                Utils4Zygote.setReturnValue(frame, value.result)
            }
        }
    }

    internal fun hookAfter(
        clazz: String,
        methodName: String,
        hookOnce: Boolean = true,
        paramCount: Int = -1,
        hook: (param: HookParam) -> Unit,
    ) {
        addAll(clazz, methodName, hookOnce, paramCount) { original, frame ->
            val value = ReturnValue()

            try {
                Transformers.invokeExactPlain(original, frame)
            } catch (it: Throwable) {
                value.throwable = it
            }

            if (value.throwable == null) {
                value.setResultWithoutReplace(frame.accessor().getValue(RETURN_VALUE_IDX))
            }

            try {
                hook(HookParam(clazz, original, frame, methodName, value))
            } catch (it: Throwable) {
                logE(TAG, it.message ?: "Unknown error on hook", it)
            }

            value.throwable?.let { throw it }

            Utils4Zygote.setReturnValue(frame, value.result)
        }
    }

    internal fun applyHook(
        clazz: String,
        element: HookElement,
        loader: ClassLoader? = SystemServerHook.classLoader,
    ): Boolean {
        var curClazz: Class<*>?
        try {
            curClazz = Class.forName(clazz, true, loader)
        } catch (ex: ClassNotFoundException) {
            logE(TAG, "Class $clazz not found", ex)
            return false
        }

        fun applyForClass(clazz: Class<*>?) {
            val executables = Reflection.getHiddenExecutables(clazz).filter { executable ->
                element.methodName == executable.name &&
                    (element.paramCount in listOf(-1, executable.parameterCount - 1))
            }.sortedWith { v1, v2 ->
                v1.parameterCount.compareTo(v2.parameterCount)
            }

            for (executable in executables) {
                if (!element.hookFinished) {
                    if (BuildConfig.DEBUG) {
                        logI(TAG, "Hooked: $executable")
                    }

                    Hooks.hook(
                        executable, Hooks.EntryPointType.DIRECT,
                        element.impl, Hooks.EntryPointType.DIRECT
                    )

                    element.applyCount++

                    if (element.hookOnce) {
                        element.hookFinished = true
                        break
                    }
                }
            }
        }

        while (
            !element.hookFinished &&
            curClazz != null &&
            curClazz.javaClass.simpleName != "Object"
        ) {
            applyForClass(curClazz)
            curClazz = curClazz.superclass
        }

        if (!element.hookOnce && element.applyCount >= 1) {
            element.hookFinished = true
        }

        return element.hookFinished
    }

    class ReturnValue(initialValue: Any? = null) {
        var replace: Boolean = false
            private set

        var result: Any? = initialValue
            set(newValue) {
                field = newValue
                replace = true
            }

        fun setResultWithoutReplace(newValue: Any?) {
            result = newValue
            replace = false
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
            get() = returnValue.result
            set(newValue) { returnValue.result = newValue }

        /**
         * Returns the first argument
         */
        val thisObject by lazy { Utils4Zygote.getArgument(frame, 0) }

        fun getArgument(index: Int) = Utils4Zygote.getArgument(frame, index)

        fun setArgument(index: Int, value: Any) = Utils4Zygote.setArgument(frame, index, value)

        /**
         * - `args[0] == thisObject`
         * - `args[1:] == function args`
         */
        val args by lazy { Utils4Zygote.dumpArgs(frame) }

        var throwable: Throwable?
            get() = returnValue.throwable
            set(newValue) { returnValue.throwable = newValue }
    }

    data class HookElement(
        val impl: HookTransformer,
        val methodName: String,
        val hookOnce: Boolean,
        var hookFinished: Boolean = false,
        val paramCount: Int = -1,
        var applyCount: Int = 0,
    )
}
