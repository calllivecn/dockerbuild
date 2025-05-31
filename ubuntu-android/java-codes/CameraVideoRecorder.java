// 这个是测试成功的 (请注意，这是一个复杂且依赖于 Android 内部 API 的示例，可能需要 root 权限)

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log; // 尽管最终会替换，但编译时仍可能需要导入
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// 重新添加：导入 InitializeAndroidEnvironment 类，以解决编译时找不到符号的问题
//import InitializeAndroidEnvironment;

@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi", "InternalInsetResource", "DiscouragedApi"})
public final class CameraVideoRecorder {

    private static final String TAG = "CameraVideoRecorder"; // 调试TAG，实际输出中不会显示

    // --- 默认 MediaCodec 参数 ---
    private static String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC; // H.264 AVC，默认编码器
    private static int FRAME_RATE = 30; // 帧率
    private static int I_FRAME_INTERVAL = 1; // I帧间隔 (秒)
    private static int BIT_RATE = 2000000; // 比特率 (2 Mbps)
    private static int VIDEO_WIDTH = 1280; // 视频宽度
    private static int VIDEO_HEIGHT = 720; // 视频高度
    // private static long RECORDING_DURATION_MS = 10000; // 录制时长 (毫秒)
    private static int ROTATE = 0; // 新增：旋转角度，默认0度

    // --- 命令行参数接收的变量 ---
    private static String OUTPUT_FILE_PATH = "output.video"; // 已修改为默认值 "output.video"
    private static String CAMERA_ID_TO_USE = null; // 默认不指定，让程序自动选择后置摄像头

    // --- 文件输出 ---
    private FileOutputStream mFileOutputStream;

    // --- Android 环境设置 ---
    private static Context sContext; // 直接使用 InitializeAndroidEnvironment 获取的 Context

