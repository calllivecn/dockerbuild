// 这个是测试成功的 (请注意，这是一个复杂且依赖于 Android 内部 API 的示例，可能需要 root 权限)
// 此版本添加了通过 TCP 套接字发送编码后视频数据的功能，并在客户端连接/断开时控制录制。

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult; // 导入 TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
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
import java.nio.ByteOrder;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi", "InternalInsetResource", "DiscouragedApi"})
public final class CameraServer {

    private static final String TAG = "CameraServer";

    // --- 默认 MediaCodec 参数 ---
    private static boolean ENABLE_VIDEO = true; // 是否录制视频，默认启用
    private static String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC; // H.264 AVC，默认编码器
    private static int FPS = 30; // 帧率
    private static int I_FRAME_INTERVAL = 1; // I帧间隔 (秒)
    private static int BIT_RATE_MB = 1000000;
    private static int BIT_RATE = 2*BIT_RATE_MB; // 比特率 (2 Mbps)
    private static int VIDEO_WIDTH = 1280; // 视频宽度
    private static int VIDEO_HEIGHT = 720; // 视频高度
    private static int ROTATE = 0; // 新增：旋转角度，默认0度

    // --- 音频参数 ---
    private static boolean ENABLE_AUDIO = true; // 是否录制音频，默认启用
    private static int AUDIO_SAMPLE_RATE = 44100; // 采样率
    private static int AUDIO_CHANNELS = 2; // 立体声
    private static int AUDIO_BIT_RATE = 128000; // 128 kbps

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
    private static String mCameraDeviceId; // 保存摄像头 ID
    private CameraCaptureSession mCaptureSession;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1); // 防止相机并发访问
    private Executor mExecutor; // 用于 SessionConfiguration

    // TCP packet type
    private Short VideoKeyframe=100;
    private Short VideoNormal=1;
    private Short VideoConfig=101;
    private Short AudioData=2;
    private Short AudioConfig=201;

    // --- MediaCodec 相关 (视频) ---
    private MediaCodec mMediaCodec;
    private Surface mEncoderInputSurface; // 连接到MediaCodec的输入Surface
    private HandlerThread mEncoderThread;
    private Handler mEncoderHandler;
    private boolean mIsRecording = false;
    // 用来保存SPS/PPS数据。
    private byte[] mConfigData = null;
    private int mConfigData_len = 0;

    // --- MediaCodec 相关 (音频) ---
    private MediaCodec mAudioCodec;
    private HandlerThread mAudioThread;
    private Handler mAudioHandler;
    private AudioRecord mAudioRecord;
    private boolean mIsAudioRecording = false;
    private byte[] mAudioConfigData = null;  // 保存ADTS配置数据，每个音频帧都会附加
    private int mAudioConfigData_len = 0;
    private BlockingQueue<byte[]> mAudioDataQueue = new LinkedBlockingQueue<>(300); // 改用 BlockingQueue

    // --- TCP发送队列 ---
    private BlockingQueue<TcpPacket> mTcpSendQueue = new LinkedBlockingQueue<>(1000);
    private Thread mTcpSendThread;

    private static boolean showHelp = false; // 添加 showHelp 标志

    // TCP数据包类
    static class TcpPacket {
        public byte[] header;
        public byte[] data;
        
        public TcpPacket(byte[] header, byte[] data) {
            this.header = header;
            this.data = data;
        }
    }

    public static void main(String[] args) {

        System.out.println(TAG + " 已启动。");

        // 解析命令行参数
        parseArguments(args);

        // 如果用户请求帮助信息，则显示并退出
        if (showHelp) {
            printHelp();
            System.exit(0);
        }

        // 检查是否同时禁用了音频和视频录制
        if (!ENABLE_VIDEO && !ENABLE_AUDIO) {
            System.out.println("错误: 视频和音频不能同时都禁用。请至少启用其中一个。");
            printHelp();
            System.exit(1);
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
            if (argMap.containsKey("fps")) {
                FPS = Integer.parseInt(argMap.get("fps"));
                System.out.println("参数: fps = " + FPS);
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
                mCameraDeviceId = argMap.get("camera_id");
                System.out.println("参数: camera_id = " + mCameraDeviceId);
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
            // 视频参数
            if (argMap.containsKey("enable_video")) {
                String enableVideo = argMap.get("enable_video").toLowerCase();
                if (enableVideo.equals("false") || enableVideo.equals("0")) {
                    ENABLE_VIDEO = false;
                    System.out.println("参数: 已禁用视频录制");
                } else {
                    ENABLE_VIDEO = true;
                    System.out.println("参数: 已启用视频录制");
                }
            }
            // 音频参数
            if (argMap.containsKey("enable_audio")) {
                String enableAudio = argMap.get("enable_audio").toLowerCase();
                if (enableAudio.equals("false") || enableAudio.equals("0")) {
                    ENABLE_AUDIO = false;
                    System.out.println("参数: 已禁用音频录制");
                } else {
                    ENABLE_AUDIO = true;
                    System.out.println("参数: 已启用音频录制");
                }
            }
            
            if (argMap.containsKey("audio_sample_rate")) {
                AUDIO_SAMPLE_RATE = Integer.parseInt(argMap.get("audio_sample_rate"));
                System.out.println("参数: audio_sample_rate = " + AUDIO_SAMPLE_RATE + " Hz");
            }
            if (argMap.containsKey("audio_channels")) {
                AUDIO_CHANNELS = Integer.parseInt(argMap.get("audio_channels"));
                System.out.println("参数: audio_channels = " + AUDIO_CHANNELS);
            }
            if (argMap.containsKey("audio_bit_rate")) {
                AUDIO_BIT_RATE = Integer.parseInt(argMap.get("audio_bit_rate")) * 1000; // 转换为 bps
                System.out.println("参数: audio_bit_rate = " + AUDIO_BIT_RATE);
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
        System.out.println("  enable_video=<true|false>   : 启用或禁用视频录制 (默认: true)。");
        System.out.println("  enable_audio=<true|false>   : 启用或禁用音频录制 (默认: true)。");
        System.out.println("  fps=<值>                     : 设置视频帧率 (例如: 30)。默认值: " + FPS);
        System.out.println("  i_frame_interval=<值>       : 设置 I 帧间隔 (秒)。默认值: " + I_FRAME_INTERVAL);
        System.out.println("  bit_rate=<值>               : 设置视频比特率 (例如: 2)。单位 Mbps。默认值: " + BIT_RATE + "Mbps");
        System.out.println("  size=<宽度>x<高度>          : 设置视频分辨率 (例如: 1920x1080)。默认值: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
        System.out.println("  tcp_addr=<地址>               : 设置 TCP 监听地址。默认值: " + TCP_HOST);
        System.out.println("  tcp_port=<端口号>             : 设置 TCP 监听端口。默认值: " + TCP_PORT);
        System.out.println("  camera_id=<ID>              : 指定要使用的摄像头 ID (例如: 0 或 1)。默认自动选择后置摄像头。");
        System.out.println("  codec=<类型>                : 设置视频编码器类型 (例如: avc 或 hevc)。默认值: " + (MIME_TYPE.equals(MediaFormat.MIMETYPE_VIDEO_AVC) ? "avc (H.264)" : "hevc (H.265)"));
        System.out.println("  rotate=<角度>               : 顺时针旋转视频角度 (0, 90, 180, 270)。默认值: " + ROTATE);
        System.out.println("  audio_sample_rate=<值>      : 设置音频采样率 (例如: 44100)。默认值: " + AUDIO_SAMPLE_RATE);
        System.out.println("  audio_channels=<值>         : 设置音频通道数 (1=单声道, 2=立体声)。默认值: " + AUDIO_CHANNELS);
        System.out.println("  audio_bit_rate=<值>         : 设置音频比特率 (单位: kbps)。默认值: " + (AUDIO_BIT_RATE / 1000) + " kbps");
        System.out.println("\n示例:");
        System.out.println("  java -jar CameraServer.jar tcp_addr=" + TCP_HOST + " size=1280x720");
        System.out.println("  java -jar CameraServer.jar tcp_addr=" + TCP_HOST + " tcp_port=" + TCP_PORT + " enable_video=false enable_audio=true");
        System.out.println("  java -jar CameraServer.jar tcp_addr=" + TCP_HOST + " tcp_port=" + TCP_PORT + " enable_video=true enable_audio=false");
        System.out.println("  java -jar CameraServer.jar tcp_addr=" + TCP_HOST + " tcp_port=" + TCP_PORT + " enable_audio=true audio_sample_rate=48000");
    }

    // --- 启动网络服务器 ---
    public void startServer() throws IOException {
        try {
            mServerSocket = new ServerSocket(TCP_PORT, 50, java.net.InetAddress.getByName(TCP_HOST));
            System.out.println("TCP 服务器已启动，监听 " + TCP_HOST + ":" + TCP_PORT);

            // 启动TCP发送线程
            startTcpSendThread();

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

    // --- 启动TCP发送线程 ---
    private void startTcpSendThread() {
        mTcpSendThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 从队列中获取数据包
                    TcpPacket packet = mTcpSendQueue.take();
                    
                    // 遍历所有客户端发送数据
                    for (Socket client : mTcpClients) {
                        try {
                            OutputStream out = client.getOutputStream();
                            out.write(packet.header);
                            out.write(packet.data);
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "TcpSendThread");
        mTcpSendThread.start();
    }

    // --- 停止网络服务器 ---
    public void stopServer() {
        System.out.println("正在停止网络服务器...");

        // 关闭TCP发送线程
        if (mTcpSendThread != null && mTcpSendThread.isAlive()) {
            mTcpSendThread.interrupt();
            try {
                mTcpSendThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mTcpSendThread = null;
        }

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
            // 1. 启动摄像头线程（如果启用视频录制）
            if (ENABLE_VIDEO) {
                mCameraThread = new HandlerThread("CameraThread");
                mCameraThread.start();
                mCameraHandler = new Handler(mCameraThread.getLooper());
                System.out.println("摄像头线程已启动。");
            }

            // 2. 启动编码器线程（如果启用视频录制）
            if (ENABLE_VIDEO) {
                mEncoderThread = new HandlerThread("MediaCodecThread");
                mEncoderThread.start();
                mEncoderHandler = new Handler(mEncoderThread.getLooper());
                System.out.println("编码器线程已启动。");
            }

            // 3. 如果启用音频，启动音频线程
            if (ENABLE_AUDIO) {
                mAudioThread = new HandlerThread("AudioThread");
                mAudioThread.start();
                mAudioHandler = new Handler(mAudioThread.getLooper());
                System.out.println("音频线程已启动。");
            }

            // 创建 Executor 用于摄像头会话
            mExecutor = Executors.newSingleThreadExecutor();

            // 4. 初始化视频 MediaCodec 编码器（如果启用视频录制）
            if (ENABLE_VIDEO) {
                setupMediaCodec();
                System.out.println("视频 MediaCodec 设置完成，mEncoderInputSurface 已就绪。");
            }

            // 5. 如果启用音频，初始化音频 MediaCodec 编码器
            if (ENABLE_AUDIO) {
                setupAudioCodec();
                System.out.println("音频 MediaCodec 设置完成。");
                startAudioRecordThread();
                System.out.println("音频录制线程已启动。");
            }

            // 6. 打开摄像头（如果启用视频录制）
            if (ENABLE_VIDEO) {
                openCamera();
                System.out.println("摄像头打开请求已发送。");
            }

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
        mIsAudioRecording = false;

        // 1. 关闭摄像头（如果启用视频录制）
        if (ENABLE_VIDEO) {
            closeCamera();
        }

        // 2. 停止视频编码器（如果启用视频录制）
        if (ENABLE_VIDEO) {
            stopMediaCodec();
        }

        // 3. 停止音频编码器和录制（如果启用音频录制）
        if (ENABLE_AUDIO) {
            stopAudioCodec();
            stopAudioRecord();
        }

        // 4. 停止线程
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
        if (mAudioThread != null) {
            mAudioThread.quitSafely();
            try {
                mAudioThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mAudioThread = null;
        }

        System.out.println("录制已停止。");
    }

    // --- 设置 MediaCodec 编码器 ---
    private void setupMediaCodec() throws IOException {
        System.out.println("正在设置 MediaCodec 编码器，分辨率 " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + " @ " + FPS + "fps, " + BIT_RATE + "bps...");

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
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        System.out.println("MediaCodec 配置: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + " @ " + FPS + "fps, bitrate=" + BIT_RATE);

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
                    // 调用处理方法，现在会正确处理复合数据包
                    processOutputBuffer(outputBuffer, info);
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

                // 我去这里拿到的 可能不是最新的编码器配置 还是需要使用： onOutputBufferAvailable() CODEC_CONFIG 标记中拿到的才算。

                System.out.println("=== MediaCodec onOutputFormatChanged 被调用 ===");
                
                /*
                // 使用官方API获取配置数据，兼容H.264/H.265
                mConfigData = getFullCsd(format);
                if (mConfigData != null) {
                    mConfigData_len = mConfigData.length;
                    System.out.println("✓ 从 onOutputFormatChanged 获取配置数据，大小: " + mConfigData_len);
                    // 发送配置数据包 (type=101)
                    // sendvideoConfigData(mConfigData); // 配置数据的时间戳通常为0
                } else {
                    System.err.println("❌ 从 onOutputFormatChanged 未能获取配置数据");
                }
                */
            }
        }, mEncoderHandler); // <--- 用编码线程的 Handler


        // 启动 MediaCodec
        mMediaCodec.start();
        System.out.println("MediaCodec 已启动。");
    }
    /*
    // --- 获取完整的配置数据 (SPS/PPS for H.264, VPS/SPS/PPS for H.265) ---
    private byte[] getFullCsd(MediaFormat format) {
        // 使用官方API获取配置数据，兼容H.264/H.265
        ByteBuffer csd0 = format.getByteBuffer("csd-0"); // SPS (H.264) 或 VPS (H.265)
        ByteBuffer csd1 = format.getByteBuffer("csd-1"); // PPS (H.264) 或 SPS (H.265)
        ByteBuffer csd2 = format.getByteBuffer("csd-2"); // H.265 的 PPS
        
        if (csd0 == null) {
            System.err.println("❌ csd-0 为 null");
            return null;
        }
        
        // 计算总长度
        int totalLen = 0;
        if (csd0 != null) totalLen += csd0.remaining();
        if (csd1 != null) totalLen += csd1.remaining();
        if (csd2 != null) totalLen += csd2.remaining();
        
        byte[] fullCsd = new byte[totalLen];
        int offset = 0;
        
        if (csd0 != null) {
            csd0.rewind();
            csd0.get(fullCsd, offset, csd0.remaining());
            offset += csd0.remaining();
        }
        if (csd1 != null) {
            csd1.rewind();
            csd1.get(fullCsd, offset, csd1.remaining());
            offset += csd1.remaining();
        }
        if (csd2 != null) {
            csd2.rewind();
            csd2.get(fullCsd, offset, csd2.remaining());
        }
        
        return fullCsd;
    }
    */

    // --- 检查摄像头支持的分辨率，如需要则自动调整 ---
    private void checkCameraResolution() throws CameraAccessException {
        System.out.println("正在打开摄像头...");

        CameraManager manager = (CameraManager) sContext.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("CameraManager 服务不可用。");
        }

        System.out.println("检查摄像头支持的分辨率...");
        
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

            // 如果指定了 camera_id，则检查该摄像头
            if (mCameraDeviceId != null && mCameraDeviceId.equals(id)) {
                mCameraDeviceId = id;
            }
            // 否则自动选择后置摄像头
            else if (mCameraDeviceId == null && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                mCameraDeviceId = id;
            }

            if (mCameraDeviceId != null) {
                break;
            }
        }

        if (mCameraDeviceId == null) {
            System.err.println("未找到合适的摄像头");
            return;
        }

        // 检查该摄像头支持的分辨率
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDeviceId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            System.err.println("摄像头 " + mCameraDeviceId + " 没有 StreamConfigurationMap");
            return;
        }

        Size[] videoSizes = map.getOutputSizes(MediaCodec.class);
        if (videoSizes == null || videoSizes.length == 0) {
            System.err.println("摄像头 " + mCameraDeviceId + " 没有支持 MediaCodec 的输出尺寸");
            return;
        }

        System.out.println("摄像头 " + mCameraDeviceId + " 支持的分辨率 (" + MIME_TYPE + "):");
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

        // 新增：输出支持的帧率范围
        Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (fpsRanges != null) {
            System.out.println("    支持的帧率范围:");
            for (Range<Integer> range : fpsRanges) {
                System.out.println("      - " + range);
                if(range.getUpper() >= FPS) {
                    System.out.println("       ✓ 支持当前选择的 fps");
                }
            }
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

        checkCameraResolution();

        CameraManager manager = (CameraManager) sContext.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("CameraManager 服务不可用。");
        }

        // 请求打开摄像头
        mCameraOpenCloseLock.acquire(); // 获取信号量，防止多次打开
        manager.openCamera(mCameraDeviceId, mStateCallback, mCameraHandler);
        System.out.println("已请求打开摄像头: " + mCameraDeviceId + " (分辨率: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + ")");
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
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(FPS, FPS)); // 使用导入的 Range
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            
            // 添加画面旋转, 不知道怎么搞的 在我android-34.jar 是说没有这个属性。
            // captureRequestBuilder.set(CaptureRequest.CONTROL_ROTATION, ROTATE);
            
            // 关键：设置摄像头帧率（纳秒）
            long frameDurationNs = (long)(1_000_000_000.0 / FPS);
            captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDurationNs);
            System.out.println("摄像头帧间隔设置为: " + frameDurationNs + " ns (对应 " + FPS + " fps)");
            
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

    private void processOutputBuffer(ByteBuffer buffer, MediaCodec.BufferInfo info) {
        if (!mIsRecording) return;
        if (info.size <= 0) return;

        boolean isKeyFrame = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;

        // 检查是否是配置数据
        boolean isConfig = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
        if(isConfig && isKeyFrame) System.out.println("// 可能同时是codec_config 和 keyframe: true。 在安卓上这种情况一般不会出现");

        if (isConfig){
            System.out.print("从 onOutputBufferAvailable() 中拿到的 BUFFER_FLAG_CODEC_CONFIG: ");
            byte[] data = new byte[info.size];
            synchronized (buffer) {
                buffer.position(info.offset);
                buffer.limit(info.size);
                buffer.get(data);
            }

            /* 
            总是 Annex-B 格式
            // debug 输出
            boolean isAnnexB = (data.length >= 4 && data[0] == 0x00 && data[1] == 0x00 && ((data[2] == 0x00 && data[3] == 0x01) || data[2] == 0x01));
            byte[] annexb;
            if(isAnnexB){
                // System.out.println("当前buffer帧是 AnnexB");
            }else{
                // System.out.println("当前帧是 AVCC");
                annexb = avccToAnnexB(data);
            }
            */
            
            sendVideoConfigData(data);

            return;
        }

        // 普通帧数据
        byte[] data = new byte[info.size];
        synchronized (buffer) {
            buffer.position(info.offset);
            buffer.limit(info.size);
            buffer.get(data);
        }

        /*
        // 当前安卓编译器输出是会 Annex-B
        // 如果是 AVCC 格式 转换为 Annex-B 格式
        boolean isAnnexB = (data.length >= 4 && data[0] == 0x00 && data[1] == 0x00 && ((data[2] == 0x00 && data[3] == 0x01) || data[2] == 0x01));
        // byte[] annexb = isAnnexB ? data : avccToAnnexB(data);
        byte[] annexb;
        if(isAnnexB){
            System.out.println("当前buffer帧是 AnnexB");
            annexb = data;
        }else{
            System.out.println("当前帧是 AVCC");
            // annexb = avccToAnnexB(data);
        }
        */

        long pts = info.presentationTimeUs; // 获取时间戳

        // sendVideoFrame(annexb, pts, isKeyFrame);
        sendVideoFrame(data, pts, isKeyFrame);

    }

    // 发送配置数据 H.264(SPS/PPS) H.265(VPS/SPS/PPS)
    private void sendVideoConfigData(byte[] config) {
        // 构造配置数据包头：type(2字节) + data_len(4字节) + pts(8字节) + data
        short configType = (short)101; // 101=配置数据
        int configDataLen = config.length;

        // 构造配置数据包头
        ByteBuffer header = ByteBuffer.allocate(14);
        header.order(ByteOrder.BIG_ENDIAN); // 显式指定网络字节序
        header.putShort(configType);
        header.putInt(configDataLen);
        header.putLong(0);

        // 发送配置数据包 - 放入TCP发送队列
        try {
            mTcpSendQueue.put(new TcpPacket(header.array(), config));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("发送配置数据 (type=101), size=" + configDataLen); // 调试输出
    }

    // 发送视频帧数据
    private void sendVideoFrame(byte[] frame, long pts, boolean isKeyFrame) {
        // 构造视频帧包头：type(2字节) + data_len(4字节) + pts(8字节) + data
        short videoType = isKeyFrame ? VideoKeyframe : VideoNormal; // 100=关键视频帧, 1=普通视频帧
        int videoDataLen = frame.length;

        // 构造视频帧包头
        ByteBuffer header = ByteBuffer.allocate(14);
        header.order(ByteOrder.BIG_ENDIAN); // 显式指定网络字节序
        header.putShort(videoType);
        header.putInt(videoDataLen);
        header.putLong(pts);

        // 发送视频帧包 - 放入TCP发送队列
        try {
            mTcpSendQueue.put(new TcpPacket(header.array(), frame));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // System.out.println("发送视频帧 (type=" + videoType + "), size=" + videoDataLen); // 调试输出
    }

    /*
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
    */

    // ==================== 音频处理方法 ====================

    // --- 设置音频 MediaCodec 编码器 ---
    private void setupAudioCodec() throws IOException {
        System.out.println("正在设置音频 MediaCodec 编码器，采样率 " + AUDIO_SAMPLE_RATE + " Hz, " + AUDIO_CHANNELS + " 通道, " + AUDIO_BIT_RATE + " bps...");
        System.out.println("DEBUG: mAudioHandler = " + mAudioHandler);
        System.out.println("DEBUG: mAudioThread = " + mAudioThread);
        System.out.println("DEBUG: mAudioThread.isAlive() = " + (mAudioThread != null ? mAudioThread.isAlive() : "null"));

        try {
            mAudioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            System.out.println("✓ 音频编码器创建成功");
        } catch (Exception e) {
            System.err.println("错误: 创建音频 MediaCodec 编码器失败: " + e.getMessage());
            throw e;
        }

        MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, AUDIO_CHANNELS);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);

        mAudioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        System.out.println("✓ 音频编码器配置完成");

        // 设置音频 MediaCodec 异步回调（同视频编码）
        try {
            mAudioCodec.setCallback(new MediaCodec.Callback() {
                private int inputCallCount = 0;
                private int outputCallCount = 0;
                
                @Override
                public void onInputBufferAvailable(MediaCodec codec, int index) {
                    inputCallCount++;
                    // System.out.println("[DEBUG] onInputBufferAvailable 被调用, count=" + inputCallCount);
                    // 异步模式下，通过回调获得输入缓冲区
                    ByteBuffer inputBuffer = codec.getInputBuffer(index);
                    if (inputBuffer != null) {
                        // 从队列取出音频数据，如果队列为空则等待最多 100ms
                        byte[] audioData = null;
                        try {
                            audioData = mAudioDataQueue.poll(100, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        if (audioData != null) {
                            inputBuffer.clear();
                            inputBuffer.put(audioData);
                            long presentationTimeUs = System.nanoTime() / 1000;
                            codec.queueInputBuffer(index, 0, audioData.length, presentationTimeUs, 0);
                            // if (inputCallCount % 50 == 0) {
                                // System.out.println("[音频编码器] 输入缓冲区可用，已填充 " + audioData.length + " 字节");
                            // }
                        // } else {
                            // if (inputCallCount % 50 == 0) {
                                // System.out.println("[音频编码器] 输入缓冲区可用，但队列为空（等待超时）！");
                            // }
                        }
                    }
                }

                @Override
                public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                    outputCallCount++;
                    // System.out.println("[DEBUG] onOutputBufferAvailable 被调用, count=" + outputCallCount + ", size=" + info.size);
                    // 异步模式下，直接收到输出缓冲区
                    if (info.size > 0) {
                        ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                        if (outputBuffer != null) {
                            // if (outputCallCount % 50 == 0) {
                                // System.out.println("[音频编码器] 输出缓冲区可用，大小 " + info.size + " 字节，客户端数: " + mTcpClients.size());
                            // }
                            sendAudioDataToClients(outputBuffer, info);
                        }
                    }
                    codec.releaseOutputBuffer(index, false);
                }

                @Override
                public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                    System.err.println("音频编码器错误: " + e.getMessage());
                }

                @Override
                public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                    System.out.println("音频输出格式已改变");
                }
            }, mAudioHandler);
            System.out.println("✓ 音频编码器异步回调设置成功");
        } catch (Exception e) {
            System.err.println("❌ 音频编码器设置回调失败: " + e.getMessage());
            e.printStackTrace();
        }

        mAudioCodec.start();
        System.out.println("✓ 音频 MediaCodec 已启动（异步模式）。");
        mIsAudioRecording = true;
    }

    // --- 启动音频录制线程 ---
    private void startAudioRecordThread() {
        Thread audioRecordThread = new Thread(() -> {
            try {
                int bufferSize = AudioRecord.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AUDIO_CHANNELS == 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                );
                System.out.println("音频 AudioRecord 缓冲区大小: " + bufferSize);

                mAudioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AUDIO_SAMPLE_RATE,
                    AUDIO_CHANNELS == 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                );

                if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    System.err.println("错误: AudioRecord 初始化失败。");
                    mIsAudioRecording = false;
                    return;
                }

                mAudioRecord.startRecording();
                System.out.println("AudioRecord 已启动录音。");

                byte[] audioBuffer = new byte[4096];
                int readCount = 0;
                
                while (mIsAudioRecording && mIsRecording) {
                    // 同步读取音频数据，放入队列供异步编码器使用
                    int readSize = mAudioRecord.read(audioBuffer, 0, audioBuffer.length);
                    if (readSize > 0) {
                        readCount++;
                        // if (readCount % 50 == 0) { // 每50次打一条日志
                            // System.out.println("[音频] 读取PCM数据: " + readSize + " 字节，队列大小: " + mAudioDataQueue.size());
                        // }
                        // 复制一份数据放入队列
                        byte[] audioData = new byte[readSize];
                        System.arraycopy(audioBuffer, 0, audioData, 0, readSize);
                        try {
                            // 使用 put 会阻塞直到队列有空间（或队列满时丢弃）
                            if (!mAudioDataQueue.offer(audioData, 100, TimeUnit.MILLISECONDS)) {
                                // 队列满，丢弃这帧
                                System.err.println("[音频] 警告：队列满，丢弃音频帧");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } else if (readSize < 0) {
                        System.err.println("AudioRecord 读取错误: " + readSize);
                    }
                }

            } catch (Exception e) {
                System.err.println("音频录制线程错误: " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                stopAudioRecord();
            }
        }, "AudioRecordThread");
        audioRecordThread.start();
    }

    // --- 发送编码后的音频数据到客户端 ---
    private void sendAudioDataToClients(ByteBuffer buffer, MediaCodec.BufferInfo info) {
        if (!mIsAudioRecording || !mIsRecording) {
            return;
        }
        if (info.size <= 0) {
            return;
        }

        // 保存配置数据（ADTS头）
        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            mAudioConfigData_len = info.size;
            mAudioConfigData = new byte[info.size];
            buffer.position(info.offset);
            buffer.get(mAudioConfigData);
            System.out.println("[音频] 已缓存音频配置数据，大小: " + mAudioConfigData_len);
            sendAudioFrame(mAudioConfigData, 0, AudioConfig);
            return;  // 配置数据本身不发送，后面会在每个音频帧中附加
        }

        byte[] audioData = new byte[info.size];
        buffer.position(info.offset);
        buffer.limit(info.offset + info.size);
        buffer.get(audioData);

        long pts = info.presentationTimeUs;

        // System.out.println("[音频发送] 发送音频帧，大小: " + audioDataLen + " 字节，客户端数: " + mTcpClients.size());
        sendAudioFrame(audioData, pts, AudioData);
    }

    // --- 停止音频编码器 ---
    private void stopAudioCodec() {
        if (mAudioCodec != null) {
            try {
                mAudioCodec.stop();
                mAudioCodec.release();
                System.out.println("音频 MediaCodec 已停止。");
            } catch (Exception e) {
                System.err.println("停止音频编码器失败: " + e.getMessage());
            } finally {
                mAudioCodec = null;
            }
        }
    }

    // --- 停止音频录制 ---
    private void stopAudioRecord() {
        if (mAudioRecord != null) {
            try {
                if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    mAudioRecord.stop();
                    System.out.println("AudioRecord 已停止。");
                }
                mAudioRecord.release();
            } catch (Exception e) {
                System.err.println("停止 AudioRecord 失败: " + e.getMessage());
            } finally {
                mAudioRecord = null;
            }
        }
    }
    // 发送视频帧数据
    private void sendAudioFrame(byte[] frame, long pts, short audioType) {
        // 构造视频帧包头：type(2字节) + data_len(4字节) + pts(8字节) + data
        // audioType 2=声音数据, 201=配置extradata 
        int audioDataLen = frame.length;

        // 构造视频帧包头
        ByteBuffer header = ByteBuffer.allocate(14);
        header.order(ByteOrder.BIG_ENDIAN); // 显式指定网络字节序
        header.putShort(audioType);
        header.putInt(audioDataLen);
        header.putLong(pts);

        // 发送视频帧包 - 放入TCP发送队列
        try {
            mTcpSendQueue.put(new TcpPacket(header.array(), frame));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // System.out.println("发送视频帧 (type=" + videoType + "), size=" + videoDataLen); // 调试输出
    }

} 

