package org.traccar.redis;

import jakarta.inject.Inject;
import org.traccar.model.Device;
import org.traccar.model.Position;
import org.traccar.session.DeviceSession;
import org.traccar.session.cache.CacheManager;

public class RedisHandler {
    private final RedisPositionManager redisPositionManager;
    private final CacheManager cacheManager;

    @Inject
    public RedisHandler(RedisPositionManager redisPositionManager, CacheManager cacheManager) {
        this.redisPositionManager = redisPositionManager;
        this.cacheManager = cacheManager;
    }

    public void handle(Position position, DeviceSession deviceSession) {
        if (position != null) {
            String uniqueId = getUniqueId(position, deviceSession);

            if (uniqueId != null) {
                redisPositionManager.store(position, uniqueId);
            }
        }
    }

    private String getUniqueId(Position position, DeviceSession deviceSession) {
        String uniqueId = null;

        if (deviceSession != null) {
            uniqueId = deviceSession.getUniqueId();
        }

        if (uniqueId == null) {
            Device device = cacheManager.getObject(Device.class, position.getDeviceId());
            if (device != null) {
                uniqueId = device.getUniqueId();
            }
        }

        return uniqueId;
    }

    public void addDevice(Device device) {
        if (device != null && device.getUniqueId() != null) {
            redisPositionManager.addDevice(device);
        }
    }

    public void removeDevice(Device device) {
        if (device != null && device.getUniqueId() != null) {
            redisPositionManager.removeDevice(device);
        }
    }
}