    // --- Camera 相关 ---
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1); // 防止相机并发访问

    // --- MediaCodec 相关 ---
    private MediaCodec mMediaCodec;
    private Surface mEncoderInputSurface; // 连接到MediaCodec的输入Surface
    private HandlerThread mEncoderThread;
    private Handler mEncoderHandler;
    private boolean mIsRecording = false;

    public static void main(String[] args) {
        System.out.println(TAG + " 已启动。");

        // 解析命令行参数
        parseArguments(args);

        // 如果用户请求帮助信息，则显示并退出
        if (showHelp) {
            printHelp();
            System.exit(0);
        }

        // 根据选择的编码器类型更新默认的输出文件扩展名
        // 只有当 OUTPUT_FILE_PATH 仍然是其初始默认值 "output.video" 时才修改
        if (OUTPUT_FILE_PATH.equals("output.video")) {
            if (MIME_TYPE.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                OUTPUT_FILE_PATH = "output.h265";
            } else if (MIME_TYPE.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                OUTPUT_FILE_PATH = "output.h264";
            }
        }

        // 使用 InitializeAndroidEnvironment 进行初始化
        try {
            // 注意: 这里假设 InitializeAndroidEnvironment 类在编译时仍然可用，
            // 并且其 getSystemContext() 方法可以直接调用。
            // 如果 InitializeAndroidEnvironment 不再是同一个包，需要调整。
            sContext = InitializeAndroidEnvironment.getSystemContext();
            System.out.println("Android 环境模拟设置完成。已通过 InitializeAndroidEnvironment 获取上下文。");
        } catch (RuntimeException e) {
            System.err.println("致命错误: 初始化 Android 环境失败: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }


        CameraVideoRecorder recorder = new CameraVideoRecorder();
        try {
            recorder.startRecording();
            /*
            System.out.println("正在录制，时长 " + (RECORDING_DURATION_MS / 1000) + " 秒...");
            Thread.sleep(RECORDING_DURATION_MS);
            recorder.stopRecording();
            System.out.println("录制完成。输出文件: " + OUTPUT_FILE_PATH);
            */

           // 注册一个 Shutdown Hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("检测到 CTRL+C（或其他关闭信号），正在清理资源...");
                // 执行你的清理逻辑，比如关闭线程、释放资源等
                recorder.stopRecording();
                System.out.println("录制完成。输出文件: " + OUTPUT_FILE_PATH);
            }));

            // 一直录制。
            while (true) {
                // 这里可以添加一些逻辑来检查录制状态或处理其他任务
                Thread.sleep(1000); // 每秒检查一次
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("录制被中断: " + e.getMessage());

        } catch (Exception e) {
            System.err.println("录制过程中发生错误: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            recorder.releaseResources(); // 确保释放所有资源
            System.out.println(TAG + " 已完成。");
            System.exit(0);
        }
    }

    private static boolean showHelp = false; // 新增变量来控制是否显示帮助信息

    // --- 解析命令行参数 ---
    private static void parseArguments(String[] args) {
        Map<String, String> argMap = new HashMap<>();
        for (String arg : args) {
            String[] parts = arg.split("=", 2);
            if (parts.length == 2) {
                argMap.put(parts[0].toLowerCase(), parts[1]);
            } else if (arg.equalsIgnoreCase("--help")) { // 支持 --help 参数
                showHelp = true; // 标记为显示帮助
                return; // 直接返回，不再解析其他参数，因为即将退出
            } else {
                System.err.println("警告: 忽略无效的参数格式: " + arg);
            }
        }

        try {
            if (argMap.containsKey("frame_rate")) {
                FRAME_RATE = Integer.parseInt(argMap.get("frame_rate"));
                System.out.println("参数: frame_rate = " + FRAME_RATE);
            }
            if (argMap.containsKey("i_frame_interval")) {
                I_FRAME_INTERVAL = Integer.parseInt(argMap.get("i_frame_interval"));
                System.out.println("参数: I_frame_interval = " + I_FRAME_INTERVAL);
            }
            if (argMap.containsKey("bit_rate")) {
                BIT_RATE = Integer.parseInt(argMap.get("bit_rate"));
                System.out.println("参数: bit_rate = " + BIT_RATE);
            }
            if (argMap.containsKey("size")) {
                String sizeStr = argMap.get("size");
                String[] sizeParts = sizeStr.split("x");
                if (sizeParts.length == 2) {
                    VIDEO_WIDTH = Integer.parseInt(sizeParts[0]);
                    VIDEO_HEIGHT = Integer.parseInt(sizeParts[1]);
                    System.out.println("参数: size = " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
                } else {
                    System.err.println("警告: 无效的尺寸格式: " + sizeStr + "。使用默认 1280x720。");
                }
            }
            if (argMap.containsKey("output")) {
                OUTPUT_FILE_PATH = argMap.get("output");
                System.out.println("参数: output = " + OUTPUT_FILE_PATH);
            }
            if (argMap.containsKey("camera_id")) {
                CAMERA_ID_TO_USE = argMap.get("camera_id");
                System.out.println("参数: camera_id = " + CAMERA_ID_TO_USE);
            }

            /*
            if (argMap.containsKey("duration_ms")) { // 可以添加一个可选的录制时长参数
                RECORDING_DURATION_MS = Long.parseLong(argMap.get("duration_ms"));
                System.out.println("参数: duration_ms = " + RECORDING_DURATION_MS);
            }
            */
            if (argMap.containsKey("codec")) {
                String codecStr = argMap.get("codec").toLowerCase();
                if (codecStr.equals("hevc") || codecStr.equals("h265")) {
                    MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_HEVC;
                    System.out.println("参数: codec = " + codecStr + " (使用 HEVC/H.265 编码)。");
                } else if (codecStr.equals("avc") || codecStr.equals("h264")) {
                    MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
                    System.out.println("参数: codec = " + codecStr + " (使用 AVC/H.264 编码)。");
                } else {
                    System.err.println("警告: 未知的编码器类型: " + codecStr + "。使用默认 AVC/H.264。");
                }
            }

            if (argMap.containsKey("rotate")) {
                int rotation = Integer.parseInt(argMap.get("rotate"));
                if (rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270) {
                    ROTATE = rotation;
                    System.out.println("参数: rotate = " + ROTATE + " (顺时针旋转)。");
                } else {
                    System.err.println("警告: 无效的旋转角度: " + rotation + "。只支持 0, 90, 180, 270。使用默认 0。");
                }
            }

        } catch (NumberFormatException e) {
            System.err.println("错误: 参数中的数字格式无效: " + e.getMessage());
            e.printStackTrace(System.err);
            System.err.println("使用默认视频参数。");
        }
    }

    // --- 打印帮助信息 ---
    private static void printHelp() {
        System.out.println("用法: java -jar CameraVideoRecorder.jar [参数列表]");
        System.out.println("可选参数:");
        System.out.println("  --help                        : 显示此帮助信息并退出。");
        System.out.println("  frame_rate=<值>             : 设置视频帧率 (例如: 30)。默认值: " + FRAME_RATE);
        System.out.println("  i_frame_interval=<值>       : 设置 I 帧间隔 (秒)。默认值: " + I_FRAME_INTERVAL);
        System.out.println("  bit_rate=<值>               : 设置视频比特率 (例如: 2000000)。默认值: " + BIT_RATE);
        System.out.println("  size=<宽度>x<高度>          : 设置视频分辨率 (例如: 1920x1080)。默认值: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
        // 注意：此处 OUTPUT_FILE_PATH 默认值可能已因 codec 更改而变化，帮助信息将显示当前默认值
        System.out.println("  output=<文件路径>           : 设置输出文件路径 (例如: /sdcard/video.h264 或 output.h265)。默认值: " + OUTPUT_FILE_PATH);
        System.out.println("  camera_id=<ID>              : 指定要使用的摄像头 ID (例如: 0 或 1)。默认自动选择后置摄像头。");
        // System.out.println("  duration_ms=<毫秒>          : 设置录制时长 (毫秒)。默认值: " + RECORDING_DURATION_MS + " (即 " + (RECORDING_DURATION_MS / 1000) + " 秒)。");
        System.out.println("  codec=<类型>                : 设置视频编码器类型 (例如: avc 或 hevc)。默认值: " + (MIME_TYPE.equals(MediaFormat.MIMETYPE_VIDEO_AVC) ? "avc (H.264)" : "hevc (H.265)"));
        System.out.println("  rotate=<角度>               : 顺时针旋转视频角度 (0, 90, 180, 270)。默认值: " + ROTATE);
        System.out.println("\n示例:");
        System.out.println("  java -jar CameraVideoRecorder.jar output=/sdcard/my_video.h265 size=1920x1080 frame_rate=60 duration_ms=5000 camera_id=0 codec=hevc");
        System.out.println("  java -jar CameraVideoRecorder.jar output=my_video.h264 size=1280x720");
    }


    // --- 开始录制 ---
    public void startRecording() throws IOException, CameraAccessException, InterruptedException {
        if (mIsRecording) {
            System.err.println("警告: 正在录制中。");
            return;
        }

        mIsRecording = true;

        // 1. 启动编码器线程
        mEncoderThread = new HandlerThread("MediaCodecThread");
        mEncoderThread.start();
        mEncoderHandler = new Handler(mEncoderThread.getLooper());

        // 2. 启动摄像头线程
        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        // 3. 初始化 MediaCodec 编码器
        setupMediaCodec();

        // 4. 打开文件输出流
        File outputFile = new File(OUTPUT_FILE_PATH);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs(); // 确保目录存在
            System.out.println("已创建父目录: " + parentDir.getAbsolutePath());
        }
        mFileOutputStream = new FileOutputStream(outputFile);
        System.out.println("文件输出流已打开: " + OUTPUT_FILE_PATH);

        // 5. 打开摄像头
        openCamera();
    }

    // --- 停止录制 ---
    public void stopRecording() {
        if (!mIsRecording) {
            System.err.println("警告: 未在录制中。");
            return;
        }

        mIsRecording = false;

        // 停止捕获会话并关闭摄像头
        try {
            if (mCaptureSession != null) {
                mCaptureSession.stopRepeating();
                mCaptureSession.close();
                mCaptureSession = null;
                System.out.println("CameraCaptureSession 已停止并关闭。");
            }
        } catch (CameraAccessException e) {
            System.err.println("错误: 停止摄像头捕获会话失败: " + e.getMessage());
            e.printStackTrace(System.err);
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
            System.out.println("CameraDevice 已关闭。");
        }

        // 停止并释放 MediaCodec
        if (mMediaCodec != null) {
            try {
                mMediaCodec.stop();
                mMediaCodec.release();
                System.out.println("MediaCodec 已停止并释放。");
            } catch (IllegalStateException e) {
                System.err.println("错误: MediaCodec 停止/释放失败，状态不正确: " + e.getMessage());
                e.printStackTrace(System.err);
            }
            mMediaCodec = null;
        }

        // 关闭文件输出流
        if (mFileOutputStream != null) {
            try {
                mFileOutputStream.close();
                System.out.println("文件输出流已关闭。");
            } catch (IOException e) {
                System.err.println("错误: 关闭文件输出流失败: " + e.getMessage());
                e.printStackTrace(System.err);
            }
            mFileOutputStream = null;
        }

        // 停止线程
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            try {
                mCameraThread.join();
                System.out.println("CameraThread 已停止。");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("错误: 摄像头线程等待中断: " + e.getMessage());
                e.printStackTrace(System.err);
            }
            mCameraThread = null;
            mCameraHandler = null;
        }

        if (mEncoderThread != null) {
            mEncoderThread.quitSafely();
            try {
                mEncoderThread.join();
                System.out.println("EncoderThread 已停止。");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("错误: 编码器线程等待中断: " + e.getMessage());
                e.printStackTrace(System.err);
            }
            mEncoderThread = null;
            mEncoderHandler = null;
        }

        System.out.println("录制已停止，资源已释放。");
    }

    // --- 确保所有资源被释放 ---
    private void releaseResources() {
        stopRecording(); // 确保停止并释放
    }

    // --- 设置 MediaCodec 编码器 ---
    private void setupMediaCodec() throws IOException {
        System.out.println("正在设置 MediaCodec 编码器，分辨率 " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + " @ " + FRAME_RATE + "fps, " + (BIT_RATE / 1000000.0) + "Mbps...");
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);

        // 设置编码器参数
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface); // 颜色格式设置为Surface
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        format.setInteger(MediaFormat.KEY_ROTATION, ROTATE);
        System.out.println("MediaCodec 将设置 KEY_ROTATION 为: " + ROTATE);


        // 尝试选择一个支持Surface输入的编码器
        try {
            mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            System.err.println("错误: 创建 MediaCodec 编码器失败，类型为 " + MIME_TYPE + ": " + e.getMessage());
            e.printStackTrace(System.err);
            throw e;
        }

        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        // 获取编码器输入Surface
        mEncoderInputSurface = mMediaCodec.createInputSurface();
        System.out.println("MediaCodec 输入 Surface 已创建。");

        // 设置 MediaCodec 异步回调
        mMediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) { // 移除 @NonNull
                // 通常用于解码器或需要手动提供数据的情况。编码器使用 Surface 自动获取数据。
                // System.out.println("调试: onInputBufferAvailable: " + index);
            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) { // 移除 @NonNull
                if (mFileOutputStream == null) {
                    System.err.println("警告: 文件输出流为空，丢弃编码数据。");
                    codec.releaseOutputBuffer(index, false);
                    return;
                }
                try {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                    if (outputBuffer != null) {
                        // 写入 SPS/PPS 等配置数据 (如果这是第一帧或者配置数据有更新)
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            // System.out.println("调试: 编码器配置缓冲区: " + info.size + " 字节");
                            byte[] data = new byte[info.size];
                            outputBuffer.get(data);
                            mFileOutputStream.write(data, 0, info.size);
                        } else {
                            // 写入视频数据
                            // System.out.println("调试: 视频数据缓冲区: " + info.size + " 字节, 标志: " + info.flags);
                            outputBuffer.position(info.offset);
                            outputBuffer.limit(info.offset + info.size);

                            byte[] data = new byte[info.size];
                            outputBuffer.get(data);
                            mFileOutputStream.write(data, 0, info.size);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("错误: 写入编码数据到文件失败: " + e.getMessage());
                    e.printStackTrace(System.err);
                } finally {
                    codec.releaseOutputBuffer(index, false); // 释放输出缓冲区
                }
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) { // 移除 @NonNull
                System.err.println("致命错误: MediaCodec 错误: " + e.getMessage());
                e.printStackTrace(System.err);
                // 在这里处理致命错误，可能需要停止录制
                stopRecording();
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) { // 移除 @NonNull
                System.out.println("MediaCodec 输出格式已更改: " + format);
                // 这里可以获取新的输出格式，例如新的 SPS/PPS 数据
            }
        }, mEncoderHandler); // 将回调设置到编码器线程的 Handler

        mMediaCodec.start();
        System.out.println("MediaCodec 已启动。");
    }

    // --- 打开摄像头 ---
    private void openCamera() throws CameraAccessException, InterruptedException {
        System.out.println("正在打开摄像头...");
        CameraManager manager = (CameraManager) sContext.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("CameraManager 服务不可用。");
        }

        String selectedCameraId = null;
        System.out.println("可用的摄像头 ID 及其特性:");

        for (String id : manager.getCameraIdList()) {
            System.out.println("  摄像头 ID: " + id);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

            String facingStr = "未知";
            if (facing != null) {
                if (facing == CameraCharacteristics.LENS_FACING_BACK) facingStr = "后置";
                else if (facing == CameraCharacteristics.LENS_FACING_FRONT) facingStr = "前置";
                else if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) facingStr = "外部";
            }
            System.out.println("    LENS_FACING (镜头朝向): " + facingStr);

            // 新增：输出支持的帧率范围
            android.util.Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            if (fpsRanges != null) {
                System.out.println("    支持的帧率范围:");
                for (android.util.Range<Integer> range : fpsRanges) {
                    System.out.println("      - " + range);
                }
            }

            Boolean size_bool = false;
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Size[] videoSizes = map.getOutputSizes(MediaCodec.class);
                if (videoSizes != null && videoSizes.length <= 0) {
                    System.out.println("    此摄像头没有 StreamConfigurationMap。");
                    throw new RuntimeException("此摄像头没有 StreamConfigurationMap。");
                }

                for (Size size : videoSizes) {
                    if (size.getWidth() == VIDEO_WIDTH && size.getHeight() == VIDEO_HEIGHT){
                        System.out.println("    设置size:" + size.getWidth() + "x" + size.getHeight());
                        size_bool = true; // 标记找到匹配的分辨率
                        break; // 找到匹配的分辨率后退出循环
                    }
                }

                if (!size_bool) {
                    System.out.println("    支持的 MediaCodec 尺寸 (" + MIME_TYPE + "):");
                    for (Size size : videoSizes) {
                        System.out.println("      - " + size.getWidth() + "x" + size.getHeight());
                    }
                }
            } else {
                System.out.println("    此摄像头没有 StreamConfigurationMap。");
                System.exit(1);
            }


            // 如果指定了 camera_id，则优先使用指定的摄像头
            if (CAMERA_ID_TO_USE != null && CAMERA_ID_TO_USE.equals(id)) {
                selectedCameraId = id;
                System.out.println("    --> 这是 camera_id 指定的摄像头: " + CAMERA_ID_TO_USE);
                // 检查指定摄像头是否支持所需分辨率
                if (map != null) {
                    Size[] sizes = map.getOutputSizes(MediaCodec.class);
                    if (sizes != null) {
                        boolean resolutionSupported = false;
                        for (Size s : sizes) {
                            if (s.getWidth() == VIDEO_WIDTH && s.getHeight() == VIDEO_HEIGHT) {
                                resolutionSupported = true;
                                break;
                            }
                        }
                        if (!resolutionSupported) {
                            System.err.println("错误: 指定的摄像头 " + CAMERA_ID_TO_USE + " 不支持 " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + " 用于 MediaCodec。");
                            // 可以选择抛出异常或尝试找一个最近的，这里为了严格，我们继续抛异常
                            // 否则，如果在这里设置 selectedCameraId，即使分辨率不匹配也会尝试打开
                            throw new RuntimeException("指定的摄像头 " + CAMERA_ID_TO_USE + " 不支持所需分辨率 " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
                        }
                    }
                }
                break; // 找到并验证指定摄像头后退出循环
            }
            // 如果没有指定 camera_id，则选择后置摄像头
            else if (CAMERA_ID_TO_USE == null && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                // 检查后置摄像头是否支持所需分辨率
                if (map != null) {
                    Size[] sizes = map.getOutputSizes(MediaCodec.class);
                    if (sizes != null) {
                        for (Size s : sizes) {
                            if (s.getWidth() == VIDEO_WIDTH && s.getHeight() == VIDEO_HEIGHT) {
                                selectedCameraId = id; // 找到一个支持所需分辨率的后置摄像头
                                System.out.println("    --> 找到一个支持所需分辨率的后置摄像头: " + id);
                                break; // 找到即退出
                            }
                        }
                        if (selectedCameraId != null) break; // 如果已经找到适合的后置摄像头并指定分辨率，则退出外层循环
                    }
                }
            }
        }

        if (selectedCameraId == null) {
            String resolutionInfo = VIDEO_WIDTH + "x" + VIDEO_HEIGHT;
            String cameraSelectionInfo = (CAMERA_ID_TO_USE != null) ? "指定的摄像头 ID " + CAMERA_ID_TO_USE : "一个合适的后置摄像头";
            throw new RuntimeException("未找到支持所需视频分辨率 " + resolutionInfo + " 用于 MediaCodec 的合适摄像头 (" + cameraSelectionInfo + ")。");
        }

        // 请求打开摄像头
        mCameraOpenCloseLock.acquire(); // 获取信号量，防止多次打开
        manager.openCamera(selectedCameraId, mStateCallback, mCameraHandler);
        System.out.println("已请求打开摄像头: " + selectedCameraId);
    }

    // --- 摄像头状态回调 ---
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) { // 移除 @NonNull
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            System.out.println("摄像头 " + cameraDevice.getId() + " 已打开。");
            createCameraPreviewSession(); // 摄像头打开后创建捕获会话
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) { // 移除 @NonNull
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            System.err.println("警告: 摄像头已断开连接。");
            stopRecording(); // 相机断开连接，停止录制
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) { // 移除 @NonNull
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            System.err.println("错误: 摄像头错误: " + error);
            stopRecording(); // 相机出错，停止录制
        }
    };

    // --- 创建摄像头捕获会话 ---
    private void createCameraPreviewSession() {
        if (mCameraDevice == null || mEncoderInputSurface == null || mCameraHandler == null) {
            System.err.println("错误: CameraDevice、编码器输入 Surface 或摄像头 Handler 为空，无法创建捕获会话。");
            stopRecording();
            return;
        }

        try {
            // 创建用于 MediaCodec 的 CaptureRequest.Builder
            final CaptureRequest.Builder captureRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD); // 录制模板

            captureRequestBuilder.addTarget(mEncoderInputSurface); // 将编码器输入Surface作为目标

            // 创建 CaptureSession
            mCameraDevice.createCaptureSession(Collections.singletonList(mEncoderInputSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) { // 移除 @NonNull
                            if (mCameraDevice == null) {
                                return;
                            }
                            mCaptureSession = cameraCaptureSession;
                            System.out.println("CameraCaptureSession 已配置。");

                            try {
                                // 设置自动对焦、自动曝光等
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                                // 新增：设置帧率范围
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                        new android.util.Range<>(FRAME_RATE, FRAME_RATE));

                                // 开始重复捕获请求
                                mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mCameraHandler);
                                System.out.println("摄像头 setRepeatingRequest 已启动。");
                            } catch (CameraAccessException e) {
                                System.err.println("错误: 启动摄像头预览/录制失败: " + e.getMessage());
                                e.printStackTrace(System.err);
                                stopRecording();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) { // 移除 @NonNull
                            System.err.println("错误: 配置摄像头捕获会话失败。");
                            stopRecording();
                        }
                    }, mCameraHandler); // 回调在摄像头线程上执行

        } catch (CameraAccessException e) {
            System.err.println("错误: 创建摄像头捕获会话失败: " + e.getMessage());
            e.printStackTrace(System.err);
            stopRecording();
        }
    }

    // --- 辅助方法: 选择最接近期望分辨率的尺寸 ---
    // 这个方法在此版本中主要用于调试日志，实际选择逻辑在 openCamera 中完成
    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        // 简单地返回最大的一个，用于调试时查看
        System.out.println("调试: 尝试在以下尺寸中选择最佳尺寸: " + Arrays.toString(choices));
        return Collections.max(Arrays.asList(choices), new Comparator<Size>() {
            @Override
            public int compare(Size lhs, Size rhs) {
                return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
            }
        });
    }
}
