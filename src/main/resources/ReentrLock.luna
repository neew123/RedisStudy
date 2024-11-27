-- 获取锁的luna脚本
local ley = KEYS[1];
local threadId = ARGV[1];
local releaseTime = ARGV[2];
-- 判断锁是否存在
if (redis.call('exists',key) == 0) then
    -- 锁不存在,获取suo
    redis.call('hset',key,thread,'1');
    -- 设置有效期
    redis.call('expire',key,releaseTime);
    -- 返回结果
    return 1;
end;
-- 锁存在,判断是否是当前线程
if (redis.call('hexists',key,threadId) == 1) then
    -- 是当前线程,获取锁,锁加1
    redis.call('hincrby',key,thread,'1');
    -- 设置有效期
    redis.call('expire',key,releaseTime);
    -- 返回结果
    return 1;
end;
return 0; -- 获取锁失败