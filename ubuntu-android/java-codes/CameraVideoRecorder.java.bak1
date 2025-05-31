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

@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi", "InternalInsetResource", "DiscouragedApi"})
public final class CameraVideoRecorder {

    private static final String TAG = "CameraVideoRecorder"; // 调试TAG，实际输出中不会显示

    // --- 默认 MediaCodec 参数 ---
    private static String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC; // H.264 AVC
    private static int FRAME_RATE = 30; // 帧率
    private static int I_FRAME_INTERVAL = 1; // I帧间隔 (秒)
    private static int BIT_RATE = 2000000; // 比特率 (2 Mbps)
    private static int VIDEO_WIDTH = 1280; // 视频宽度
    private static int VIDEO_HEIGHT = 720; // 视频高度
    private static long RECORDING_DURATION_MS = 10000; // 录制时长 (毫秒)

    // --- 命令行参数接收的变量 ---
    private static String OUTPUT_FILE_PATH = "/data/local/tmp/output.h264";
    private static String CAMERA_ID_TO_USE = null; // 默认不指定，让程序自动选择后置摄像头

    // --- 文件输出 ---
    private FileOutputStream mFileOutputStream;

    // --- Android 环境设置 ---
    private static Class<?> ACTIVITY_THREAD_CLASS;
    private static Object ACTIVITY_THREAD_INSTANCE;
    private static Context sContext;

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

