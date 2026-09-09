package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Cache degrades to in-memory when Redis is unreachable; never throws. */
@ExtendWith(MockitoExtension.class)
class LookupCacheTest {

  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> ops;

  private LookupCache cache() {
    return new LookupCache(redis);
  }

  @Test
  void miss_returnsNull() {
    when(redis.opsForValue()).thenReturn(ops);
    when(ops.get("etg:lookup:+15550008001")).thenReturn(null);
    assertThat(cache().get("+15550008001")).isNull();
  }

  @Test
  void hit_returnsCachedLineType() {
    when(redis.opsForValue()).thenReturn(ops);
    when(ops.get("etg:lookup:+15550008002")).thenReturn("mobile");
    assertThat(cache().get("+15550008002")).contains(Optional.of("mobile"));
  }

  @Test
  void negativeHit_returnsEmpty() {
    when(redis.opsForValue()).thenReturn(ops);
    when(ops.get("etg:lookup:+15550008003")).thenReturn("NONE");
    assertThat(cache().get("+15550008003")).contains(Optional.empty());
  }

  @Test
  void redisDown_fallsBackToLocal() {
    when(redis.opsForValue()).thenThrow(new RuntimeException("connection refused"));
    LookupCache c = cache();
    assertThat(c.get("+15550008004")).isNull(); // miss, no throw
    c.put("+15550008004", Optional.of("voip"), Duration.ofHours(1));
    assertThat(c.get("+15550008004")).contains(Optional.of("voip"));
  }

  @Test
  void put_writesThroughToRedis() {
    when(redis.opsForValue()).thenReturn(ops);
    cache().put("+15550008005", Optional.empty(), Duration.ofHours(24));
    verify(ops).set(eq("etg:lookup:+15550008005"), eq("NONE"), eq(Duration.ofHours(24)));
  }
}
