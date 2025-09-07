package org.traccar.redis;

import jakarta.inject.Inject;
import org.traccar.model.Position;

public class RedisPositionManager {

    private final RedisManager redisManager;

    @Inject
    public RedisPositionManager(RedisManager redisManager) {
        this.redisManager = redisManager;
    }

    public void store(Position position, String uniqueId) {
        try {
            redisManager.writePosition(position, uniqueId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
