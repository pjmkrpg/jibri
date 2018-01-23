package org.jitsi.jibri

import org.jitsi.jibri.capture.Capturer
import org.jitsi.jibri.capture.CapturerParams
import org.jitsi.jibri.capture.ffmpeg.FfmpegCapturer
import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.jitsi.jibri.sink.FileSink
import org.jitsi.jibri.sink.Sink
import org.jitsi.jibri.util.ProcessMonitor
import org.jitsi.jibri.util.error
import java.io.File
import java.io.IOException
import java.util.logging.Logger

data class RecordingOptions(
        // The directory in which recordings should be created
        val recordingDirectory: File,
        val baseUrl: String,
        val callName: String,
        val finalizeScriptPath: String
)

class JibriFileRecording(val recordingOptions: RecordingOptions) : JibriService {
    private val logger = Logger.getLogger(this::class.simpleName)
    private val sink: Sink
    private val jibriSelenium: JibriSelenium
    private val capturer: Capturer
    private val finalizeScriptPath: String = recordingOptions.finalizeScriptPath
    private var capturerMonitor: ProcessMonitor? = null
    init {
        sink = FileSink(recordingsDirectory = recordingOptions.recordingDirectory,
                callName = recordingOptions.callName)
        jibriSelenium = JibriSelenium(JibriSeleniumOptions(baseUrl = recordingOptions.baseUrl))
        capturer = FfmpegCapturer()
    }

    @Synchronized
    override fun start() {
        jibriSelenium.joinCall(recordingOptions.callName)
        val capturerParams = CapturerParams()
        capturer.start(capturerParams, sink)
        //TODO: should we monitor selenium as well?
        capturerMonitor = ProcessMonitor(processToMonitor = capturer) { exitCode ->
            logger.error("Capturer process is no longer running, exited " +
                    "with code $exitCode")
            capturer.start(capturerParams, sink)
        }
    }

    @Synchronized
    override fun stop() {
        capturerMonitor?.stopMonitoring()
        logger.info("Stopping capturer")
        capturer.stop()
        logger.info("Quitting selenium")
        jibriSelenium.leaveCallAndQuitBrowser()
        logger.info("Finalizing the recording")
        try {
            val finalizeProc = Runtime.getRuntime().exec(finalizeScriptPath)
            finalizeProc.waitFor()
            logger.info("Recording finalize script finished with exit " +
                    "value: ${finalizeProc.exitValue()}")
        } catch (e: IOException) {
            logger.error("Failed to run finalize script: $e")
        }
    }
}