    // --- 静态初始化 Android 环境 ---
    private static void initializeAndroidEnvironment() {
        try {
            if (Looper.myLooper() == null) {
                Looper.prepareMainLooper();
                System.out.println("Prepared MainLooper for the current thread.");
            } else {
                System.out.println("Looper already prepared for the current thread.");
            }

            ACTIVITY_THREAD_CLASS = Class.forName("android.app.ActivityThread");
            Constructor<?> activityThreadConstructor = ACTIVITY_THREAD_CLASS.getDeclaredConstructor();
            activityThreadConstructor.setAccessible(true);
            ACTIVITY_THREAD_INSTANCE = activityThreadConstructor.newInstance();
            // System.out.println("Created new ActivityThread instance."); // 调试信息，可以省略

            Field sCurrentActivityThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            sCurrentActivityThreadField.set(null, ACTIVITY_THREAD_INSTANCE);
            // System.out.println("Set sCurrentActivityThread field."); // 调试信息，可以省略

            try {
                Field mSystemThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("mSystemThread");
                mSystemThreadField.setAccessible(true);
                mSystemThreadField.setBoolean(ACTIVITY_THREAD_INSTANCE, true);
                // System.out.println("Set mSystemThread field to true."); // 调试信息，可以省略
            } catch (NoSuchFieldException e) {
                System.err.println("WARN: mSystemThread field not found, skipping. " + e.getMessage());
            }

            Method getSystemContextMethod = ACTIVITY_THREAD_CLASS.getDeclaredMethod("getSystemContext");
            getSystemContextMethod.setAccessible(true);
            sContext = (Context) getSystemContextMethod.invoke(ACTIVITY_THREAD_INSTANCE);

            if (sContext == null) {
                throw new IllegalStateException("Failed to get system context after setup.");
            }
            System.out.println("Android environment simulation setup complete. Context obtained.");

        } catch (Exception e) {
            System.err.println("FATAL: Failed to initialize Android environment simulation: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        System.out.println(TAG + " started.");

        // 解析命令行参数
        parseArguments(args);

        // 如果输出文件路径不是绝对路径，可以考虑加上默认目录
        if (!OUTPUT_FILE_PATH.startsWith("/")) {
            OUTPUT_FILE_PATH = "/data/local/tmp/" + OUTPUT_FILE_PATH;
        }

        initializeAndroidEnvironment(); // 初始化 Android 环境

        CameraVideoRecorder recorder = new CameraVideoRecorder();
        try {
            recorder.startRecording();
            System.out.println("Recording for " + (RECORDING_DURATION_MS / 1000) + " seconds...");

            // 等待录制时长
            Thread.sleep(RECORDING_DURATION_MS);

            recorder.stopRecording();
            System.out.println("Recording finished. Output file: " + OUTPUT_FILE_PATH);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Recording interrupted: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An error occurred during recording: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            recorder.releaseResources(); // 确保释放所有资源
            System.out.println(TAG + " finished.");
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
            } else {
                System.err.println("WARNING: Ignoring invalid argument format: " + arg);
            }
        }

        try {
            if (argMap.containsKey("frame_rate")) {
                FRAME_RATE = Integer.parseInt(argMap.get("frame_rate"));
                System.out.println("Parameter: frame_rate = " + FRAME_RATE);
            }
            if (argMap.containsKey("i_frame_interval")) {
                I_FRAME_INTERVAL = Integer.parseInt(argMap.get("i_frame_interval"));
                System.out.println("Parameter: I_frame_interval = " + I_FRAME_INTERVAL);
            }
            if (argMap.containsKey("bit_rate")) {
                BIT_RATE = Integer.parseInt(argMap.get("bit_rate"));
                System.out.println("Parameter: bit_rate = " + BIT_RATE);
            }
            if (argMap.containsKey("size")) {
                String sizeStr = argMap.get("size");
                String[] sizeParts = sizeStr.split("x");
                if (sizeParts.length == 2) {
                    VIDEO_WIDTH = Integer.parseInt(sizeParts[0]);
                    VIDEO_HEIGHT = Integer.parseInt(sizeParts[1]);
                    System.out.println("Parameter: size = " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
                } else {
                    System.err.println("WARNING: Invalid size format: " + sizeStr + ". Using default 1280x720.");
                }
            }
            if (argMap.containsKey("output")) {
                OUTPUT_FILE_PATH = argMap.get("output");
                System.out.println("Parameter: output = " + OUTPUT_FILE_PATH);
            }
            if (argMap.containsKey("camera_id")) {
                CAMERA_ID_TO_USE = argMap.get("camera_id");
                System.out.println("Parameter: camera_id = " + CAMERA_ID_TO_USE);
            }
            if (argMap.containsKey("duration_ms")) { // 可以添加一个可选的录制时长参数
                RECORDING_DURATION_MS = Long.parseLong(argMap.get("duration_ms"));
                System.out.println("Parameter: duration_ms = " + RECORDING_DURATION_MS);
            }

        } catch (NumberFormatException e) {
            System.err.println("ERROR: Invalid number format in arguments: " + e.getMessage());
            e.printStackTrace(System.err);
            System.err.println("Using default video parameters.");
        }
    }


    // --- 开始录制 ---
    public void startRecording() throws IOException, CameraAccessException, InterruptedException {
        if (mIsRecording) {
            System.err.println("WARN: Already recording.");
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
            System.out.println("Created parent directory: " + parentDir.getAbsolutePath());
        }
        mFileOutputStream = new FileOutputStream(outputFile);
        System.out.println("Output file stream opened: " + OUTPUT_FILE_PATH);

        // 5. 打开摄像头
        openCamera();
    }

    // --- 停止录制 ---
    public void stopRecording() {
        if (!mIsRecording) {
            System.err.println("WARN: Not recording.");
            return;
        }

        mIsRecording = false;

        // 停止捕获会话并关闭摄像头
        try {
            if (mCaptureSession != null) {
                mCaptureSession.stopRepeating();
                mCaptureSession.close();
                mCaptureSession = null;
                System.out.println("CameraCaptureSession stopped and closed.");
            }
        } catch (CameraAccessException e) {
            System.err.println("ERROR: Failed to stop camera capture session: " + e.getMessage());
            e.printStackTrace(System.err);
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
            System.out.println("CameraDevice closed.");
        }

        // 停止并释放 MediaCodec
        if (mMediaCodec != null) {
            try {
                mMediaCodec.stop();
                mMediaCodec.release();
                System.out.println("MediaCodec stopped and released.");
            } catch (IllegalStateException e) {
                System.err.println("ERROR: MediaCodec stop/release failed, already in wrong state: " + e.getMessage());
                e.printStackTrace(System.err);
            }
            mMediaCodec = null;
        }

        // 关闭文件输出流
        if (mFileOutputStream != null) {
            try {
                mFileOutputStream.close();
                System.out.println("File output stream closed.");
            } catch (IOException e) {
                System.err.println("ERROR: Failed to close file output stream: " + e.getMessage());
                e.printStackTrace(System.err);
            }
            mFileOutputStream = null;
        }

        // 停止线程
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            try {
                mCameraThread.join();
                System.out.println("CameraThread stopped.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("ERROR: Camera thread join interrupted: " + e.getMessage());
                e.printStackTrace(System.err);
            }
            mCameraThread = null;
            mCameraHandler = null;
        }

        if (mEncoderThread != null) {
            mEncoderThread.quitSafely();
            try {
                mEncoderThread.join();
                System.out.println("EncoderThread stopped.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("ERROR: Encoder thread join interrupted: " + e.getMessage());
                e.printStackTrace(System.err);
            }
            mEncoderThread = null;
            mEncoderHandler = null;
        }

        System.out.println("Recording stopped and resources released.");
    }

    // --- 确保所有资源被释放 ---
    private void releaseResources() {
        stopRecording(); // 确保停止并释放
    }

    // --- 设置 MediaCodec 编码器 ---
    private void setupMediaCodec() throws IOException {
        System.out.println("Setting up MediaCodec encoder for " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + " @" + FRAME_RATE + "fps, " + (BIT_RATE / 1000000.0) + "Mbps...");
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);

        // 设置编码器参数
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface); // 颜色格式设置为Surface
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        // 尝试选择一个支持Surface输入的编码器
        try {
            mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to create MediaCodec encoder for " + MIME_TYPE + ": " + e.getMessage());
            e.printStackTrace(System.err);
            throw e;
        }

        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        // 获取编码器输入Surface
        mEncoderInputSurface = mMediaCodec.createInputSurface();
        System.out.println("MediaCodec input Surface created.");

        // 设置 MediaCodec 异步回调
        mMediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                // 通常用于解码器或需要手动提供数据的情况。编码器使用 Surface 自动获取数据。
                // System.out.println("DEBUG: onInputBufferAvailable: " + index);
            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                if (mFileOutputStream == null) {
                    System.err.println("WARN: File output stream is null, dropping encoded data.");
                    codec.releaseOutputBuffer(index, false);
                    return;
                }
                try {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                    if (outputBuffer != null) {
                        // 写入 SPS/PPS 等配置数据 (如果这是第一帧或者配置数据有更新)
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            // System.out.println("DEBUG: Codec config buffer: " + info.size + " bytes");
                            byte[] data = new byte[info.size];
                            outputBuffer.get(data);
                            mFileOutputStream.write(data, 0, info.size);
                        } else {
                            // 写入视频数据
                            // System.out.println("DEBUG: Video data buffer: " + info.size + " bytes, flags: " + info.flags);
                            outputBuffer.position(info.offset);
                            outputBuffer.limit(info.offset + info.size);

                            byte[] data = new byte[info.size];
                            outputBuffer.get(data);
                            mFileOutputStream.write(data, 0, info.size);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("ERROR: Failed to write encoded data to file: " + e.getMessage());
                    e.printStackTrace(System.err);
                } finally {
                    codec.releaseOutputBuffer(index, false); // 释放输出缓冲区
                }
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                System.err.println("FATAL: MediaCodec error: " + e.getMessage());
                e.printStackTrace(System.err);
                // 在这里处理致命错误，可能需要停止录制
                stopRecording();
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                System.out.println("MediaCodec output format changed: " + format);
                // 这里可以获取新的输出格式，例如新的 SPS/PPS 数据
            }
        }, mEncoderHandler); // 将回调设置到编码器线程的 Handler

