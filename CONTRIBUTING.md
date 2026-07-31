# Guiding

## 本机构建

```bash
./scripts/build-android15.sh
```

也可以传入指定的 Gradle 任务：

```bash
./scripts/build-android15.sh testDebugUnitTest assembleDebug
```

调试 APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

发布构建已启用 R8 代码混淆。仓库不包含发布密钥，因此以下命令生成未签名 APK，正式分发前需要使用自己的 keystore 签名：

```bash
./scripts/build-android15.sh assembleRelease
```

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

## 自动发布

每次代码推送到 `main` 分支时，GitHub Actions 会使用 JDK 25 运行单元测试、
Release Lint 和 R8 构建，随后签名 APK，并创建包含 APK 与 SHA-256 校验文件的
GitHub Release。CI 会根据工作流运行编号生成递增的 `versionCode`，发布标签格式为
`v1.0.0-main.<run_number>`。

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
- 扫码只把结构有效的 HTTPS 二维码作为空间标识载体，不会打开或执行二维码链接；空间与预约信息始终由图书馆 API 校验。
- 定位权限仅在具有距离限制的签到流程中按需申请，WGS-84 定位会在中国境内转换为服务端使用的 GCJ-02 坐标。

## API 契约

从网站 JavaScript 还原并校正后的接口定义位于 [reservation-openapi.yaml](reservation-openapi.yaml)。
