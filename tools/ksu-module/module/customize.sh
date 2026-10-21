SKIPUNZIP=0

if [ ! -f "$MODPATH/system/priv-app/MD3Agent/MD3Agent.apk" ]; then
  ui_print "! MD3Agent.apk is missing from the module, aborting"
  abort "! Rebuild the module with build.sh after assembleDebug/Release"
fi

ui_print "- Installing MD3 Agent as a privileged system app"
ui_print "- It replaces any user-installed copy on reboot (same signature)"

set_perm_recursive "$MODPATH/system/priv-app/MD3Agent" 0 0 0755 0644
