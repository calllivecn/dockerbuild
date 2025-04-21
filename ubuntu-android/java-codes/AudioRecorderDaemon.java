package com.calllivecn.recording;

import android.util.Log;
import android.media.MediaRecorder; // 录音相关的类
import java.io.IOException; // 用于处理IO异常

public class AudioRecorderDaemon {

    private static final String TAG = "AudioRecorderDaemon";
    private MediaRecorder recorder = null;
    private boolean isRecording = false;

    public static void main(String[] args) {
        // app_process 启动时会调用这个 main 方法
        Log.d(TAG, "Daemon started with args: " + String.join(" ", args));

        // 创建一个实例来执行实际的录音逻辑
        AudioRecorderDaemon daemon = new AudioRecorderDaemon();

        // 解析命令行参数 (如果需要)
        String outputPath = "/sdcard/recorded_audio.3gp"; // 默认输出路径
        if (args.length > 0) {
            outputPath = args[0];
        }

        try {
            daemon.startRecording(outputPath);
            Log.d(TAG, "Recording started to " + outputPath);

            // 为了让 daemon 保持运行，可以进入一个无限循环或等待状态
            // 注意：简单的无限循环可能会导致进程难以终止
            // 更优雅的方式可能是使用线程或者等待特定的信号/事件
            // 这里为了演示，我们让它录制一段时间后停止或者等待外部信号
            Thread.sleep(60000); // 示例：录制 60 秒

            daemon.stopRecording();
            Log.d(TAG, "Recording stopped.");

        } catch (IOException e) {
            Log.e(TAG, "Recording error", e);
        } catch (InterruptedException e) {
            Log.d(TAG, "Daemon interrupted.", e);
            if (daemon.isRecording) {
                 daemon.stopRecording();
            }
        } finally {
             Log.d(TAG, "Daemon exiting.");
        }
    }

    public void startRecording(String outputPath) throws IOException {
        if (isRecording) {
            Log.w(TAG, "Already recording.");
            return;
        }

        recorder = new MediaRecorder();
        // 设置音频源 (麦克风)
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        // 设置输出格式
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        // 设置音频编码器
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        // 设置输出文件路径
        recorder.setOutputFile(outputPath);

        try {
            recorder.prepare();
            recorder.start(); // 开始录音
            isRecording = true;
            Log.d(TAG, "MediaRecorder started.");
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed", e);
            releaseRecorder(); // 释放资源
            throw e;
        }
    }

    public void stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording.");
            return;
        }
        try {
            recorder.stop(); // 停止录音
            Log.d(TAG, "MediaRecorder stopped.");
        } catch (RuntimeException e) {
             // 当stop在start之前或状态不正确时可能抛出异常
             Log.e(TAG, "stop() failed", e);
        } finally {
            releaseRecorder(); // 释放资源
            isRecording = false;
        }
    }

    private void releaseRecorder() {
        if (recorder != null) {
            recorder.release(); // 释放录音器占用的资源
            recorder = null;
            Log.d(TAG, "MediaRecorder released.");
        }
    }

    // 在 daemon 运行时，你可能需要一种方式来优雅地停止它
    // 这可以通过监听一个文件、接收一个信号或者通过 Binder 调用来实现
    // 对于简单的命令行工具，可能只是运行一段时间后自动退出，或者等待 SIGTERM

}
