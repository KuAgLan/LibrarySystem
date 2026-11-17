package com.library.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 系统设置（键值） */
@Data
@TableName("setting")
public class Setting {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String k;
    private String v;
}
