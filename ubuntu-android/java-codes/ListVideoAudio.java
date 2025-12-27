// 兼容 Android 9.0 (API 28) 及以上版本
// 编译建议：minSdkVersion 28, targetSdkVersion 28+

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

// 导入 InitializeAndroidEnvironment 类，如果它们在同一个包下，则不需要显式导入。
// 如果 InitializeAndroidEnvironment 在不同的包中，例如 `com.example.utils`，则需要 `import com.example.utils.InitializeAndroidEnvironment;`


@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi", "InternalInsetResource", "DiscouragedApi"})
public final class ListVideoAudio {

    private static final String TAG = "音视频信息工具";

    private static Context sContext; // 这个 sContext 现在从外部获取

    public static void main(String[] args) {
        System.out.println("--- " + TAG + " 开始 ---");

        // 从 InitializeAndroidEnvironment 类中获取 Context
        try {
            sContext = InitializeAndroidEnvironment.getSystemContext();
        } catch (RuntimeException e) {
            System.err.println("错误: 无法初始化 Android 环境。程序退出。");
            // 错误已经在 InitializeAndroidEnvironment 中处理并System.exit(1)
            return;
        }

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
                for (int format : map.getOutputFormats()) {
                    try {
                        Size[] outputSizes = map.getOutputSizes(format);
                        if (outputSizes != null && outputSizes.length > 0) {
                            String formatName = getImageFormatName(format);
                            System.out.println("    格式: " + formatName);
                            for (Size size : outputSizes) {
                                System.out.println("      - " + size.getWidth() + "x" + size.getHeight());
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        System.err.println("警告: 无法获取格式 " + format + " 的输出尺寸: " + e.getMessage());
                    }
                }
                
                System.out.println("  支持的视频编码器输出尺寸:");
                Size[] videoSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                if (videoSizes != null && videoSizes.length > 0) {
                    System.out.println("    适用于视频编码的 YUV_420_888 尺寸:");
                    for (Size size : videoSizes) {
                        System.out.println("      - " + size.getWidth() + "x" + size.getHeight());
                    }
                } else {
                        System.out.println("      此摄像头不支持 YUV_420_888 格式输出。");
                }

                System.out.println("  支持的视频编码器信息:");
                printVideoEncoderInfo(characteristics);

                // 打印支持的帧率范围
                android.util.Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                if (fpsRanges != null && fpsRanges.length > 0) {
                    System.out.println("  支持的帧率范围 (CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES):");
                    for (android.util.Range<Integer> range : fpsRanges) {
                        System.out.println("    - " + range.getLower() + " ~ " + range.getUpper() + " fps");
                    }
                } else {
                    System.out.println("  未获取到支持的帧率范围信息。");
                }

                System.out.println("----------------------------------------");
            }
        } catch (CameraAccessException e) {
            System.err.println("错误: 无法访问摄像头: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            System.out.println("\n--- " + TAG + " 结束 ---");
            System.out.println("\n--- 音频编码信息 ---");
            printAudioEncoderInfo();
            System.exit(0);
        }
    }

    // 辅助方法：打印 MediaCodec 视频编码器能力
    private static void printVideoEncoderInfo(CameraCharacteristics characteristics) {
        String[] supportedVideoMimeTypes = {
            MediaFormat.MIMETYPE_VIDEO_AVC,
            MediaFormat.MIMETYPE_VIDEO_HEVC,
            MediaFormat.MIMETYPE_VIDEO_VP8,
            MediaFormat.MIMETYPE_VIDEO_VP9
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

    // 辅助方法：打印音频编码器信息
    private static void printAudioEncoderInfo() {
        String[] supportedAudioMimeTypes = {
            MediaFormat.MIMETYPE_AUDIO_AAC,
            MediaFormat.MIMETYPE_AUDIO_AMR_NB,
            MediaFormat.MIMETYPE_AUDIO_AMR_WB,
            MediaFormat.MIMETYPE_AUDIO_MPEG,
            MediaFormat.MIMETYPE_AUDIO_VORBIS,
            MediaFormat.MIMETYPE_AUDIO_OPUS,
            MediaFormat.MIMETYPE_AUDIO_FLAC
        };

        for (String mimeType : supportedAudioMimeTypes) {
            System.out.println("音频编码器能力 (" + mimeType + "):");
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

                            System.out.println("  - 编码器名称: " + info.getName());
                            
                            // 安全获取支持的采样率
                            try {
                                int[] supportedSampleRates = capabilities.getAudioCapabilities().getSupportedSampleRates();
                                if (supportedSampleRates != null && supportedSampleRates.length > 0) {
                                    System.out.print("    支持的采样率: ");
                                    for (int i = 0; i < supportedSampleRates.length; i++) {
                                        System.out.print(supportedSampleRates[i]);
                                        if (i < supportedSampleRates.length - 1) {
                                            System.out.print(", ");
                                        }
                                    }
                                    System.out.println(" Hz");
                                } else {
                                    System.out.println("    支持的采样率: 未知");
                                }
                            } catch (Exception e) {
                                System.out.println("    支持的采样率: 无法获取 (" + e.getMessage() + ")");
                            }

                            // 获取支持的声道数范围（使用getMaxInputChannelCount替代getSupportedChannelCounts）
                            try {
                                int maxChannels = capabilities.getAudioCapabilities().getMaxInputChannelCount();
                                System.out.println("    最大支持声道数: " + maxChannels + " channels");
                            } catch (Exception e) {
                                System.out.println("    最大支持声道数: 无法获取 (" + e.getMessage() + ")");
                            }

                            // 获取支持的比特率范围
                            try {
                                android.util.Range<Integer> bitrateRange = capabilities.getAudioCapabilities().getBitrateRange();
                                if (bitrateRange != null) {
                                    int lowerKbps = bitrateRange.getLower() / 1000;
                                    int upperKbps = bitrateRange.getUpper() / 1000;
                                    System.out.println("    支持的比特率范围: " + lowerKbps + "-" + upperKbps + " kbps");
                                } else {
                                    System.out.println("    支持的比特率范围: 未知");
                                }
                            } catch (Exception e) {
                                System.out.println("    支持的比特率范围: 无法获取 (" + e.getMessage() + ")");
                            }

                        } catch (IllegalArgumentException e) {
                            System.err.println("警告: 无法获取编码器 " + info.getName() + " 的能力 (" + mimeType + "): " + e.getMessage());
                        }
                    }
                }
            }
            if (!foundCodec) {
                System.out.println("  未找到支持 " + mimeType + " 的硬件编码器。");
            }
            System.out.println();
        }
        
