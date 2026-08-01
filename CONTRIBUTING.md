# 贡献指南

## 开发环境

项目使用 [mise](https://mise.jdx.dev/) 统一管理本机工具，包括 JDK 25、Android SDK、Rust 和 `cargo-ndk`。首次检出代码后，请先核对 [mise.toml](mise.toml) 内容并信任该配置：

```bash
mise trust
mise install
mise run setup
```

`setup` 会请求确认 Android SDK 许可证，然后安装以下基础组件：

- Android platform 37
- Android build tools 37.0.0
- Android platform tools

原生 crate 未包含在公开仓库中。项目默认使用已提交的 arm64 预构建库：

```text
app/prebuilt/readerQrNative/jniLibs/arm64-v8a/libreader_qr_native.so
```

如果开发者有权限使用私有原生仓库，请将其检出到：

```text
native/reader-qr-native/
```

当 `native/reader-qr-native/Cargo.toml` 存在时，`setup` 还会安装 Android NDK r29 (`29.0.14206865`) 和 Rust target `aarch64-linux-android`；Gradle 会自动改为从源码构建 `.so`。没有该目录时，Rust 检查任务会跳过，Android 构建不需要 Cargo 或 NDK。

## 本机验证与构建

运行 Rust 格式检查、Clippy、Rust 测试、Android debug 单元测试和 Android lint：

```bash
mise run check
```

运行完整检查并生成 debug APK：

```bash
mise run build
```

调试 APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

如果已连接 ADB 设备，可构建、覆盖安装并启动 debug 应用：

```bash
mise run install
```

可以单独验证或构建 Rust 原生 crate：

```bash
mise run native:check
mise run native:build
```

可以在私有原生仓库存在时强制验证公开仓库的预构建路径：

```bash
mise run prebuilt:check
```

原有构建脚本仍可用，但它使用当前 shell 环境中的工具：

```bash
./scripts/build-android15.sh
./scripts/build-android15.sh testDebugUnitTest assembleDebug
```

发布构建已启用 R8 代码混淆。仓库不包含发布密钥，因此以下命令生成未签名 APK，正式分发前需要使用自己的 keystore 签名：

```bash
mise exec -- ./gradlew assembleRelease
```

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

## 自动发布

每次代码推送到 `main` 分支时，GitHub Actions 会运行单元测试、Release Lint 和 R8 构建。公开仓库使用已提交的预构建 `.so`；只有工作区中存在私有原生 crate 时，才会额外安装 Rust Android target、`cargo-ndk` 和 NDK r29。工作流随后签名 APK，并创建包含 APK 与 SHA-256 校验文件的 GitHub Release。CI 会根据工作流运行编号生成递增的 `versionCode`，发布标签格式为 `v1.0.0-main.<run_number>`。

首次启用前，需要在仓库的 GitHub Actions Secrets 中配置：

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_PASSWORD`

可以使用 GitHub CLI 写入密钥文件和其他签名参数：

```bash
openssl base64 -A -in release.jks | gh secret set RELEASE_KEYSTORE_BASE64
gh secret set RELEASE_KEY_ALIAS
gh secret set RELEASE_KEYSTORE_PASSWORD
gh secret set RELEASE_KEY_PASSWORD
```

## 安全说明

- 应用仅允许 HTTPS 请求。
- 登录密码在请求前按网站协议进行 AES-CBC 加密，设备不会保存明文密码。
- 具有服务端有效期的登录 Cookie 使用 Android Keystore 的 AES-GCM 密钥加密后持久化；会话 Cookie 仅保留在内存中。
- 读者证二维码的请求与签名在 Rust 原生库中完成；连接使用固定证书，只有内置证书到期后才回退到 Android 系统 CA。
- 扫码只把结构有效的 HTTPS 二维码作为空间标识载体，不会打开或执行二维码链接；空间与预约信息始终由图书馆 API 校验。
- 定位权限仅在具有距离限制的签到流程中按需申请，WGS-84 定位会在中国境内转换为服务端使用的 GCJ-02 坐标。

## API 契约

从网站 JavaScript 还原并校正后的接口定义位于 [reservation-openapi.yaml](reservation-openapi.yaml)。
