package org.frknkrc44.hma_oss.zygote.hook

import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.UserHandle
import android.util.ArrayMap
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.Constants.VENDING_PACKAGE_NAME
import icu.nullptr.hidemyapplist.common.Utils
import org.frknkrc44.hma_oss.zygote.BulkHooker
import org.frknkrc44.hma_oss.zygote.HMAService
import org.frknkrc44.hma_oss.zygote.Utils4Zygote.callMethod
import org.frknkrc44.hma_oss.zygote.Utils4Zygote.getCallingApps
import org.frknkrc44.hma_oss.zygote.Utils4Zygote.getPackageNameFromPackageSettings
import org.frknkrc44.hma_oss.zygote.ZygoteConstants.COMPUTER_ENGINE_CLASS
import org.frknkrc44.hma_oss.zygote.logD
import org.frknkrc44.hma_oss.zygote.logV
import java.util.concurrent.atomic.AtomicReference

abstract class PmsHookTargetBase(protected val service: HMAService) : IFrameworkHook {
    @Suppress("PropertyName")
    abstract val TAG: String

    protected var lastFilteredApp: AtomicReference<String?> = AtomicReference(null)

    protected val psPackageInfo by lazy {
        try {
            Utils.getPackageInfoCompat(
                service.pms,
                VENDING_PACKAGE_NAME,
                PackageManager.GET_SIGNING_CERTIFICATES.toLong(),
                0
            )
        } catch (_: Throwable) {
            null
        }
    }

    abstract val fakeSystemPackageInstallSourceInfo: Any?
    abstract val fakeUserPackageInstallSourceInfo: Any?

    override fun load() {
        BulkHooker.instance.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hookAfter(
                    COMPUTER_ENGINE_CLASS,
                    "getPackageStates",
                ) { param ->
                    val callingUid = Binder.getCallingUid()
                    if (callingUid == Constants.UID_SYSTEM) return@hookAfter

                    val callingApps = getCallingApps(service, callingUid)
                    val caller = callingApps.firstOrNull { service.isHookEnabled(it) }
                    if (caller != null) {
                        logD(TAG, "@getPackageStates: incoming query from $caller")

                        val result = param.result as ArrayMap<*, *>
                        val markedToRemove = mutableListOf<Any>()

                        for (pair in result.entries) {
                            val packageSettings = pair.value
                            val packageName = getPackageNameFromPackageSettings(packageSettings)
                            if (service.shouldHide(caller, packageName)) {
                                markedToRemove.add(pair.key)
                            }
                        }

                        if (markedToRemove.isNotEmpty()) {
                            val copyResult = ArrayMap(result)
                            copyResult.removeAll(markedToRemove)
                            logD(TAG, "@getPackageStates: removed ${markedToRemove.size} entries from $caller")
                            param.result = copyResult
                            service.increasePMFilterCount(caller)
                        }
                    }
                }

                hookBefore(
                    COMPUTER_ENGINE_CLASS,
                    "addPackageHoldingPermissions",
                ) { param ->
                    val callingUid = Binder.getCallingUid()
                    val packageSettings = param.getArgument(2)
                    val targetApp = getPackageNameFromPackageSettings(packageSettings) ?: return@hookBefore
                    if (service.shouldHideFromUid(callingUid, targetApp) == true) {
                        param.result = null
                        service.increasePMFilterCount(callingUid)
                        logD(TAG, "@addPackageHoldingPermissions caller cache: $callingUid, target: $targetApp")
                        return@hookBefore
                    }
                    val callingApps = getCallingApps(service, callingUid)
                    val caller = callingApps.firstOrNull { service.shouldHide(it, targetApp) }
                    if (caller != null) {
                        logD(TAG, "@addPackageHoldingPermissions caller: $callingUid $caller, target: $targetApp")
                        param.result = null
                        service.putShouldHideUidCache(callingUid, caller, targetApp)
                        service.increasePMFilterCount(caller)
                    }
                }

                hookBefore(
                    COMPUTER_ENGINE_CLASS,
                    "isCallerInstallerOfRecord",
                ) { param ->
                    val callingUid = param.args.last { it is Int } as Int
                    if (callingUid == Constants.UID_SYSTEM) return@hookBefore

                    val pkg = param.args[1] ?: return@hookBefore
                    val query = callMethod(
                        pkg,
                        if (pkg.javaClass.simpleName == "PackageImpl") {
                        "getManifestPackageName"
                    } else {
                        "getPackageName"
                    }) as? String ?: return@hookBefore

                    val callingApps = getCallingApps(service, callingUid)
                    val callingHandle = UserHandle.getUserHandleForUid(callingUid)

                    for (caller in callingApps) {
                        when (service.shouldHideInstallationSource(caller, query, callingHandle)) {
                            Constants.FAKE_INSTALLATION_SOURCE_USER -> param.result = callingUid == psPackageInfo?.applicationInfo?.uid
                            Constants.FAKE_INSTALLATION_SOURCE_SYSTEM -> param.result = false
                            else -> continue
                        }

                        service.increaseInstallerFilterCount(caller)
                        break
                    }
                }

                hookBefore(
                    COMPUTER_ENGINE_CLASS,
                    "getPackageInfoInternal",
                ) { param ->
                    val targetApp = param.args.firstOrNull { it is String } as? String ?: return@hookBefore
                    val callingUid = param.args.firstOrNull { it is Int } as? Int ?: Binder.getCallingUid()
                    if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                    logV(TAG, "@${param.methodName} incoming query: $callingUid => $targetApp")
                    if (service.shouldHideFromUid(callingUid, targetApp) == true) {
                        param.result = null
                        service.increasePMFilterCount(callingUid)
                        logD(TAG, "@${param.methodName} caller cache: $callingUid, target: $targetApp")
                        return@hookBefore
                    }
                    val callingApps = getCallingApps(service, callingUid)
                    val caller = callingApps.firstOrNull { service.shouldHide(it, targetApp) }
                    if (caller != null) {
                        logD(TAG, "@${param.methodName} caller: $callingUid $caller, target: $targetApp")
                        param.result = null
                        service.putShouldHideUidCache(callingUid, caller, targetApp)
                        service.increasePMFilterCount(caller)
                    }
                }

                hookBefore(
                    COMPUTER_ENGINE_CLASS,
                    "getApplicationInfoInternal",
                ) { param ->
                    val targetApp = param.args.firstOrNull { it is String } as? String ?: return@hookBefore
                    val callingUid = param.args.firstOrNull { it is Int } as? Int ?: Binder.getCallingUid()
                    if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                    logV(TAG, "@${param.methodName} incoming query: $callingUid => $targetApp")
                    if (service.shouldHideFromUid(callingUid, targetApp) == true) {
                        param.result = null
                        service.increasePMFilterCount(callingUid)
                        logD(TAG, "@${param.methodName} caller cache: $callingUid, target: $targetApp")
                        return@hookBefore
                    }
                    val callingApps = getCallingApps(service, callingUid)
                    val caller = callingApps.firstOrNull { service.shouldHide(it, targetApp) }
                    if (caller != null) {
                        logD(TAG, "@${param.methodName} caller: $callingUid $caller, target: $targetApp")
                        param.result = null
                        service.putShouldHideUidCache(callingUid, caller, targetApp)
                        service.increasePMFilterCount(caller)
                    }
                }
            }

