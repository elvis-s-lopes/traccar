package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.traccar.BaseProtocolEncoder;
import org.traccar.Protocol;
import org.traccar.helper.DataConverter;
import org.traccar.model.Command;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Jt808ProtocolEncoder extends BaseProtocolEncoder {

    private static final int MSG_OIL_CONTROL = 40966;
    private static final int MSG_TERMINAL_CONTROL = 33029;
    private static final int MSG_OIL_CONTROL_PRORRAC = 34048;

    public Jt808ProtocolEncoder(Protocol protocol) {
        super(protocol);
    }

    @Override
    protected Object encodeCommand(Command command) {
        org.traccar.model.Device device = getCacheManager().getObject(
                org.traccar.model.Device.class, command.getDeviceId());
        boolean alternative = device.getBoolean("jt808.alternative");
        boolean alternative2 = device.getBoolean("jt808.alternative2");

        ByteBuf id = Unpooled.wrappedBuffer(
                DataConverter.parseHex(getUniqueId(command.getDeviceId())));

        try {
            ByteBuf data = Unpooled.buffer();
            byte[] time = DataConverter.parseHex((new SimpleDateFormat("yyMMddHHmmss")).format(new Date()));

            switch (command.getType()) {
                case Command.TYPE_ENGINE_STOP:
                    if (alternative) {
                        data.writeByte(1);
                        data.writeBytes(time);
                        return Jt808ProtocolDecoder.formatMessage(MSG_OIL_CONTROL, id, false, data);
                    }
                    if (alternative2) {
                        data.writeByte(240);
                        return Jt808ProtocolDecoder.formatMessage(MSG_TERMINAL_CONTROL, id, false, data);
                    }
                    data.writeByte(1);
                    return Jt808ProtocolDecoder.formatMessage(MSG_OIL_CONTROL_PRORRAC, id, false, data);

                case Command.TYPE_ENGINE_RESUME:
                    if (alternative) {
                        data.writeByte(0);
                        data.writeBytes(time);
                        return Jt808ProtocolDecoder.formatMessage(MSG_OIL_CONTROL, id, false, data);
                    }
                    if (alternative2) {
                        data.writeByte(241);
                        return Jt808ProtocolDecoder.formatMessage(MSG_TERMINAL_CONTROL, id, false, data);
                    }
                    data.writeByte(0);
                    return Jt808ProtocolDecoder.formatMessage(MSG_OIL_CONTROL_PRORRAC, id, false, data);

                case Command.TYPE_REBOOT_DEVICE:
                    data.writeByte(4);
                    return Jt808ProtocolDecoder.formatMessage(MSG_TERMINAL_CONTROL, id, false, data);

                default:
                    break;
            }

            return null;
        } finally {
            id.release();
        }
    }

}
