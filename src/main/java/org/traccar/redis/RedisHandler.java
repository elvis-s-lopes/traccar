package org.traccar.redis;

import jakarta.inject.Inject;
import org.traccar.model.Position;
import org.traccar.session.DeviceSession;

public class RedisHandler {

    private final RedisPositionManager redisPositionManager;

    @Inject
    public RedisHandler(RedisPositionManager redisPositionManager) {
        this.redisPositionManager = redisPositionManager;
    }

    public void handle(Position position, DeviceSession deviceSession) {
        if (position != null && deviceSession != null) {
            String uniqueId = deviceSession.getUniqueId();
            if (uniqueId != null) {
                redisPositionManager.store(position, uniqueId);
            }
        }
    }
}
