package org.traccar.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.traccar.model.Device;
import org.traccar.model.Position;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import java.util.Date;

public class RedisManager {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JedisPool pool;

    public RedisManager(String host, int port) {
        this.pool = new JedisPool(host, port);

        clearOldConnections();
    }

    private void clearOldConnections() {
        try (Jedis jedis = pool.getResource()) {
            for (String key : jedis.keys("connected.*")) {
                jedis.del(key);
            }
        } catch (Exception e) {
        }
    }

    public void writePosition(Position position, String uniqueId) throws Exception {
        Date deviceTime = position.getDeviceTime();
        Date fixTime = position.getFixTime();
        long time = 0L;

        if (time == 0L && fixTime != null) {
            time = fixTime.getTime();
        }
        if (time == 0L && deviceTime != null) {
            time = deviceTime.getTime();
        }
        if (time == 0L) {
            time = System.currentTimeMillis();
        }

        String key = "positions." + uniqueId;
        String value = objectMapper.writeValueAsString(position);

        try (Jedis jedis = pool.getResource()) {
            jedis.lpush(key, value);
        }
    }

    public void addDevice(Device device) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setnx("connected." + device.getUniqueId(), String.valueOf(new Date().getTime()));
        } catch (Exception e) {
        }
    }

    public void removeDevice(Device device) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del("connected." + device.getUniqueId());
        } catch (Exception e) {
        }
    }

    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