            if (service.pmn != null) {
                hookBefore(
                    service.pmn.javaClass.name,
                    "getInstallerForPackage",
                ) { param ->
                    val query = param.getArgument(1) as? String ?: return@hookBefore

                    val callingUid = Binder.getCallingUid()
                    if (callingUid == Constants.UID_SYSTEM) return@hookBefore

                    val callingApps = getCallingApps(service, callingUid)
                    val callingHandle = UserHandle.getUserHandleForUid(callingUid)

                    for (caller in callingApps) {
                        when (service.shouldHideInstallationSource(caller, query, callingHandle)) {
                            Constants.FAKE_INSTALLATION_SOURCE_USER -> param.result = VENDING_PACKAGE_NAME
                            Constants.FAKE_INSTALLATION_SOURCE_SYSTEM -> param.result = "preload"
                            else -> continue
                        }

                        service.increaseInstallerFilterCount(caller)
                        break
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hookBefore(
                    service.pms.javaClass.name,
                    "getInstallSourceInfo",
                ) { param ->
                    val query = param.getArgument(1) as? String ?: return@hookBefore

                    val callingUid = Binder.getCallingUid()
                    if (callingUid == Constants.UID_SYSTEM) return@hookBefore

                    val callingApps = getCallingApps(service, callingUid)
                    val callingHandle = UserHandle.getUserHandleForUid(callingUid)

                    for (caller in callingApps) {
                        when (service.shouldHideInstallationSource(caller, query, callingHandle)) {
                            Constants.FAKE_INSTALLATION_SOURCE_USER -> param.result = fakeUserPackageInstallSourceInfo
                            Constants.FAKE_INSTALLATION_SOURCE_SYSTEM -> param.result = fakeSystemPackageInstallSourceInfo
                            else -> continue
                        }

                        service.increaseInstallerFilterCount(caller)
                        break
                    }
                }
            }

            hookBefore(
                service.pms.javaClass.name,
                "getInstallerPackageName",
            ) { param ->
                val query = param.getArgument(1) as? String ?: return@hookBefore

                val callingUid = Binder.getCallingUid()
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore

                val callingApps = getCallingApps(service, callingUid)
                val callingHandle = UserHandle.getUserHandleForUid(callingUid)

                for (caller in callingApps) {
                    when (service.shouldHideInstallationSource(caller, query, callingHandle)) {
                        Constants.FAKE_INSTALLATION_SOURCE_USER -> param.result = VENDING_PACKAGE_NAME
                        Constants.FAKE_INSTALLATION_SOURCE_SYSTEM -> param.result = null
                        else -> continue
                    }

                    service.increaseInstallerFilterCount(caller)
                    break
                }
            }
        }
    }
}
