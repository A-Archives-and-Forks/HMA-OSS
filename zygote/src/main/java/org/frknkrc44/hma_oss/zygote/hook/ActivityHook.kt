package org.frknkrc44.hma_oss.zygote.hook

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.Utils
import org.frknkrc44.hma_oss.zygote.BulkHooker
import org.frknkrc44.hma_oss.zygote.HMAService
import org.frknkrc44.hma_oss.zygote.Utils4Zygote
import org.frknkrc44.hma_oss.zygote.Utils4Zygote.getObjectField
import org.frknkrc44.hma_oss.zygote.Utils4Zygote.getStaticIntField
import org.frknkrc44.hma_oss.zygote.ZygoteConstants.ACTIVITY_STARTER_CLASS
import org.frknkrc44.hma_oss.zygote.ZygoteConstants.COMPUTER_ENGINE_CLASS
import org.frknkrc44.hma_oss.zygote.ZygoteConstants.PACKAGE_MANAGER_SERVICE_CLASS
import org.frknkrc44.hma_oss.zygote.logD
import org.frknkrc44.hma_oss.zygote.logI
import org.frknkrc44.hma_oss.zygote.logV

class ActivityHook(private val service: HMAService) : IFrameworkHook {
    companion object {
        private const val TAG = "ActivityHook"
        private val fakeReturnCode by lazy {
            getStaticIntField(
                "android.app.ActivityManager",
                "START_CLASS_NOT_FOUND",
            )
        }
    }

    override fun load() {
        logI(TAG, "Load hook")

        BulkHooker.instance.apply {
            hookBefore(
                ACTIVITY_STARTER_CLASS,
                "execute",
            ) { param ->
                val request = getObjectField(param.thisObject, "mRequest") ?: return@hookBefore
                val caller = getObjectField(request, "callingPackage") as String?
                val intent = getObjectField(request, "intent") as Intent?
                val targetApp = intent?.component?.packageName

                if (service.shouldHideActivityLaunch(caller, targetApp)) {
                    logD(
                        TAG,
                        "@executeRequest: insecure query from $caller, target: ${intent?.component}"
                    )
                    param.result = fakeReturnCode
                    service.increaseALFilterCount(caller)
                }
            }

            /*
            // TODO: Maybe not required anymore?
            hookBefore(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ACTIVITY_TASK_SUPERVISOR_CLASS
                } else {
                    ACTIVITY_STACK_SUPERVISOR_CLASS
                },
                "checkStartAnyActivityPermission",
            ) { param ->
                var throwable = param.throwable

                while (throwable != null) {
                    val newTrace = throwable.stackTrace.filter { item ->
                        !Utils.containsMultiple(
                            item.className,
                            "HookBridge",
                            "LSPHooker",
                            "LSPosed",
                        )
                    }

                    if (newTrace.size != throwable.stackTrace.size) {
                        throwable.stackTrace = newTrace.toTypedArray()

                        val callingUid = param.args!!.lastOrNull { it is Int } as Int?

                        logD(TAG, "@checkStartAnyActivityPermission: ${throwable.stackTrace.size - newTrace.size} remnants cleared for $callingUid!")

                        service.increaseALFilterCount(callingUid)
                    }

                    throwable = throwable.cause
                }
            }
             */

            if (!Utils.isSamsung()) {
                hookBefore(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        COMPUTER_ENGINE_CLASS
                    } else {
                        PACKAGE_MANAGER_SERVICE_CLASS
                    },
                    "applyPostResolutionFilter",
                ) { param ->
                    @Suppress("UNCHECKED_CAST") // I know what I do
                    val list = param.args[1] as List<ResolveInfo>?
                    if (list.isNullOrEmpty()) return@hookBefore

                    val callingUid = param.args.first { it is Int } as Int
                    if (callingUid == Constants.UID_SYSTEM) return@hookBefore

                    val callingApps = Utils4Zygote.getCallingApps(service, callingUid)
                    val caller = callingApps.firstOrNull { service.isHookEnabled(it) }
                    if (caller != null) {
                        logV(TAG, "@${param.methodName}: $caller requested a resolve info")

                        val filteredList = list.filter { resolveInfo ->
                            val targetApp = Utils.getPackageNameFromResolveInfo(resolveInfo)

                            logV(TAG, "@${param.methodName}: Checking $targetApp for $caller")

                            (!service.shouldHideActivityLaunch(caller, targetApp)).apply {
                                if (!this) {
                                    logD(TAG, "@${param.methodName}: Filtered $targetApp from $caller")
                                }
                            }
                        }

                        if (filteredList.size != list.size) {
                            param.setArgument(1, filteredList.toList())

                            service.increasePMFilterCount(caller)
                        }
                    }
                }
            }
        }
    }
}
