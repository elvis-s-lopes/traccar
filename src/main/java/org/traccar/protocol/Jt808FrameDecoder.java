package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.traccar.BaseFrameDecoder;

public class Jt808FrameDecoder extends BaseFrameDecoder {

    private static final byte FRAME_END = 126;
    private static final byte ESCAPE_CHAR = 125;
    private static final byte ESCAPE_ALTERNATIVE = 1;
    private static final byte ESCAPE_FRAME_END = 2;
    private static final byte ASCII_DELIMITER = 40;
    private static final byte ASCII_END = 41;

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ByteBuf buf) throws Exception {
        if (buf.readableBytes() < 2)
            return null;

        if (buf.getByte(buf.readerIndex()) == ASCII_DELIMITER) {
            int index = buf.indexOf(buf.readerIndex() + 1, buf.writerIndex(), ASCII_END);
            if (index >= 0)
                return buf.readRetainedSlice(index + 1);
        } else {
            int index = buf.indexOf(buf.readerIndex() + 1, buf.writerIndex(), FRAME_END);
            if (index >= 0) {
                ByteBuf result = Unpooled.buffer(index + 1 - buf.readerIndex());
                while (buf.readerIndex() <= index) {
                    int b = buf.readUnsignedByte();
                    if (b == ESCAPE_CHAR) {
                        int ext = buf.readUnsignedByte();
                        if (ext == ESCAPE_ALTERNATIVE) {
                            result.writeByte(ESCAPE_CHAR);
                        } else if (ext == ESCAPE_FRAME_END) {
                            result.writeByte(FRAME_END);
                        }
                    } else {
                        result.writeByte(b);
                    }
                }
                return result;
            }
        }
        return null;
    }

}
