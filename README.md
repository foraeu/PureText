<div align="center">

# 📖 PureText
**一个专为 Android 打造的高性能、纯净文本与源码阅读及智能编辑器**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=flat-square&logo=android)](https://developer.android.com/about/versions)
[![Language](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![UI Framework](https://img.shields.io/badge/Jetpack_Compose-2024.09-4285F4?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Build Status](https://img.shields.io/badge/CI%2FCD-GitHub_Actions-2088FF?style=flat-square&logo=githubactions)](https://github.com/features/actions)

<p align="center">
  🚀 采用先进的<b>视口行级懒加载</b>与<b>智能分块编辑</b>架构，专为在移动端流畅阅读和修改十万行级大文件而生。
</p>

</div>

---

## 🌟 核心亮点

### ⚡ 极致性能与大文件优化
* **毫秒级首屏渲染**：首屏秒开前 1000 行，后续数据在后台协程流式分包加载，彻底解决大文件加载导致界面卡死的问题。
* **智能分块编辑 (Sliding Viewport)**：自动截取当前阅读行周边的 400 行局部缓存作为编辑区，打字输入零延迟。

### 🎨 源码级阅读体验
* **全能语法高亮**：原生支持 Kotlin, Java, Python, Rust, TS/JS, JSON, Markdown, HTML, Bash, CSS 等主流语言。
* **专业级文字排版**：内置 5 款精选阅读主题（浅色、暗色、深黑、复古、调试），并支持对字号、行距、边距及等宽字体进行细致调节。

### 🔍 精准搜索与智能定位
* **全文检索引擎**：支持正则表达式搜索与大小写敏感过滤。
* **轨迹联动跳转**：高亮匹配结果，并支持通过跳转控件一键飞速滚动定位。

---

## 🛠️ 技术栈

PureText 基于现代化 Android 原生技术栈构建：

| 模块 | 采用技术 | 说明 |
| :--- | :--- | :--- |
| **UI 框架** | Jetpack Compose (M3) | 全声明式响应式 UI，高度解耦与丝滑过渡 |
| **持久化存储** | Room Database (SQLite) | 本地配置持久化与历史文件信息跟踪 |
| **异步流处理** | Kotlin Coroutines & Flow | 承载高性能的文件流式解析与检索过滤器 |
| **签名系统** | 默认自签名 | 移除了依赖硬编码的证书，零障碍本地编译 |

---

## 🚀 快速开始

### 本地编译运行

1. 克隆代码库：
   ```bash
   git clone https://github.com/foraeu/PureText.git
   ```
2. 使用最新版 **Android Studio** 打开该项目。
3. 等待 Gradle 同步完成后，连接您的 Android 真机或模拟器。
4. 点击 **Run** 按钮即可一键编译并安装运行。

### GitHub Actions 自动构建

本项目已经集成了自动构建流水线，无需在本地配置任何 Android 编译环境：

1. 提交或推送代码至 `main` / `master` 分支，或者在 GitHub 仓库的 **Actions** 页面手动触发。
2. 编译完成后，在工作流运行页面的 **Artifacts** 区域即可直接下载打包好的 `PureText-Debug-APK` 安装包。

---

## 🛡️ 许可证

本项目遵循 [MIT License](LICENSE) 许可协议。
