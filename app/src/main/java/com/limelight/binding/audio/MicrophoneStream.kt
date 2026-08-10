package com.limelight.binding.audio

import com.limelight.LimeLog
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.jni.MoonBridge

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class MicrophoneStream(
    private val conn: NvConnection
) : MicrophoneCapture.MicrophoneDataCallback {

    private var capture: MicrophoneCapture? = null
    private var encoder: OpusEncoder? = null
    private val encoderLock = Any()
    private var senderThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val micActive = AtomicBoolean(false)
    private val hostRequested = AtomicBoolean(false)
    private var packetQueue = LinkedBlockingQueue<ByteArray>(MicrophoneConfig.MAX_QUEUE_SIZE)

    init {
        LimeLog.info("初始化麦克风流")
    }

    fun start(): Boolean {
        try {
            AudioDiagnostics.resetStatistics()

            if (!running.get()) {
                if (MoonBridge.isMicrophoneRequested()) {
                    LimeLog.info("主机请求麦克风，开始捕获")

                    if (MoonBridge.isMicrophoneEncryptionEnabled()) {
                        LimeLog.info("麦克风加密已启用")
                    }

                    hostRequested.set(true)
                    return startMicrophoneCapture()
                } else {
                    LimeLog.info("主机未请求麦克风，将等待请求")
                    return true
                }
            } else {
                if (!micActive.get()) {
                    LimeLog.info("重新启动麦克风捕获")
                    return startMicrophoneCapture()
                } else {
                    LimeLog.info("麦克风已经在运行")
                    return true
                }
            }
        } catch (e: Exception) {
            LimeLog.severe("启动麦克风流失败: ${e.message}")
            cleanup()
            return false
        }
    }

    fun isMicrophoneAvailable(): Boolean = MoonBridge.isMicrophoneRequested()

    private fun stopMicrophoneCapture() {
        if (!micActive.get()) return

        micActive.set(false)

        capture?.stop()
        capture = null

        cleanup()
    }

    private fun startMicrophoneCapture(): Boolean {
        if (micActive.get()) return true

        try {
            val micPort = MoonBridge.getMicPortNumber()
            if (micPort == 0) {
                LimeLog.warning("未获取到协商的麦克风端口")
            } else {
                LimeLog.info("使用协商的麦克风端口: $micPort")
            }

            encoder = OpusEncoder(MicrophoneConfig.SAMPLE_RATE, MicrophoneConfig.CHANNELS, MicrophoneConfig.getOpusBitrate())

            capture = MicrophoneCapture(this)
            if (!capture!!.start()) {
                LimeLog.severe("无法启动麦克风捕获")
                cleanup()
                return false
            }

            if (senderThread == null || !senderThread!!.isAlive) {
                running.set(true)
                senderThread = Thread(::senderThreadProc, "MicSender").apply {
                    priority = Thread.MAX_PRIORITY
                    start()
                }
            }

            micActive.set(true)
            LimeLog.info("麦克风捕获已启动")
            return true
        } catch (e: SecurityException) {
            LimeLog.severe("麦克风权限不足: ${e.message}")
            cleanup()
            return false
        } catch (e: Exception) {
            LimeLog.severe("启动麦克风捕获失败: ${e.message}")
            cleanup()
            return false
        }
    }

    fun stop() {
        running.set(false)
        micActive.set(false)
        hostRequested.set(false)

        capture?.stop()
        capture = null

        senderThread?.let {
            try {
                it.join(300)
            } catch (_: InterruptedException) { }
        }
        senderThread = null

        cleanup()
        LimeLog.info("麦克风流已停止")
    }

    fun pause() {
        if (micActive.get()) {
            stopMicrophoneCapture()
            LimeLog.info("麦克风捕获已暂停")
        }
    }

    fun resume(): Boolean {
        if (!micActive.get() && running.get()) {
            LimeLog.info("尝试恢复麦克风捕获")
            val result = startMicrophoneCapture()
            if (result) {
                LimeLog.info("麦克风捕获恢复成功")
            } else {
                LimeLog.warning("麦克风捕获恢复失败")
            }
            return result
        }
        return false
    }

    fun isRunning(): Boolean = running.get() && micActive.get()

    fun isInitialized(): Boolean = running.get()

    fun getAudioContinuityStatus(): String = AudioDiagnostics.getCurrentStats()

    fun generateDiagnosticReport() {
        AudioDiagnostics.reportStatistics()
    }

    private fun cleanup() {
        synchronized(encoderLock) {
            encoder?.release()
            encoder = null
        }
        packetQueue.clear()
    }

    override fun onMicrophoneData(data: ByteArray, offset: Int, length: Int) {
        if (!running.get() || !micActive.get()) return

        try {
            var encoded: ByteArray? = null

            synchronized(encoderLock) {
                if (encoder != null) {
                    encoded = encoder!!.encode(data, offset, length)
                }
            }

            if (encoded != null) {
                AudioDiagnostics.recordFrameEncoded()

                val queueSize = packetQueue.size

                if (queueSize >= MicrophoneConfig.MAX_QUEUE_SIZE) {
                    packetQueue.poll()
                    AudioDiagnostics.recordFrameDropped()
                    LimeLog.warning("音频队列已满，丢弃最旧数据包")
                }

                if (!packetQueue.offer(encoded)) {
                    AudioDiagnostics.recordFrameDropped()
                    LimeLog.warning("无法将编码数据加入队列，丢弃当前数据包")
                }
            }
        } catch (e: Exception) {
            AudioDiagnostics.recordEncodingError()
            LimeLog.warning("音频编码错误: ${e.message}")
        }
    }

    private fun senderThreadProc() {
        var lastSendTime = 0L
        var sendCount = 0L
        var totalLatency = 0L
        var maxLatency = 0L
        var lastStatsTime = System.currentTimeMillis()

        while (running.get()) {
            try {
                if (!hostRequested.get() || !micActive.get()) {
                    Thread.sleep(MicrophoneConfig.SENDER_THREAD_SLEEP_MS.toLong())
                    continue
                }

                if (!isConnectionActive()) {
                    LimeLog.info("检测到连接断开，停止麦克风发送")
                    break
                }

                val currentTime = System.currentTimeMillis()

                val queueSize = packetQueue.size
                var targetInterval = MicrophoneConfig.FRAME_INTERVAL_MS.toLong()

                if (queueSize > (MicrophoneConfig.MAX_QUEUE_SIZE * 0.7).toInt()) {
                    targetInterval = maxOf(5L, MicrophoneConfig.FRAME_INTERVAL_MS.toLong() / 2)
                }

                if (currentTime - lastSendTime < targetInterval) {
                    Thread.sleep(1)
                    continue
                }

                val encoded = packetQueue.poll()
                if (encoded == null) {
                    Thread.sleep(1)
                    continue
                }

                val sendLatency = currentTime - lastSendTime
                totalLatency += sendLatency
                maxLatency = maxOf(maxLatency, sendLatency)

                val result = MoonBridge.sendMicrophoneOpusData(encoded)
                if (result < 0) {
                    AudioDiagnostics.recordSendingError()
                    LimeLog.warning("麦克风数据发送失败: $result")
                    continue
                }

                lastSendTime = currentTime
                sendCount++

                AudioDiagnostics.recordFrameSent()

                if (sendCount % 12000 == 0L) {
                    val currentStatsTime = System.currentTimeMillis()
                    val statsInterval = currentStatsTime - lastStatsTime
                    val avgLatency = if (sendCount > 0) totalLatency.toDouble() / sendCount else 0.0

                    LimeLog.info(
                        String.format(
                            "麦克风发送统计: 包数=%d, 队列大小=%d, 平均延迟=%.1fms, 最大延迟=%dms, 统计间隔=%dms",
                            sendCount, queueSize, avgLatency, maxLatency, statsInterval
                        )
                    )

                    lastStatsTime = currentStatsTime
                    totalLatency = 0
                    maxLatency = 0
                }
            } catch (_: InterruptedException) {
                break
            }
        }

        LimeLog.info("麦克风发送线程已结束")
    }

    private fun isConnectionActive(): Boolean {
        return try {
            val hostAddress = conn.host
            !hostAddress.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
