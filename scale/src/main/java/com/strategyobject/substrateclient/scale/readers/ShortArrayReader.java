package com.strategyobject.substrateclient.scale.readers;

import com.google.common.base.Preconditions;
import com.strategyobject.substrateclient.common.io.Streamer;
import com.strategyobject.substrateclient.scale.ScaleReader;
import lombok.NonNull;
import lombok.val;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ShortArrayReader implements ScaleReader<short[]> {
    @Override
    public short[] read(@NonNull InputStream stream, ScaleReader<?>... readers) throws IOException {
        Preconditions.checkArgument(readers == null || readers.length == 0);

        val len = CompactIntegerReader.readInternal(stream);
        val result = new short[len];
        val src = Streamer.readBytes(len * 2, stream);
        val buf = ByteBuffer.allocate(src.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(src);
        buf.flip();
        buf.asShortBuffer().get(result);
        return result;
    }
}
