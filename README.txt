BubbleVPN - Pure Connected Stealth Edition
==========================================

1. Display on Mobile:
- Strictly shows "BubbleVPN", the circular Power button, and "Connected" (or "Disconnected").
- Absolutely NO IP address (ftp://192.168.8.101:2222), no port, and no file browser prompts on screen.

2. Background Execution & Zero Notifications:
- Absolutely NO notification at the top of the mobile device.
- Runs silently in the background on port 2222.
- android:stopWithTask="false" and onTaskRemoved() handler ensure that swiping the app away (swiping left in recent apps) DOES NOT stop the background server.
- The server stays running until you reopen BubbleVPN and tap the circle to disconnect.
