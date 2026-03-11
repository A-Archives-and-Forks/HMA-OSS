package org.frknkrc44.hma_oss.zygote.hook

import android.content.ComponentName
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.Utils
import icu.nullptr.hidemyapplist.common.settings_presets.InputMethodPreset
import org.frknkrc44.hma_oss.zygote.service.BulkHooker
import org.frknkrc44.hma_oss.zygote.service.HMAService
import org.frknkrc44.hma_oss.zygote.service.HookParam
import org.frknkrc44.hma_oss.zygote.util.Utils4Zygote
import org.frknkrc44.hma_oss.zygote.util.ZygoteConstants.IMM_IMPL_CLASS
import org.frknkrc44.hma_oss.zygote.util.ZygoteConstants.IMM_SERVICE_CLASS
import org.frknkrc44.hma_oss.zygote.util.logD
import org.frknkrc44.hma_oss.zygote.util.logV
import java.util.Collections

class ImmHook(private val service: HMAService) : IFrameworkHook {
    override val TAG = "ImmHook"

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
                logV(TAG, e.message ?: "", e)
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
        // OEMs (especially Samsung and Xiaomi) messes up whole framework code,
        // so nothing left except messing up this code
        BulkHooker.instance.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                findAltMethod(
                    listOf(IMM_SERVICE_CLASS, IMM_IMPL_CLASS),
                    listOf("getCurrentInputMethodInfoAsUser"),
                )?.let {
                    hookBefore(
                        it.declaringClass.name,
                        it.name,
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
            }

            findAltMethod(
                listOf(IMM_SERVICE_CLASS),
                listOf("getInputMethodList", "getInputMethodListInternal"),
            )?.let {
                hookBefore(
                    it.declaringClass.name,
                    it.name,
                ) { param ->
                    listHook(param)
                }
            }

            findAltMethod(
                listOf(IMM_SERVICE_CLASS),
                listOf("getEnabledInputMethodList", "getEnabledInputMethodListInternal"),
            )?.let {
                hookBefore(
                    it.declaringClass.name,
                    it.name,
                ) { param ->
                    listHook(param)
                }
            }

            findAltMethod(
                listOf(IMM_SERVICE_CLASS, IMM_IMPL_CLASS),
                listOf("getCurrentInputMethodSubtype"),
            )?.let {
                hookBefore(
                    it.declaringClass.name,
                    it.name,
                ) { param ->
                    subtypeHook(param)
                }
            }

            findAltMethod(
                listOf(IMM_SERVICE_CLASS, IMM_IMPL_CLASS),
                listOf("getLastInputMethodSubtype"),
            )?.let {
                hookBefore(
                    it.declaringClass.name,
                    it.name,
                ) { param ->
                    subtypeHook(param)
                }
            }

            findAltMethod(
                listOf(IMM_SERVICE_CLASS, IMM_IMPL_CLASS),
                listOf("getEnabledInputMethodSubtypeListInternal", "getEnabledInputMethodSubtypeList")
            )?.let {
                hookBefore(
                    it.declaringClass.name,
                    it.name,
                ) { param ->
                    subtypeListHook(param)
                }
            }
        }
    }

    private fun listHook(param: HookParam) {
        val callingApps = Utils4Zygote.getCallingApps(service)

        val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
        if (caller != null) {
            logD(TAG, "@${param.methodName} spoofed input method for $caller")

            listOf(getFakeInputMethodInfo(caller)).let { list ->
                val returnType = param.frame.type().returnType()
                param.result = if (returnType.simpleName == "InputMethodInfoSafeList") {
                    returnType.getDeclaredMethod(
                        "create",
                        List::class.java,
                    ).apply { isAccessible = true }.invoke(null, list)
                } else { list }
            }

            service.increaseSettingsFilterCount(caller)
        }
    }

    private fun subtypeHook(param: HookParam) {
        val callingApps = Utils4Zygote.getCallingApps(service)

        val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
        if (caller != null) {
            logD(TAG, "@${param.methodName} spoofed input method subtype for ${callingApps.contentToString()}")

            // TODO: Find a method to get exact value for spoofed input method
            param.result = null
            service.increaseSettingsFilterCount(caller)
        }
    }

    private fun subtypeListHook(param: HookParam) {
        val callingApps = Utils4Zygote.getCallingApps(service)

        val caller = callingApps.firstOrNull { callerIsSpoofed(it) }
        if (caller != null) {
            logD(TAG, "@${param.methodName} spoofed input method subtype for ${callingApps.contentToString()}")

            // TODO: Find a method to get exact list for spoofed input method
            Collections.emptyList<InputMethodSubtype>().let { list ->
                val returnType = param.frame.type().returnType()
                param.result = if (returnType.simpleName == "InputMethodSubtypeSafeList") {
                    returnType.getDeclaredMethod(
                        "create",
                        List::class.java,
                    ).apply { isAccessible = true }.invoke(null, list)
                } else { list }
            }

            service.increaseSettingsFilterCount(caller)
        }
    }

    private fun callerIsSpoofed(caller: String) =
        service.getEnabledSettingsPresets(caller).contains(InputMethodPreset.NAME)
}
