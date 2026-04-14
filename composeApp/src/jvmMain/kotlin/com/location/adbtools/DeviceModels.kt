package com.location.adbtools

/**
 * 已连接设备的连接类型。
 *
 * 这里只保留当前界面真正需要区分的两类设备，
 * 避免后续在 UI 和状态层反复通过 serial 文本做分散判断。
 */
enum class DeviceConnectionType {
    Usb,
    Network,
}

/**
 * 当前 adb 可操作的已连接设备。
 *
 * @property serial adb 设备唯一标识；USB 设备通常是序列号，网络设备通常是 `host:port`。
 * @property displayName 展示在下拉框中的文案，直接体现连接类型和 serial。
 * @property connectionType 当前设备的连接来源，只区分 USB 与网络连接。
 */
data class ConnectedDevice(
    val serial: String,
    val displayName: String,
    val connectionType: DeviceConnectionType,
)

/**
 * 根据 adb serial 构造界面侧使用的设备对象。
 *
 * 这里把连接类型识别和展示文案拼装集中起来，
 * 避免连接成功兜底和设备列表解析各自维护一套判断规则。
 *
 * @param serial adb 设备唯一标识；网络设备通常为 `host:port`。
 * @return 可直接放入页面状态的设备对象。
 */
internal fun buildConnectedDevice(serial: String): ConnectedDevice {
    val connectionType = if (serial.contains(':')) {
        DeviceConnectionType.Network
    } else {
        DeviceConnectionType.Usb
    }
    val displayName = when (connectionType) {
        DeviceConnectionType.Usb -> "USB · $serial"
        DeviceConnectionType.Network -> "网络 · $serial"
    }
    return ConnectedDevice(
        serial = serial,
        displayName = displayName,
        connectionType = connectionType,
    )
}
