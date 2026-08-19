package com.minhtriet.autopsy.ratelimit;

import org.antlr.v4.runtime.Token;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TokenBucketRateLimit {

    private final StringRedisTemplate redis;
    private final long capacity;
    private final double refillPerSec;
    private final DefaultRedisScript<Long> script;

    public TokenBucketRateLimit(StringRedisTemplate redis,
                                @Value("${ratelimit.capacity}") long capacity,
                                @Value("${ratelimit.refill-per-sec}") double refillPerSec) {
        this.redis = redis;
        this.capacity = capacity;
        this.refillPerSec = refillPerSec;
        this.script = new DefaultRedisScript<>(LUA,  Long.class);
    }

    public boolean allow(String key) {
        Long allowed = redis.execute(script, List.of("ratelimit:" + key),
                String.valueOf(capacity),
                String.valueOf(refillPerSec),
                String.valueOf(System.currentTimeMillis()));
        return allowed != null && allowed == 1L;
    }

    private static final String LUA= """
        local key      = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local refill   = tonumber(ARGV[2])       -- tokens mỗi giây
        local now      = tonumber(ARGV[3])       -- ms

        local data   = redis.call('HMGET', key, 'tokens', 'ts')
        local tokens = tonumber(data[1])
        local ts     = tonumber(data[2])
        if tokens == nil then tokens = capacity; ts = now end

        local elapsed = math.max(0, now - ts) / 1000.0     -- hồi token theo thời gian trôi
        tokens = math.min(capacity, tokens + elapsed * refill)

        local allowed = 0
        if tokens >= 1 then
            tokens = tokens - 1
            allowed = 1
        end

        redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
        redis.call('PEXPIRE', key, 3600000)               -- key rảnh 1h thì tự dọn
        return allowed
        """;
}