--local key = KEYS[1];
--local thread1 = ARGV[1];

--local id = redis.call('get',KEYS[1]);
if(redis.call('get',KEYS[1])==ARGV[1])then
    return redis.call('del',KEYS[1]);
end
return 0;