        // 特别处理AAC编码器的详细参数
        printDetailedAACInfo();
    }

    // 辅助方法：打印详细的AAC编码信息
    private static void printDetailedAACInfo() {
        System.out.println("AAC音频编码详细信息:");
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : codecList.getCodecInfos()) {
            if (!info.isEncoder()) {
                continue;
            }
            String[] types = info.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_AAC)) {
                    try {
                        MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC);
                        MediaCodecInfo.AudioCapabilities audioCapabilities = capabilities.getAudioCapabilities();
                        
                        System.out.println("  编码器: " + info.getName());
                        
                        // 采样率
                        try {
                            int[] sampleRates = audioCapabilities.getSupportedSampleRates();
                            System.out.print("    采样率: ");
                            if (sampleRates != null && sampleRates.length > 0) {
                                for (int i = 0; i < sampleRates.length; i++) {
                                    System.out.print(sampleRates[i]);
                                    if (i < sampleRates.length - 1) {
                                        System.out.print(", ");
                                    }
                                }
                                System.out.println(" Hz");
                            } else {
                                System.out.println("未知");
                            }
                        } catch (Exception e) {
                            System.out.println("无法获取采样率 (" + e.getMessage() + ")");
                        }
                        
                        // 比特率
                        try {
                            android.util.Range<Integer> bitrateRange = audioCapabilities.getBitrateRange();
                            if (bitrateRange != null) {
                                int lowerKbps = bitrateRange.getLower() / 1000;
                                int upperKbps = bitrateRange.getUpper() / 1000;
                                System.out.println("    比特率: " + lowerKbps + "-" + upperKbps + " kbps");
                            } else {
                                System.out.println("    比特率: 未知");
                            }
                        } catch (Exception e) {
                            System.out.println("无法获取比特率 (" + e.getMessage() + ")");
                        }
                        
                        // 声道数
                        try {
                            int maxChannels = audioCapabilities.getMaxInputChannelCount();
                            System.out.println("    最大声道数: " + maxChannels + " channels");
                        } catch (Exception e) {
                            System.out.println("无法获取声道数 (" + e.getMessage() + ")");
                        }
                        
                        // 获取并打印所有参数
                        System.out.println("    详细参数:");
                        for (MediaCodecInfo.CodecProfileLevel profileLevel : capabilities.profileLevels) {
                            System.out.println("      Profile Level: " + getAACProfileLevelName(profileLevel.profile));
                        }
                        
                        System.out.println();
                        
                    } catch (IllegalArgumentException e) {
                        System.err.println("警告: 无法获取编码器 " + info.getName() + " 的详细能力: " + e.getMessage());
                    }
                }
            }
        }
    }

    // 辅助方法：获取AAC Profile Level名称
    private static String getAACProfileLevelName(int profile) {
        switch (profile) {
            case MediaCodecInfo.CodecProfileLevel.AACObjectMain: return "Main";
            case MediaCodecInfo.CodecProfileLevel.AACObjectLC: return "LC (Low Complexity)";
            case MediaCodecInfo.CodecProfileLevel.AACObjectSSR: return "SSR (Scalable Sample Rate)";
            case MediaCodecInfo.CodecProfileLevel.AACObjectLTP: return "LTP (Long Term Prediction)";
            case MediaCodecInfo.CodecProfileLevel.AACObjectHE: return "HE (High Efficiency)";
            case MediaCodecInfo.CodecProfileLevel.AACObjectScalable: return "Scalable";
            case MediaCodecInfo.CodecProfileLevel.AACObjectERLC: return "ER LC (Error Resilient LC)";
            case MediaCodecInfo.CodecProfileLevel.AACObjectLD: return "LD (Low Delay)";
            case MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS: return "HE_PS (High Efficiency with Parametric Stereo)";
            case MediaCodecInfo.CodecProfileLevel.AACObjectELD: return "ELD (Enhanced Low Delay)";
            default: return "未知 Profile (" + profile + ")";
        }
    }

    // 辅助方法：将颜色格式代码转换为可读名称
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
            default: return "未知 (0x" + Integer.toHexString(format) + ")";
        }
    }

    // 辅助方法：将图像格式代码转换为可读名称
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
            case ImageFormat.HEIC: return "HEIC";
            case ImageFormat.DEPTH_JPEG: return "DEPTH_JPEG";
            default: return "未知 (0x" + Integer.toHexString(format) + ")";
        }
    }
}



