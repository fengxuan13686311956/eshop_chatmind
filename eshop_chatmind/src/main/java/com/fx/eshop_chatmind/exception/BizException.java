package com.fx.eshop_chatmind.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 400;
    }
}
