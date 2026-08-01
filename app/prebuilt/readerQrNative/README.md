# Reader QR Native Prebuilt

该目录保存公开 Android 项目使用的 arm64 预构建原生库。当 `native/reader-qr-native/Cargo.toml` 不存在时，Gradle 会直接打包此文件。

当私有原生仓库更新后，请先运行源码构建，再将经过验证的产物覆盖到此目录：

```shell
mise run native:build
cp app/build/generated/readerQrNative/jniLibs/arm64-v8a/libreader_qr_native.so \
  app/prebuilt/readerQrNative/jniLibs/arm64-v8a/libreader_qr_native.so
mise run prebuilt:check
```

Android 系统 CA 验证辅助类由 `app/libs/rustls-platform-verifier.aar` 提供。私有原生仓库存在时，运行 `mise run rustls-aar:update` 可根据 Cargo 解析出的兼容版本更新该文件。
