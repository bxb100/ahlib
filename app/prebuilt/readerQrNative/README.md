# Reader QR Native Prebuilt

该目录保存公开 Android 项目使用的 arm64 预构建原生库。当 `native/reader-qr-native/Cargo.toml` 不存在时，Gradle 会直接打包此文件。

当私有原生仓库更新后，请先运行源码构建，再将经过验证的产物覆盖到此目录：

```shell
mise run native:build
cp app/build/generated/readerQrNative/jniLibs/arm64-v8a/libreader_qr_native.so \
  app/prebuilt/readerQrNative/jniLibs/arm64-v8a/libreader_qr_native.so
mise run prebuilt:check
```

Android 系统 CA 验证辅助类由 `app/libs/rustls-platform-verifier-0.1.1.aar` 提供，需与原生库使用的 `rustls-platform-verifier` 版本保持兼容。
