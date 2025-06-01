// 这个是测试成功的 (请注意，这是一个复杂且依赖于 Android 内部 API 的示例，可能需要 root 权限)
// 此版本添加了通过 TCP 套接字发送编码后视频数据的功能，并在客户端连接/断开时控制录制。

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult; // 导入 TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.util.Range;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi", "InternalInsetResource", "DiscouragedApi"})
public final class CameraServer {

    private static final String TAG = "CameraServer";

    // --- 默认 MediaCodec 参数 ---
    private static String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC; // H.264 AVC，默认编码器
    private static int FRAME_RATE = 30; // 帧率
    private static int I_FRAME_INTERVAL = 1; // I帧间隔 (秒)
    private static int BIT_RATE_MB = 1000000;
    private static int BIT_RATE = 2*BIT_RATE_MB; // 比特率 (2 Mbps)
    private static int VIDEO_WIDTH = 1280; // 视频宽度
    private static int VIDEO_HEIGHT = 720; // 视频高度
    private static int ROTATE = 0; // 新增：旋转角度，默认0度

    // --- 命令行参数接收的变量 ---
    private static String CAMERA_ID_TO_USE = null; // 默认不指定，让程序自动选择后置摄像头

    // --- 网络相关 ---
    private static int TCP_PORT = 58888; // 改为非 final
    private static String TCP_HOST = "::1"; // 改为非 final
    private ServerSocket mServerSocket;
    private List<Socket> mTcpClients = new CopyOnWriteArrayList<>();
    private Thread mTcpServerThread;

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

    private static boolean showHelp = false; // 添加 showHelp 标志

