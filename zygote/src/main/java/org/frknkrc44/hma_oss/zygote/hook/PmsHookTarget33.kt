package org.frknkrc44.hma_oss.zygote.hook

import android.content.pm.PackageInstaller
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
import org.frknkrc44.hma_oss.zygote.util.logD
import org.frknkrc44.hma_oss.zygote.util.logI
import org.frknkrc44.hma_oss.zygote.service.HMAServiceCache

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class PmsHookTarget33(service: HMAService) : PmsHookTargetBase(service) {

    override val TAG = "PmsHookTarget33"

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
            5,
        )!!.newInstance(
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
            5,
        )!!.newInstance(
            VENDING_PACKAGE_NAME,
            psPackageInfo?.signingInfo,
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
        }

        super.load()
    }
}
