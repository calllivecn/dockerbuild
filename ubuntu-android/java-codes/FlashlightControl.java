import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;

import java.lang.reflect.Method; // 导入反射相关的类

public class FlashlightControl {

    private static final String TAG = "FlashlightControl";

    public static void main(String[] args) {
        // 调试输出接收到的所有参数
        Log.d(TAG, "Received " + args.length + " arguments:");
        for (int i = 0; i < args.length; i++) {
            Log.d(TAG, "arg[" + i + "]: " + args[i]);
        }

        // 根据 app_process 的典型调用方式，实际命令参数应该是 args[0]
        // 如果 args.length != 1，说明命令行参数不对
        if (args.length != 1) {
            System.err.println("Usage: CLASSPATH=/path/to/your.jar app_process /system/bin/ FlashlightControl <on|off>");
            Log.e(TAG, "Incorrect number of arguments: " + args.length);
            System.exit(1);
            return; // 不可达，但为了清晰保留
        }

        String command = args[0];
        boolean enable;

        if ("on".equalsIgnoreCase(command)) {
            enable = true;
        } else if ("off".equalsIgnoreCase(command)) {
            enable = false;
        } else {
            System.err.println("Invalid command: " + command);
            System.err.println("Usage: CLASSPATH=/path/to/your.jar app_process /system/bin/ FlashlightControl <on|off>");
            Log.e(TAG, "Invalid command: " + command);
            System.exit(1);
            return; // 不可达
        }

        Context context = null;
        try {
            // !!! 修改点 1: 使用反射调用 ActivityThread.currentApplication() !!!
            // 这不会在编译时检查 ActivityThread，而是在运行时查找和调用
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
            context = (Context) currentApplicationMethod.invoke(null);

            if (context == null) {
                System.err.println("Failed to get application context via reflection.");
                Log.e(TAG, "Failed to get application context via reflection.");
                System.exit(1);
                return;
            }
            System.out.println("Successfully obtained application context.");
            Log.i(TAG, "Successfully obtained application context: " + context);

        } catch (Exception e) {
            // 捕获反射可能抛出的各种异常 (ClassNotFoundException, NoSuchMethodException,
            // IllegalAccessException, InvocationTargetException)
            System.err.println("Error getting context via reflection: " + e.getMessage());
            e.printStackTrace();
            Log.e(TAG, "Error getting context via reflection", e);
            System.exit(1);
            return;
        }

        CameraManager cameraManager = null;
        try {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) {
                 System.err.println("Failed to get CameraManager service.");
                 Log.e(TAG, "Failed to get CameraManager service.");
                 System.exit(1);
                 return;
            }
            System.out.println("Successfully obtained CameraManager service.");
            Log.i(TAG, "Successfully obtained CameraManager service.");
        } catch (Exception e) {
             // 捕获获取服务过程中可能出现的异常
             System.err.println("Error getting CameraManager service: " + e.getMessage());
             e.printStackTrace();
             Log.e(TAG, "Error getting CameraManager service", e);
             System.exit(1);
             return;
        }


        String cameraId = null;
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds.length == 0) {
                 System.err.println("No cameras found on this device.");
                 Log.e(TAG, "No cameras found on this device.");
                 System.exit(1);
                 return;
            }
            System.out.println("Found " + cameraIds.length + " cameras.");
            Log.d(TAG, "Found " + cameraIds.length + " cameras.");

            for (String id : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                // Check if the camera has a flash unit
                Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Log.d(TAG, "Checking camera " + id + ", flash available: " + flashAvailable);
                if (flashAvailable != null && flashAvailable) {
                    cameraId = id;
                    System.out.println("Found camera with flash unit: " + cameraId);
                    Log.i(TAG, "Found camera with flash unit: " + cameraId);
                    break; // Found a camera with a flash, use this one
                }
            }
        } catch (CameraAccessException e) {
            System.err.println("Error accessing camera list: " + e.getMessage());
            e.printStackTrace();
            Log.e(TAG, "Error accessing camera list", e);
            System.exit(1);
            return;
        }

        if (cameraId == null) {
            System.err.println("No camera with a flash unit found on this device.");
            Log.e(TAG, "No camera with a flash unit found on this device.");
            System.exit(1);
            return;
        }

        try {
            // Set the torch mode
            System.out.println("Setting torch mode for camera " + cameraId + " to " + (enable ? "ON" : "OFF") + "...");
            Log.i(TAG, "Setting torch mode for camera " + cameraId + " to " + (enable ? "ON" : "OFF"));
            // !!! 修改点 2: 移除对 SecurityException 的特殊处理 !!!
            // 在 Root 环境下，通常不会因为权限问题在此失败
            cameraManager.setTorchMode(cameraId, enable);
            System.out.println("Flashlight turned " + (enable ? "ON" : "OFF") + " (Camera ID: " + cameraId + ")");
            Log.i(TAG, "Flashlight operation successful.");

        } catch (CameraAccessException e) {
             // 捕获因摄像头设备状态引起的错误
             System.err.println("Error setting torch mode (CameraAccessException): " + e.getMessage());
             e.printStackTrace();
             Log.e(TAG, "Error setting torch mode (CameraAccessException)", e);
             System.exit(1);
        } catch (IllegalArgumentException e) {
             // 捕获因参数错误引起的错误 (如无效的 Camera ID)
             System.err.println("Error setting torch mode (IllegalArgumentException): Invalid camera ID or arguments: " + e.getMessage());
             e.printStackTrace();
             Log.e(TAG, "Error setting torch mode (IllegalArgumentException)", e);
             System.exit(1);
        } catch (Exception e) {
             // 捕获其他所有意外异常
             System.err.println("An unexpected error occurred: " + e.getMessage());
             e.printStackTrace();
             Log.e(TAG, "An unexpected error occurred", e);
             System.exit(1);
        }

        // app_process does not return control to the shell immediately,
        // but the process will exit after the main method finishes.
        System.exit(0);
    }
}
