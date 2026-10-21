package dev.huecoder.md3agent.system.python

/** What a script can ask of the app (phone control), see md3_runner/phone.py; [abort] ends pending operations. */
interface ScriptBridge {
    fun begin(operation: String, payload: String): Int

    fun poll(handle: Int): String?

    fun abort()
}
