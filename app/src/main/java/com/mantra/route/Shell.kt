package com.mantra.route

import rikka.shizuku.Shizuku
import java.io.BufferedReader

/**
 * The privileged channel.
 *
 * Everything the app cannot do as itself, it does here as uid 2000 (shell) through Shizuku.
 * `Shizuku.newProcess` is not part of the published API surface, so it is reached by
 * reflection — that is the ordinary way this is done and it is stable across 13.x, but it is
 * also the single most likely thing to break on a Shizuku upgrade, which is why the failure
 * is reported by name rather than swallowed.
 */
data class ShellResult(
    val ok: Boolean,
    val exitCode: Int,
    val out: String,
    val err: String,
) {
    val text: String get() = (out + "\n" + err).trim()
}

object Shell {

    const val NOT_RUNNING = "Shizuku is not running"
    const val NO_PERMISSION = "Shizuku permission not granted"

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (t: Throwable) {
            // Nothing to do: the caller re-reads state and shows the fault.
        }
    }

    /**
     * Run one command as shell.
     *
     * TEST 3, "never answers": a shell that accepts the command and then goes quiet is not an
     * error any catch block will see, so every call carries a deadline. Without it a probe that
     * hangs looks identical to a probe that is thinking.
     */
    fun run(command: String, timeoutMs: Long = 5_000): ShellResult {
        if (!isRunning()) return ShellResult(false, -1, "", NOT_RUNNING)
        if (!hasPermission()) return ShellResult(false, -1, "", NO_PERMISSION)

        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null,
            ) as Process

            val out = StringBuilder()
            val err = StringBuilder()
            val outThread = drain(process.inputStream.bufferedReader(), out)
            val errThread = drain(process.errorStream.bufferedReader(), err)

            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                outThread.join(500)
                errThread.join(500)
                return ShellResult(false, -1, out.toString(), "timed out after ${timeoutMs}ms")
            }
            outThread.join(500)
            errThread.join(500)

            val code = process.exitValue()
            ShellResult(code == 0, code, out.toString().trim(), err.toString().trim())
        } catch (t: Throwable) {
            ShellResult(false, -1, "", (t.cause ?: t).toString())
        }
    }

    private fun drain(reader: BufferedReader, into: StringBuilder): Thread =
        Thread {
            try {
                reader.forEachLine { into.append(it).append('\n') }
            } catch (t: Throwable) {
                // Stream closed under us by destroyForcibly. Not a fault worth reporting.
            }
        }.also { it.isDaemon = true; it.start() }
}
