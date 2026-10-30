package com.arthenica.ffmpegkit

import java.util.concurrent.ExecutorService

enum class Level {
    AV_LOG_QUIET,
    AV_LOG_PANIC,
    AV_LOG_FATAL,
    AV_LOG_ERROR,
    AV_LOG_WARNING,
    AV_LOG_INFO,
    AV_LOG_VERBOSE,
    AV_LOG_DEBUG,
    AV_LOG_TRACE
}

data class ReturnCode(val value: Int) {
    val isValueSuccess: Boolean get() = value == 0
}

data class Log(val level: Level, val message: String)

data class Statistics(
    val videoFrameNumber: Int = 0,
    val videoFps: Float = 0f,
    val time: Int = 0,
    val bitrate: Double = 0.0,
    val speed: Double = 1.0
)

data class FFmpegSession(
    val returnCode: ReturnCode = ReturnCode(0),
    val output: String? = "FFmpeg stub executed successfully."
)

fun interface FFmpegSessionCompleteCallback {
    fun apply(session: FFmpegSession)
}

fun interface LogCallback {
    fun apply(log: Log)
}

fun interface StatisticsCallback {
    fun apply(statistics: Statistics)
}

object FFmpegKit {
    @JvmStatic
    fun listSessions(): List<FFmpegSession> = emptyList()

    @JvmStatic
    fun executeAsync(
        command: String,
        completeCallback: FFmpegSessionCompleteCallback,
        logCallback: LogCallback,
        statisticsCallback: StatisticsCallback,
        executor: ExecutorService
    ): FFmpegSession {
        val session = FFmpegSession()
        executor.execute {
            logCallback.apply(Log(Level.AV_LOG_INFO, "Stub executing: $command"))
            statisticsCallback.apply(Statistics())
            completeCallback.apply(session)
        }
        return session
    }
}
