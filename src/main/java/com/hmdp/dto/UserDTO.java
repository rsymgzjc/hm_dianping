package com.hmdp.dto;

import com.hmdp.entity.User;
import lombok.*;

import java.io.Serializable;

@Data
public class UserDTO extends User implements Serializable {
    private Long id;
    private String nickName;
    private String icon;
}