        mMediaCodec.start();
        System.out.println("MediaCodec started.");
    }

    // --- 打开摄像头 ---
    private void openCamera() throws CameraAccessException, InterruptedException {
        System.out.println("Opening camera...");
        CameraManager manager = (CameraManager) sContext.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("CameraManager service not available.");
        }

        String selectedCameraId = null;
        System.out.println("Available camera IDs and their characteristics:");

        for (String id : manager.getCameraIdList()) {
            System.out.println("  Camera ID: " + id);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

            String facingStr = "Unknown";
            if (facing != null) {
                if (facing == CameraCharacteristics.LENS_FACING_BACK) facingStr = "BACK";
                else if (facing == CameraCharacteristics.LENS_FACING_FRONT) facingStr = "FRONT";
                else if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) facingStr = "EXTERNAL";
            }
            System.out.println("    LENS_FACING: " + facingStr);

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Size[] videoSizes = map.getOutputSizes(MediaCodec.class);
                if (videoSizes != null && videoSizes.length > 0) {
                    System.out.println("    Supported sizes for MediaCodec (" + MIME_TYPE + "):");
                    for (Size size : videoSizes) {
                        System.out.println("      - " + size.getWidth() + "x" + size.getHeight());
                    }
                } else {
                    System.out.println("    No supported sizes for MediaCodec (" + MIME_TYPE + ") on this camera.");
                }
            } else {
                System.out.println("    No StreamConfigurationMap available for this camera.");
            }

            // 如果指定了 camera_id，则优先使用指定的摄像头
            if (CAMERA_ID_TO_USE != null && CAMERA_ID_TO_USE.equals(id)) {
                selectedCameraId = id;
                System.out.println("    --> This is the camera specified by camera_id: " + CAMERA_ID_TO_USE);
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
                            System.err.println("ERROR: Specified camera " + CAMERA_ID_TO_USE + " does NOT support " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + " for MediaCodec.");
                            // 可以选择抛出异常或尝试找一个最近的，这里为了严格，我们继续抛异常
                            // 否则，如果在这里设置 selectedCameraId，即使分辨率不匹配也会尝试打开
                            throw new RuntimeException("Specified camera " + CAMERA_ID_TO_USE + " does not support desired resolution " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
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
                                System.out.println("    --> Found a suitable BACK camera with desired resolution: " + id);
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
            String cameraSelectionInfo = (CAMERA_ID_TO_USE != null) ? "specified camera ID " + CAMERA_ID_TO_USE : "a suitable BACK camera";
            throw new RuntimeException("No suitable camera found (" + cameraSelectionInfo + ") supporting the desired video resolution " + resolutionInfo + " for MediaCodec.");
        }

        // 请求打开摄像头
        mCameraOpenCloseLock.acquire(); // 获取信号量，防止多次打开
        manager.openCamera(selectedCameraId, mStateCallback, mCameraHandler);
        System.out.println("Requested to open camera: " + selectedCameraId);
    }

    // --- 摄像头状态回调 ---
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            System.out.println("Camera " + cameraDevice.getId() + " opened.");
            createCameraPreviewSession(); // 摄像头打开后创建捕获会话
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            System.err.println("WARN: Camera disconnected.");
            stopRecording(); // 相机断开连接，停止录制
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            System.err.println("ERROR: Camera error: " + error);
            stopRecording(); // 相机出错，停止录制
        }
    };

    // --- 创建摄像头捕获会话 ---
    private void createCameraPreviewSession() {
        if (mCameraDevice == null || mEncoderInputSurface == null || mCameraHandler == null) {
            System.err.println("ERROR: CameraDevice, Encoder Input Surface or Camera Handler is null, cannot create capture session.");
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
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            if (mCameraDevice == null) {
                                return;
                            }
                            mCaptureSession = cameraCaptureSession;
                            System.out.println("CameraCaptureSession configured.");

                            try {
                                // 设置自动对焦、自动曝光等
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

                                // 开始重复捕获请求
                                mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mCameraHandler);
                                System.out.println("Camera setRepeatingRequest started.");
                            } catch (CameraAccessException e) {
                                System.err.println("ERROR: Failed to start camera preview/recording: " + e.getMessage());
                                e.printStackTrace(System.err);
                                stopRecording();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            System.err.println("ERROR: Failed to configure camera capture session.");
                            stopRecording();
                        }
                    }, mCameraHandler); // 回调在摄像头线程上执行

        } catch (CameraAccessException e) {
            System.err.println("ERROR: Failed to create camera capture session: " + e.getMessage());
            e.printStackTrace(System.err);
            stopRecording();
        }
    }

    // --- 辅助方法: 选择最接近期望分辨率的尺寸 ---
    // 这个方法在此版本中主要用于调试日志，实际选择逻辑在 openCamera 中完成
    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        // 简单地返回最大的一个，用于调试时查看
        System.out.println("DEBUG: Attempting to choose optimal size among: " + Arrays.toString(choices));
        return Collections.max(Arrays.asList(choices), new Comparator<Size>() {
            @Override
            public int compare(Size lhs, Size rhs) {
                return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
            }
        });
    }
}
