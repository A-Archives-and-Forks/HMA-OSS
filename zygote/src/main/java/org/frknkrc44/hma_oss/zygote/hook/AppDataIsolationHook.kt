package org.frknkrc44.hma_oss.zygote.hook

import android.os.Build
import android.os.SystemProperties
import androidx.annotation.RequiresApi
import icu.nullptr.hidemyapplist.common.Utils
import org.frknkrc44.hma_oss.common.BuildConfig
import org.frknkrc44.hma_oss.zygote.BulkHooker
import org.frknkrc44.hma_oss.zygote.HMAService
import org.frknkrc44.hma_oss.zygote.Utils4Zygote.getBooleanField
import org.frknkrc44.hma_oss.zygote.Utils4Zygote.getIntField
import org.frknkrc44.hma_oss.zygote.Utils4Zygote.getObjectField
import org.frknkrc44.hma_oss.zygote.Utils4Zygote.setBooleanField
import org.frknkrc44.hma_oss.zygote.ZygoteConstants.PROCESS_LIST_CLASS
import org.frknkrc44.hma_oss.zygote.ZygoteConstants.STORAGE_MANAGER_SERVICE_CLASS
import org.frknkrc44.hma_oss.zygote.logD
import org.frknkrc44.hma_oss.zygote.logE
import org.frknkrc44.hma_oss.zygote.logI

@RequiresApi(Build.VERSION_CODES.R)
class AppDataIsolationHook(private val service: HMAService): IFrameworkHook {

    companion object {
        private const val TAG = "AppDataIsolationHook"
        private const val APPDATA_ISOLATION_ENABLED = "mAppDataIsolationEnabled"
        private const val VOLD_APPDATA_ISOLATION_ENABLED = "mVoldAppDataIsolationEnabled"
        private const val FUSE_PROP = "persist.sys.fuse"
    }

    private var voldHookSkipped = false

    override fun load() {
        if (!(service.config.altAppDataIsolation || service.config.altVoldAppDataIsolation)) return
        logI(TAG, "Load hook")

        BulkHooker.instance.apply {
            hookBefore(
                PROCESS_LIST_CLASS,
                "startProcess",
            ) { param ->
                if (service.config.altAppDataIsolation) {
                    val isEnabled = getBooleanField(
                        param.thisObject,
                        APPDATA_ISOLATION_ENABLED,
                    )

                    if (!isEnabled) {
                        setBooleanField(
                            param.thisObject,
                            APPDATA_ISOLATION_ENABLED,
                            true
                        )

                        logI(TAG, "ProcessList - App data isolation is forced")
                    }
                }

                if (service.config.altVoldAppDataIsolation && !voldHookSkipped) {
                    val fuseEnabled = SystemProperties.getBoolean(FUSE_PROP, false)

                    if (!fuseEnabled) {
                        voldHookSkipped = true
                        logE(TAG, "ProcessList - FUSE storage is not enabled, skip vold hook")
                    } else {
                        val isolationEnabled = getBooleanField(
                            param.thisObject,
                            VOLD_APPDATA_ISOLATION_ENABLED
                        )

                        if (!isolationEnabled) {
                            setBooleanField(
                                param.thisObject,
                                VOLD_APPDATA_ISOLATION_ENABLED,
                                true
                            )

                            logI(TAG, "ProcessList - Vold app data isolation is forced")
                        }
                    }
                }
            }

            hookAfter(
                PROCESS_LIST_CLASS,
                "needsStorageDataIsolation",
            ) { param ->
                if (service.config.altVoldAppDataIsolation) {
                    val app = param.args.find { it?.javaClass?.simpleName == "ProcessRecord" }!!
                    val uid = getIntField(app, "uid")
                    val processName = runCatching {
                        getObjectField(app, "processName")
                    }.getOrDefault("<unknown>")
                    val mountNode = runCatching {
                        getIntField(app, "mMountMode")
                    }.getOrDefault(0)
                    val isolated = runCatching {
                        getBooleanField(app, "isolated")
                    }.getOrDefault(false)
                    val appZygote = runCatching {
                        getBooleanField(app, "appZygote")
                    }.getOrDefault(false)

                    val apps = Utils.binderLocalScope {
                        service.pms.getPackagesForUid(uid)
                    } ?: return@hookAfter

                    logD(
                        TAG,
                        "@needsStorageDataIsolation $uid and ${apps.contentToString()} - $processName value without override: ${param.result}, mount node: $mountNode, isolated: $isolated, appZygote: $appZygote"
                    )

                    // Do not isolate this module for safety
                    if (apps.contains(BuildConfig.APP_PACKAGE_NAME)) {
                        param.result = false
                        return@hookAfter
                    }

                    if (apps.any { service.isAppDataIsolationExcluded(it) }) {
                        param.result = false
                    }

                    if (service.config.skipSystemAppDataIsolation) {
                        val isSystemApp = service.systemApps.any { apps.contains(it) }
                        logD(
                            TAG,
                            "@needsStorageDataIsolation $uid and ${apps.contentToString()} - isSystemApp: $isSystemApp"
                        )

                        if (isSystemApp) {
                            param.result = false
                            return@hookAfter
                        }
                    }
                }
            }

            hookBefore(
                STORAGE_MANAGER_SERVICE_CLASS,
                "onVolumeStateChangedLocked",
            ) { param ->
                if (service.config.altVoldAppDataIsolation && !voldHookSkipped) {
                    val fuseEnabled = SystemProperties.getBoolean(FUSE_PROP, false)

                    if (!fuseEnabled) {
                        logE(TAG, "StorageManagerService - FUSE storage is not enabled, skip vold hook")
                        voldHookSkipped = true
                        return@hookBefore
                    }

                    val isolationEnabled = getBooleanField(
                        param.thisObject,
                        VOLD_APPDATA_ISOLATION_ENABLED
                    )

                    if (!isolationEnabled) {
                        setBooleanField(
                            param.thisObject,
                            VOLD_APPDATA_ISOLATION_ENABLED,
                            true
                        )

                        logI(TAG, "StorageManagerService - Vold app data isolation is forced")
                    }
                }
            }

            hookBefore(
                STORAGE_MANAGER_SERVICE_CLASS,
                "remountAppStorageDirs",
            ) { param ->
                if (!voldHookSkipped && service.config.altVoldAppDataIsolation && service.config.skipSystemAppDataIsolation) {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    val pidPkgMap = param.getArgument(1) as java.util.Map<*, *>
                    val userId = param.getArgument(2) as Int

                    val keysToRemove = mutableSetOf<Any>()

                    for (entry in pidPkgMap.entrySet()) {
                        val pid = entry.key
                        val packageName = entry.value as String

                        val apps = Utils.binderLocalScope {
                            val uid = Utils.getPackageUidCompat(service.pms, packageName, 0L, userId)
                            service.pms.getPackagesForUid(uid)
                        } ?: continue

                        for (app in apps) {
                            if (app in service.systemApps || app == BuildConfig.APP_PACKAGE_NAME) {
                                logD(
                                    TAG,
                                    "@remountAppStorageDirs SYSTEM $pid - $packageName is marked to remove"
                                )
                                keysToRemove += pid
                                break
                            }
                        }
                    }

                    keysToRemove.forEach { pidPkgMap.remove(it) }
                }
            }
        }
    }
}
