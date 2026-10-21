package dev.huecoder.md3agent.system.python;

import dev.huecoder.md3agent.system.python.IScriptBridge;

// Runs scripts in the separate :python process so a stuck or crashing script can be killed without touching the app.
interface IPythonService {
    int pid();

    // Blocks until the script ends or hits its own timeout; returns {"output", "failed", "timedOut"} as JSON.
    String run(String code, String workdir, long timeoutSeconds, IScriptBridge bridge);
}
