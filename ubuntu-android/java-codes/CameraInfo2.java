// 兼容 Android 9.0 (API 28) 及以上版本
// 编译建议：minSdkVersion 28, targetSdkVersion 28+

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.EncoderProfiles;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Range;
import android.util.Size;
import java.util.Arrays;
import java.util.List;
import android.os.Build;

// 导入 InitializeAndroidEnvironment 类，如果它们在同一个包下，则不需要显式导入。
// 如果 InitializeAndroidEnvironment 在不同的包中，例如 `com.example.utils`，则需要 `import com.example.utils.InitializeAndroidEnvironment;`


@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi", "InternalInsetResource", "DiscouragedApi"})
public final class CameraInfo2 {

    private static Context sContext; // 这个 sContext 现在从外部获取

    public static void main(String[] args) {
        System.out.println("--- 开始 ---");

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

                listAllSupportedVideoProfiles(id);
            }
        } catch (CameraAccessException e) {
            System.err.println("错误: 无法访问摄像头: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            System.out.println("\n--- 结束 ---");
            System.exit(0);
        }
    }

    public static void listAllSupportedVideoProfiles(String cameraId) {
        System.out.println("  CamcorderProfile 是静态配置表。不同 [分辨率@fps] 的组合可以直接参考");
        int[] qualityLevels = {
            CamcorderProfile.QUALITY_LOW,
            CamcorderProfile.QUALITY_HIGH,
            CamcorderProfile.QUALITY_QCIF,
            CamcorderProfile.QUALITY_CIF,
            CamcorderProfile.QUALITY_480P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_1080P,
            // API 21-30 (Android 5.0-11)	QUALITY_2160P	旧命名，表示 3840×2160
            // API 31+ (Android 12+)	QUALITY_4KUHD	新命名，但 QUALITY_2160P 仍然保留
            CamcorderProfile.QUALITY_2160P,      // 等价于 QUALITY_4KUHD
            // CamcorderProfile.QUALITY_4KUHD, // 这里我是需要兼容 sdk 28 android9
            CamcorderProfile.QUALITY_QVGA,

            // 高帧率慢动作（API 24+）
            CamcorderProfile.QUALITY_HIGH_SPEED_LOW,
            CamcorderProfile.QUALITY_HIGH_SPEED_HIGH,
            CamcorderProfile.QUALITY_HIGH_SPEED_480P,
            CamcorderProfile.QUALITY_HIGH_SPEED_720P,
            CamcorderProfile.QUALITY_HIGH_SPEED_1080P,
        };

        for (int quality : qualityLevels) {
            String name = getQualityName(quality);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // --- API 31+ 逻辑：原生支持多编码器遍历 ---
                EncoderProfiles profiles = CamcorderProfile.getAll(cameraId, quality);
                if (profiles != null) {
                    List<EncoderProfiles.VideoProfile> vProfiles = profiles.getVideoProfiles();
                    List<EncoderProfiles.AudioProfile> aProfiles = profiles.getAudioProfiles();

                    for (int i = 0; i < vProfiles.size(); i++) {
                        EncoderProfiles.VideoProfile v = vProfiles.get(i);
                        // 匹配对应的音频流，如果没有则取第一个
                        EncoderProfiles.AudioProfile a = (i < aProfiles.size()) ? aProfiles.get(i) : 
                                                         (!aProfiles.isEmpty() ? aProfiles.get(0) : null);

                        double videoBitrateMbps = v.getBitrate() / 1000000.0;
                        String profileLabel = (vProfiles.size() > 1) ? String.format("%s[%d]", name, i) : name;

                        System.out.println(String.format(
                            "  VideoProfile %-15s: %dx%d @ %d fps | Video: %-12s (%.2f Mbps) | Audio: %s",
                            profileLabel,
                            v.getWidth(),
                            v.getHeight(),
                            v.getFrameRate(),
                            getVideoCodecName(v.getCodec()),
                            videoBitrateMbps,
                            (a != null) ? getAudioCodecName(a.getCodec()) : "NONE"
                        ));
                    }
                }

            } else {
                // --- API 28 - 30 逻辑 ---
                // 兼容性处理：由于你之前报错，这里必须先将 String 类型的 cameraId 转为 int
                try {
                    int idAsInt = Integer.parseInt(cameraId);
                    if (CamcorderProfile.hasProfile(idAsInt, quality)) {
                        CamcorderProfile profile = CamcorderProfile.get(idAsInt, quality);
                        if (profile != null) {
                            double videoBitrateMbps = profile.videoBitRate / 1000000.0;

                            System.out.println(String.format(
                                "  VideoProfile %-15s: %dx%d @ %d fps | Video: %-12s (%.2f Mbps) | Audio: %s",
                                name,
                                profile.videoFrameWidth,
                                profile.videoFrameHeight,
                                profile.videoFrameRate,
                                getVideoCodecName(profile.videoCodec),
                                videoBitrateMbps,
                                getAudioCodecName(profile.audioCodec)
                            ));
                        }
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid cameraId for legacy API: " + cameraId);
                }
            }
        }
    }

    // 辅助方法：将常量转为可读名称
    private static String getQualityName(int quality) {
        switch (quality) {
            case CamcorderProfile.QUALITY_LOW: return "LOW";
            case CamcorderProfile.QUALITY_HIGH: return "HIGH";
            case CamcorderProfile.QUALITY_QCIF: return "QCIF";
            case CamcorderProfile.QUALITY_CIF: return "CIF";
            case CamcorderProfile.QUALITY_480P: return "480P";
            case CamcorderProfile.QUALITY_720P: return "720P";
            case CamcorderProfile.QUALITY_1080P: return "1080P";
            case CamcorderProfile.QUALITY_2160P: return "2160P";
            // case CamcorderProfile.QUALITY_4KUHD: return "4KUHD";
            case CamcorderProfile.QUALITY_QVGA: return "QVGA";
            case CamcorderProfile.QUALITY_HIGH_SPEED_LOW: return "HFR_LOW";
            case CamcorderProfile.QUALITY_HIGH_SPEED_HIGH: return "HFR_HIGH";
            case CamcorderProfile.QUALITY_HIGH_SPEED_480P: return "HFR_480P";
            case CamcorderProfile.QUALITY_HIGH_SPEED_720P: return "HFR_720P";
            case CamcorderProfile.QUALITY_HIGH_SPEED_1080P: return "HFR_1080P";
            default: return "UNKNOWN(" + quality + ")";
        }
    }

    // --- 修正后的映射函数 ---
    public static String getVideoCodecName(int codec) {
        // 参考 MediaRecorder.VideoEncoder
        switch (codec) {
            case 1: return "H.263";
            case 2: return "H.264 (AVC)";
            case 3: return "MPEG-4 SP";
            case 4: return "VP8";
            case 5: return "HEVC (H.265)";
            case 6: return "VP9";
            case 7: return "AV1";
            default: return "CODEC_" + codec;
        }
    }

    public static String getAudioCodecName(int codec) {
        // 参考 MediaRecorder.AudioEncoder
        switch (codec) {
            case 1: return "AMR_NB";
            case 2: return "AMR_WB";
            case 3: case 4: case 5: return "AAC"; // AAC/HE_AAC/AAC_ELD
            case 6: return "VORBIS";
            case 7: return "OPUS";
            default: return "CODEC_" + codec;
        }
    }

    // 辅助方法：打印 MediaCodec 编码器能力 (保持不变)
    private static void printEncoderInfo(CameraCharacteristics characteristics) {
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

    // 辅助方法：将颜色格式代码转换为可读名称 (保持不变)
    private static String getColorFormatName(int format) {
        switch (format) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible: return "YUV420Flexible (通用YUV420)";
            // case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar: return "YUV420Planar (YV12)"; 旧的
            // case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: return "YUV420SemiPlanar (NV12)";
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface: return "Surface (GPU纹理)";
            // case MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888: return "ARGB8888 (32-bit)";
            // case MediaCodecInfo.CodecCapabilities.COLOR_Format24bitRGB888: return "RGB888 (24-bit)";
            // case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: return "YUV420PackedSemiPlanar";
            case MediaCodecInfo.CodecCapabilities.COLOR_Format16bitRGB565: return "RGB565 (16-bit)";
            default: return "未知 (0x" + Integer.toHexString(format) + ")";
        }
    }

    // 辅助方法：将图像格式代码转换为可读名称 (保持不变)
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