    public static void main(String[] args) {

        System.out.println(TAG + " 已启动。");

        // 解析命令行参数
        parseArguments(args);

        // 如果用户请求帮助信息，则显示并退出
        if (showHelp) {
            printHelp();
            System.exit(0);
        }

        // 使用 InitializeAndroidEnvironment 进行初始化
        try {
            sContext = InitializeAndroidEnvironment.getSystemContext();
            System.out.println("Android 环境模拟设置完成。已通过 InitializeAndroidEnvironment 获取上下文。");
        } catch (RuntimeException e) {
            System.err.println("致命错误: 初始化 Android 环境失败: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }

        CameraServer server = new CameraServer();
        try {
            System.out.println("启动网络服务器 (只启动 TCP Socket)");
            server.startServer();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("检测到 CTRL+C（或其他关闭信号），正在清理资源...");
                server.stopRecording(); // 停止录制 (如果正在进行)
                server.stopServer(); // 停止网络服务器
                System.out.println("服务器已停止。");
            }));

            System.out.println("主线程保持运行，直到收到中断信号");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 恢复中断状态
                }
            }

        } /* 移除此 catch 块，因为 InterruptedException 已在内部处理
        catch (InterruptedException e) {
            System.err.println("服务器被中断: " + e.getMessage());
            Thread.currentThread().interrupt(); // 恢复中断状态
        }
        */ catch (Exception e) {
            System.err.println("服务器过程中发生错误: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            // server.releaseResources(); // 移除此行，清理已在 shutdown hook 中处理
            System.out.println(TAG + " 已完成。");
            System.exit(0);
        }
    }

    // --- 解析命令行参数 ---
    private static void parseArguments(String[] args) {
        Map<String, String> argMap = new HashMap<>();
        for (String arg : args) {
            String[] parts = arg.split("=", 2);
            if (parts.length == 2) {
                argMap.put(parts[0].toLowerCase(), parts[1]);
            } else if (arg.equalsIgnoreCase("--help")) {
                showHelp = true;
                return;
            } else {
                System.err.println("警告: 忽略无效的参数格式: " + arg);
            }
        }

        try {
            if (argMap.containsKey("tcp_addr")) {
                TCP_HOST = argMap.get("tcp_addr");
                System.out.println("参数: tcp_addr = " + TCP_HOST);
            }
            if (argMap.containsKey("tcp_port")) {
                TCP_PORT = Integer.parseInt(argMap.get("tcp_port"));
                System.out.println("参数: tcp_port = " + TCP_PORT);
            }
            if (argMap.containsKey("frame_rate")) {
                FRAME_RATE = Integer.parseInt(argMap.get("frame_rate"));
                System.out.println("参数: frame_rate = " + FRAME_RATE);
            }
            if (argMap.containsKey("i_frame_interval")) {
                I_FRAME_INTERVAL = Integer.parseInt(argMap.get("i_frame_interval"));
                System.out.println("参数: I_frame_interval = " + I_FRAME_INTERVAL);
            }
            if (argMap.containsKey("bit_rate")) {
                BIT_RATE = Integer.parseInt(argMap.get("bit_rate")) * BIT_RATE_MB; // 转换为 bps
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
            if (argMap.containsKey("camera_id")) {
                CAMERA_ID_TO_USE = argMap.get("camera_id");
                System.out.println("参数: camera_id = " + CAMERA_ID_TO_USE);
            }
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
            System.err.println("使用默认视频参数和网络参数。");
        }
    }

    // --- 打印帮助信息 ---
    private static void printHelp() {
        System.out.println("用法: java -jar CameraServer.jar [参数列表]");
        System.out.println("可选参数:");
        System.out.println("  --help                        : 显示此帮助信息并退出。");
        System.out.println("  frame_rate=<值>             : 设置视频帧率 (例如: 30)。默认值: " + FRAME_RATE);
        System.out.println("  i_frame_interval=<值>       : 设置 I 帧间隔 (秒)。默认值: " + I_FRAME_INTERVAL);
        System.out.println("  bit_rate=<值>               : 设置视频比特率 (例如: 2)。单位 Mbps。默认值: " + BIT_RATE + "Mbps");
        System.out.println("  size=<宽度>x<高度>          : 设置视频分辨率 (例如: 1920x1080)。默认值: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
        System.out.println("  tcp_addr=<地址>               : 设置 TCP 监听地址。默认值: " + TCP_HOST);
        System.out.println("  tcp_port=<端口号>             : 设置 TCP 监听端口。默认值: " + TCP_PORT);
        System.out.println("  camera_id=<ID>              : 指定要使用的摄像头 ID (例如: 0 或 1)。默认自动选择后置摄像头。");
        System.out.println("  codec=<类型>                : 设置视频编码器类型 (例如: avc 或 hevc)。默认值: " + (MIME_TYPE.equals(MediaFormat.MIMETYPE_VIDEO_AVC) ? "avc (H.264)" : "hevc (H.265)"));
        System.out.println("  rotate=<角度>               : 顺时针旋转视频角度 (0, 90, 180, 270)。默认值: " + ROTATE);
        System.out.println("\n示例:");
        System.out.println("  java -jar CameraServer.jar tcp_addr=" + TCP_HOST + "size=1280x720");
        System.out.println("  java -jar CameraServer.jar tcp_addr=" + TCP_HOST + "tcp_port=" + TCP_PORT + "camera_id=1 codec=hevc");
    }

    // --- 启动网络服务器 ---
    public void startServer() throws IOException {
        try {
            mServerSocket = new ServerSocket(TCP_PORT, 50, java.net.InetAddress.getByName(TCP_HOST));
            System.out.println("TCP 服务器已启动，监听 " + TCP_HOST + ":" + TCP_PORT);

            // 主线程直接处理客户端连接
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket client = mServerSocket.accept();
                    System.out.println("新的 TCP 客户端连接: " + client.getRemoteSocketAddress());
                    boolean wasEmpty = mTcpClients.isEmpty();
                    mTcpClients.add(client);
                    if (wasEmpty) {
                        System.out.println("检测到第一个客户端连接，开始录制...");
                        try {
                            startRecording();
                        } catch (Throwable t) {
                            System.err.println("startRecording() 发生异常: " + t.getClass().getName() + ": " + t.getMessage());
                            t.printStackTrace(System.err);
                        }
                    }
                } catch (IOException e) {
                    if (!mServerSocket.isClosed()) {
                        System.err.println("TCP 服务器接受连接错误: " + e.getMessage());
                    }
                } catch (Throwable t) {
                    System.err.println("TcpServer主线程未捕获异常: " + t.getClass().getName() + ": " + t.getMessage());
                    t.printStackTrace(System.err);
                }
            }
        } catch (IOException e) {
            System.err.println("无法启动 TCP 服务器: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            stopServer();
        }
    }

    // --- 停止网络服务器 ---
    public void stopServer() {
        System.out.println("正在停止网络服务器...");

        // 关闭所有 TCP 客户端连接
        for (Socket client : mTcpClients) {
            try {
                client.close();
            } catch (IOException e) {
                System.err.println("关闭 TCP 客户端连接错误: " + e.getMessage());
            }
        }
        mTcpClients.clear();
        System.out.println("所有 TCP 客户端连接已关闭。");

        // 关闭 TCP 服务器
        if (mServerSocket != null && !mServerSocket.isClosed()) {
            try {
                mServerSocket.close();
                System.out.println("TCP 服务器已关闭。");
            } catch (IOException e) {
                System.err.println("关闭 TCP 服务器错误: " + e.getMessage());
            } finally {
                mServerSocket = null;
            }
        }

        // 中断并等待服务器线程结束
        if (mTcpServerThread != null && mTcpServerThread.isAlive()) {
            mTcpServerThread.interrupt();
            try {
                mTcpServerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mTcpServerThread = null;
        }
        System.out.println("网络服务器已停止。");
    }


    // --- 开始录制 ---
    public void startRecording() {
        if (mIsRecording) {
            System.err.println("警告: 正在录制中。");
            return;
        }

        System.out.println("开始录制...");
        mIsRecording = true;

        try {
            // 1. 启动编码器线程
            mEncoderThread = new HandlerThread("MediaCodecThread");
            mEncoderThread.start();
            mEncoderHandler = new Handler(mEncoderThread.getLooper());
            System.out.println("编码器线程已启动。");

            // 2. 启动摄像头线程
            mCameraThread = new HandlerThread("CameraThread");
            mCameraThread.start();
            mCameraHandler = new Handler(mCameraThread.getLooper());
            System.out.println("摄像头线程已启动。");

            // 3. 初始化 MediaCodec 编编码器
            setupMediaCodec();
            System.out.println("MediaCodec 设置完成。");

            // 4. 打开摄像头
            openCamera();
            System.out.println("摄像头打开请求已发送。");

        } catch (IOException | CameraAccessException | InterruptedException e) {
            System.err.println("启动录制过程中发生错误: " + e.getMessage());
            e.printStackTrace(System.err);
            mIsRecording = false;
            // 尝试清理资源
            try {
                stopRecording();
            } catch (Exception ex) {
                System.err.println("停止录制时发生错误: " + ex.getMessage());
                ex.printStackTrace(System.err);
            }
        }
    }

    // --- 停止录制 ---
    public void stopRecording() {
        if (!mIsRecording) {
            System.err.println("警告: 当前未在录制中。");
            return;
        }

        System.out.println("正在停止录制...");
        mIsRecording = false;

        // 1. 关闭摄像头
        closeCamera();

        // 2. 停止编码器
        stopMediaCodec();

        // 3. 停止线程
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            try {
                mCameraThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mCameraThread = null;
        }
        if (mEncoderThread != null) {
            mEncoderThread.quitSafely();
            try {
                mEncoderThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mEncoderThread = null;
        }

        System.out.println("录制已停止。");
    }

    // --- 设置 MediaCodec 编码器 ---
    private void setupMediaCodec() throws IOException {
        System.out.println("正在设置 MediaCodec 编码器，分辨率 " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + " @ " + FRAME_RATE + "fps, " + BIT_RATE + "Mbps...");

        // 释放之前的 MediaCodec 实例（如果存在）
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }

        // 尝试选择一个支持Surface输入的编码器
        try {
            mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (Exception e) {
            System.err.println("错误: 创建 MediaCodec 编码器失败，类型为 " + MIME_TYPE + ": " + e.getMessage());
            e.printStackTrace(System.err);
            throw e;
        }

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
        // 设置编码器参数
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        format.setInteger(MediaFormat.KEY_ROTATION, ROTATE);
        System.out.println("MediaCodec 将设置 KEY_ROTATION 为: " + ROTATE);

        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); // 修正 configure 调用

        // 创建输入 Surface
        mEncoderInputSurface = mMediaCodec.createInputSurface();
        System.out.println("MediaCodec 输入 Surface 创建成功。");

        // 设置 MediaCodec 异步回调
        mMediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {}

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                if (outputBuffer != null) {
                    sendDataToClients(outputBuffer, info);
                }
                codec.releaseOutputBuffer(index, false);
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                System.err.println("致命错误: MediaCodec 错误: " + e.getMessage());
                e.printStackTrace(System.err);
                stopRecording();
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                System.out.println("MediaCodec 输出格式已更改: " + format);
            }
        }, mEncoderHandler); // <--- 用编码线程的 Handler


        // 启动 MediaCodec
        mMediaCodec.start();
        System.out.println("MediaCodec 已启动。");
    }

    // --- 停止 MediaCodec ---
    private void stopMediaCodec() {
        if (mMediaCodec != null) {
            try {
                mMediaCodec.stop();
                mMediaCodec.release();
                System.out.println("MediaCodec 已停止并释放。");
            } catch (Exception e) {
                System.err.println("停止 MediaCodec 时发生错误: " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                mMediaCodec = null;
            }
        }
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
            Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            if (fpsRanges != null) {
                System.out.println("    支持的帧率范围:");
                for (Range<Integer> range : fpsRanges) {
                    System.out.println("      - " + range);
                }
            }

            boolean size_bool = false;
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Size[] videoSizes = map.getOutputSizes(MediaCodec.class);
                if (videoSizes == null || videoSizes.length == 0) { // 修正检查条件
                    System.out.println("    此摄像头没有支持 MediaCodec 的输出尺寸。");
                    continue; // 跳过此摄像头
                }

                for (Size size : videoSizes) {
                    if (size.getWidth() == VIDEO_WIDTH && size.getHeight() == VIDEO_HEIGHT){
                        System.out.println("    设置size:" + size.getWidth() + "x" + size.getHeight());
                        size_bool = true;
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
                continue; // 跳过此摄像头
            }


            // 如果指定了 camera_id，则优先使用指定的摄像头
            if (CAMERA_ID_TO_USE != null && CAMERA_ID_TO_USE.equals(id)) {
                selectedCameraId = id;
                System.out.println("    --> 这是 camera_id 指定的摄像头: " + CAMERA_ID_TO_USE);
                // 检查指定摄像头是否支持所需分辨率
                if (map != null) {
                    Size[] sizes = map.getOutputSizes(MediaCodec.class);
                    boolean resolutionSupported = false;
                    if (sizes != null) {
                        for (Size s : sizes) {
                            if (s.getWidth() == VIDEO_WIDTH && s.getHeight() == VIDEO_HEIGHT) {
                                resolutionSupported = true;
                                break;
                            }
                        }
                    }
                    if (!resolutionSupported) {
                        System.err.println("错误: 指定的摄像头 " + CAMERA_ID_TO_USE + " 不支持 " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + " 用于 MediaCodec。");
                        throw new RuntimeException("指定的摄像头 " + CAMERA_ID_TO_USE + " 不支持所需分辨率 " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
                    }
                } else {
                     System.err.println("错误: 指定的摄像头 " + CAMERA_ID_TO_USE + " 没有 StreamConfigurationMap。");
                     throw new RuntimeException("指定的摄像头 " + CAMERA_ID_TO_USE + " 没有 StreamConfigurationMap。");
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

    // --- 关闭摄像头 ---
    private void closeCamera() {
        if (mCameraDevice != null) {
            try {
                mCameraDevice.close();
                System.out.println("摄像头已关闭: " + (mCameraDevice != null ? mCameraDevice.getId() : "N/A")); // 使用 mCameraDevice.getId()
            } catch (Exception e) {
                System.err.println("关闭摄像头时发生错误: " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                mCameraDevice = null;
            }
        }
    }


    // --- 摄像头状态回调 ---
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            System.out.println("摄像头 " + cameraDevice.getId() + " 已打开。");
            createCameraPreviewSession(); // 摄像头打开后创建捕获会话
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            System.err.println("警告: 摄像头已断开连接。");
            stopRecording(); // 摄像头断开时停止录制
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            System.err.println("错误: 摄像头错误: " + error);
            stopRecording(); // 摄像头错误时停止录制
        }
    };

    // --- 创建摄像头捕获会话 (用于录制到 Surface) ---
    private void createCameraPreviewSession() {
        if (mCameraDevice == null || mEncoderInputSurface == null || mCameraHandler == null) {
            System.err.println("错误: CameraDevice、编码器输入 Surface 或摄像头 Handler 为空，无法创建捕获会话。");
            stopRecording();
            return;
        }

        try { // try block for createCaptureSession
            final CaptureRequest.Builder captureRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD); // 使用 TEMPLATE_RECORD

            captureRequestBuilder.addTarget(mEncoderInputSurface);

            // 配置自动对焦和曝光等（使用 CaptureRequest 常量）
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO); // 修正 CameraMetadata
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(FRAME_RATE, FRAME_RATE)); // 使用导入的 Range
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);


            // 使用新版API（Executor）创建会话，消除废弃警告
            // 注意：在模拟环境中，Executor 可能无法完全模拟 Android 的行为
            // 如果遇到问题，可以考虑回退到 Handler 版本
            mCameraDevice.createCaptureSession(
                Collections.singletonList(mEncoderInputSurface),
                new CameraCaptureSession.StateCallback() { // StateCallback anonymous class
                    @Override
                    public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                        if (mCameraDevice == null) {
                            return;
                        }
                        mCaptureSession = cameraCaptureSession;
                        System.out.println("CameraCaptureSession 已配置。");

                        try { // try block for setRepeatingRequest
                            mCaptureSession.setRepeatingRequest(
                                captureRequestBuilder.build(),
                                new CameraCaptureSession.CaptureCallback() { // CaptureCallback anonymous class
                                    @Override
                                    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                        // 处理每帧图像（如果需要），例如获取时间戳等
                                        // System.out.println("捕获完成，时间戳: " + result.get(CaptureResult.SENSOR_TIMESTAMP));
                                    } // <-- Closing brace for onCaptureCompleted
                                }, // <-- Closing brace for CaptureCallback anonymous class
                                mCameraHandler // 在模拟环境中，使用 Handler 可能更稳定
                            );
                            System.out.println("摄像头 setRepeatingRequest (录制) 已启动。");
                        } catch (CameraAccessException e) { // <-- Catch block for try at 591
                            System.err.println("错误: 启动摄像头录制请求失败: " + e.getMessage());
                            e.printStackTrace(System.err);
                            stopRecording();
                        } // <-- End catch block
                    } // <-- Closing brace for onConfigured

                    @Override
                    public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                        System.err.println("错误: 配置摄像头捕获会话失败。");
                        stopRecording();
                    } // <-- Closing brace for onConfigureFailed
                }, // <-- Closing brace for StateCallback anonymous class
                mCameraHandler // 只能用 Handler
                // Executors.newSingleThreadExecutor() // 尝试使用 Executor (Java 21+)
            ); // <-- Closing parenthesis and semicolon for createCaptureSession call

        } catch (CameraAccessException e) { // <-- Catch block for try at 575
            System.err.println("错误: 创建摄像头捕获会话失败: " + e.getMessage());
            e.printStackTrace(System.err);
            stopRecording();
        } // <-- End catch block
    } // <-- Closing brace for createCameraPreviewSession


    // --- 将编码后的数据发送给所有连接的客户端 ---
    private void sendDataToClients(ByteBuffer buffer, MediaCodec.BufferInfo info) {
        if (!mIsRecording) return;
        if (info.size <= 0) return;

        // 避免频繁分配大数组
        byte[] data = new byte[info.size];
        synchronized (buffer) {
            buffer.position(info.offset);
            buffer.limit(info.offset + info.size);
            buffer.get(data);
        }

        // 转换 AVCC 格式为 Annex B 格式
        // 判断是否已是Annex B格式（0x00000001或0x000001开头）
        boolean isAnnexB = (data.length >= 4 && data[0] == 0x00 && data[1] == 0x00 &&
                   ((data[2] == 0x00 && data[3] == 0x01) || data[2] == 0x01));
        byte[] annexb = isAnnexB ? data : avccToAnnexB(data);

        // 构造包头：type(2字节) + data_len(4字节) + pts(8字节) + data
        int type = 1; // 1=视频帧
        int dataLen = data.length;
        byte[] header = new byte[14];
        long pts = info.presentationTimeUs; // 获取时间戳

        // type: 2字节网络字节序
        header[0] = (byte) ((type >> 8) & 0xFF);
        header[1] = (byte) (type & 0xFF);
        // data_len: 4字节网络字节序
        header[2] = (byte) ((dataLen >> 24) & 0xFF);
        header[3] = (byte) ((dataLen >> 16) & 0xFF);
        header[4] = (byte) ((dataLen >> 8) & 0xFF);
        header[5] = (byte) (dataLen & 0xFF);
        // pts: 8字节网络字节序（高位在前）
        header[6]  = (byte) ((pts >> 56) & 0xFF);
        header[7]  = (byte) ((pts >> 48) & 0xFF);
        header[8]  = (byte) ((pts >> 40) & 0xFF);
        header[9]  = (byte) ((pts >> 32) & 0xFF);
        header[10] = (byte) ((pts >> 24) & 0xFF);
        header[11] = (byte) ((pts >> 16) & 0xFF);
        header[12] = (byte) ((pts >> 8) & 0xFF);
        header[13] = (byte) (pts & 0xFF);

        for (Socket client : mTcpClients) {
            try {
                OutputStream out = client.getOutputStream();
                out.write(header);
                out.write(annexb);
                out.flush();
            } catch (IOException e) {
                System.err.println("发送数据到 TCP 客户端失败，断开连接: " + e.getMessage());
                try {
                    client.close();
                } catch (IOException closeException) {
                    System.err.println("关闭 TCP 客户端连接错误: " + closeException.getMessage());
                }
                mTcpClients.remove(client);
                System.out.println("客户端断开连接。当前连接数: " + mTcpClients.size());
                if (mTcpClients.isEmpty()) {
                    System.out.println("所有客户端已断开连接，停止录制...");
                    stopRecording();
                }
            }
        }
    }

    // 将 AVCC 格式（长度前缀）转为 Annex B（起始码）
    private static byte[] avccToAnnexB(byte[] avcc) {
        ByteBuffer buf = ByteBuffer.wrap(avcc);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (buf.remaining() > 4) {
            int len = buf.getInt();
            // 检查长度合法性
            if (len <= 0 || len > buf.remaining()) {
                // 非法长度，跳出或跳过
                System.err.println("警告: AVCC NALU 长度非法: " + len + ", 剩余: " + buf.remaining());
                break;
            }
            out.write(0x00);
            out.write(0x00);
            out.write(0x00);
            out.write(0x01);
            byte[] nalu = new byte[len];
            buf.get(nalu);
            out.write(nalu, 0, len);
        }
        return out.toByteArray();
    }
}