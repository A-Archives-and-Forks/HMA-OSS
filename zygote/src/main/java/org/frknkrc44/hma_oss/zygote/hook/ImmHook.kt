package org.frknkrc44.hma_oss.zygote.hook

import android.content.ComponentName
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.Utils
import icu.nullptr.hidemyapplist.common.settings_presets.InputMethodPreset
import org.frknkrc44.hma_oss.zygote.BulkHooker
import org.frknkrc44.hma_oss.zygote.HMAService
import org.frknkrc44.hma_oss.zygote.Utils4Zygote
import org.frknkrc44.hma_oss.zygote.ZygoteConstants.IMM_SERVICE_CLASS
import org.frknkrc44.hma_oss.zygote.logD
import org.frknkrc44.hma_oss.zygote.logE
import java.util.Collections

class ImmHook(private val service: HMAService) : IFrameworkHook {
    companion object {
        private const val TAG = "ImmHook"
    }

    // TODO: Find a method to get settings activity
    fun getFakeInputMethodInfo(packageName: String): InputMethodInfo {
        val defaultInputMethod = service.getSpoofedSetting(
            packageName,
            Settings.Secure.DEFAULT_INPUT_METHOD,
            Constants.SETTINGS_SECURE,
        )

        if (defaultInputMethod?.value != null) {
            try {
                val component = ComponentName.unflattenFromString(defaultInputMethod.value!!)!!
                logD(TAG, "Package component: \"$component\"")

                val pkgManager = Utils4Zygote.getPackageManager()
                val kbdPackage = Utils.binderLocalScope {
                    pkgManager.getApplicationInfo(component.packageName, 0)
                }

                return InputMethodInfo(
                    component.packageName,
                    component.className,
                    kbdPackage.loadLabel(pkgManager),
                    null,
                )
            } catch (e: Throwable) {
                logE(TAG, e.message ?: "", e)
            }
        }

        return InputMethodInfo(
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin.LatinIME",
            "Gboard",
            null,
        )
    }

    override fun load() {
        BulkHooker.instance.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                hookBefore(
                    IMM_SERVICE_CLASS,
                    "getCurrentInputMethodInfoAsUser",
                ) { param ->
                    val callingApps = Utils4Zygote.getCallingApps(service)

                    val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
                    if (caller != null) {
                        logD(TAG, "@${param.methodName} spoofed input method for $caller")

                        param.result = getFakeInputMethodInfo(caller)
                        service.increaseSettingsFilterCount(caller)
                    }
                }
            }

            hookBefore(
                IMM_SERVICE_CLASS,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                    "getInputMethodListInternal"
                else
                    "getInputMethodList",
            ) { param ->
                listHook(param)
            }

            hookBefore(
                IMM_SERVICE_CLASS,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                    "getEnabledInputMethodListInternal"
                else
                    "getEnabledInputMethodList",
            ) { param ->
                listHook(param)
            }

            hookBefore(
                IMM_SERVICE_CLASS,
                "getCurrentInputMethodSubtype",
            ) { param ->
                subtypeHook(param)
            }

            hookBefore(
                IMM_SERVICE_CLASS,
                "getLastInputMethodSubtype",
            ) { param ->
                subtypeHook(param)
            }

            hookBefore(
                IMM_SERVICE_CLASS,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
                    "getEnabledInputMethodSubtypeListInternal"
                else
                    "getEnabledInputMethodSubtypeList",
            ) { param ->
                subtypeListHook(param)
            }
        }
    }

    private fun listHook(param: BulkHooker.HookParam) {
        val callingApps = if (param.methodName.endsWith("Internal")) {
            val callingUid = param.args.findLast { it is Int } as Int
            Utils4Zygote.getCallingApps(service, callingUid)
        } else {
            Utils4Zygote.getCallingApps(service)
        }

        val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
        if (caller != null) {
            logD(TAG, "@${param.methodName} spoofed input method for $caller")

            param.result = listOf(getFakeInputMethodInfo(caller))
            service.increaseSettingsFilterCount(caller)
        }
    }

    private fun subtypeHook(param: BulkHooker.HookParam) {
        val callingApps = Utils4Zygote.getCallingApps(service)

        val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
        if (caller != null) {
            logD(TAG, "@${param.methodName} spoofed input method subtype for ${callingApps.contentToString()}")

            // TODO: Find a method to get exact value for spoofed input method
            param.result = null
            service.increaseSettingsFilterCount(caller)
        }
    }

    private fun subtypeListHook(param: BulkHooker.HookParam) {
        val callingApps = Utils4Zygote.getCallingApps(service)

        val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
        if (caller != null) {
            logD(TAG, "@${param.methodName} spoofed input method subtype for ${callingApps.contentToString()}")

            // TODO: Find a method to get exact list for spoofed input method
            param.result = Collections.emptyList<InputMethodSubtype>()
            service.increaseSettingsFilterCount(caller)
        }
    }

    private fun callerIsSpoofed(caller: String) =
        service.getEnabledSettingsPresets(caller).contains(InputMethodPreset.NAME)
}
