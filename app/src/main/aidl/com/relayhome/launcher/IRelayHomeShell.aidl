package com.relayhome.launcher;

interface IRelayHomeShell {
    String setRelayHome(String stockPackageName, String stockActivityName, boolean disableStockLauncher);
    String restoreStockLauncher(String packageName, String activityName);
}
