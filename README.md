# chimpchat-rest
Exposing the chimpchat APIs as REST APIs, to control an Android box.

[chimpchat](https://android.googlesource.com/platform/tools/swt/+/master/chimpchat) is
an unexposed Android Java APIs that can control an Android device. It is the undelying APIs
used in MonkeyRunner, a crappy Jython 2.5 age obsolete testing framework. Chimpchat is provided
in the Android SDK at `<sdk>/tools/lib/chimpchat.jar`. It depends on several other jars in the same
folder.

Yet Chimpchat itself is way better than the unnecessary MonkeyRunner, providing the
following features:
* simple (x,y)-based screen touch
* install APK
* emulate key press
* take screen shot
* wake up the device
* perform shell command on the device
* reboot
* broadcast intents
* drag

Though the above are mostly covered by MonkeyRunner's successor UIAutomator, the following
 fancy feature seems to be only available in ChimpChat and MonkeyRunner:
* customizable trail touch (with DOWN, MOVE and then UP)

