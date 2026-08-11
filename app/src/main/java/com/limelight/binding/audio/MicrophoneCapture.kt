package com.limelight.binding.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.os.SystemClock

import com.limelight.LimeLog

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class MicrophoneCapture(
    private val dataCallback: MicrophoneDataCallback
) {
    private var captureThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private val running = AtomicBoolean(false)
    private var bufferSize: Int = MicrophoneConfig.CAPTURE_BUFFER_SIZE

    private var echoCanceler: AcousticEchoCanceler? = null
    private var gainControl: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    // 音量增益及其平衡处理器（软件音量处理）
    private val volumeProcessor = MicrophoneVolumeProcessor()

    private val frameBuffer = ByteArray(MicrophoneConfig.BYTES_PER_FRAME)
    private var frameBufferPos = 0

    private var lastFrameTime = 0L
    private var frameCount = 0L

    fun interface MicrophoneDataCallback {
        fun onMicrophoneData(data: ByteArray, offset: Int, length: Int)
    }

    fun start(): Boolean {
        if (running.get()) return true

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(MicrophoneConfig.SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                LimeLog.severe("不支持的音频参数")
                return false
            } else if (minBufferSize == AudioRecord.ERROR) {
                LimeLog.severe("无法获取最小缓冲区大小")
                return false
            }

            bufferSize = maxOf(minBufferSize * 2, MicrophoneConfig.CAPTURE_BUFFER_SIZE)

            val audioSource = if (MicrophoneConfig.useVoiceCommunication())
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            else
                MediaRecorder.AudioSource.MIC

            audioRecord = AudioRecord(audioSource,
                MicrophoneConfig.SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)

            if (audioRecord!!.state != AudioRecord.STATE_INITIALIZED) {
                LimeLog.severe("无法初始化AudioRecord，状态: ${audioRecord!!.state}")
                release()
                return false
            }

            // 从配置加载音量增益及其平衡参数
            volumeProcessor.configure(
                enabled = MicrophoneConfig.isVolumeProcessingEnabled(),
                gainEnabled = MicrophoneConfig.isVolumeGainEnabled(),
                gainDb = MicrophoneConfig.getVolumeGainDb(),
                balanceEnabled = MicrophoneConfig.isVolumeBalanceEnabled(),
                balanceTargetPercent = MicrophoneConfig.getVolumeBalanceTargetPercent(),
                voiceEnhancementEnabled = MicrophoneConfig.isVoiceEnhancementEnabled()
            )

            initializeAudioEffects()

            running.set(true)
            lastFrameTime = SystemClock.elapsedRealtimeNanos()

            captureThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

                val buffer = ByteBuffer.allocateDirect(bufferSize)
                val data = ByteArray(bufferSize)

                try {
                    audioRecord!!.startRecording()
                    LimeLog.info("麦克风捕获已启动，缓冲区大小: $bufferSize 字节")

                    while (running.get()) {
                        val bytesRead = audioRecord!!.read(buffer, bufferSize)
                        if (bytesRead > 0) {
                            buffer.get(data, 0, bytesRead)
                            buffer.clear()
                            processAudioData(data, 0, bytesRead)
                        } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                            LimeLog.warning("AudioRecord读取错误: ERROR_INVALID_OPERATION")
                            break
                        } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                            LimeLog.warning("AudioRecord读取错误: ERROR_BAD_VALUE")
                            break
                        }
                    }
                } catch (e: SecurityException) {
                    LimeLog.severe("麦克风权限不足: ${e.message}")
                } catch (e: Exception) {
                    LimeLog.severe("麦克风捕获出错: ${e.message}")
                } finally {
                    running.set(false)
                    if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                        try {
                            audioRecord!!.stop()
                        } catch (e: Exception) {
                            LimeLog.warning("停止AudioRecord时出错: ${e.message}")
                        }
                    }
                    release()
                }
            }, "MicrophoneCapture")

            captureThread!!.start()
            return true
        } catch (e: SecurityException) {
            LimeLog.severe("麦克风权限不足: ${e.message}")
            release()
            return false
        } catch (e: Exception) {
            LimeLog.severe("无法创建麦克风捕获: ${e.message}")
            release()
            return false
        }
    }

    private fun processAudioData(data: ByteArray, offset: Int, length: Int) {
        var remainingBytes = length
        var dataOffset = offset

        while (remainingBytes > 0) {
            val bytesNeeded = MicrophoneConfig.BYTES_PER_FRAME - frameBufferPos
            val bytesToCopy = minOf(remainingBytes, bytesNeeded)

            System.arraycopy(data, dataOffset, frameBuffer, frameBufferPos, bytesToCopy)
            frameBufferPos += bytesToCopy
            dataOffset += bytesToCopy
            remainingBytes -= bytesToCopy

            if (frameBufferPos >= MicrophoneConfig.BYTES_PER_FRAME) {
                val currentTime = SystemClock.elapsedRealtimeNanos()
                val timeDiff = currentTime - lastFrameTime

                if (MicrophoneConfig.ENABLE_AUDIO_SYNC &&
                    timeDiff < MicrophoneConfig.FRAME_INTERVAL_NS * 0.8) {
                    try {
                        Thread.sleep(1)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    continue
                }

                // 先应用音量处理，再交给编码器。
                volumeProcessor.processFrame(frameBuffer, 0, MicrophoneConfig.BYTES_PER_FRAME)
                dataCallback.onMicrophoneData(frameBuffer, 0, MicrophoneConfig.BYTES_PER_FRAME)
                frameBufferPos = 0
                lastFrameTime = currentTime
                frameCount++

                AudioDiagnostics.recordFrameCaptured()

                if (frameCount % 12000 == 0L) {
                    LimeLog.info("麦克风帧统计: $frameCount 帧, 平均间隔: ${timeDiff / 1000000}ms")
                }
            }
        }
    }

    fun stop() {
        running.set(false)

        // Stop AudioRecord first so a blocking read can return before its native resources
        // are released. Releasing while the capture thread is still inside read() can crash
        // on some Android audio implementations.
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) { }

        var interrupted = false
        val thread = captureThread
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(1000)
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }

        if (thread?.isAlive != true) {
            captureThread = null
            release()
        } else {
            LimeLog.warning("麦克风采集线程未及时退出，将在线程结束后释放AudioRecord")
        }

        if (interrupted) {
            Thread.currentThread().interrupt()
        }
    }

    private fun initializeAudioEffects() {
        val record = audioRecord ?: run {
            LimeLog.warning("AudioRecord为空，无法初始化音频效果器")
            return
        }

        val audioSessionId = record.audioSessionId
        LimeLog.info("开始初始化音频效果器，AudioSessionId: $audioSessionId")

        if (MicrophoneConfig.enableAcousticEchoCanceler()) {
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(audioSessionId)
                    if (echoCanceler != null) {
                        val result = echoCanceler!!.setEnabled(true)
                        if (result == 0) {
                            LimeLog.info("✓ 回声消除器(AEC)已启用")
                        } else {
                            LimeLog.warning("回声消除器启用失败，错误码: $result")
                        }
                    } else {
                        LimeLog.warning("无法创建回声消除器实例")
                    }
                } catch (e: Exception) {
                    LimeLog.warning("初始化回声消除器失败: ${e.message}")
                }
            } else {
                LimeLog.info("设备不支持硬件回声消除(AEC)")
            }
        } else {
            LimeLog.info("回声消除器已被配置禁用")
        }

        if (MicrophoneConfig.enableAutomaticGainControl()) {
            if (AutomaticGainControl.isAvailable()) {
                try {
                    gainControl = AutomaticGainControl.create(audioSessionId)
                    if (gainControl != null) {
                        val result = gainControl!!.setEnabled(true)
                        if (result == 0) {
                            LimeLog.info("✓ 自动增益控制(AGC)已启用")
                        } else {
                            LimeLog.warning("自动增益控制启用失败，错误码: $result")
                        }
                    } else {
                        LimeLog.warning("无法创建自动增益控制实例")
                    }
                } catch (e: Exception) {
                    LimeLog.warning("初始化自动增益控制失败: ${e.message}")
                }
            } else {
                LimeLog.info("设备不支持自动增益控制(AGC)")
            }
        } else {
            LimeLog.info("自动增益控制已被配置禁用")
        }

        if (MicrophoneConfig.enableNoiseSuppressor()) {
            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                    if (noiseSuppressor != null) {
                        val result = noiseSuppressor!!.setEnabled(true)
                        if (result == 0) {
                            LimeLog.info("✓ 噪声抑制器(NS)已启用")
                        } else {
                            LimeLog.warning("噪声抑制器启用失败，错误码: $result")
                        }
                    } else {
                        LimeLog.warning("无法创建噪声抑制器实例")
                    }
                } catch (e: Exception) {
                    LimeLog.warning("初始化噪声抑制器失败: ${e.message}")
                }
            } else {
                LimeLog.info("设备不支持噪声抑制(NS)")
            }
        } else {
            LimeLog.info("噪声抑制器已被配置禁用")
        }
    }

    private fun releaseAudioEffects() {
        echoCanceler?.let {
            try {
                it.setEnabled(false)
                it.release()
                LimeLog.info("回声消除器已释放")
            } catch (e: Exception) {
                LimeLog.warning("释放回声消除器失败: ${e.message}")
            }
        }
        echoCanceler = null

        gainControl?.let {
            try {
                it.setEnabled(false)
                it.release()
                LimeLog.info("自动增益控制已释放")
            } catch (e: Exception) {
                LimeLog.warning("释放自动增益控制失败: ${e.message}")
            }
        }
        gainControl = null

        noiseSuppressor?.let {
            try {
                it.setEnabled(false)
                it.release()
                LimeLog.info("噪声抑制器已释放")
            } catch (e: Exception) {
                LimeLog.warning("释放噪声抑制器失败: ${e.message}")
            }
        }
        noiseSuppressor = null
    }

    private fun release() {
        releaseAudioEffects()

        audioRecord?.let {
            if (it.state == AudioRecord.STATE_INITIALIZED) {
                it.stop()
            }
            it.release()
        }
        audioRecord = null
    }

    fun isAudioEffectsWorking(): Boolean {
        return (echoCanceler?.enabled == true) ||
                (gainControl?.enabled == true) ||
                (noiseSuppressor?.enabled == true)
    }

    fun getAudioSourceInfo(): String {
        return if (MicrophoneConfig.useVoiceCommunication()) {
            "VOICE_COMMUNICATION (系统级AEC/AGC/NS)"
        } else {
            "MIC (使用硬件音频效果器)"
        }
    }

    companion object {
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
