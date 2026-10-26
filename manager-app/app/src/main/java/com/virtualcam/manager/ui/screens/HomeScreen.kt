package com.virtualcam.manager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier as M
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.virtualcam.manager.R
import com.virtualcam.manager.data.AutoSetup
import com.virtualcam.manager.data.MediaImportHelper
import com.virtualcam.manager.data.PrerequisiteChecker
import com.virtualcam.manager.ui.components.GitHubRepoAction
import com.virtualcam.manager.ui.theme.Danger
import com.virtualcam.manager.ui.theme.Primary
import com.virtualcam.manager.ui.theme.Success
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val checker = remember { PrerequisiteChecker() }
    val setup = remember { AutoSetup(ctx) }
    val importer = remember { MediaImportHelper() }
    var status by remember { mutableStateOf(checker.let { null as com.virtualcam.manager.data.PrerequisiteStatus? }) }
    var loading by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var msgOk by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() = scope.launch {
        loading = true
        status = checker.check()
        enabled = setup.isEnabled()
        loading = false
    }

    LaunchedEffect(Unit) {
        status = checker.check()
        if (status?.rootAvailable == true) setup.runFullSetup(enable = setup.isEnabled())
        enabled = setup.isEnabled()
        status = checker.check()
        loading = false
    }

    LaunchedEffect(enabled) {
        while (enabled) {
            delay(1200)
            status = checker.check()
        }
    }

    val msgImporting = stringResource(R.string.home_msg_importing)
    val msgReadyOpenCamera = stringResource(R.string.home_msg_ready_open_camera)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            msg = msgImporting
            msgOk = true
            val r = importer.importFromUri(ctx, uri)
            msg = r.message
            msgOk = r.success
            if (r.success && status?.rootAvailable == true) {
                setup.runFullSetup(enable = true)
                enabled = true
                msg = msgReadyOpenCamera
                msgOk = true
            }
            status = checker.check()
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    // 右上角：项目仓库入口，图标为朴素 octocat 剪影、tint 随主题
                    GitHubRepoAction()
                    IconButton(onClick = { refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                }
            )
        },
        content = { pad ->
        Column(
            M.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (loading) {
                Box(M.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
                return@Column
            }

            val s = status
            val rootOk = s?.rootAvailable == true
            val modOk = s?.moduleInstalled == true || s?.zygiskLibPresent == true
            val vidOk = s?.hasVirtualVideo == true
            val hook = s?.hookStatus.orEmpty()
            val frames = s?.decoderFrames ?: 0
            val texId = s?.textureId ?: 0L
            val hits = s?.bindHits ?: 0

            val live = enabled && (
                hook.startsWith("gl_bind_redir") ||
                hook.startsWith("decoder_frames") ||
                hook.startsWith("gl_ready") ||
                hook.startsWith("gl_hooked") ||
                hook.startsWith("decoder_running") ||
                hook.startsWith("gl_tex_created") ||
                hook.startsWith("oes_ready") ||
                hook.startsWith("oes_fallback") ||
                frames > 0 ||
                hits > 0 ||
                hook.startsWith("nv21_video") ||
                hook.startsWith("nv21_pattern") ||
                hook.startsWith("surface_playing") ||
                hook.startsWith("texture_swapped")
            )
            val partial = enabled && !live && (
                hook.startsWith("gl_partial") ||
                hook.startsWith("gl_hook_fail") ||
                hook.startsWith("gl_hook_pending") ||
                hook.startsWith("gl_installing") ||
                hook.startsWith("decoder_start") ||
                hook.startsWith("no_video") ||
                hook.isNotEmpty()
            )

            // 状态卡的标题/正文/是否正常：hook 状态码本身是技术标识，不参与翻译
            val (title, body, ok) = when {
                !rootOk -> Triple(
                    stringResource(R.string.home_state_need_root_title),
                    stringResource(R.string.home_state_need_root_body),
                    false
                )
                !modOk -> Triple(
                    stringResource(R.string.home_state_need_module_title),
                    stringResource(R.string.home_state_need_module_body),
                    false
                )
                !vidOk -> Triple(
                    stringResource(R.string.home_state_need_video_title),
                    stringResource(R.string.home_state_need_video_body),
                    false
                )
                !enabled -> Triple(
                    stringResource(R.string.home_state_need_enable_title),
                    stringResource(R.string.home_state_need_enable_body),
                    false
                )
                live -> Triple(
                    stringResource(R.string.home_state_active_title),
                    stringResource(R.string.home_state_active_body),
                    true
                )
                partial -> Triple(
                    stringResource(R.string.home_state_starting_title),
                    stringResource(R.string.home_state_starting_body),
                    true
                )
                else -> Triple(
                    stringResource(R.string.home_state_ready_title),
                    stringResource(R.string.home_state_ready_body),
                    true
                )
            }

            Card(
                M.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        live -> Success.copy(alpha = 0.14f)
                        ok -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(M.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    if (enabled && hook.isNotEmpty()) {
                        // 包名后缀用独立资源拼接，避免把技术标识塞进翻译文本
                        val pkgSuffix = s?.lastHookPkg?.let { pkg ->
                            stringResource(R.string.home_pkg_suffix, pkg)
                        } ?: ""
                        Text(
                            stringResource(R.string.home_status_line, hook, pkgSuffix),
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                live -> Success
                                partial -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            if (enabled && (frames > 0 || texId > 0 || hits > 0 || live || partial)) {
                val texNone = stringResource(R.string.home_chip_tex_none)
                Row(
                    M.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.home_chip_frames, frames)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (frames > 0) Success.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(
                                    R.string.home_chip_tex,
                                    if (texId > 0) texId.toString() else texNone
                                )
                            )
                        }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.home_chip_binds, hits)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (hits > 0) Success.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    s?.pathMode?.let { mode ->
                        // pathMode 是 native 侧写入的技术枚举（如 oes），原样大写显示不翻译
                        AssistChip(
                            onClick = {},
                            label = { Text(mode.uppercase()) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (mode == "oes") Success.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            Card(M.fillMaxWidth()) {
                Row(
                    M.fillMaxWidth().padding(16.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Column(M.weight(1f)) {
                        Text(
                            stringResource(
                                if (enabled) R.string.home_switch_on else R.string.home_switch_off
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(
                                if (enabled) R.string.home_switch_on_sub else R.string.home_switch_off_sub
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = enabled,
                        enabled = !busy && rootOk,
                        onCheckedChange = { want ->
                            scope.launch {
                                busy = true
                                val r = setup.runFullSetup(enable = want)
                                msg = r.message
                                msgOk = r.rootOk
                                enabled = setup.isEnabled()
                                status = checker.check()
                                busy = false
                            }
                        }
                    )
                }
            }

            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                enabled = !busy && rootOk,
                modifier = M.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(M.width(8.dp))
                Text(
                    stringResource(
                        when {
                            busy -> R.string.home_pick_working
                            vidOk -> R.string.home_pick_replace
                            else -> R.string.home_pick_choose
                        }
                    )
                )
            }
            if (!vidOk) {
                Text(
                    stringResource(R.string.home_pick_hint_image),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            msg?.let { Text(it, color = if (msgOk) Success else Danger) }

            if (enabled) {
                Card(
                    M.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(M.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.home_expect_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(R.string.home_expect_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            TextButton(onClick = { details = !details }, modifier = M.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (details) R.string.home_details_hide else R.string.home_details_show
                    )
                )
            }

            if (details && s != null) {
                // 详情清单：label 走资源，value 为布尔；Module 一行带版本号
                val moduleLabel = if (s.moduleVersion != null) {
                    stringResource(R.string.home_check_module_version, s.moduleVersion!!)
                } else {
                    stringResource(R.string.home_check_module)
                }
                listOf(
                    stringResource(R.string.home_check_root) to s.rootAvailable,
                    stringResource(R.string.home_check_magisk) to s.magiskPresent,
                    moduleLabel to (s.moduleInstalled || s.zygiskLibPresent),
                    stringResource(R.string.home_check_zygisk_so) to s.zygiskLibPresent,
                    stringResource(R.string.home_check_virtual_mp4) to s.hasVirtualVideo,
                    stringResource(R.string.home_check_enabled) to s.vcamEnabled
                ).forEach { (label, good) ->
                    Card(M.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            M.fillMaxWidth().padding(12.dp),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Text(label)
                            Icon(
                                if (good) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (good) Success else Danger
                            )
                        }
                    }
                }
                s.hookStatus?.let { hs ->
                    val pkgSuffix = s.lastHookPkg?.let { pkg ->
                        stringResource(R.string.home_pkg_suffix, pkg)
                    } ?: ""
                    Text(
                        stringResource(R.string.home_hook_line, hs, pkgSuffix),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (frames > 0 || texId > 0 || hits > 0) {
                    val texNone = stringResource(R.string.home_chip_tex_none)
                    Text(
                        stringResource(
                            R.string.home_telemetry_line,
                            frames,
                            if (texId > 0) texId.toString() else texNone,
                            hits,
                            s.pathMode ?: texNone
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    stringResource(R.string.home_arch_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    })
}
