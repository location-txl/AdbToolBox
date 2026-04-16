package com.location.adbtools.adb

/**
 * 主页面使用的 adb 调用入口。
 *
 * 这里只保留当前界面真实会用到的方法，
 * 目的是隔离 `EmbeddedAdb` 细节并支撑 ViewModel 单测，
 * 不额外引入 repository / usecase 套层。
 */
interface AdbGateway {

    /**
     * 连接到指定设备地址。
     *
     * @param endpoint 目标设备地址，通常为 `host:port`。
     * @return 原始 adb 命令执行结果。
     */
    fun connect(endpoint: String): EmbeddedAdb.AdbCommandResult

    /**
     * 断开指定设备连接。
     *
     * @param endpoint 目标设备地址或 serial。
     * @return 原始 adb 命令执行结果。
     */
    fun disconnect(endpoint: String): EmbeddedAdb.AdbCommandResult

    /**
     * 查询当前 adb 可直接操作的设备列表。
     *
     * @return 设备列表和原始命令结果。
     */
    fun listDevices(): EmbeddedAdb.DeviceListResult

    /**
     * 读取设备目录下的一层文件列表。
     *
     * @param serial 目标设备 serial。
     * @param directoryPath 待读取目录。
     * @return 目录条目列表和原始命令结果。
     */
    fun listRemoteEntries(serial: String, directoryPath: String): EmbeddedAdb.RemoteEntryListResult

    /**
     * 下载远端文件或目录。
     *
     * @param serial 目标设备 serial。
     * @param remotePath 设备端文件或目录路径。
     * @param localPath 本地保存路径。
     * @param onProgress 下载进度回调。
     * @return 原始 adb 命令执行结果。
     */
    fun pull(
        serial: String,
        remotePath: String,
        localPath: String,
        onProgress: ((EmbeddedAdb.PullProgress) -> Unit)? = null,
    ): EmbeddedAdb.AdbCommandResult

    /**
     * 上传本地文件到远端目录。
     *
     * @param serial 目标设备 serial。
     * @param localPath 本地文件绝对路径。
     * @param remotePath 设备端目标目录。
     * @param onProgress 上传进度回调。
     * @return 原始 adb 命令执行结果。
     */
    fun push(
        serial: String,
        localPath: String,
        remotePath: String,
        onProgress: ((EmbeddedAdb.PullProgress) -> Unit)? = null,
    ): EmbeddedAdb.AdbCommandResult

    /**
     * 在指定设备上安装单个 APK。
     *
     * @param serial 目标设备 serial。
     * @param apkPath 本地 APK 绝对路径。
     * @return 原始 adb 命令执行结果。
     */
    fun installApk(serial: String, apkPath: String): EmbeddedAdb.AdbCommandResult

    /**
     * 删除远端文件或目录。
     *
     * @param serial 目标设备 serial。
     * @param remotePath 待删除的远端路径。
     * @param isDirectory 当前目标是否为目录。
     * @return 原始 adb 命令执行结果。
     */
    fun deleteRemoteEntry(
        serial: String,
        remotePath: String,
        isDirectory: Boolean,
    ): EmbeddedAdb.AdbCommandResult

    /**
     * 判断 connect 是否成功。
     *
     * @param result `adb connect` 的执行结果。
     * @return 是否成功建立连接。
     */
    fun isConnectSuccess(result: EmbeddedAdb.AdbCommandResult): Boolean
}

/**
 * 当前项目默认使用的 adb 网关实现。
 *
 * 这里只做直接委托，保持行为与现有 `EmbeddedAdb` 一致。
 */
class EmbeddedAdbGateway : AdbGateway {
    override fun connect(endpoint: String): EmbeddedAdb.AdbCommandResult = EmbeddedAdb.connect(endpoint)

    override fun disconnect(endpoint: String): EmbeddedAdb.AdbCommandResult = EmbeddedAdb.disconnect(endpoint)

    override fun listDevices(): EmbeddedAdb.DeviceListResult = EmbeddedAdb.listDevices()

    override fun listRemoteEntries(
        serial: String,
        directoryPath: String,
    ): EmbeddedAdb.RemoteEntryListResult = EmbeddedAdb.listRemoteEntries(serial, directoryPath)

    override fun pull(
        serial: String,
        remotePath: String,
        localPath: String,
        onProgress: ((EmbeddedAdb.PullProgress) -> Unit)?,
    ): EmbeddedAdb.AdbCommandResult {
        return EmbeddedAdb.pull(
            serial = serial,
            remotePath = remotePath,
            localPath = localPath,
            onProgress = onProgress,
        )
    }

    override fun push(
        serial: String,
        localPath: String,
        remotePath: String,
        onProgress: ((EmbeddedAdb.PullProgress) -> Unit)?,
    ): EmbeddedAdb.AdbCommandResult {
        return EmbeddedAdb.push(
            serial = serial,
            localPath = localPath,
            remotePath = remotePath,
            onProgress = onProgress,
        )
    }

    override fun installApk(serial: String, apkPath: String): EmbeddedAdb.AdbCommandResult {
        return EmbeddedAdb.installApk(serial, apkPath)
    }

    override fun deleteRemoteEntry(
        serial: String,
        remotePath: String,
        isDirectory: Boolean,
    ): EmbeddedAdb.AdbCommandResult {
        return EmbeddedAdb.deleteRemoteEntry(
            serial = serial,
            remotePath = remotePath,
            isDirectory = isDirectory,
        )
    }

    override fun isConnectSuccess(result: EmbeddedAdb.AdbCommandResult): Boolean {
        return EmbeddedAdb.isConnectSuccess(result)
    }
}
