package net.verity.client.voice;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;

public class MicrophoneRecorder {

    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;

    private final AudioFormat format;
    private TargetDataLine microphone;
    private volatile boolean recording = false;
    private ByteArrayOutputStream buffer;
    private Thread recordThread;

    public MicrophoneRecorder() {
        this.format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, true, false);
    }

    public void start() throws LineUnavailableException {
        if (recording) return;

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Microphone not supported");
        }

        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        microphone.start();

        buffer = new ByteArrayOutputStream();
        recording = true;

        recordThread = new Thread(() -> {
            byte[] chunk = new byte[4096];
            while (recording && microphone != null && microphone.isOpen()) {
                int read = microphone.read(chunk, 0, chunk.length);
                if (read > 0) {
                    buffer.write(chunk, 0, read);
                }
            }
        }, "Verity-MicRecorder");
        recordThread.setDaemon(true);
        recordThread.start();
    }

    public byte[] stop() {
        recording = false;

        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }

        if (recordThread != null) {
            try {
                recordThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] pcmData = buffer != null ? buffer.toByteArray() : new byte[0];
        return createWavFile(pcmData);
    }

    public boolean isRecording() {
        return recording;
    }

    private byte[] createWavFile(byte[] pcmData) {
        int dataLength = pcmData.length;
        int totalLength = 44 + dataLength;
        byte[] wav = new byte[totalLength];

        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        writeInt32LE(wav, 4, totalLength - 8);
        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
        wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
        writeInt32LE(wav, 16, 16);
        writeInt16LE(wav, 20, 1);
        writeInt16LE(wav, 22, CHANNELS);
        writeInt32LE(wav, 24, SAMPLE_RATE);
        writeInt32LE(wav, 28, SAMPLE_RATE * CHANNELS * SAMPLE_SIZE_BITS / 8);
        writeInt16LE(wav, 32, CHANNELS * SAMPLE_SIZE_BITS / 8);
        writeInt16LE(wav, 34, SAMPLE_SIZE_BITS);
        wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a';
        writeInt32LE(wav, 40, dataLength);

        System.arraycopy(pcmData, 0, wav, 44, dataLength);
        return wav;
    }

    private static void writeInt32LE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeInt16LE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
