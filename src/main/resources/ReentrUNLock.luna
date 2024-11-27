-- 释放锁的luna脚本
local ley = KEYS[1];
local threadId = ARGV[1];
local releaseTime = ARGV[2];

-- 判断当前锁是否还是自己持有，不是自己直接返回，是自己则重入次数-1
if (redis.call('hexists',key,threadId) == 0) then
    return nil;
end;
-- 重入次数-1
local count = redis.call('hincrby',key,threadId,-1);
if(count>0) then
    -- 重入次数大于0，不能释放锁并重置有效期
    redis.call('expire',key,releaseTime);
    return nil;
else
    -- 重入次数等于0，删除锁
    redis.call('del',key);
    return nil;
end;