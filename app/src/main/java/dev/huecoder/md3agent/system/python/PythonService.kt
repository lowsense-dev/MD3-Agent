package dev.huecoder.md3agent.system.python

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File

/** Hosts the Python interpreter in the :python process (see the manifest); the app talks to it through [PythonRuntime]. */
class PythonService : Service() {
    private val binder = object : IPythonService.Stub() {
        override fun pid(): Int = Process.myPid()

        override fun run(code: String, workdir: String, timeoutSeconds: Long, bridge: IScriptBridge?): String = try {
            File(workdir).mkdirs()
            val script = interpreter().getModule(RUNNER_MODULE).callAttr(START_FUNCTION, code, workdir, bridge)
            val result = script.callAttr(WAIT_FUNCTION, timeoutSeconds).asList()
            reply(result[0].toString(), result[1].toBoolean(), result[2].toBoolean())
        } catch (e: Exception) {
            // Most exceptions do not cross Binder intact, so report them as the script's failure instead.
            reply("Python could not run the script: ${e.message ?: e.javaClass.simpleName}", failed = true, timedOut = false)
        }
    }

    private fun reply(output: String, failed: Boolean, timedOut: Boolean): String =
        JSONObject().put(OUTPUT, output).put(FAILED, failed).put(TIMED_OUT, timedOut).toString()

    override fun onBind(intent: Intent?): IBinder = binder

    @Synchronized
    private fun interpreter(): Python {
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        return Python.getInstance()
    }

    companion object {
        const val OUTPUT = "output"
        const val FAILED = "failed"
        const val TIMED_OUT = "timedOut"
        private const val RUNNER_MODULE = "md3_runner"
        private const val START_FUNCTION = "start"
        private const val WAIT_FUNCTION = "wait"
    }
}
