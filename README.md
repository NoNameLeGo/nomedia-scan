# nomedia 扫描助手（nomedia-scan）

临时放开安卓超大文件夹的媒体扫描，让网盘「相册备份」能稳定备份，备份完再隐藏回去。

## 解决什么问题

- 手机上有超大文件夹（如 150G，全部是子文件夹包图片），内含 `.nomedia` → 系统媒体库不索引 → 网盘 App 的相册备份扫不到
- 直接让网盘 App 备份该文件夹（SAF 授权路径）在大目录下不稳定
- 本应用：**移出 `.nomedia` → 触发媒体扫描 → 网盘走相册备份通道 → 备份完放回 `.nomedia` → 清理索引**

## 工作流程

```
① 临时打开：移出 .nomedia + 分批触发扫描（显示进度）
② 备份窗口：去网盘 App 用「相册备份」备份
③ 恢复隐藏：放回 .nomedia + 清理媒体库索引，图库恢复原样
```

## 两种模式

| 模式 | 权限 | 特点 |
|---|---|---|
| **Shizuku**（推荐） | Shizuku 授权 + 媒体读取 | shell 直操文件，秒级完成，适合超大目录 |
| **SAF**（低权限兜底） | 媒体读取 + 系统选择器授权 | 免装 Shizuku，但大目录遍历较慢 |

## 构建

GitHub Actions 自动构建（push tag 如 `beta0.0.1` 触发签名 release）：

```bash
git tag beta0.0.1 && git push origin beta0.0.1
```

Release 签名需要仓库配置 Secrets：

- `ANDROID_RELEASE_KEYSTORE_BASE64`（keystore 的 base64）
- `ANDROID_RELEASE_STORE_PASSWORD` / `ANDROID_RELEASE_KEY_ALIAS` / `ANDROID_RELEASE_KEY_PASSWORD`

## 本地开发

```bash
cd android
./gradlew assembleDebug   # 需要 JDK 17 + Android SDK
```

## 注意事项

- 150G 全量索引耗时 20 分钟~数小时，期间保持应用前台
- 部分 ROM 有扫描数量上限，进度观察 + 续扫
- beta 版本，仅用于个人备份场景

## License

个人项目，仅供学习参考。
