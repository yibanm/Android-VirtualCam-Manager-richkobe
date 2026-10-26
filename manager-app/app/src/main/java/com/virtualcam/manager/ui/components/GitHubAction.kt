package com.virtualcam.manager.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.virtualcam.manager.R

/**
 * 顶栏右上角的 GitHub 仓库入口。
 *
 * 图标复用 ic_github（GitHub octicons，MIT）：朴素 octocat 剪影、无底色无渐变，
 * 由 Icon 的 tint 跟随主题着色，保持顶栏干净。
 *
 * 打开浏览器失败时（设备无可处理 http(s) 的应用，或被系统策略拦截）用 Toast 把仓库地址
 * 显示出来，而不是让点击静默无反应——避免"看着能点、点了没用"的假按钮。
 */
@Composable
fun GitHubRepoAction(@StringRes contentDescriptionRes: Int = R.string.action_open_repository) {
    val context = LocalContext.current
    val description = stringResource(contentDescriptionRes)
    IconButton(onClick = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
        } catch (e: ActivityNotFoundException) {
            // 设备上没有可处理 http(s) 的应用；明确告知而不是静默失败
            Toast.makeText(context, "$description: $REPO_URL", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "$description: $REPO_URL", Toast.LENGTH_LONG).show()
        }
    }) {
        Icon(
            painter = painterResource(id = R.drawable.ic_github),
            contentDescription = description
        )
    }
}

/** 项目仓库地址。集中在此处，避免散落到多个界面文件里各自硬编码。 */
private const val REPO_URL = "https://github.com/yibanm/Android-VirtualCam-Manager-richkobe"
