package io.appetizer.chimpchatrest;

import com.android.chimpchat.ChimpChat;
import com.android.chimpchat.adb.AdbChimpDevice;
import com.android.chimpchat.core.IChimpDevice;
import com.android.chimpchat.core.IChimpImage;
import com.android.chimpchat.core.TouchPressType;
import com.android.ddmlib.*;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TheMain extends NanoHTTPD {
    private final ChimpChat cc;
    private IChimpDevice device;

    private TheMain(String hostname, int port) {
        super(hostname, port);
        cc = ChimpChat.getInstance();
    }

    @Override public Response serve(IHTTPSession session) {
        Map<String, List<String>> qs = new HashMap<>();
        String postBody = null;
        if (Method.GET.equals(session.getMethod())) {
            qs = decodeParameters(session.getQueryParameterString());
        }
        if (Method.POST.equals(session.getMethod())) {
            InputStream is = session.getInputStream();
            int size;
            if (session.getHeaders().containsKey("content-length")) {
                size = Integer.parseInt(session.getHeaders().get("content-length"));
            } else {
                size = 0;
            }
            byte buf[] = new byte[size];
            try {
                is.read(buf);
            } catch (IOException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        "SERVER INTERNAL ERROR: IOException: " + e.getMessage());
            }
            postBody = new String(buf);
        }

        final String[] components = session.getUri().split("/");
        if (components.length <= 1) {
            // the root
            return newFixedLengthResponse("chimpchat-rest: control an Android device via REST APIs");
        }
        switch (components[1]) {
            // connect to device
            case "init": return init(qs);
            case "dispose": return dispose(qs);
            // reboot and wake
            case "reboot": return reboot(qs);
            case "wake": return wake(qs);
            // about apk installation and removal
            case "install": return install(qs);
            case "remove": return remove(qs);
            // pull/push file
            case "pull": return pull(qs);
            case "push": return push(qs);
            // bootloader variables and system properties
            case "getVar": return getVar(qs);
            case "getProp": return getProp(qs);
            // keyboard input
            case "type": return type(qs);
            // case "press": return press(qs);
            // screen shot
            case "takeSnapshot": return takeSnapshot(qs);
            // swiss knife
            case "shell": return shell(postBody); // shell uses POST
            // intent story
            // case "broadcastIntent": return broadcastIntent(qs);
            // case "startActivity": return startActivity(qs);
            // screen magic
            case "touch": return touch(qs);
            case "drag": return drag(qs);
            case "favicon.ico": return newFixedLengthResponse("");
            default: return get404();
        }
    }

    /**
     * /init?timeout=<timeout in milliseconds>?serialno=<device serial no string>
     */
    private Response init(Map<String, List<String>> qs) {
        if (device != null) {
            return newFixedLengthResponse("already connected");
        }
        final long timeout = getStringOrDefault(qs, "timeout", 10);
        final String serialno = getStringOrDefault(qs, "serialno", ".*"); // it is actually a regex
        try {
            device = cc.waitForConnection(timeout, serialno);
        } catch (Exception e) {
            System.err.println("Chimpchat exception: " + e.toString());
            e.printStackTrace();
        }
        return newFixedLengthResponse("connected");
    }

    /**
     * /dispose
     */
    private Response dispose(Map<String, List<String>> qs) {
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            device.dispose();
            device = null;
            return newFixedLengthResponse("disposed");
        }
    }

    /**
     * /reboot?into=<reboot mode>
     */
    private Response reboot(Map<String, List<String>> qs) {
        final String into = getStringOrDefault(qs, "into", null);
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            device.reboot(into);
            return newFixedLengthResponse("rebooted");
        }
    }

    /**
     * /wake
     */
    private Response wake(Map<String, List<String>> qs) {
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            device.wake();
            return newFixedLengthResponse("morning");
        }
    }

    /**
     * /install?apk=<path to the apk file>
     */
    private Response install(Map<String, List<String>> qs) {
        final String apk = getStringOrDefault(qs, "apk", null);
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            device.installPackage(apk);
            return newFixedLengthResponse("installed");
        }
    }


    /**
     * /remove?pkg=<the package name to be removed>
     */
    private Response remove(Map<String, List<String>> qs) {
        final String pkg = getStringOrDefault(qs, "pkg", null);
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            return newFixedLengthResponse(Boolean.toString(device.removePackage(pkg)));
        }
    }


    /**
     * /remove?pkg=<the package name to be removed>
     */
    private Response pull(Map<String, List<String>> qs) {
        final String src = getStringOrDefault(qs, "src", null);
        final String dst = getStringOrDefault(qs, "dst", null);
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            try {
                getDDMDevice().pullFile(src, dst);
            } catch (IOException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        "SERVER INTERNAL ERROR: IOException: " + e.getMessage());
            } catch (AdbCommandRejectedException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        "SERVER INTERNAL ERROR: AdbCommandRejectedException: " + e.getMessage());
            } catch (TimeoutException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        "SERVER INTERNAL ERROR: TimeoutException: " + e.getMessage());
            } catch (SyncException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        "SERVER INTERNAL ERROR: SyncException: " + e.getMessage());
            }
            return newFixedLengthResponse("done");
        }
    }

    /**
     * /remove?pkg=<the package name to be removed>
     */
    private Response push(Map<String, List<String>> qs) {
        final String src = getStringOrDefault(qs, "src", null);
        final String dst = getStringOrDefault(qs, "dst", null);
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            try {
                getDDMDevice().pushFile(src, dst);
            } catch (IOException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        "SERVER INTERNAL ERROR: IOException: " + e.getMessage());
            } catch (AdbCommandRejectedException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        "SERVER INTERNAL ERROR: AdbCommandRejectedException: " + e.getMessage());
            } catch (TimeoutException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        "SERVER INTERNAL ERROR: TimeoutException: " + e.getMessage());
            } catch (SyncException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        "SERVER INTERNAL ERROR: SyncException: " + e.getMessage());
            }
            return newFixedLengthResponse("done");
        }
    }

    /**
     * /getVar?var=<property name>
     *  Get the bootloader variable
     */
    private Response getVar(Map<String, List<String>> qs) {
        final String var = getStringOrDefault(qs, "var", null);
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            return newFixedLengthResponse(device.getProperty(var));
        }
    }

    /**
     * /getProp?prop=<property name>
     * Get the system property
     */
    private Response getProp(Map<String, List<String>> qs) {
        final String prop = getStringOrDefault(qs, "prop", null);
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            return newFixedLengthResponse(device.getSystemProperty(prop));
        }
    }

    /**
     * /type?s=<the string to be typed>
     */
    private Response type(Map<String, List<String>> qs) {
        final String s = getStringOrDefault(qs, "s", null);
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            device.type(s);
            return newFixedLengthResponse("typed");
        }
    }

    /**
     * /touch?x=<x axis pixel number>&y=<y axis pixel number >&t=<TYPE>
     * Type could be "downAndUp" or "down" or "up" or "move"
     */
    private Response press(Map<String, List<String>> qs) {
        final String keyname = getStringOrDefault(qs, "keyname", "KEYCODE_HOME");
        final String t = getStringOrDefault(qs, "t", "downAndUp");
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            device.press(keyname, TouchPressType.fromIdentifier(t));
            return newFixedLengthResponse("sent");
        }
    }

    /**
     * /takeSnapshot?path=<path to save the screenshot>&format=<the format of the image savedfile>
     */
    private Response takeSnapshot(Map<String, List<String>> qs) {
        final String path = getStringOrDefault(qs, "path", null);
        final String format = getStringOrDefault(qs, "format", null);
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            IChimpImage img = device.takeSnapshot();
            img.writeToFile(path, format);
            return newFixedLengthResponse("taken");
        }
    }

    /**
     * /touch?x=<x axis pixel number>&y=<y axis pixel number >&t=<TYPE>
     * Type could be "downAndUp" or "down" or "up" or "move"
     */
    private Response touch(Map<String, List<String>> qs) {
        final int x = Integer.parseInt(getStringOrDefault(qs, "x", "0"));
        final int y = Integer.parseInt(getStringOrDefault(qs, "y", "0"));
        final String t = getStringOrDefault(qs, "t", "downAndUp");
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            device.touch(x, y, TouchPressType.fromIdentifier(t));
            return newFixedLengthResponse("sent");
        }
    }

    /**
     * /drag?startx=xx&starty=xx&endx=xx&endy=xx&steps=xx&ms=xx
     */
    private Response drag(Map<String, List<String>> qs) {
        final int startx = Integer.parseInt(getStringOrDefault(qs, "startx", "0"));
        final int starty = Integer.parseInt(getStringOrDefault(qs, "starty", "0"));
        final int endx = Integer.parseInt(getStringOrDefault(qs, "endx", "200"));
        final int endy = Integer.parseInt(getStringOrDefault(qs, "endy", "200"));
        final int steps = Integer.parseInt(getStringOrDefault(qs, "steps", "10"));
        final int ms = Integer.parseInt(getStringOrDefault(qs, "ms", "100"));
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else {
            try {
                device.drag(startx, starty, endx, endy, steps, ms);
            } catch (Exception e) {
                System.err.println("Chimpchat drag failed: " + e.toString());
                e.printStackTrace();
            }
            return newFixedLengthResponse("sent");
        }
    }

    /**
     * /shell
     * Command transmitted as the POST body
     */
    private Response shell(String cmd) {
        if (device == null) {
            return getDeviceNotReadyResponse();
        } else if (cmd == null) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "use POST");
        } else {
            return newFixedLengthResponse(device.shell(cmd));
        }
    }

    private Response getDeviceNotReadyResponse() {
        return newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "device not connected.");
    }

    private Response get404() {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "not supported.");
    }

    private static String getStringOrDefault(Map<String, List<String>> qs, String k, String default_value) {
        return (qs.containsKey(k) ? qs.get(k).get(0) : default_value);
    }

    private static long getStringOrDefault(Map<String, List<String>> qs, String k, long default_value) {
        return (qs.containsKey(k) ? Long.parseLong(qs.get(k).get(0)): default_value);
    }

    /**
     * Get the bare DDM device object from the ChimpDevice object, by reflection.
     * @return the DDM device or null if anything wrong
     */
    private IDevice getDDMDevice() {
        if (device == null) {
            return null;
        }
        if (!(device instanceof AdbChimpDevice)) {
            System.err.println("ChimpDevice is not AdbChimpDevice, why?");
            return null;
        }
        AdbChimpDevice chimp_device = (AdbChimpDevice) device;
        try {
            final Field f = AdbChimpDevice.class.getDeclaredField("device");
            f.setAccessible(true);
            return (IDevice) f.get(chimp_device);
        } catch (NoSuchFieldException e) {
            System.err.println("cannot get ddm device from the ChimpDevice");
            return null;
        } catch (IllegalAccessException e) {
            System.err.println("failed to get the ddm device from a ChimpDevice");
            return null;
        }
    }

    public static void main(String[] args) {
        final String hostname = (args.length >= 1 ? args[0] : "0.0.0.0");
        final int port = (args.length >= 2 ? Integer.parseInt(args[1]): 8080);
        final TheMain app = new TheMain(hostname, port);
        try {
            app.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            System.out.println("server starts at " + hostname + ":" + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
