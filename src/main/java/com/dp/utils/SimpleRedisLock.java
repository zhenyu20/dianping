package com.dp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 依旧存在问题：不可重入，不可重试，锁超时释放，分布式Redis主从复制
 */
public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    //Lua
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 1.获取标示
        //版本一：线程Id
//        String threadId = Thread.currentThread().getId() + "";
        //版本二：UUID +线程ID
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 2.获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 版本三：使用lua脚本保证原子性
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

    public void unlock1() {
        // 版本二：获取标示，判断是不是自己的锁，是才删除
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁中的标识
        String cacheId = stringRedisTemplate.opsForValue()
                .get(KEY_PREFIX + name);
        if(threadId.equals(cacheId)){
            //版本一:直接删除
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
