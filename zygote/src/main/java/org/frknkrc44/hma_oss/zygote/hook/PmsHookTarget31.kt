package org.frknkrc44.hma_oss.zygote.hook

import android.os.Binder
import android.os.Build
import androidx.annotation.RequiresApi
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.Constants.VENDING_PACKAGE_NAME
import org.frknkrc44.hma_oss.zygote.BulkHooker
import org.frknkrc44.hma_oss.zygote.HMAService
import org.frknkrc44.hma_oss.zygote.Utils4Zygote
import org.frknkrc44.hma_oss.zygote.Utils4Zygote.findConstructor
import org.frknkrc44.hma_oss.zygote.ZygoteConstants.APPS_FILTER_CLASS
import org.frknkrc44.hma_oss.zygote.ZygoteConstants.PMS_COMPUTER_TRACKER_CLASS
import org.frknkrc44.hma_oss.zygote.logD
import org.frknkrc44.hma_oss.zygote.logI
import org.frknkrc44.hma_oss.zygote.logV

@RequiresApi(Build.VERSION_CODES.S)
class PmsHookTarget31(service: HMAService) : PmsHookTargetBase(service) {

    override val TAG = "PmsHookTarget31"

    override val fakeSystemPackageInstallSourceInfo: Any by lazy {
        findConstructor(
            "android.content.pm.InstallSourceInfo",
            4,
        )!!.newInstance(
            null,
            null,
            null,
            null,
        )
    }

    override val fakeUserPackageInstallSourceInfo: Any by lazy {
        findConstructor(
            "android.content.pm.InstallSourceInfo",
            4,
        )!!.newInstance(
            VENDING_PACKAGE_NAME,
            psPackageInfo?.signingInfo,
            VENDING_PACKAGE_NAME,
            VENDING_PACKAGE_NAME,
        )
    }

    override fun load() {
        logI(TAG, "Load hook")

        BulkHooker.instance.apply {
            hookBefore(
                PMS_COMPUTER_TRACKER_CLASS,
                "getPackageSetting",
            ) { param ->
                val callingUid = Binder.getCallingUid()
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                val targetApp = param.getArgument(1) as String
                if (service.shouldHideFromUid(callingUid, targetApp) == true) {
                    param.result = null
                    service.increasePMFilterCount(callingUid)
                    logD(TAG, "@getPackageSetting - Computer cache: insecure query from $callingUid to $targetApp")
                    return@hookBefore
                }
                val callingApps = Utils4Zygote.getCallingApps(service, callingUid)
                val caller = callingApps.firstOrNull { service.shouldHide(it, targetApp) }
                if (caller != null) {
                    logD(TAG, "@getPackageSetting - Computer: insecure query from $caller to $targetApp")
                    param.result = null
                    service.putShouldHideUidCache(callingUid, caller, targetApp)
                    service.increasePMFilterCount(caller)
                }
            }

            hookBefore(
                PMS_COMPUTER_TRACKER_CLASS,
                "getPackageSettingInternal",
            ) { param ->
                val callingUid = param.getArgument(2) as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                val targetApp = param.getArgument(1) as String
                if (service.shouldHideFromUid(callingUid, targetApp) == true) {
                    param.result = null
                    service.increasePMFilterCount(callingUid)
                    logD(TAG, "@getPackageSettingInternal - Computer cache: insecure query from $callingUid to $targetApp")
                    return@hookBefore
                }
                val callingApps = Utils4Zygote.getCallingApps(service, callingUid)
                val caller = callingApps.firstOrNull { service.shouldHide(it, targetApp) }
                if (caller != null) {
                    logD(TAG, "@getPackageSettingInternal - Computer: insecure query from $caller to $targetApp")
                    param.result = null
                    service.putShouldHideUidCache(callingUid, caller, targetApp)
                    service.increasePMFilterCount(caller)
                }
            }

            hookBefore(
                PMS_COMPUTER_TRACKER_CLASS,
                "getPackageInfoInternal",
            ) { param ->
                val callingUid = param.getArgument(4) as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                val targetApp = param.getArgument(1) as? String? ?: return@hookBefore
                logV(TAG, "@${param.methodName} incoming query: $callingUid => $targetApp")
                if (service.shouldHideFromUid(callingUid, targetApp) == true) {
                    param.result = null
                    service.increasePMFilterCount(callingUid)
                    logD(TAG, "@${param.methodName} caller cache: $callingUid, target: $targetApp")
                    return@hookBefore
                }
                val callingApps = Utils4Zygote.getCallingApps(service, callingUid)
                val caller = callingApps.firstOrNull { service.shouldHide(it, targetApp) }
                if (caller != null) {
                    logD(TAG, "@${param.methodName} caller: $callingUid $caller, target: $targetApp")
                    param.result = null
                    service.putShouldHideUidCache(callingUid, caller, targetApp)
                    service.increasePMFilterCount(caller)
                }
            }

            hookBefore(
                PMS_COMPUTER_TRACKER_CLASS,
                "getApplicationInfoInternal",
            ) { param ->
                val callingUid = param.getArgument(3) as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                val targetApp = param.getArgument(1) as? String? ?: return@hookBefore
                logV(TAG, "@${param.methodName} incoming query: $callingUid => $targetApp")
                if (service.shouldHideFromUid(callingUid, targetApp) == true) {
                    param.result = null
                    service.increasePMFilterCount(callingUid)
                    logD(TAG, "@${param.methodName} caller cache: $callingUid, target: $targetApp")
                    return@hookBefore
                }
                val callingApps = Utils4Zygote.getCallingApps(service, callingUid)
                val caller = callingApps.firstOrNull { service.shouldHide(it, targetApp) }
                if (caller != null) {
                    logD(TAG, "@${param.methodName} caller: $callingUid $caller, target: $targetApp")
                    param.result = null
                    service.putShouldHideUidCache(callingUid, caller, targetApp)
                    service.increasePMFilterCount(caller)
                }
            }

            hookBefore(
                APPS_FILTER_CLASS,
                "shouldFilterApplication",
            ) { param ->
                logV(TAG, "@shouldFilterApplication call: ${param.args.contentToString()}")

                val callingUid = param.getArgument(1) as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                val targetApp = Utils4Zygote.getPackageNameFromPackageSettings(param.getArgument(3))
                if (service.shouldHideFromUid(callingUid, targetApp) == true) {
                    param.result = true
                    service.increasePMFilterCount(callingUid)
                    logD(TAG, "@shouldFilterApplication caller cache: $callingUid, target: $targetApp")
                    return@hookBefore
                }
                val callingApps = Utils4Zygote.getCallingApps(service, callingUid)
                val caller = callingApps.firstOrNull { service.shouldHide(it, targetApp) }
                if (caller != null) {
                    param.result = true
                    service.putShouldHideUidCache(callingUid, caller, targetApp!!)
                    service.increasePMFilterCount(caller)
                    val last = lastFilteredApp.getAndSet(caller)
                    if (last != caller) logI(TAG, "@shouldFilterApplication: query from $caller")
                    logD(TAG, "@shouldFilterApplication caller: $callingUid $caller, target: $targetApp")
                }
            }
        }

        // super.load()
    }
}
