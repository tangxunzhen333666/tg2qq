# TG2QQ - Telegram 频道消息转发到 QQ 群

LSPosed/Xposed 模块：Hook Telegram 客户端，本地监听频道新消息，通过 [NapCat](https://napneko.github.io/) OneBot HTTP API 自动转发到指定 QQ 群。无需 Telegram Bot，直接复用已登录的 Telegram 客户端。

## 原理

```
Telegram 客户端 (org.telegram.messenger / plus / mdgram)
        │  被 LSPosed Hook
        ▼
ForwardService 监听新消息事件
        │  反射读取频道消息（文本/图片/视频等）
        ▼
ForwardQueue 异步队列（2s 轮询）
        │
        ▼
NapcatClient → NapCat OneBot API (http://127.0.0.1:3001)
        │
        ▼
QQ 群消息
```

## 功能特性

- 多频道路由：可将不同 TG 频道分别转发到不同 QQ 会话
- 媒体转发：图片、视频、文件自动下载后发送
- 关键词过滤：命中关键词的消息自动跳过
- 去话题标签（#hashtag）
- 消息前缀/后缀自定义
- 配置项实时生效，无需重启 TG

## 构建

环境要求：JDK 17、Android SDK（build-tools 35.0.0、platform android-28）、aapt2、d8、apksigner。

```bash
bash build3.sh
```

产物：`out/tgforward.apk`（自签名，签名密钥 `/sdcard/Download/TG2QQ/keystore.jks`）

## 配置

通过 LSPosed 管理器激活模块并勾选 Telegram 客户端后，在模块设置页或 `shared_prefs/tg_forward.xml` 中配置：

| 键 | 说明 |
|---|---|
| `enabled` | 总开关 |
| `chat_id` | 源 TG 频道 ID（如 -100xxxxxxxxxx） |
| `routes` | 路由表，格式：`频道ID1,频道ID2=QQ会话ID` |
| `group_id` | 目标 QQ 群号/会话号 |
| `napcat_url` | NapCat OneBot HTTP 地址 |
| `interval_ms` | 轮询间隔（默认 2000） |
| `keywords` | 关键词过滤（命中即跳过） |
| `forward_media` | 是否转发媒体文件 |
| `strip_hashtags` | 是否去除话题标签 |
| `prefix` / `suffix` | 消息前后缀 |
| `max_age_sec` | 消息最大有效时长（默认 86400） |
| `dl_timeout_sec` | 媒体下载超时（默认 120） |

## 目录结构

```
├── AndroidManifest.xml      # 模块声明（xposedmodule）
├── src/                     # 模块源码
│   └── com/operit/tg2qq/
│       ├── HookEntry.java       # LSPosed 入口
│       ├── core/                # 转发核心（监听/解析/队列/媒体）
│       ├── network/             # NapCat OneBot 客户端
│       ├── config/              # 配置与设置页
│       └── util/                # 工具类
├── stub/                    # Xposed API stub
├── res/                     # 资源
├── assets/                  # xposed_init 声明
└── build*.sh                # 构建脚本
```

## 免责声明

本项目仅供学习交流使用，请勿用于违反 Telegram/QQ 服务条款的用途。
