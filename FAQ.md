# Moonlight V+ 常见问题

这份 Q&A 只收录已经在 Moonlight V+ Issue / PR 中反复出现、或有明确排障结论的问题。提交新 Issue 前，建议先按这里的步骤排查一次。

通用 Moonlight 配置可参考上游文档：[Moonlight 设置指南](https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide) 和 [Moonlight 排障指南](https://github.com/moonlight-stream/moonlight-docs/wiki/Troubleshooting)。

## 提交问题前先做基础验证

请先用保守配置验证基础链路：

- 1080p，60 FPS
- H.264 或自动编码
- 关闭 HDR
- 10-20 Mbps 码率
- 主机尽量使用有线网络

如果这个配置稳定，再逐个提高分辨率、帧率、码率，打开 HEVC / AV1、HDR 和 V+ 增强功能。这样能更快判断问题是在网络、解码器、显示模式、主机端，还是某个 V+ 增强功能。

反馈串流问题时，请尽量提供：

- Moonlight V+ 版本
- 客户端设备型号和 Android 版本
- 主机端软件及版本：Sunshine、Foundation Sunshine 或 GFE
- 显卡型号和驱动版本
- 分辨率、帧率、编码格式、HDR、码率
- 网络路径：局域网、外网、VPN、EasyTier、Tailscale、ZeroTier 等
- 性能覆盖层截图或关键数值
- 崩溃日志、logcat 或 ADB 诊断信息

## 画面与解码器

### Q：Android TV / 电视盒子用 H.265 / HEVC 黑屏，应该先怎么排查？

按顺序试：

1. 切到 H.264，确认同一串流能正常显示。
2. 先测 1080p60 HEVC，再尝试 1440p、4K、HDR 或高刷新率。
3. 关闭 HDR 后重新连接。
4. 有条件的话，对比原版 Moonlight。
5. 复现时抓一份 logcat。

有些电视盒子虽然宣称支持 HEVC，但在特定分辨率或解码路径下会黑屏或变成极低帧率。ONN 4K Pro / S905X 类设备反馈过 HEVC 黑屏或低 FPS，而 H.264 正常。PR #289 缓解了一条 Amlogic HEVC 黑屏路径，但 HEVC 极低性能仍可能是设备固件/解码器实现限制。

来源：[#249](https://github.com/qiin2333/moonlight-vplus/issues/249)、[#289](https://github.com/qiin2333/moonlight-vplus/pull/289)

### Q：MTK 电视更新后串流延迟变成几秒，是设置问题吗？

如果降低分辨率、降低帧率、关闭 HDR 都没改善，可能不是带宽或主机负载问题，而是设备解码调度路径的回归。

海信 U7Q 的案例中，诊断信息指向 MTK HEVC 解码输出逐渐积压。PR #300 移除了风险较高的 MTK `KEY_OPERATING_RATE = Short.MAX_VALUE` 覆盖，同时保留较安全的低延迟 vendor 参数。反馈者确认测试包修复了问题。

请更新到包含 PR #300 或之后的版本。如果仍异常，请附性能覆盖层数据，以及 SurfaceFlinger、meminfo、media player state、logcat 等 ADB 诊断信息。

来源：[#299](https://github.com/qiin2333/moonlight-vplus/issues/299)、[#300](https://github.com/qiin2333/moonlight-vplus/pull/300)

### Q：HDR 发灰、过亮、过暗，或第一次颜色不正常怎么办？

先隔离是不是 HDR 本身的问题：

1. 关闭 HDR 后重新连接。
2. 用 1080p60 这类标准分辨率/刷新率测试。
3. 如果基础串流稳定，再打开 HDR，并尝试 V+ 的 HDR 亮度校准。

Moonlight V+ 已加入手动 HDR 亮度校准，用来处理部分设备 HDR 能力上报不准导致的观感问题。

来源：[#374](https://github.com/qiin2333/moonlight-vplus/pull/374)、[#395](https://github.com/qiin2333/moonlight-vplus/issues/395)

## 网络与远程连接

### Q：通过虚拟局域网 / VPN 时发现不到或添加不了电脑？

先确认不经过 VPN 时，普通局域网串流是否正常。然后反馈：

- 使用的虚拟网络：EasyTier、Tailscale、ZeroTier、Clash TUN 等
- 是发现主机失败、手动 IP 添加失败，还是配对后串流失败
- 是否与某个 V+ 版本变化有关
- Android 当前是 VPN、TUN、SOCKS 还是应用代理路径

12.6.5 之后曾出现过虚拟网络发现主机异常。PR #301 修复了 VPN 虚拟局域网设备探测问题，反馈者确认后续构建可正常识别。

来源：[#297](https://github.com/qiin2333/moonlight-vplus/issues/297)、[#301](https://github.com/qiin2333/moonlight-vplus/pull/301)

### Q：EasyTier 的 Network Secret 留空连不上？

安全起见，推荐在 EasyTier 服务端设置 Network Secret。如果你确实需要空密钥，请使用包含 EasyTier 空密钥恢复逻辑的版本。应用也应该给出提示，而不是静默失败。

来源：[#383](https://github.com/qiin2333/moonlight-vplus/issues/383)、[#392](https://github.com/qiin2333/moonlight-vplus/pull/392)

## 输入

### Q：USB 手柄被识别成了错误的手柄类型？

如果蓝牙连接正常、USB 连接映射不同，先检查 Sunshine 的手柄输入设置。有一个案例中，把 Sunshine 输入手柄从自动改为强制 Xbox 后解决了错误识别。

反馈时请提供手柄型号、USB/蓝牙连接方式、Android 设备、Sunshine 设置，以及原版 Moonlight 是否同样表现。

来源：[#328](https://github.com/qiin2333/moonlight-vplus/issues/328)

### Q：触控笔、手写笔或触控板输入不对？

请先使用较新的版本。V+ 在这块持续改进：

- PR #348 增加了平板原生精密触摸板支持。
- PR #382 在指针捕获前优先路由触控笔输入。
- PR #312 修复了串流分辨率与平板硬件分辨率不一致时 S Pen 悬停坐标偏移。

反馈触控/手写笔问题时，请说明是否平板设备、是否启用指针捕获、串流分辨率、物理屏幕分辨率、横竖屏，以及是否外接显示器。

来源：[#348](https://github.com/qiin2333/moonlight-vplus/pull/348)、[#382](https://github.com/qiin2333/moonlight-vplus/pull/382)、[#312](https://github.com/qiin2333/moonlight-vplus/pull/312)

## 音频

### Q：画面正常，但 Android TV 上声音轻微卡顿？

先在主机系统里降低声卡输出采样率。也建议用 1080p60、较低码率的保守串流配置测试，因为部分电视 SoC 同时处理 4K120/高码率视频和音频时余量不足。

如果开启了音频直通，先测试关闭直通；如果接了 AVR 并需要直通，再尝试更大的直通缓冲。

来源：[#298](https://github.com/qiin2333/moonlight-vplus/issues/298)

