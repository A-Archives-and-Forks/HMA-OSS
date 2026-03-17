package org.frknkrc44.hma_oss.zygote.hook

import android.content.pm.PackageInstaller
import android.os.Binder
import android.os.Build
import androidx.annotation.RequiresApi
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.Constants.VENDING_PACKAGE_NAME
import icu.nullptr.hidemyapplist.common.Utils
import org.frknkrc44.hma_oss.zygote.service.BulkHooker
import org.frknkrc44.hma_oss.zygote.service.HMAService
import org.frknkrc44.hma_oss.zygote.util.Utils4Zygote
import org.frknkrc44.hma_oss.zygote.util.Utils4Zygote.findConstructor
import org.frknkrc44.hma_oss.zygote.util.Utils4Zygote.findMethod
import org.frknkrc44.hma_oss.zygote.util.ZygoteConstants.APPS_FILTER_IMPL_CLASS
import org.frknkrc44.hma_oss.zygote.util.ZygoteConstants.PACKAGE_MANAGER_SERVICE_CLASS
import org.frknkrc44.hma_oss.zygote.util.Logcat.logD
import org.frknkrc44.hma_oss.zygote.util.Logcat.logI
import org.frknkrc44.hma_oss.zygote.service.HMAServiceCache

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PmsHookTarget34(service: HMAService) : PmsHookTargetBase(service) {

    override val TAG = "PmsHookTarget34"

    private val getPackagesForUidMethod by lazy {
        findMethod(
            "com.android.server.pm.Computer",
            "getPackagesForUid",
            isDeclared = false,
            systemClassLoader = true,
            Int::class.java,
        )
    }

    override val fakeSystemPackageInstallSourceInfo: Any by lazy {
        findConstructor(
            "android.content.pm.InstallSourceInfo",
            6,
        )!!.newInstance(
            null,
            null,
            null,
            null,
            null,
            PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED,
        )
    }

    override val fakeUserPackageInstallSourceInfo: Any by lazy {
        findConstructor(
            "android.content.pm.InstallSourceInfo",
            6,
        )!!.newInstance(
            VENDING_PACKAGE_NAME,
            psPackageInfo?.signingInfo,
            VENDING_PACKAGE_NAME,
            VENDING_PACKAGE_NAME,
            VENDING_PACKAGE_NAME,
            PackageInstaller.PACKAGE_SOURCE_STORE,
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun load() {
        logI(TAG, "Load hook")

        BulkHooker.instance.apply {
            hookBefore(
                APPS_FILTER_IMPL_CLASS,
                "shouldFilterApplication",
            ) { param ->
                val callingUid = param.getArgument(2) as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                val targetApp = Utils4Zygote.getPackageNameFromPackageSettings(param.getArgument(4)) // PackageSettings <- PackageStateInternal
                if (HMAServiceCache.instance.shouldHideFromUid(callingUid, targetApp) == true) {
                    param.result = true
                    service.increasePMFilterCount(callingUid)
                    logD(TAG, "@shouldFilterApplication caller cache: $callingUid, target: $targetApp")
                    return@hookBefore
                }
                val snapshot = param.getArgument(1)
                val callingApps = Utils.binderLocalScope {
                    getPackagesForUidMethod.invoke(snapshot, callingUid) as Array<String>?
                } ?: return@hookBefore
                val caller = callingApps.firstOrNull { service.shouldHide(it, targetApp) }
                if (caller != null) {
                    param.result = true
                    HMAServiceCache.instance.putShouldHideUidCache(callingUid, caller, targetApp!!)
                    service.increasePMFilterCount(caller)
                    val last = lastFilteredApp.getAndSet(caller)
                    if (last != caller) logI(TAG, "@shouldFilterApplication: query from $caller")
                    logD(TAG, "@shouldFilterApplication caller: $callingUid $caller, target: $targetApp")
                }
            }

            // AOSP exploit - https://github.com/aosp-mirror/platform_frameworks_base/commit/5bc482bd99ea18fe0b4064d486b29d5ae2d65139
            // Only 14 QPR2+ has this method
            // UPDATE: Samsung adds getArchivedPackage instead of getArchivedPackageInternal
            val altNames = findAltMethod(
                listOf(PACKAGE_MANAGER_SERVICE_CLASS),
                listOf("getArchivedPackageInternal", "getArchivedPackage"),
            ) ?: return@apply

            hookBefore(
                altNames.declaringClass.name,
                altNames.name,
            ) { param ->
                val callingUid = Binder.getCallingUid()
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                val targetApp = param.getArgument(1).toString()
                if (HMAServiceCache.instance.shouldHideFromUid(callingUid, targetApp) == true) {
                    param.result = null
                    service.increasePMFilterCount(callingUid)
                    logD(TAG, "@getArchivedPackageInternal caller cache: $callingUid, target: $targetApp")
                    return@hookBefore
                }
                val callingApps = Utils4Zygote.getCallingApps(service, callingUid)
                val caller = callingApps.firstOrNull { service.shouldHide(it, targetApp) }
                if (caller != null) {
                    param.result = null
                    HMAServiceCache.instance.putShouldHideUidCache(callingUid, caller, targetApp)
                    service.increasePMFilterCount(caller)
                    val last = lastFilteredApp.getAndSet(caller)
                    if (last != caller) logI(TAG, "@getArchivedPackageInternal: query from $caller")
                    logD(TAG, "@getArchivedPackageInternal caller: $callingUid $caller, target: $targetApp")
                }
            }
        }

        super.load()
    }
}
