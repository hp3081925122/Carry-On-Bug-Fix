package org.hp.carryonbugfix.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 支持在后台音频流线程按帧精确跳过指定已播放 tick 的 PCM 字节包装流，确保中途接入或跨维续播准确衔接。
 */
public final class SeekingAudioStream implements AudioStream {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SKIP_CHUNK_BYTES = 32768;

    private final AudioStream delegate;
    private ByteBuffer leftover;

    private SeekingAudioStream(AudioStream delegate, ByteBuffer leftover) {
        this.delegate = delegate;
        this.leftover = leftover;
    }

    /**
     * 从底层解码流跳过 skipTicks 对应的帧字节数，返回从目标时间点开始的音频流。
     */
    public static AudioStream skipTicks(AudioStream delegate, long skipTicks) {
        if (skipTicks <= 0L) {
            return delegate;
        }
        AudioFormat format = delegate.getFormat();
        int frameSize = format.getFrameSize();
        if (frameSize <= 0) {
            int bytesPerSample = Math.max(1, format.getSampleSizeInBits() / 8);
            frameSize = bytesPerSample * Math.max(1, format.getChannels());
        }
        float sampleRate = format.getSampleRate();
        if (sampleRate <= 0.0F) {
            return delegate;
        }

        long targetFrames = (long) ((skipTicks / 20.0D) * sampleRate);
        long remainingBytes = targetFrames * frameSize;
        ByteBuffer leftover = null;

        try {
            while (remainingBytes > 0L) {
                int request = (int) Math.min(SKIP_CHUNK_BYTES, Math.max(remainingBytes, frameSize));
                ByteBuffer chunk = delegate.read(request);
                if (chunk == null || !chunk.hasRemaining()) {
                    break;
                }
                int available = chunk.remaining();
                if (available <= remainingBytes) {
                    remainingBytes -= available;
                } else {
                    int skip = (int) remainingBytes;
                    skip -= skip % frameSize;
                    chunk.position(chunk.position() + skip);
                    leftover = chunk.slice();
                    remainingBytes = 0L;
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to seek audio stream to tick {}", skipTicks, ex);
        }
        return new SeekingAudioStream(delegate, leftover);
    }

    @Override
    public AudioFormat getFormat() {
        return this.delegate.getFormat();
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (this.leftover == null || !this.leftover.hasRemaining()) {
            this.leftover = null;
            return this.delegate.read(size);
        }

        int take = Math.min(size, this.leftover.remaining());
        ByteBuffer out = BufferUtils.createByteBuffer(take);
        int oldLimit = this.leftover.limit();
        this.leftover.limit(this.leftover.position() + take);
        out.put(this.leftover);
        this.leftover.limit(oldLimit);
        out.flip();
        if (!this.leftover.hasRemaining()) {
            this.leftover = null;
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        this.leftover = null;
        this.delegate.close();
    }
}
