package org.frknkrc44.hma_oss.zygote.service

import android.annotation.SuppressLint
import android.content.pm.IPackageManager
import com.v7878.r8.annotations.DoNotShrink
import com.v7878.unsafe.Reflection.getDeclaredMethod
import com.v7878.unsafe.invoke.EmulatedStackFrame
import com.v7878.unsafe.invoke.Transformers
import com.v7878.vmtools.Hooks
import org.frknkrc44.hma_oss.zygote.util.Utils4Zygote
import org.frknkrc44.hma_oss.zygote.util.logD
import org.frknkrc44.hma_oss.zygote.util.logE
import org.frknkrc44.hma_oss.zygote.util.logI
import org.frknkrc44.hma_oss.zygote.util.logV
import kotlin.concurrent.thread

@SuppressLint("PrivateApi")
object SystemServerHook {
    private const val TAG = "SystemServerHook"
    private const val SYSTEM_SERVER: String = "com.android.server.SystemServer"
    private const val RUNTIME_INIT: String = "com.android.internal.os.RuntimeInit"

    var classLoader: ClassLoader? = null
    var initialized = false

    @Throws(Throwable::class)
    fun onSystemServer(loader: ClassLoader?) {
        logV(TAG, "Class loader found: $loader")

        classLoader = loader

        if (!initialized) {
            initialized = true

            thread {
                val pms = Utils4Zygote.waitForService("package") as IPackageManager
                val pmn = Utils4Zygote.waitForService("package_native")
                logD(TAG, "Got pms: $pms, $pmn")

                runCatching {
                    UserService.register(pms, pmn)
                    logI(TAG, "User service started")
                }.onFailure {
                    logE(TAG, "System service crashed", it)
                }
            }
        }
    }

    @Throws(Throwable::class)
    private fun checkSystemServer(frame: EmulatedStackFrame) {
        val accessor = frame.accessor()
        if (SYSTEM_SERVER == accessor.getReference(0)) {
            val loader: ClassLoader? = accessor.getReference(2)
            onSystemServer(loader)
        }
    }

    @DoNotShrink
    @Throws(Throwable::class)
    @JvmStatic
    fun init() {
        val method = getDeclaredMethod(
            Class.forName(RUNTIME_INIT), "findStaticMain",
            String::class.java, Array<String>::class.java, ClassLoader::class.java
        )

        Hooks.hook(method, Hooks.EntryPointType.CURRENT, { original, frame ->
            try {
                checkSystemServer(frame)
            } catch (th: Throwable) {
                logE(TAG, "An exception occurred while checkSystemServer", th)
            }
            Transformers.invokeExact(original, frame)
        }, Hooks.EntryPointType.DIRECT)
    }
}
