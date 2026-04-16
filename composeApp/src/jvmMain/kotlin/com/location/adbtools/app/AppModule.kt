package com.location.adbtools.app

import com.location.adbtools.adb.AdbGateway
import com.location.adbtools.adb.EmbeddedAdbGateway
import com.location.adbtools.app.home.AdbToolsViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * 应用级 Koin 模块。
 *
 * 当前只注册桌面主页面需要的依赖，
 * 避免为了未来扩展先拆一堆没有收益的模块。
 */
val appModule = module {
    single<AdbGateway> { EmbeddedAdbGateway() }
    viewModel {
        AdbToolsViewModel(
            adbGateway = get(),
        )
    }
}
