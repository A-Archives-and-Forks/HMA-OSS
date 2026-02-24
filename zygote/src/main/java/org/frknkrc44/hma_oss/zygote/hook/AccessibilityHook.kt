package org.frknkrc44.hma_oss.zygote.hook

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ParceledListSlice
import icu.nullptr.hidemyapplist.common.settings_presets.AccessibilityPreset
import org.frknkrc44.hma_oss.zygote.BulkHooker
import org.frknkrc44.hma_oss.zygote.HMAService
import org.frknkrc44.hma_oss.zygote.Utils4Zygote
import org.frknkrc44.hma_oss.zygote.ZygoteConstants.ACCESSIBILITY_SERVICE_CLASS
import org.frknkrc44.hma_oss.zygote.logD
import org.frknkrc44.hma_oss.zygote.logE

class AccessibilityHook(private val service: HMAService) : IFrameworkHook {
    companion object {
        private const val TAG = "AccessibilityHook"
    }

    override fun load() {
        BulkHooker.instance.apply {
            hookBefore(
                ACCESSIBILITY_SERVICE_CLASS,
                "getInstalledAccessibilityServiceList",
                dumpArgs = false,
            ) { param -> hookedMethod(param) }

            hookBefore(
                ACCESSIBILITY_SERVICE_CLASS,
                "getEnabledAccessibilityServiceList",
                dumpArgs = false,
            ) { param -> hookedMethod(param) }

            hookBefore(
                ACCESSIBILITY_SERVICE_CLASS,
                "addClient",
                dumpArgs = false,
            ) { param ->
                val callingApps = Utils4Zygote.getCallingApps(service)
                if (callingApps.isEmpty()) return@hookBefore

                val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
                if (caller != null) {
                    param.result = 0L
                    // service.increasePMFilterCount(caller)
                }
            }
        }
    }

    private fun callerIsSpoofed(caller: String) =
        service.getEnabledSettingsPresets(caller).contains(AccessibilityPreset.NAME)

    private fun hookedMethod(param: BulkHooker.HookParam) {
        try {
            val callingApps = Utils4Zygote.getCallingApps(service)
            if (callingApps.isEmpty()) return

            val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
            if (caller != null) {
                val returnedList = java.util.ArrayList<AccessibilityServiceInfo>()

                logD(TAG, "@${param.methodName} returned empty list for ${callingApps.contentToString()}")

                val returnParcel = param.frame.type().returnType().javaClass == ParceledListSlice::class.java
                param.result = if (returnParcel) {
                    ParceledListSlice(returnedList)
                } else {
                    returnedList
                }

                // service.increasePMFilterCount(caller)
            }
        } catch (e: Throwable) {
            logE(TAG, "Fatal error occurred, ignore hooks", e)
        }
    }
}
