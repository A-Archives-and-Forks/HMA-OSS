package org.frknkrc44.hma_oss.zygote.service

import com.v7878.unsafe.invoke.EmulatedStackFrame
import com.v7878.vmtools.HookTransformer
import org.frknkrc44.hma_oss.zygote.util.Utils4Zygote
import java.lang.invoke.MethodHandle
import java.lang.reflect.Executable

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
    var method: Executable? = null,
    var memoryAddresses: android.util.Pair<Long, Long>? = null,
    var hookFinished: Boolean = false,
    val paramCount: Int = -1,
    var applyCount: Int = 0,
)
