// 测试在android 9 上可以
// 测试在android 14 上可以

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Range;
import android.util.Size;
import android.os.Looper; // 导入 Looper 类

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi", "InternalInsetResource", "DiscouragedApi"})
public final class CameraInfo {

    private static final String TAG = "CameraInfo工具";

    // --- Android 环境设置 ---
    private static Class<?> ACTIVITY_THREAD_CLASS;
    private static Object ACTIVITY_THREAD_INSTANCE;
    private static Context sContext;

    // --- 静态初始化 Android 环境 ---
    private static void initializeAndroidEnvironment() {
        try {
            // 在创建 ActivityThread 实例之前，检查并准备 Looper
            if (Looper.myLooper() == null) {
                System.out.println("当前线程没有 Looper，正在调用 Looper.prepare()...");
                Looper.prepare(); // 为当前线程准备 Looper
            } else {
                System.out.println("当前线程已有 Looper。");
            }


            ACTIVITY_THREAD_CLASS = Class.forName("android.app.ActivityThread");
            // 获取 ActivityThread 的无参构造函数
            Constructor<?> activityThreadConstructor = ACTIVITY_THREAD_CLASS.getDeclaredConstructor();
            // 允许访问私有构造函数
            activityThreadConstructor.setAccessible(true);
            // 创建 ActivityThread 实例
            ACTIVITY_THREAD_INSTANCE = activityThreadConstructor.newInstance();

            // 获取 sCurrentActivityThread 静态字段
            Field sCurrentActivityThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("sCurrentActivityThread");
            // 允许访问私有字段
            sCurrentActivityThreadField.setAccessible(true);
            // 将我们创建的实例设置给 sCurrentActivityThread
            sCurrentActivityThreadField.set(null, ACTIVITY_THREAD_INSTANCE);

            try {
                // 尝试获取 mSystemThread 字段并设置为 true，模拟系统进程
                Field mSystemThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("mSystemThread");
                mSystemThreadField.setAccessible(true);
                mSystemThreadField.setBoolean(ACTIVITY_THREAD_INSTANCE, true);
            } catch (NoSuchFieldException e) {
                // 如果找不到该字段，则打印警告，不影响程序运行
                System.err.println("警告: 未找到 mSystemThread 字段，跳过设置。 " + e.getMessage());
            }

            // 获取 getSystemContext 方法
            Method getSystemContextMethod = ACTIVITY_THREAD_CLASS.getDeclaredMethod("getSystemContext");
            // 允许访问私有方法
            getSystemContextMethod.setAccessible(true);
            // 调用 getSystemContext 方法获取系统上下文
            sContext = (Context) getSystemContextMethod.invoke(ACTIVITY_THREAD_INSTANCE);

            if (sContext == null) {
                throw new IllegalStateException("设置后未能获取系统上下文。");
            }
            System.out.println("Android 环境模拟设置完成。已获取上下文。");

            // 在 main 方法的末尾，我们不会调用 Looper.loop()，因为这是一个短暂运行的脚本，
            // 它的目的不是持续运行一个消息循环。
            // 如果你需要处理异步消息（例如，监听事件），那么就需要 Looper.loop()。
            // 但对于获取相机信息这种同步操作，Looper.prepare() 只是满足了 Handler 的构造要求。

        } catch (Exception e) {
            System.err.println("致命错误: 初始化 Android 环境模拟失败: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1); // 退出程序
        }
    }

    public static void main(String[] args) {
        System.out.println("--- " + TAG + " 开始 ---");

        // 初始化 Android 环境
        initializeAndroidEnvironment();

        // 获取 CameraManager 服务
        CameraManager cameraManager = (CameraManager) sContext.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            System.err.println("错误: CameraManager 服务不可用。");
            System.exit(1);
        }

        try {
            // 获取所有摄像头 ID 列表
            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds.length == 0) {
                System.out.println("此设备上未找到任何摄像头。");
                return;
            }

            System.out.println("\n--- 检测到的摄像头 ---");
            for (String id : cameraIds) {
                System.out.println("摄像头 ID: " + id);
                // 获取摄像头特性
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);

                // 摄像头朝向
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                String facingStr = "未知";
                if (facing != null) {
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) facingStr = "后置";
                    else if (facing == CameraCharacteristics.LENS_FACING_FRONT) facingStr = "前置";
                    else if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) facingStr = "外置";
                }
                System.out.println("  朝向 (LENS_FACING): " + facingStr);

                // 获取 StreamConfigurationMap，其中包含支持的流配置
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    System.out.println("  此摄像头无 StreamConfigurationMap。");
                    continue;
                }

                System.out.println("  支持的输出分辨率 (常见图像格式):");
                // 遍历所有支持的输出格式 (int 数组)
                for (int format : map.getOutputFormats()) {
                    try {
                        // 直接使用 int 类型的 format 获取输出尺寸
                        Size[] outputSizes = map.getOutputSizes(format);
                        if (outputSizes != null && outputSizes.length > 0) {
                            String formatName = getImageFormatName(format); // 获取可读的图像格式名称
                            System.out.println("    格式: " + formatName);
                            // 打印所有支持的分辨率
                            for (Size size : outputSizes) {
                                System.out.println("      - " + size.getWidth() + "x" + size.getHeight());
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        System.err.println("警告: 无法获取格式 " + format + " 的输出尺寸: " + e.getMessage());
                    }
                }
                
                // 专门列出 MediaCodec 视频编码器支持的尺寸
                System.out.println("  支持的视频编码器输出尺寸:");
                String[] videoMimeTypes = {
                    MediaFormat.MIMETYPE_VIDEO_AVC,  // H.264
                    MediaFormat.MIMETYPE_VIDEO_HEVC, // H.265
                    MediaFormat.MIMETYPE_VIDEO_VP8,  // VP8
                    MediaFormat.MIMETYPE_VIDEO_VP9   // VP9
                };
                for (String mimeType : videoMimeTypes) {
                    try {
                        Size[] videoSizes = map.getOutputSizes(ImageFormat.YUV_420_888); // 获取通用的YUV格式尺寸
                        if (videoSizes != null && videoSizes.length > 0) {
                            System.out.println("    适用于视频编码的 YUV_420_888 尺寸:");
                            for (Size size : videoSizes) {
                                System.out.println("      - " + size.getWidth() + "x" + size.getHeight());
                            }
                        } else {
                             System.out.println("      此摄像头不支持 YUV_420_888 格式输出。");
                        }
                    } catch (IllegalArgumentException e) {
                        System.err.println("调试: 摄像头 " + id + " 无法获取视频输出尺寸: " + e.getMessage());
                    }
                }

                System.out.println("  支持的视频编码器信息:");
                printEncoderInfo(characteristics);

                System.out.println("----------------------------------------");
            }
        } catch (CameraAccessException e) {
            System.err.println("错误: 无法访问摄像头: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            System.out.println("\n--- " + TAG + " 结束 ---");
            // 对于这种短暂运行的脚本，我们通常不需要调用 Looper.loop()，
            // 也不需要 Looper.quit()。
            // Looper.prepare() 只是为了满足 Handler 的构造需求。
            System.exit(0);
        }
    }

    // 辅助方法：打印 MediaCodec 编码器能力
    private static void printEncoderInfo(CameraCharacteristics characteristics) {
        String[] supportedVideoMimeTypes = {
            MediaFormat.MIMETYPE_VIDEO_AVC, // H.264
            MediaFormat.MIMETYPE_VIDEO_HEVC, // H.265
            MediaFormat.MIMETYPE_VIDEO_VP8,  // VP8
            MediaFormat.MIMETYPE_VIDEO_VP9   // VP9
        };

        for (String mimeType : supportedVideoMimeTypes) {
            System.out.println("    编码器能力 (" + mimeType + "):");
            boolean foundCodec = false;
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo info : codecList.getCodecInfos()) {
                if (!info.isEncoder()) {
                    continue;
                }
                String[] types = info.getSupportedTypes();
                for (String type : types) {
                    if (type.equalsIgnoreCase(mimeType)) {
                        foundCodec = true;
                        try {
                            MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(mimeType);

                            StringBuilder colorFormats = new StringBuilder();
                            for (int format : capabilities.colorFormats) {
                                colorFormats.append(getColorFormatName(format)).append(" ");
                            }
                            System.out.println("      - 编码器名称: " + info.getName());
                            System.out.println("        支持的颜色格式: " + colorFormats.toString().trim());

                            MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
                            if (videoCapabilities != null) {
                                Range<Integer> frameRates = videoCapabilities.getSupportedFrameRates();
                                System.out.println("        支持的帧率范围: " + frameRates.getLower() + "-" + frameRates.getUpper() + " fps");
                                System.out.println("        支持的比特率范围: " + videoCapabilities.getBitrateRange().getLower() / 1000 + "k-" + videoCapabilities.getBitrateRange().getUpper() / 1000000 + "M bps");
                                System.out.println("        支持的宽度范围: " + videoCapabilities.getSupportedWidths().getLower() + "-" + videoCapabilities.getSupportedWidths().getUpper());
                                System.out.println("        支持的高度范围: " + videoCapabilities.getSupportedHeights().getLower() + "-" + videoCapabilities.getSupportedHeights().getUpper());

                            }
                        } catch (IllegalArgumentException e) {
                            System.err.println("警告: 无法获取编码器 " + info.getName() + " 的能力 (" + mimeType + "): " + e.getMessage());
                        }
                    }
                }
            }
            if (!foundCodec) {
                System.out.println("      未找到支持 " + mimeType + " 的硬件编码器。");
            }
        }
    }

    private static String getColorFormatName(int format) {
        switch (format) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible: return "YUV420Flexible (通用YUV420)";
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar: return "YUV420Planar (YV12)";
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: return "YUV420SemiPlanar (NV12)";
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface: return "Surface (GPU纹理)";
            case MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888: return "ARGB8888 (32-bit)";
            case MediaCodecInfo.CodecCapabilities.COLOR_Format24bitRGB888: return "RGB888 (24-bit)";
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: return "YUV420PackedSemiPlanar";
            case MediaCodecInfo.CodecCapabilities.COLOR_Format16bitRGB565: return "RGB565 (16-bit)";
            // MediaCodecInfo.CodecCapabilities.COLOR_FormatRGBAFlexible 是 API 29+ 才有的，
            // 如果你的编译目标是 Android 9 或更早版本，可能会找不到。
            // 这里为了兼容性，先不添加，如果需要，请自行确认API版本。
            // case MediaCodecInfo.CodecCapabilities.COLOR_FormatRGBAFlexible: return "RGBA Flexible";
            default: return "未知 (0x" + Integer.toHexString(format) + ")";
        }
    }

    private static String getImageFormatName(int format) {
        switch (format) {
            case ImageFormat.JPEG: return "JPEG";
            case ImageFormat.NV21: return "NV21";
            case ImageFormat.YUY2: return "YUY2";
            case ImageFormat.YV12: return "YV12";
            case ImageFormat.YUV_420_888: return "YUV_420_888 (通用YUV)";
            case ImageFormat.RAW_SENSOR: return "RAW_SENSOR";
            case ImageFormat.RAW10: return "RAW10";
            case ImageFormat.RAW12: return "RAW12";
            case ImageFormat.DEPTH16: return "DEPTH16";
            case ImageFormat.DEPTH_POINT_CLOUD: return "DEPTH_POINT_CLOUD";
            case ImageFormat.PRIVATE: return "PRIVATE (私有格式, 如Surface)";
            case ImageFormat.HEIC: return "HEIC"; // API 28+
            case ImageFormat.DEPTH_JPEG: return "DEPTH_JPEG"; // API 29+
            default: return "未知 (0x" + Integer.toHexString(format) + ")";
        }
    }
}
