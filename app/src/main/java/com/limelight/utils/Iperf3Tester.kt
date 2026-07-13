package com.limelight.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.limelight.R

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern

class Iperf3Tester(
        private val context: Context,
        private val defaultServerAddress: String?
) {
    @Volatile
    private var iperfProcess: Process? = null

    private lateinit var serverInput: EditText
    private lateinit var portEditText: EditText
    private lateinit var udpBandwidthEditText: EditText
    private lateinit var rawArgsInput: EditText
    private lateinit var directionRadioGroup: RadioGroup
    private lateinit var downloadRadioButton: RadioButton
    private lateinit var durationSeekBar: SeekBar
    private lateinit var durationValueTextView: TextView
    private lateinit var outputView: TextView
    private lateinit var udpCheckBox: CheckBox
    private lateinit var udpBandwidthLayout: LinearLayout
    private lateinit var outputScrollView: ScrollView

    private val allOutputLines = ArrayList<String>()

    fun show() {
        val builder = AlertDialog.Builder(context, R.style.Iperf3DialogTheme)
        builder.setTitle("iPerf3 Network Test")

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_iperf3_test, null)
        builder.setView(dialogView)

        initializeUiElements(dialogView)
        setupUiListeners()

        if (!defaultServerAddress.isNullOrEmpty()) {
            serverInput.setText(defaultServerAddress)
        }

        builder.setPositiveButton(R.string.dialog_button_start, null)
        builder.setNegativeButton(R.string.dialog_button_stop, null)
        builder.setNeutralButton(R.string.dialog_button_close) { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.setOnDismissListener { killProcess() }
        dialog.setOnShowListener {
            AppDialogStyler.apply(dialog, context)
            val startButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val stopButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            stopButton.isEnabled = false

            startButton.setOnClickListener { executeTest(startButton, stopButton) }
            stopButton.setOnClickListener { killProcess() }
        }

        dialog.show()
    }

    private fun initializeUiElements(view: View) {
        serverInput = view.findViewById(R.id.edit_text_server_ip)
        directionRadioGroup = view.findViewById(R.id.radio_group_direction)
        downloadRadioButton = view.findViewById(R.id.radio_download)
        durationSeekBar = view.findViewById(R.id.seek_bar_duration)
        durationValueTextView = view.findViewById(R.id.text_view_duration_value)
        portEditText = view.findViewById(R.id.edit_text_port)
        udpCheckBox = view.findViewById(R.id.checkbox_udp)
        udpBandwidthLayout = view.findViewById(R.id.layout_udp_bandwidth)
        udpBandwidthEditText = view.findViewById(R.id.edit_text_udp_bandwidth)
        rawArgsInput = view.findViewById(R.id.edit_text_raw_args)
        outputScrollView = view.findViewById(R.id.output_scroll_view)
        outputView = view.findViewById(R.id.text_view_iperf3_output)
    }

    private fun setupUiListeners() {
        durationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                durationValueTextView.text = String.format("%ds", maxOf(1, progress))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        durationValueTextView.text = String.format("%ds", durationSeekBar.progress)

        udpCheckBox.setOnCheckedChangeListener { _, isChecked ->
            udpBandwidthLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun executeTest(startButton: Button, stopButton: Button) {
        if (!validateInputs()) return

        killProcess()
        allOutputLines.clear()
        outputView.text = ""

        setUiState(false, startButton, stopButton)

        Thread {
            try {
                val iperfPath = getIperfPath()
                val command = buildCommand(iperfPath)

                appendOutput(context.getString(R.string.iperf_output_executing, TextUtils.join(" ", command)) + "\n\n")

                val pb = ProcessBuilder(command)
                pb.redirectErrorStream(true)
                iperfProcess = pb.start()

                BufferedReader(InputStreamReader(iperfProcess!!.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        allOutputLines.add(line!!)
                        appendOutput(translateIperfOutput(line!!) + "\n")
                    }
                }

                val exitCode = iperfProcess!!.waitFor()
                appendOutput("\n${context.getString(R.string.iperf_output_complete_exit_code, exitCode)}\n")
                parseAndDisplaySummary()

            } catch (e: IOException) {
                e.printStackTrace()
                appendOutput("\n${context.getString(R.string.iperf_error_with_hint, e.message)}\n")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                appendOutput("\n${context.getString(R.string.iperf_interrupted)}\n")
            } finally {
                iperfProcess = null
                setUiState(true, startButton, stopButton)
            }
        }.start()
    }

    private fun validateInputs(): Boolean {
        val serverIp = serverInput.text.toString().trim()
        if (serverIp.isEmpty() || serverIp.matches(Regex(".*[;&|`<>\\$\\(\\)].*"))) {
            Toast.makeText(context, R.string.iperf_invalid_server, Toast.LENGTH_SHORT).show()
            return false
        }
        val portStr = portEditText.text.toString().trim()
        if (!portStr.matches(Regex("\\d+")) || portStr.toInt() > 65535) {
            Toast.makeText(context, R.string.iperf_invalid_port, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    @Throws(IOException::class)
    private fun getIperfPath(): String {
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        val iperfPath = "$nativeLibraryDir/libiperf3.so"
        if (!File(iperfPath).exists()) {
            throw IOException(context.getString(R.string.iperf_binary_missing))
        }
        return iperfPath
    }

    private fun buildCommand(iperfPath: String): ArrayList<String> {
        val command = ArrayList<String>()
        command.add(iperfPath)
        command.add("-c")
        command.add(serverInput.text.toString().trim())
        command.add("-p")
        command.add(portEditText.text.toString().trim())

        if (downloadRadioButton.isChecked) command.add("-R")

        command.add("-t")
        command.add(durationSeekBar.progress.toString())

        if (udpCheckBox.isChecked) {
            command.add("-u")
            val bandwidth = udpBandwidthEditText.text.toString().trim()
            if (bandwidth.isNotEmpty()) {
                command.add("-b")
                command.add(bandwidth)
            } else {
                appendOutput("${context.getString(R.string.iperf_udp_bandwidth_default)}\n")
                command.add("-b")
                command.add("1M")
            }
        }

        val rawArgs = rawArgsInput.text.toString().trim()
        if (rawArgs.isNotEmpty()) {
            val managedArgs = setOf("-p", "--port", "-r", "--reverse", "-t", "--time", "-u", "--udp", "-b", "--bandwidth")
            val rawArgsList = rawArgs.split("\\s+".toRegex())
            val filteredArgs = ArrayList<String>()
            val ignoredArgs = ArrayList<String>()

            var i = 0
            while (i < rawArgsList.size) {
                val currentArg = rawArgsList[i]
                if (managedArgs.contains(currentArg.lowercase())) {
                    ignoredArgs.add(currentArg)
                    if (i + 1 < rawArgsList.size && !rawArgsList[i + 1].startsWith("-")) {
                        ignoredArgs.add(rawArgsList[i + 1])
                        i++
                    }
                } else if (ALLOWED_IPERF_ARGS.contains(currentArg.lowercase())) {
                    filteredArgs.add(currentArg)
                } else {
                    ignoredArgs.add(currentArg)
                }
                i++
            }

            if (filteredArgs.isNotEmpty()) command.addAll(filteredArgs)
            if (ignoredArgs.isNotEmpty()) {
                appendOutput("${context.getString(R.string.iperf_ignored_args, TextUtils.join(" ", ignoredArgs))}\n")
            }
        }
        return command
    }

    private fun parseAndDisplaySummary() {
        val direction = context.getString(
                if (downloadRadioButton.isChecked) R.string.iperf_direction_download else R.string.iperf_direction_upload
        )

        val tcpPattern = Pattern.compile(
                "\\[\\s*\\d+\\s*\\]\\s+[\\d.-]+\\s+sec\\s+.*\\s+([\\d.]+)\\s+(Mbits/sec|Gbits/sec|Kbits/sec|bits/sec)"
        )

        val udpPattern = Pattern.compile(
                "\\[\\s*\\d+\\s*\\]\\s+[\\d.-]+\\s+sec\\s+.*\\s+" +
                        "([\\d.]+)\\s+(Mbits/sec|Gbits/sec|Kbits/sec|bits/sec)\\s+" +
                        "([\\d.]+)\\s+(ms)\\s+" +
                        "(\\d+)\\s*/\\s*(\\d+)\\s+\\(([\\d.]+)%\\)"
        )

        var summaryResult = ""
        var foundSummary = false

        for (i in allOutputLines.indices.reversed()) {
            if (foundSummary) break

            val line = allOutputLines[i]
            if (!line.contains(" sec ") || !line.contains("bits/sec")) continue

            if (udpCheckBox.isChecked) {
                val udpMatcher = udpPattern.matcher(line)
                if (udpMatcher.find()) {
                    summaryResult =
                            "\n========================================\n" +
                            "             ${context.getString(R.string.iperf_summary_title)}\n" +
                            "----------------------------------------\n" +
                            " ${context.getString(R.string.iperf_summary_avg_bandwidth, direction, udpMatcher.group(1), udpMatcher.group(2))}\n" +
                            " ${context.getString(R.string.iperf_summary_jitter, udpMatcher.group(3))}\n" +
                            " ${context.getString(R.string.iperf_summary_packet_loss, udpMatcher.group(5), udpMatcher.group(6), udpMatcher.group(7))}\n" +
                            "========================================\n"
                    foundSummary = true
                }
            } else {
                val tcpMatcher = tcpPattern.matcher(line)
                if (tcpMatcher.find()) {
                    summaryResult =
                            "\n========================================\n" +
                            "             ${context.getString(R.string.iperf_summary_title)}\n" +
                            "----------------------------------------\n" +
                            " ${context.getString(R.string.iperf_summary_avg_bandwidth, direction, tcpMatcher.group(1), tcpMatcher.group(2))}\n" +
                            "========================================\n"
                    foundSummary = true
                }
            }
        }

        if (summaryResult.isNotEmpty()) {
            appendOutput(summaryResult)
        } else {
            appendOutput("\n${context.getString(R.string.iperf_summary_parse_failed)}\n")
        }
    }

    private fun translateIperfOutput(originalLine: String): String {
        return when {
            originalLine.contains("Connecting to host") ->
                originalLine.replace("Connecting to host", context.getString(R.string.iperf_output_connecting))
            originalLine.contains("local") && originalLine.contains("port") ->
                originalLine.replace("local", context.getString(R.string.iperf_output_local))
                        .replace("port", context.getString(R.string.iperf_output_port))
            originalLine.contains("Interval") ->
                originalLine.replace("Interval", context.getString(R.string.iperf_output_interval))
                        .replace("Transfer", context.getString(R.string.iperf_output_transfer))
                        .replace("Bitrate", context.getString(R.string.iperf_output_bitrate))
                        .replace("Bandwidth", context.getString(R.string.iperf_output_bandwidth))
                        .replace("Retr", context.getString(R.string.iperf_output_retr))
                        .replace("Jitter", context.getString(R.string.iperf_output_jitter))
                        .replace("Lost/Total", context.getString(R.string.iperf_output_lost_total))
                        .replace("Datagrams", context.getString(R.string.iperf_output_datagrams))
            originalLine.contains("sender") -> originalLine.replace("sender", context.getString(R.string.iperf_output_sender))
            originalLine.contains("receiver") -> originalLine.replace("receiver", context.getString(R.string.iperf_output_receiver))
            originalLine.contains("iperf Done.") -> context.getString(R.string.iperf_output_done)
            originalLine.contains("unable to connect to server") ->
                context.getString(R.string.iperf_output_connect_failed)
            originalLine.contains("interrupt - the client has terminated") ->
                context.getString(R.string.iperf_output_client_interrupted)
            else -> originalLine
        }
    }

    private fun killProcess() {
        iperfProcess?.destroy()
        iperfProcess = null
    }

    private fun setUiState(isEnabled: Boolean, startButton: Button, stopButton: Button) {
        runOnUiThread {
            startButton.isEnabled = isEnabled
            stopButton.isEnabled = !isEnabled
            serverInput.isEnabled = isEnabled
            directionRadioGroup.isEnabled = isEnabled
            for (i in 0 until directionRadioGroup.childCount) {
                directionRadioGroup.getChildAt(i).isEnabled = isEnabled
            }
            durationSeekBar.isEnabled = isEnabled
            portEditText.isEnabled = isEnabled
            udpCheckBox.isEnabled = isEnabled
            udpBandwidthEditText.isEnabled = isEnabled
            rawArgsInput.isEnabled = isEnabled
        }
    }

    private fun appendOutput(text: String) {
        runOnUiThread {
            outputView.append(text)
            outputScrollView.post { outputScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun runOnUiThread(action: Runnable) {
        if (context is Activity) {
            context.runOnUiThread(action)
        }
    }

    companion object {
        private val ALLOWED_IPERF_ARGS = setOf(
                "-f", "--format",
                "-i", "--interval",
                "-j", "--json",
                "-p", "--parallel",
                "-w", "--window",
                "-m", "--set-mss",
                "-n", "--no-delay",
                "-v", "--version",
                "-l", "--len",
                "-z", "--zerocopy",
                "-o", "--omit",
                "-t", "--title",
                "-c", "--congestion",
                "-k", "--blockcount"
        )
    }
}
