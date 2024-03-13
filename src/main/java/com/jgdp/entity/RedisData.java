package com.jgdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 喜欢悠然独自在
 * @version 1.0
 */
@Data
public class RedisData {

    //实体对象
    private Object data;

    //逻辑过期时间
    private LocalDateTime expireTime;

}
