// 这个是测试成功的
//
import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;
import android.os.Looper; // 导入 Looper 类

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi", "InternalInsetResource", "DiscouragedApi"})
public final class FlashlightControl2 { // 标记为 final class

    private static final String TAG = "FlashlightControl";

    // --- Start of Scrcpy-like Environment Setup Workaround ---
    private static Class<?> ACTIVITY_THREAD_CLASS;
    private static Object ACTIVITY_THREAD_INSTANCE; // 重命名以区分类和实例

    private static void initializeAndroidEnvironment() {
        try {
            // 1. 确保主线程的 Looper 已经准备好
            // Scrcpy 使用 Looper.prepareMainLooper()
            if (Looper.myLooper() == null) {
                 // Looper.prepareLoop(); // 之前尝试的为当前线程准备Looper
                 Looper.prepareMainLooper(); // 尝试使用 prepareMainLooper() 模拟 Scrcpy
                 Log.i(TAG, "Prepared MainLooper for the current thread.");
            } else {
                 Log.i(TAG, "Looper already prepared for the current thread.");
            }
            // 注意: Looper.loop() 会阻塞，这里只需要准备

            // 2. 通过反射创建并设置 ActivityThread 实例
            ACTIVITY_THREAD_CLASS = Class.forName("android.app.ActivityThread");
            Log.d(TAG, "Found ActivityThread class: " + ACTIVITY_THREAD_CLASS.getName());

            // 尝试获取 ActivityThread 的构造函数并创建新实例
            Constructor<?> activityThreadConstructor = ACTIVITY_THREAD_CLASS.getDeclaredConstructor();
            activityThreadConstructor.setAccessible(true);
            ACTIVITY_THREAD_INSTANCE = activityThreadConstructor.newInstance(); // 创建 ActivityThread 实例
            Log.d(TAG, "Created new ActivityThread instance.");

            // 尝试获取并设置静态字段 sCurrentActivityThread
            Field sCurrentActivityThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            sCurrentActivityThreadField.set(null, ACTIVITY_THREAD_INSTANCE); // 将新的 ActivityThread 实例设置到静态字段
            Log.d(TAG, "Set sCurrentActivityThread field.");

            // 尝试获取并设置实例字段 mSystemThread (可选，但 Scrcpy 做了，可能是为了模拟系统进程环境)
            try {
                 Field mSystemThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("mSystemThread");
                 mSystemThreadField.setAccessible(true);
                 mSystemThreadField.setBoolean(ACTIVITY_THREAD_INSTANCE, true); // 标记为系统线程
                 Log.d(TAG, "Set mSystemThread field to true.");
            } catch (NoSuchFieldException e) {
                 // 某些版本可能没有这个字段，忽略
                 Log.w(TAG, "mSystemThread field not found, skipping.", e);
            }


            System.out.println("Android environment simulation setup complete.");
            Log.i(TAG, "Android environment simulation setup complete.");

        } catch (Exception e) {
            // 捕获所有反射和 Looper 准备过程中可能出现的异常
            System.err.println("FATAL: Failed to initialize Android environment simulation: " + e.getMessage());
            e.printStackTrace();
            Log.e(TAG, "FATAL: Failed to initialize Android environment simulation", e);
            // 环境初始化失败是致命错误，后续代码无法运行
            System.exit(1); // 致命错误，直接退出
        }
    }
    // --- End of Scrcpy-like Environment Setup Workaround ---


    public static void main(String[] args) {
        System.out.println(TAG + " started.");
        Log.i(TAG, TAG + " started.");

        // !!! 第一步: 调用环境初始化方法 !!!
        initializeAndroidEnvironment();

        // 检查命令行参数 (与之前相同)
        if (args.length != 1) {
            System.err.println("Usage: CLASSPATH=/path/to/your.jar app_process /system/bin/ FlashlightControl <on|off>");
            Log.e(TAG, "Incorrect number of arguments: " + args.length);
            System.exit(1);
            return;
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
            return;
        }

        Context context = null;
        try {
            // !!! 第二步: 在环境初始化后尝试获取 Context !!!
            // 理论上，sCurrentActivityThread 现在应该被设置了
            // 我们尝试通过反射调用 getSystemContext() 方法来获取一个系统 Context，
            // 因为 ActivityThread.getSystemContext() 通常返回一个适合访问系统服务的 Context
            // 相较于 currentApplication() 可能更稳定
            Method getSystemContextMethod = ACTIVITY_THREAD_CLASS.getDeclaredMethod("getSystemContext");
            getSystemContextMethod.setAccessible(true); // getSystemContext() 可能是私有的
            context = (Context) getSystemContextMethod.invoke(ACTIVITY_THREAD_INSTANCE); // 在我们创建的实例上调用方法

            if (context == null) {
                System.err.println("FATAL: Failed to get system context after setup.");
                Log.e(TAG, "FATAL: Failed to get system context after setup.");
                System.exit(1);
                return;
            }
            System.out.println("Successfully obtained system context: " + context);
            Log.i(TAG, "Successfully obtained system context: " + context);

        } catch (Exception e) {
             // 捕获获取 Context 过程中可能出现的异常
             System.err.println("FATAL: Error getting context after setup: " + e.getMessage());
             e.printStackTrace();
             Log.e(TAG, "FATAL: Error getting context after setup", e);
             System.exit(1);
             return;
        }


        // 获取 CameraManager 并控制闪光灯 (与之前基本相同)
        CameraManager cameraManager = null;
        try {
            // 使用获取到的 Context 来获取 CameraManager 服务
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
            Log.d(TAG, "Found " + cameraIds.length + " cameras.");

            for (String id : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                 Log.d(TAG, "Checking camera " + id + ", flash available: " + flashAvailable);
                if (flashAvailable != null && flashAvailable) {
                    cameraId = id;
                    System.out.println("Found camera with flash unit: " + cameraId);
                    Log.i(TAG, "Found camera with flash unit: " + cameraId);
                    break;
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
            System.out.println("Setting torch mode for camera " + cameraId + " to " + (enable ? "ON" : "OFF") + "...");
            Log.i(TAG, "Setting torch mode for camera " + cameraId + " to " + (enable ? "ON" : "OFF"));
            // 在 Root 环境下，权限问题不应该是这里的障碍
            cameraManager.setTorchMode(cameraId, enable);
            System.out.println("Flashlight turned " + (enable ? "ON" : "OFF") + " (Camera ID: " + cameraId + ")");
            Log.i(TAG, "Flashlight operation successful.");

        } catch (CameraAccessException e) {
             System.err.println("Error setting torch mode (CameraAccessException): " + e.getMessage());
             e.printStackTrace();
             Log.e(TAG, "Error setting torch mode (CameraAccessException)", e);
             System.exit(1);
        } catch (IllegalArgumentException e) {
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

        System.out.println(TAG + " finished.");
        Log.i(TAG, TAG + " finished.");
        System.exit(0);
    }
}
