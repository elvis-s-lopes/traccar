package org.traccar.protocol;

import org.traccar.BaseProtocol;
import org.traccar.PipelineBuilder;
import org.traccar.TrackerServer;
import org.traccar.config.Config;
import org.traccar.model.Command;

import jakarta.inject.Inject;

public class Jt808Protocol extends BaseProtocol {

    @Inject
    public Jt808Protocol(Config config) {
        setSupportedDataCommands(
                Command.TYPE_ENGINE_STOP,
                Command.TYPE_ENGINE_RESUME,
                Command.TYPE_REBOOT_DEVICE);
        addServer(new TrackerServer(config, getName(), false) {
            @Override
            protected void addProtocolHandlers(PipelineBuilder pipeline, Config config) {
                pipeline.addLast(new Jt808FrameDecoder());
                pipeline.addLast(new Jt808ProtocolEncoder(Jt808Protocol.this));
                pipeline.addLast(new Jt808ProtocolDecoder(Jt808Protocol.this));
            }
        });
    }

}
