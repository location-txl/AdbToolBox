package com.location.adbtools

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * 应用级 Koin 模块。
 *
 * 当前只注册桌面主页面需要的依赖，
 * 避免为了未来扩展先拆一堆没有收益的模块。
 */
val appModule = module {
    viewModelOf(::AdbToolsViewModel)
}
