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
    fun run(command: String, timeoutMs: Long = 8_000): ShellResult {
        if (!isRunning()) return ShellResult(false, -1, "", NOT_RUNNING)
        if (!hasPermission()) return ShellResult(false, -1, "", NO_PERMISSION)

        var process: Process? = null
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            process = method.invoke(
                null,
                arrayOf("sh", "-c", ExitMarker.wrap(command)),
                null,
                null,
            ) as Process

            val out = StringBuilder()
            val err = StringBuilder()
            val outThread = drain(process.inputStream.bufferedReader(), out)
            val errThread = drain(process.errorStream.bufferedReader(), err)

            // The deadline is on the STREAM, not on the process.
            //
            // Nothing here calls waitFor(timeout, unit) or exitValue(). Both are binder calls
            // into the Shizuku server, and both are what turned every probe on the real phone
            // into a fault. EOF on stdout is the completion signal instead: it is a property of
            // the pipe, observed locally, and cannot be flattened by binder.
            outThread.join(timeoutMs)
            val timedOut = outThread.isAlive
            errThread.join(500)

            val parsed = ExitMarker.parse(out.toString())

            when {
                timedOut -> ShellResult(
                    false, -1, parsed.output,
                    "timed out after ${timeoutMs}ms",
                )
                // No marker means the shell died before it could print its own status — which
                // is a different failure from a command that ran and returned non-zero, and
                // must not be reported as one.
                !parsed.found -> ShellResult(
                    false, -1, parsed.output,
                    err.toString().trim().ifEmpty { "shell produced no exit marker" },
                )
                else -> ShellResult(
                    parsed.code == 0, parsed.code, parsed.output, err.toString().trim(),
                )
            }
        } catch (t: Throwable) {
            // Named, not swallowed: the class matters as much as the message. The v2 failure
            // was legible only because the class name came through.
            val cause = t.cause ?: t
            ShellResult(false, -1, "", cause.javaClass.name + ": " + (cause.message ?: "no message"))
        } finally {
            // destroy(), never destroyForcibly() — the latter routes through exitValue().
            runCatching { process?.destroy() }
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
