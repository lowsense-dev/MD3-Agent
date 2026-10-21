package dev.huecoder.md3agent.system.python;

// Phone control for scripts, served by the app process: begin() starts an operation, poll() returns its reply or null.
interface IScriptBridge {
    int begin(String operation, String payload);
    String poll(int handle);
}
