package com.etg.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Lookup line-type cache: Redis first, in-memory fallback when Redis is absent.
 * Either backend short-circuits to local on any failure so sends never block on cache.
 */
@Component
public class LookupCache {
  private static final Logger log = LoggerFactory.getLogger(LookupCache.class);
  static final String PREFIX = "etg:lookup:";
  private static final String NONE = "NONE";

  private final StringRedisTemplate redis;
  private final Map<String, Entry> local = new ConcurrentHashMap<>();

  private record Entry(String value, Instant expiresAt) {}

  public LookupCache(StringRedisTemplate redis) { this.redis = redis; }

  /** Null = cache miss; Optional.empty() = known non-mobile/unknown line. */
  public Optional<Optional<String>> get(String e164) {
    String hit = read(PREFIX + e164);
    if (hit == null) return null;
    return Optional.of(NONE.equals(hit) ? Optional.empty() : Optional.of(hit));
  }

  public void put(String e164, Optional<String> lineType, Duration ttl) {
    write(PREFIX + e164, lineType.orElse(NONE), ttl);
  }

  private String read(String key) {
    try {
      String v = redis.opsForValue().get(key);
      if (v != null) return v;
    } catch (Exception e) {
      log.debug("Redis read failed, using local cache: {}", e.getMessage());
    }
    Entry e = local.get(key);
    if (e == null || Instant.now().isAfter(e.expiresAt())) {
      local.remove(key);
      return null;
    }
    return e.value();
  }

  private void write(String key, String value, Duration ttl) {
    try {
      redis.opsForValue().set(key, value, ttl);
      return;
    } catch (Exception e) {
      log.debug("Redis write failed, using local cache: {}", e.getMessage());
    }
    local.put(key, new Entry(value, Instant.now().plus(ttl)));
  }
}
