package com.virtualcam.manager.data

import android.content.Context
import com.virtualcam.manager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-shot automation: prepare the pure-Magisk VirtualCam environment
 * from the APK with root. No manual shell steps required.
 *
 * 传入 Context 以便把回传给界面的提示文案本地化（中/英）。这里持有的是
 * applicationContext，不会因长期引用 Activity 造成泄漏。
 */
class AutoSetup(
    context: Context,
    private val fs: FileSystemRepository = FileSystemRepository()
) {
    private val appContext = context.applicationContext

    data class Result(
        val rootOk: Boolean,
        val camera1Ok: Boolean,
        val controlDirOk: Boolean,
        val moduleDetected: Boolean,
        val enabledWritten: Boolean,
        val message: String
    )

    suspend fun runFullSetup(enable: Boolean = true): Result = withContext(Dispatchers.IO) {
        val root = RootShell.isRootAvailable()
        if (!root) {
            return@withContext Result(
                rootOk = false,
                camera1Ok = false,
                controlDirOk = false,
                moduleDetected = false,
                enabledWritten = false,
                message = appContext.getString(R.string.msg_root_not_granted)
            )
        }

        val camera1 = fs.ensureGlobalCamera1()
        val control = fs.ensureControlDir()

        val module = RootShell.exec(
            "[ -d /data/adb/modules/virtualcam_manager ] && echo 1 || echo 0"
        ).out.firstOrNull()?.trim() == "1" ||
            RootShell.exec(
                "[ -f /data/adb/virtualcam/module_installed ] && echo 1 || echo 0"
            ).out.firstOrNull()?.trim() == "1"

        val enabledCmd = if (enable) {
            listOf(
                "echo 1 > /data/adb/virtualcam/enabled",
                "echo 'mode=global' > /data/adb/virtualcam/config",
                "echo 'path=/storage/emulated/0/DCIM/Camera1/virtual.mp4' >> /data/adb/virtualcam/config",
                "rm -f /storage/emulated/0/DCIM/Camera1/disable.jpg",
                // Keep control-plane copy in sync if Camera1 has the file
                "[ -f /storage/emulated/0/DCIM/Camera1/virtual.mp4 ] && " +
                    "cp -f /storage/emulated/0/DCIM/Camera1/virtual.mp4 /data/adb/virtualcam/virtual.mp4 || true"
            )
        } else {
            listOf(
                "echo 0 > /data/adb/virtualcam/enabled",
                "touch /storage/emulated/0/DCIM/Camera1/disable.jpg",
                "chmod 644 /storage/emulated/0/DCIM/Camera1/disable.jpg"
            )
        }
        val enabledWritten = RootShell.exec(*enabledCmd.toTypedArray()).isSuccess

        RootShell.exec("date +%s > /data/adb/virtualcam/last_setup")

        // 提示文案分段取自资源，按空格拼接；每段末尾的标点由资源本身控制
        val msg = buildString {
            append(
                appContext.getString(
                    if (enable) R.string.msg_setup_enabled else R.string.msg_setup_disabled
                )
            ).append(' ')
            if (!module) {
                append(appContext.getString(R.string.msg_setup_need_module)).append(' ')
            }
            append(
                appContext.getString(
                    if (enable) R.string.msg_setup_test else R.string.msg_setup_real_camera
                )
            )
        }

        Result(
            rootOk = true,
            camera1Ok = camera1,
            controlDirOk = control,
            moduleDetected = module,
            enabledWritten = enabledWritten,
            message = msg
        )
    }

    suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        val r = RootShell.exec(
            "[ -f /data/adb/virtualcam/enabled ] && cat /data/adb/virtualcam/enabled || echo 0"
        )
        r.out.firstOrNull()?.trim() == "1"
    }
}
