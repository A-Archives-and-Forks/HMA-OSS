package org.frknkrc44.hma_oss.zygote.hook

import android.content.Intent
import android.os.Build
import android.os.Bundle
import org.frknkrc44.hma_oss.zygote.service.BulkHooker
import org.frknkrc44.hma_oss.zygote.service.HMAService
import org.frknkrc44.hma_oss.zygote.util.ZygoteConstants.PACKAGE_MANAGER_SERVICE_CLASS
import org.frknkrc44.hma_oss.zygote.util.logI

class PmsPackageEventsHook(private val service: HMAService) : IFrameworkHook {
    override val TAG = "PmsPackageEventsHook"

    override fun load() {
        logI(TAG, "Load hook")

        BulkHooker.instance.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hookBefore(
                    "com.android.server.pm.BroadcastHelper",
                    "sendPackageBroadcastAndNotify",
                ) { param ->
                    service.handlePackageEvent(
                        param.getArgument(1) as String?,
                        param.getArgument(2) as String?,
                        param.getArgument(3) as Bundle?,
                    )
                }

                hookBefore(
                    "com.android.internal.content.PackageMonitor",
                    "onReceive",
                ) { param ->
                    val intent = param.getArgument(2) as? Intent? ?: return@hookBefore

                    service.handlePackageEvent(
                        intent.action,
                        intent.data?.encodedSchemeSpecificPart,
                        intent.extras,
                    )
                }
            } else {
                hookBefore(
                    PACKAGE_MANAGER_SERVICE_CLASS,
                    "sendPackageBroadcast",
                ) { param ->
                    service.handlePackageEvent(
                        param.getArgument(1) as String?,
                        param.getArgument(2) as String?,
                        param.getArgument(3) as Bundle?,
                    )
                }
            }
        }
    }
}
