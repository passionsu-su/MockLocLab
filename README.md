# MockLoc Lab

Android 系统模拟定位测试示例工程，适用于自有设备、测试设备及获授权的应用测试场景。目标 Android 8–15，免 Root。

## 功能
- 手动输入纬度、经度、位置名称
- 保存/载入常用位置，长按条目删除
- 调用高德地图查看坐标（外部地图跳转，不含内嵌高德 SDK）
- 开始/停止系统 mock location
- 前台通知
- 支持高德/腾讯坐标（GCJ-02）自动转换为 GPS 坐标（WGS-84），避免几百米偏移
- 同时模拟 GPS、网络、Fused 定位提供者（Android 12+）
- 启动前检查是否已在开发者选项中选为模拟位置应用
- GitHub Actions 云端构建 Debug APK

## 构建
在 Android Studio 中打开仓库根目录，等待 Gradle Sync 后运行 Build > Build APK(s)，或执行 `gradle assembleDebug`。

## 手机启用
1. 安装 `app/build/outputs/apk/debug/app-debug.apk`。
2. 打开开发者选项，在“选择模拟位置信息应用”中选择 **MockLoc Lab**。
3. 打开应用并授予定位权限，输入坐标（从高德地图复制的坐标保持勾选“GCJ-02”），点击“开始模拟定位”。
4. 点击“停止模拟定位”结束。

## GitHub Actions
打开 **Actions → Build Android APK → Run workflow**。构建成功后，在运行记录的 Artifacts 下载 `MockLocLab-debug-apk`，有效期 14 天。推送到 main/master 的相关文件变更也会自动构建。

## 限制与安全
- 目前尚未声明为经过 Android 15 真机验证的正式发布版。
- 系统模拟定位不保证第三方应用接受该坐标；第三方应用可能识别 mock location 或使用服务端/Wi-Fi/基站等其他定位依据。
- 本项目不提供隐藏模拟定位、绕过考勤风控或伪造第三方到岗记录功能。
- 仅在你拥有或获授权测试的设备和应用中使用。

## License
MIT
