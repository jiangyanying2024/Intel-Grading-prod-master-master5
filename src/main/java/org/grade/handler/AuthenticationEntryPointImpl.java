package org.grade.handler;

import cn.hutool.json.JSONUtil;
import org.grade.common.Result;
import org.grade.utils.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        Result result = new Result(HttpStatus.UNAUTHORIZED.value(), "用户认证失败");
        String json = JSONUtil.toJsonStr(result);
        // 处理异常
        SecurityUtil.renderString(response, json);
    }
}
