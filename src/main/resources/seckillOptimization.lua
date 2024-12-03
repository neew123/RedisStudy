---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by yuanmengli.
--- DateTime: 2024/12/3 11:50
---

--- 参数列表:优惠券id、用户id
local voucherId = ARGV[1]
local userId = ARGV[2]
--- key:库存key、订单key
local stockKey = 'seckill:stock:'..voucherId
local orderKey = 'seckill:order:'..voucherId

--- 判断库存是否充足
if(tonumber(redis.call('get',stockKey)) <= 0) then
    return 1
end
--- 判断用户是否重复下单
if(redis.call('sismember',orderKey,userId) == 1) then
    return 2
end

--- 扣库存、记录下单用户
redis.call('incrby',stockKey,-1)
redis.call('sadd',orderKey,userId)
return 0