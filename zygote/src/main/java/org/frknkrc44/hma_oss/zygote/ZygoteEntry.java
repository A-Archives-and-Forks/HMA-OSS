package org.frknkrc44.hma_oss.zygote;

import static org.frknkrc44.hma_oss.zygote.util.Logcat.logE;
import static org.frknkrc44.hma_oss.zygote.util.Logcat.logI;

import com.v7878.r8.annotations.DoNotObfuscate;
import com.v7878.r8.annotations.DoNotObfuscateType;
import com.v7878.r8.annotations.DoNotShrink;
import com.v7878.r8.annotations.DoNotShrinkType;
import com.v7878.zygisk.ZygoteLoader;

import org.frknkrc44.hma_oss.zygote.service.SystemServerHook;

@SuppressWarnings("all")
@DoNotObfuscateType
@DoNotShrinkType
public class ZygoteEntry {
    public static final String TAG = "ZygoteEntry";

    @DoNotObfuscate
    @DoNotShrink
    public static void premain() throws Throwable {

    }

    @DoNotObfuscate
    @DoNotShrink
    public static void main() throws Throwable {
        logI(TAG, "Injected into " + ZygoteLoader.getPackageName(), null);

        try {
            SystemServerHook.init();
        } catch (Throwable th) {
            logE(TAG, "An exception occurred while SystemServerHook init", th);
        }

        logI(TAG, "Done", null);
    }
}
