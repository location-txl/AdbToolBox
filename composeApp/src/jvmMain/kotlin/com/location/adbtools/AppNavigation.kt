package com.location.adbtools

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 桌面主页面路由。
 *
 * 现在只有一个页面，但先把 Navigation 3 根壳搭起来，
 * 后续新增设备详情、日志页时不需要再返工入口结构。
 */
@Serializable
data object AdbToolsHomeRoute : NavKey
