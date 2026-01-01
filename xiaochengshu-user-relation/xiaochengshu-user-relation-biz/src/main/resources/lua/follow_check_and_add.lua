local key = KEYS[1]
local followUserId = ARGV[1]
local timestamp = ARGV[2]

-- EXISTS 检查 ZSET 是否存在
local exists=redis.call('EXISTS',key)
if(exists==0) then
    return -1
end

-- ZCARD 校验关注人数是否上限（是否达到 1000）
local size=redis.call('ZCARD',key)
if(size>=1000) then
    return -2
end

-- ZSCORE 校验是否已经关注该用户
if(redis.call('ZSCORE',key,followUserId)) then
    return -3
end

-- ZADD 添加关注关系
redis.call('ZADD',key,timestamp,followUserId);
return 0