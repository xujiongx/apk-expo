# 工具箱

基于 Expo / React Native 的安卓个人工具箱。当前包含：

- **定时提醒**（多少分钟后 / 指定时刻 / 每天定点发系统通知）
- **快手极速版自动签到**（系统无障碍服务驱动）
- **系统能力实验室**（电量、网络、传感器、震动等 Expo API Demo）

> 无障碍自动化依赖本地原生模块 `auto-assist`，**必须安装 EAS 构建的 APK**，Expo Go / Web 只能打开应用，不能模拟点击。

## 技术栈

| 项 | 版本 / 说明 |
| --- | --- |
| Expo SDK | ~58 |
| React Native | 0.88 |
| React | 19.3 |
| 包管理 | pnpm |
| 应用名 | 工具箱（`app.json`） |
| Android 包名 | `com.example.apkdemo` |
| 版本 | 见 `app.json` → `expo.version` |

## 目录结构

```
├── App.tsx                 # 入口路由（首页 / 设备实验室 / 快手签到）
├── src/
│   ├── screens/            # 页面
│   ├── theme.ts            # 颜色 / 字号 / 间距
│   ├── ui.tsx              # 通用 UI 组件
│   └── toast/              # Toast
├── modules/auto-assist/    # 本地 Expo Module（AccessibilityService）
├── docs/                   # 设计与说明文档
├── app.json                # Expo 配置
└── eas.json                # EAS 构建配置
```

## 快速开始

```bash
pnpm install
pnpm start          # 开发服务器（Web / Expo Go，无障碍不可用）
pnpm web            # 仅 Web
pnpm build:apk      # EAS 云构建可安装 APK（preview）
```

安装 APK 后：

1. 系统设置 → 无障碍 → 已安装的服务 → 开启「工具箱自动签到」
2. （推荐）忽略电池优化，并在手机管家允许自启动 / 后台运行
3. 打开 App → 快手极速版签到 → 自动签到

## 文档

| 文档 | 内容 |
| --- | --- |
| [项目说明](./docs/project-overview.md) | 目标、范围、约束、运行方式 |
| [架构设计](./docs/architecture.md) | 分层、模块边界、无障碍流程、构建链路 |
| [功能设计](./docs/features.md) | 功能清单、交互流程、权限与边界 |

## 重要约束

- **不能**程序化永久授权无障碍；覆盖安装 / 强停后需用户重新开启。
- 快手等 App 可能屏蔽无障碍节点，自动化会回退到**屏幕比例坐标点击**。
- 修改 `modules/auto-assist` 原生代码后必须**重新打 APK**。

## License

见 [LICENSE](./LICENSE)。
