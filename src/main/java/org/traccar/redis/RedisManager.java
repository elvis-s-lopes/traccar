package org.traccar.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.traccar.model.Position;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisManager {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JedisPool pool;

    public RedisManager(String host, int port) {
        this.pool = new JedisPool(host, port);
    }

    public void writePosition(Position position, String uniqueId) throws Exception {
        String key = "positions." + uniqueId;
        String value = objectMapper.writeValueAsString(position);

        try (Jedis jedis = pool.getResource()) {
            jedis.lpush(key, value);
        }
    }
}
