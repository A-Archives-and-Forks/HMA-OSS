package org.frknkrc44.hma_oss.zygote.hook

import icu.nullptr.hidemyapplist.common.Constants
import org.frknkrc44.hma_oss.zygote.service.BulkHooker
import org.frknkrc44.hma_oss.zygote.service.HMAService
import org.frknkrc44.hma_oss.zygote.util.Utils4Zygote
import org.frknkrc44.hma_oss.zygote.util.ZygoteConstants.PACKAGE_MANAGER_SERVICE_CLASS
import org.frknkrc44.hma_oss.zygote.util.Logcat.logD
import org.frknkrc44.hma_oss.zygote.util.Logcat.logI
import org.frknkrc44.hma_oss.zygote.util.Logcat.logV
import org.frknkrc44.hma_oss.zygote.service.HMAServiceCache

class PmsHookTarget29(service: HMAService) : PmsHookTargetBase(service) {

    override val TAG = "PmsHookTarget29"

    // not required until SDK 30
    override val fakeSystemPackageInstallSourceInfo = null
    override val fakeUserPackageInstallSourceInfo = null

    @Suppress("UNCHECKED_CAST")
    override fun load() {
        logI(TAG, "Load hook")

        BulkHooker.instance.apply {
            hookBefore(
                service.pms::class.java.name,
                "filterAppAccessLPr",
                paramCount = 5,
            ) { param ->
                val callingUid = param.getArgument(2) as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                val packageSettings = param.getArgument(1)
                val targetApp = Utils4Zygote.getPackageNameFromPackageSettings(packageSettings)
                if (HMAServiceCache.instance.shouldHideFromUid(callingUid, targetApp) == true) {
                    param.result = true
                    service.increasePMFilterCount(callingUid)
                    logD(TAG, "@filterAppAccessLPr caller cache: $callingUid, target: $targetApp")
                    return@hookBefore
                }
                val callingApps = Utils4Zygote.getCallingApps(service, callingUid)
                val caller = callingApps.firstOrNull { service.shouldHide(it, targetApp) }
                if (caller != null) {
                    param.result = true
                    HMAServiceCache.instance.putShouldHideUidCache(callingUid, caller, targetApp!!)
                    service.increasePMFilterCount(caller)
                    val last = lastFilteredApp.getAndSet(caller)
                    if (last != caller) logI(TAG, "@filterAppAccessLPr query from $caller")
                    logD(TAG, "@filterAppAccessLPr caller: $callingUid $caller, target: $targetApp")
                }
            }

            hookBefore(
                PACKAGE_MANAGER_SERVICE_CLASS,
                "getPackageInfoInternal",
            ) { param ->
                val targetApp = param.getArgument(1) as? String? ?: return@hookBefore
                val callingUid = param.getArgument(4) as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                logV(TAG, "@${param.methodName} incoming query: $callingUid => $targetApp")
                if (HMAServiceCache.instance.shouldHideFromUid(callingUid, targetApp) == true) {
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
                    HMAServiceCache.instance.putShouldHideUidCache(callingUid, caller, targetApp)
                    service.increasePMFilterCount(caller)
                }
            }

            hookBefore(
                PACKAGE_MANAGER_SERVICE_CLASS,
                "getApplicationInfoInternal",
            ) { param ->
                val targetApp = param.getArgument(1) as? String? ?: return@hookBefore
                val callingUid = param.getArgument(3) as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                logV(TAG, "@${param.methodName} incoming query: $callingUid => $targetApp")
                if (HMAServiceCache.instance.shouldHideFromUid(callingUid, targetApp) == true) {
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
                    HMAServiceCache.instance.putShouldHideUidCache(callingUid, caller, targetApp)
                    service.increasePMFilterCount(caller)
                }
            }
        }

        super.load()
    }
}
