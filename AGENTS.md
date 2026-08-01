# Repository Agent Instructions

## 语言与编码风格

- 解释、讨论、分析和总结使用简体中文。
- 所有代码、注释、标识符、提交信息及 Markdown 代码块内容使用 English，不得包含中文字符。
- Android 字符串资源中的中文使用 Unicode 转义，保持现有项目风格。
- Kotlin 和 Jetpack Compose 代码遵循官方格式与社区惯例。
- 仅在行为或意图不明显时添加注释，优先解释原因。

## 功能请求完成后的默认流程

实现每个 feature request 后，除非用户明确要求跳过，必须在同一轮继续完成以下操作，用户无需再次要求构建或推送：

1. 检查改动范围，保留用户已有和无关的工作区修改。
2. 运行 `git diff --check`，修复空白、冲突标记及补丁格式问题。
3. 运行与改动相关的最小测试；Android 功能默认运行全部 debug 单元测试。
4. 构建当前源码对应的 debug APK，不得复用改动前的旧产物。
5. 运行 Android lint，并处理由本次改动引入的问题。
6. 若涉及权限、Manifest、ABI、压缩或打包配置，检查最终 APK，而不只检查源文件。
7. 若有已连接的 ADB 设备，使用覆盖安装方式推送最新 debug APK。
8. 安装后启动 debug 应用进行冒烟验证，并按风险验证实际效果：
   - UI 改动检查目标页面、交互和状态反馈。
   - 数据、Provider、权限、通知或后台任务改动读取设备实际状态进行验证。
   - WebView 改动验证真实页面加载及注入后的效果。
9. 清理验证过程中创建的临时数据、测试日程、测试文件或测试 APK。
10. 最终回复明确报告实现结果、测试结果、构建结果、ADB 安装结果、设备验证结果及 APK 路径。

Android 默认验证命令为：

```shell
env ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew testDebugUnitTest assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p cn.ahlib.reservation.debug 1
```

## 验证与失败处理

- 不得仅凭代码推断就宣称真机功能生效；能够读取设备状态时，应以实际状态为准。
- 任一步骤失败时，先定位并修复本次改动造成的问题，再重新执行相关验证。
- 如果设备未连接、权限需要用户交互或外部状态阻止验证，明确说明未完成的验证及原因。
- 不自动授予敏感权限，不修改用户数据来绕过验证；必要的测试数据必须范围明确且可清理。
- 除非用户明确要求，不执行 git stage、commit、push 或创建 PR。
