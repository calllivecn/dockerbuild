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
    private String mCameraDeviceId; // 保存摄像头 ID
    private CameraCaptureSession mCaptureSession;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1); // 防止相机并发访问
    private Executor mExecutor; // 用于 SessionConfiguration

    // --- MediaCodec 相关 ---
    private MediaCodec mMediaCodec;
    private Surface mEncoderInputSurface; // 连接到MediaCodec的输入Surface
    private HandlerThread mEncoderThread;
    private Handler mEncoderHandler;
    private boolean mIsRecording = false;
    // 用来保存SPS/PPS数据。
    private byte[] mConfigData = null;
    private int mConfigData_len = 0;

    private static boolean showHelp = false; // 添加 showHelp 标志

    private byte[] vps = null, sps = null, pps = null;

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
        System.out.println("  java -jar CameraServer.jar tcp_addr=" + TCP_HOST + " size=1280x720");
        System.out.println("  java -jar CameraServer.jar tcp_addr=" + TCP_HOST + " tcp_port=" + TCP_PORT + " camera_id=1 codec=hevc");
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
            // 1. 启动编码器线程（必须在 setupMediaCodec 之前）
            // 因为 MediaCodec 回调需要 mEncoderHandler
            mEncoderThread = new HandlerThread("MediaCodecThread");
            mEncoderThread.start();
            mEncoderHandler = new Handler(mEncoderThread.getLooper());
            System.out.println("编码器线程已启动。");

            // 2. 启动摄像头线程
            mCameraThread = new HandlerThread("CameraThread");
            mCameraThread.start();
            mCameraHandler = new Handler(mCameraThread.getLooper());
            System.out.println("摄像头线程已启动。");

            // 创建 Executor 用于摄像头会话
            mExecutor = Executors.newSingleThreadExecutor();

            // 3. 初始化 MediaCodec 编码器
            // 生命周期: configure() → createInputSurface() → start()
            // 必须在摄像头打开前完成，因为 createCaptureSession 需要 mEncoderInputSurface
            setupMediaCodec();
            System.out.println("MediaCodec 设置完成，mEncoderInputSurface 已就绪。");

            // 4. 打开摄像头（异步，openCamera 返回后摄像头可能还未真正打开）
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
        System.out.println("正在设置 MediaCodec 编码器，分辨率 " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + " @ " + FRAME_RATE + "fps, " + BIT_RATE + "bps...");

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
        System.out.println("MediaCodec 配置: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + " @ " + FRAME_RATE + "fps, bitrate=" + BIT_RATE);

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
                // 输出实际的编码参数
                System.out.println("=== MediaCodec onOutputFormatChanged 被调用 ===");
                int actualWidth = 0, actualHeight = 0, actualFPS = 0;
                if (format.containsKey("width")) {
                    actualWidth = format.getInteger("width");
                    actualHeight = format.getInteger("height");
                    actualFPS = format.getInteger("frame-rate", FRAME_RATE);
                    System.out.println("✓ MediaCodec 实际输出格式: " + actualWidth + "x" + actualHeight + " @ " + actualFPS + "fps");
                    
                    if (actualWidth != VIDEO_WIDTH || actualHeight != VIDEO_HEIGHT) {
                        System.err.println("⚠️ 分辨率不匹配!");
                        System.err.println("  配置: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
                        System.err.println("  实际: " + actualWidth + "x" + actualHeight);
                        System.err.println("  原因: 摄像头输出的分辨率可能不同");
                    }
                    if (actualFPS != FRAME_RATE) {
                        System.err.println("⚠️ 帧率不匹配!");
                        System.err.println("  配置: " + FRAME_RATE + " fps");
                        System.err.println("  实际: " + actualFPS + " fps");
                    }
                } else {
                    System.out.println("⚠ format 中没有 width/height 信息");
                }
                
                if (format.containsKey("csd-0")) vps = getBytesFromBuffer(format.getByteBuffer("csd-0"));
                if (format.containsKey("csd-1")) sps = getBytesFromBuffer(format.getByteBuffer("csd-1"));
                if (format.containsKey("csd-2")) pps = getBytesFromBuffer(format.getByteBuffer("csd-2"));
                System.out.println("保存参数集: vps=" + (vps != null) + ", sps=" + (sps != null) + ", pps=" + (pps != null));
            }

            private static byte[] getBytesFromBuffer(ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                buffer.rewind();
                return bytes;
            }
        }, mEncoderHandler); // <--- 用编码线程的 Handler


        // 启动 MediaCodec
        mMediaCodec.start();
        System.out.println("MediaCodec 已启动。");
    }

    // --- 检查摄像头支持的分辨率，如需要则自动调整 ---
    private void checkCameraResolution() throws CameraAccessException {
        System.out.println("检查摄像头支持的分辨率...");
        CameraManager manager = (CameraManager) sContext.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("CameraManager 服务不可用。");
        }

        String selectedCameraId = null;
        
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

            // 如果指定了 camera_id，则检查该摄像头
            if (CAMERA_ID_TO_USE != null && CAMERA_ID_TO_USE.equals(id)) {
                selectedCameraId = id;
            }
            // 否则自动选择后置摄像头
            else if (CAMERA_ID_TO_USE == null && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                selectedCameraId = id;
            }

            if (selectedCameraId != null) {
                break;
            }
        }

        if (selectedCameraId == null) {
            System.err.println("未找到合适的摄像头");
            return;
        }

        // 检查该摄像头支持的分辨率
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(selectedCameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        
        if (map == null) {
            System.err.println("摄像头 " + selectedCameraId + " 没有 StreamConfigurationMap");
            return;
        }

        Size[] videoSizes = map.getOutputSizes(MediaCodec.class);
        if (videoSizes == null || videoSizes.length == 0) {
            System.err.println("摄像头 " + selectedCameraId + " 没有支持 MediaCodec 的输出尺寸");
            return;
        }

        System.out.println("摄像头 " + selectedCameraId + " 支持的分辨率 (" + MIME_TYPE + "):");
        for (Size size : videoSizes) {
            System.out.println("  - " + size.getWidth() + "x" + size.getHeight());
        }

        // 检查是否支持指定的分辨率
        boolean foundMatch = false;
        for (Size size : videoSizes) {
            if (size.getWidth() == VIDEO_WIDTH && size.getHeight() == VIDEO_HEIGHT) {
                foundMatch = true;
                System.out.println("✓ 支持指定的分辨率: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
                break;
            }
        }

        // 如果不支持，选择最大的支持分辨率
        if (!foundMatch) {
            System.out.println("✗ 不支持指定的分辨率 " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
            
            Size maxSize = videoSizes[0];
            int maxPixels = maxSize.getWidth() * maxSize.getHeight();
            
            for (Size size : videoSizes) {
                int pixels = size.getWidth() * size.getHeight();
                if (pixels > maxPixels) {
                    maxPixels = pixels;
                    maxSize = size;
                }
            }
            
            VIDEO_WIDTH = maxSize.getWidth();
            VIDEO_HEIGHT = maxSize.getHeight();
            System.out.println("已自动调整为最大支持分辨率: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
        }
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

            // 简单检查该摄像头是否支持所需分辨率
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                System.out.println("    此摄像头没有 StreamConfigurationMap，跳过。");
                continue;
            }

            Size[] videoSizes = map.getOutputSizes(MediaCodec.class);
            if (videoSizes == null || videoSizes.length == 0) {
                System.out.println("    此摄像头没有支持 MediaCodec 的输出尺寸，跳过。");
                continue;
            }

            // 检查是否支持当前分辨率
            boolean supportsResolution = false;
            for (Size size : videoSizes) {
                if (size.getWidth() == VIDEO_WIDTH && size.getHeight() == VIDEO_HEIGHT) {
                    supportsResolution = true;
                    break;
                }
            }

            // 如果指定了 camera_id，则优先使用指定的摄像头
            if (CAMERA_ID_TO_USE != null && CAMERA_ID_TO_USE.equals(id)) {
                selectedCameraId = id;
                System.out.println("    --> 使用指定的摄像头: " + CAMERA_ID_TO_USE);
                if (!supportsResolution) {
                    System.err.println("警告: 指定的摄像头 " + CAMERA_ID_TO_USE + " 不支持分辨率 " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
                }
                break;
            }
            // 如果没有指定 camera_id，则选择支持所需分辨率的后置摄像头
            else if (CAMERA_ID_TO_USE == null && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                if (supportsResolution) {
                    selectedCameraId = id;
                    System.out.println("    --> 选择此后置摄像头: " + id);
                    break;
                }
            }
        }

        if (selectedCameraId == null) {
            System.err.println("未找到合适的摄像头，将使用第一个可用的摄像头。");
            String[] cameraIds = manager.getCameraIdList();
            if (cameraIds.length > 0) {
                selectedCameraId = cameraIds[0];
                System.out.println("使用摄像头: " + selectedCameraId);
            } else {
                throw new RuntimeException("没有可用的摄像头");
            }
        }

        // 检查选定的摄像头是否支持当前分辨率，如不支持则调整
        CameraCharacteristics selectedCharacteristics = manager.getCameraCharacteristics(selectedCameraId);
        StreamConfigurationMap selectedMap = selectedCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        
        System.out.println("检查摄像头 " + selectedCameraId + " 的分辨率支持...");
        
        if (selectedMap == null) {
            System.err.println("错误: selectedMap 为 null，无法检查分辨率");
        } else {
            Size[] supportedSizes = selectedMap.getOutputSizes(MediaCodec.class);
            System.out.println("supportedSizes: " + (supportedSizes == null ? "null" : "长度=" + supportedSizes.length));
            
            if (supportedSizes != null) {
                System.out.println("摄像头 " + selectedCameraId + " 通过 MediaCodec.class 支持的分辨率:");
                for (Size s : supportedSizes) {
                    System.out.println("  - " + s.getWidth() + "x" + s.getHeight());
                }
                
                // 关键诊断：检查所有输出格式的支持的大小
                System.out.println("\n摄像头 " + selectedCameraId + " 的所有输出格式及其支持的分辨率:");
                int[] outputFormats = selectedMap.getOutputFormats();
                if (outputFormats != null) {
                    for (int format : outputFormats) {
                        Size[] sizes = selectedMap.getOutputSizes(format);
                        System.out.println("  格式 0x" + Integer.toHexString(format) + " 支持 " + (sizes != null ? sizes.length : 0) + " 种分辨率");
                        if (sizes != null && sizes.length > 0) {
                            // 只显示前5个和最大的
                            int maxSize = Math.min(5, sizes.length);
                            for (int i = 0; i < maxSize; i++) {
                                System.out.println("    [" + i + "] " + sizes[i].getWidth() + "x" + sizes[i].getHeight());
                            }
                            if (sizes.length > 5) {
                                System.out.println("    ... (更多 " + (sizes.length - 5) + " 个)");
                            }
                            // 显示最大的
                            Size maxRes = sizes[sizes.length - 1];
                            System.out.println("    最大: " + maxRes.getWidth() + "x" + maxRes.getHeight());
                        }
                    }
                }
                
                boolean currentResolutionSupported = false;
                for (Size s : supportedSizes) {
                    if (s.getWidth() == VIDEO_WIDTH && s.getHeight() == VIDEO_HEIGHT) {
                        currentResolutionSupported = true;
                        break;
                    }
                }
                
                System.out.println("\n目标配置分辨率: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + 
                    (currentResolutionSupported ? " (✓ 摄像头支持)" : " (❌ 摄像头不支持，会被降低)"));
            }
        }

        // 请求打开摄像头
        mCameraOpenCloseLock.acquire(); // 获取信号量，防止多次打开
        mCameraDeviceId = selectedCameraId; // 保存摄像头 ID
        manager.openCamera(selectedCameraId, mStateCallback, mCameraHandler);
        System.out.println("已请求打开摄像头: " + selectedCameraId + " (分辨率: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + ")");
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
        // 执行前的检查和日志
        System.out.println("createCameraPreviewSession() 被调用");
        System.out.println("  mCameraDevice: " + (mCameraDevice != null ? "✓" : "✗ null"));
        System.out.println("  mEncoderInputSurface: " + (mEncoderInputSurface != null ? "✓" : "✗ null"));
        System.out.println("  mCameraHandler: " + (mCameraHandler != null ? "✓" : "✗ null"));
        
        if (mCameraDevice == null || mEncoderInputSurface == null || mCameraHandler == null) {
            System.err.println("错误: CameraDevice、编码器输入 Surface 或摄像头 Handler 为空，无法创建捕获会话。");
            stopRecording();
            return;
        }

        // 关键：检查摄像头是否真的支持指定的分辨率
        // 如果不支持，从支持列表中选择最接近的分辨率
        String actualResolution = VIDEO_WIDTH + "x" + VIDEO_HEIGHT;
        try {
            CameraManager manager = (CameraManager) sContext.getSystemService(Context.CAMERA_SERVICE);
            if (manager != null && mCameraDeviceId != null) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDeviceId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null) {
                    Size[] supportedSizes = map.getOutputSizes(MediaCodec.class);
                    if (supportedSizes != null && supportedSizes.length > 0) {
                        // 检查请求的分辨率是否在支持列表中
                        boolean found = false;
                        for (Size s : supportedSizes) {
                            if (s.getWidth() == VIDEO_WIDTH && s.getHeight() == VIDEO_HEIGHT) {
                                found = true;
                                break;
                            }
                        }
                        
                        if (!found) {
                            // 如果不支持，选择最接近的分辨率
                            int targetPixels = VIDEO_WIDTH * VIDEO_HEIGHT;
                            Size bestMatch = supportedSizes[0];
                            int bestDiff = Math.abs(bestMatch.getWidth() * bestMatch.getHeight() - targetPixels);
                            
                            for (Size s : supportedSizes) {
                                int pixelDiff = Math.abs(s.getWidth() * s.getHeight() - targetPixels);
                                if (pixelDiff < bestDiff) {
                                    bestDiff = pixelDiff;
                                    bestMatch = s;
                                }
                            }
                            
                            System.err.println("⚠️ 警告: 摄像头不支持 " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + "!");
                            System.err.println("⚠️ 自动调整为最接近的支持分辨率: " + bestMatch.getWidth() + "x" + bestMatch.getHeight());
                            actualResolution = bestMatch.getWidth() + "x" + bestMatch.getHeight();
                        }
                    }
                }
            }
        } catch (CameraAccessException e) {
            System.err.println("检查摄像头分辨率支持时出错: " + e.getMessage());
        }

        try { // try block for createCaptureSession
            final CaptureRequest.Builder captureRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD); // 使用 TEMPLATE_RECORD

            captureRequestBuilder.addTarget(mEncoderInputSurface);

            // 配置自动对焦和曝光等（使用 CaptureRequest 常量）
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO); // 修正 CameraMetadata
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            // 对于录制，通常还需要设置 AE 模式以确保 FPS 稳定
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(FRAME_RATE, FRAME_RATE)); // 使用导入的 Range
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            
            // 关键：设置摄像头帧率（纳秒）
            long frameDurationNs = (long)(1_000_000_000.0 / FRAME_RATE);
            captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDurationNs);
            System.out.println("摄像头帧间隔设置为: " + frameDurationNs + " ns (对应 " + FRAME_RATE + " fps)");
            
            // 关键：获取摄像头的 SENSOR 尺寸并设置 SCALER_CROP_REGION
            if (mCameraDeviceId != null) {
                try {
                    CameraManager manager = (CameraManager) sContext.getSystemService(Context.CAMERA_SERVICE);
                    if (manager != null) {
                        CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDeviceId);
                        android.graphics.Rect sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                        if (sensorRect != null) {
                            System.out.println("SENSOR 活跃区域: " + sensorRect.width() + "x" + sensorRect.height());
                            // 设置 crop region 为整个 sensor
                            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, sensorRect);
                        }
                    }
                } catch (CameraAccessException e) {
                    System.err.println("获取摄像头特性失败: " + e.getMessage());
                }
            }




            // 使用新版API（Executor）创建会话，消除废弃警告
            // 关键：使用 OutputConfiguration 指定摄像头输出的分辨率
            android.hardware.camera2.params.OutputConfiguration outputConfig = 
                new android.hardware.camera2.params.OutputConfiguration(mEncoderInputSurface);
            
            // 尝试通过 setStreamUseCase (API 33+) 或其他方式约束分辨率
            // 但最直接的方法：在 CaptureRequest 中设置 SCALER_CROP_REGION 或通过 StreamConfigurationMap
            try {
                // API 33+ 可用，尝试设置流的使用场景
                // outputConfig.setStreamUseCase(CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT);
                System.out.println("OutputConfiguration 已创建");
                System.out.println("  目标输出分辨率: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
                System.out.println("  注意: 摄像头实际输出受 StreamConfigurationMap 和 MediaCodec 配置影响");
            } catch (Exception e) {
                System.err.println("配置OutputConfiguration时出错: " + e.getMessage());
            }
            
            // 创建 SessionConfiguration
            android.hardware.camera2.params.SessionConfiguration sessionConfig = 
                new android.hardware.camera2.params.SessionConfiguration(
                    android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                    Collections.singletonList(outputConfig),
                    mExecutor,
                    new CameraCaptureSession.StateCallback() { // StateCallback anonymous class
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            if (mCameraDevice == null) {
                                return;
                            }
                            mCaptureSession = cameraCaptureSession;
                            System.out.println("CameraCaptureSession 已配置 (" + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + ")。");

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
                    }
                );
            
            // 使用 SessionConfiguration 创建捕获会话
            mCameraDevice.createCaptureSession(sessionConfig);
            System.out.println("摄像头捕获会话创建请求已发送 (分辨率: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + ")。");

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

        // 第一次触发时：检测到配置标记，存入成员变量
        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            mConfigData_len = info.size;
            mConfigData = new byte[info.size];
            buffer.get(mConfigData);
            // 存储后，这次回调就结束了，mConfigData 现在已经有值了
        } 

        // 避免频繁分配大数组
        byte[] data = new byte[info.size];
        synchronized (buffer) {
            buffer.position(info.offset);
            buffer.limit(info.offset + info.size);
            buffer.get(data);
        }

        // 如果是 AVCC 格式 转换为 Annex B 格式
        boolean isAnnexB = (data.length >= 4 && data[0] == 0x00 && data[1] == 0x00 && ((data[2] == 0x00 && data[3] == 0x01) || data[2] == 0x01));
        byte[] annexb = isAnnexB ? data : avccToAnnexB(data);

        long pts = info.presentationTimeUs; // 获取时间戳

        // 检查是否是关键帧
        boolean isKeyFrame = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;


        // 构造视频帧包头：type(2字节) + data_len(4字节) + pts(8字节) + data
        short videoType = isKeyFrame ? (short)100 : (short)1; // 100=关键视频帧, 1=普通视频帧
        int videoDataLen = annexb.length;


        int header_size = 14;
        // 如果是关键帧, 在前添加上SPS/PPS字节。~~先发送参数集 (type=101)~~
        if (isKeyFrame) {
            videoDataLen += mConfigData_len;
            header_size += mConfigData_len;
        }

        // 构造视频帧包头
        // type(2) + data_len(4) + pts(8) + [可选添加上 SPS/PPS字节]
        ByteBuffer header = ByteBuffer.allocate(header_size);
        header.putShort(videoType);
        header.putInt(videoDataLen);
        header.putLong(pts);

        if (isKeyFrame) {
            header.put(mConfigData);
        }

        // 发送视频帧包
        sendPacketToClients(header.array(), annexb);

        // System.out.println("发送视频帧 (type=" + videoType + "), size=" + videoDataLen); // 调试输出
    }

    /*

    // --- 将 int 转换为指定长度的网络字节序 (大端) 字节数组 ---
    private static byte[] intToBytes(int value, int numBytes) {
        byte[] bytes = new byte[numBytes];
        for (int i = 0; i < numBytes; i++) {
            bytes[i] = (byte) ((value >> ((numBytes - 1 - i) * 8)) & 0xFF);
        }
        return bytes;
    }

    // --- 将 long 转换为指定长度的网络字节序 (大端) 字节数组 ---
    private static byte[] longToBytes(long value, int numBytes) {
        byte[] bytes = new byte[numBytes];
        for (int i = 0; i < numBytes; i++) {
            bytes[i] = (byte) ((value >> ((numBytes - 1 - i) * 8)) & 0xFF);
        }
        return bytes;
    }

    */

    // --- 发送数据包 (header + data) 到所有连接的客户端 ---
    private void sendPacketToClients(byte[] header, byte[] data) {
        for (Socket client : mTcpClients) {
            try {
                OutputStream out = client.getOutputStream();
                out.write(header);
                out.write(data);
                out.flush(); // 发送完一个包后立即 flush
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

    /*
    // --- 发送参数集 (VPS/SPS/PPS) 到客户端 (type=101) ---
    private void sendParameterSet(long pts) {
        if (vps != null && sps != null && pps != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                baos.write(vps);
                baos.write(sps);
                baos.write(pps);
                byte[] paramData = baos.toByteArray();

                int paramType = 101; // 101 表示参数集 (VPS/SPS/PPS)
                int paramDataLen = paramData.length;
                byte[] paramHeader = new byte[14]; // type(2) + data_len(4) + pts(8)

                // 构造参数集包头
                System.arraycopy(intToBytes(paramType, 2), 0, paramHeader, 0, 2);
                System.arraycopy(intToBytes(paramDataLen, 4), 0, paramHeader, 2, 4);
                // 参数集的 PTS 可以使用关键帧的 PTS，或者设置为 0
                System.arraycopy(longToBytes(pts, 8), 0, paramHeader, 6, 8);

                // 发送参数集包
                sendPacketToClients(paramHeader, paramData);

                System.out.println("发送参数集 (type=101), size=" + paramDataLen); // 调试输出

            } catch (IOException e) {
                System.err.println("构造参数集数据时发生错误: " + e.getMessage());
            }
        }
    }
    */

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
