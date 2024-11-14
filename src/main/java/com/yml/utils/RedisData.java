package com.yml.utils;


import lombok.Data;

import java.time.LocalDate;

@Data
public class RedisData {
    private LocalDate expireTime;
    private Object data;
}
