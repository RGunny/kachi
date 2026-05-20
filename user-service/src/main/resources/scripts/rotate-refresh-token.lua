if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

redis.call('DEL', KEYS[1])
redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2])
return 1
