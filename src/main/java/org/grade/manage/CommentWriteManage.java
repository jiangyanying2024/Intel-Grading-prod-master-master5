//package org.grade.manage;
//
//import cn.hutool.http.HttpRequest;
//import cn.hutool.http.HttpResponse;
//import cn.hutool.json.JSONUtil;
//import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
//import org.grade.bean.request.Comment.IntelCommentRequest;
//import org.grade.bean.request.Comment.PutCommentRequest;
//import org.grade.bean.request.User.StudentInquireRequest;
//import org.grade.bean.response.StudentResponse;
//import org.grade.common.Result;
//import org.grade.model.Comment;
//import org.grade.service.ICommentService;
//import org.grade.service.IUserService;
//import org.grade.utils.SecurityUtil;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.Resource;
//import java.util.HashMap;
//import java.util.List;
//
///**
// * @author lixin
// * @date 2024/5/28
// */
//@Service
//public class CommentWriteManage {
//    @Resource
//    private IUserService userService;
//    @Resource
//    private ICommentService commentService;
//
//    public Result queryClassList() {
//        String userId = SecurityUtil
//                .getUserFromHolder().getUserId();
//        String classStr = userService.selectClass(userId);
//        List<String> classList = JSONUtil.toList(classStr, String.class);
//
//        if (classList.isEmpty()) {
//            return Result.fail("该老师未教授任何班级");
//        }
//        return Result.ok(classList);
//    }
//
//    public Result queryStudentList(StudentInquireRequest request) {
//        // 获取班级和学校
//        String studentClass = request.getStudentClass();
//        String userSchool = SecurityUtil
//                .getUserFromHolder().getUserSchool();
//
//        List<StudentResponse> list = userService.studentList(studentClass, userSchool);
//        if (list.isEmpty()) {
//            return Result.fail("该班级学生尚未注册账号");
//        }
//        return Result.ok(list);
//    }
//
//    @Value("${api.comment.url}")
//    private String COMMENT_URL;
//    @Value("${api.comment.request.header}")
//    private String COMMENT_REQUEST_HEADER;
//    @Value("${api.comment.request.secret}")
//    private String COMMENT_REQUEST_SECRET;
//
//    public Result intelComment(IntelCommentRequest request) {
//        // 拼接请求参数，获取request中的数据
//        String name = request.getUserName();
//
//        StringBuilder sb = new StringBuilder();
//        sb.append("学生名字叫");
//        sb.append(name);
//        sb.append(", ");
//        for (String tag : request.getTags()) {
//            sb.append(tag);
//            sb.append(',');
//        }
//        sb.deleteCharAt(sb.length() - 1);
//
//        HashMap<String, Object> paramMap = new HashMap<>();
//        paramMap.put("student", sb.toString());
//
//        HttpResponse execute = HttpRequest.post(COMMENT_URL)
//                .header(COMMENT_REQUEST_HEADER, COMMENT_REQUEST_SECRET)
//                .form(paramMap)
//                .execute();
//
//        if (execute.getStatus() != 200) {
//            return Result.fail("云端调用失败");
//        }
//
//        return Result.ok(execute.body());
//    }
//
//    public Result updateComment(PutCommentRequest request) {
//        String userId = request.getUserId();
//
//        QueryWrapper<Comment> wrapper = new QueryWrapper<>();
//        wrapper.eq("user_id", userId);
//
//        Comment build = Comment.builder()
//                .commentContent(request.getCommentContent())
//                .commentName(request.getCommentName())
//                .userId(userId)
//                .build();
//        boolean flag = commentService.saveOrUpdate(build, wrapper);
//        if (!flag) {
//            return Result.fail("更新失败");
//        }
//
//        return Result.ok("评语保存成功");
//    }
//}
package org.grade.manage;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.gson.Gson;
import org.grade.bean.request.Comment.IntelCommentRequest;
import org.grade.bean.request.Comment.PutCommentRequest;
import org.grade.bean.request.User.StudentInquireRequest;
import org.grade.bean.response.StudentResponse;
import org.grade.common.Result;
import org.grade.model.Comment;
import org.grade.service.ICommentService;
import org.grade.service.IUserService;
import org.grade.utils.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lixin
 * @date 2024/5/28
 */
@Service
public class CommentWriteManage {
    @Resource
    private IUserService userService;
    @Resource
    private ICommentService commentService;

    public Result queryClassList() {
        String userId = SecurityUtil.getUserFromHolder().getUserId();
        String classStr = userService.selectClass(userId);
        List<String> classList = JSONUtil.toList(classStr, String.class);

        if (classList.isEmpty()) {
            return Result.fail("该老师未教授任何班级");
        }
        return Result.ok(classList);
    }

    public Result queryStudentList(StudentInquireRequest request) {
        // 获取班级和学校
        String studentClass = request.getStudentClass();
        String userSchool = SecurityUtil.getUserFromHolder().getUserSchool();

        List<StudentResponse> list = userService.studentList(studentClass, userSchool);
        if (list.isEmpty()) {
            return Result.fail("该班级学生尚未注册账号");
        }
        return Result.ok(list);
    }

    @Value("${api.comment.url}")
    private String COMMENT_URL;
//    @Value("${api.comment.request.header}")
//    private String COMMENT_REQUEST_HEADER;
    @Value("${api.comment.request.secret}")
    private String COMMENT_REQUEST_SECRET;

    public Result intelComment(IntelCommentRequest request) {
        // 拼接请求参数，获取request中的数据
        String name = request.getUserName();

        StringBuilder sb = new StringBuilder();
        sb.append("学生名字叫");
        sb.append(name);
        sb.append(", ");
        for (String tag : request.getTags()) {
            sb.append(tag);
            sb.append(',');
        }

        sb.deleteCharAt(sb.length() - 1);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("student", sb.toString());

        try {
            URL url = new URL(COMMENT_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // 设置请求方法为 POST
            connection.setRequestMethod("POST");

            // 设置请求头
            connection.setRequestProperty("Authorization", "Bearer " + COMMENT_REQUEST_SECRET);
            connection.setRequestProperty("Content-Type", "application/json");

            // 启用输出流
            connection.setDoOutput(true);

            // 构建请求体
            RequestBody requestBody = new RequestBody(
                    "qwen-plus",
                    new Message[] {
                            new Message("system", "你是生成教师评语的助手,请直接帮我生成正文,任何其他内容不要加"),
                            new Message("user", sb.toString())
                    }
            );

            Gson gson = new Gson();
            String jsonInputString = gson.toJson(requestBody);

            // 发送请求
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(jsonInputString.getBytes(StandardCharsets.UTF_8));
                wr.flush();
            }

            // 获取响应状态码
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 读取响应内容
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }

                    // 将响应内容转化为教师评语
                    String teacherComment = convertToTeacherComment(response.toString());

                    return Result.ok(teacherComment);
                }
            } else {
                return Result.fail("云端调用失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("云端调用失败");
        }
    }

    public Result updateComment(PutCommentRequest request) {
        String userId = request.getUserId();

        QueryWrapper<Comment> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);

        Comment build = Comment.builder()
                .commentContent(request.getCommentContent())
                .commentName(request.getCommentName())
                .userId(userId)
                .build();
        boolean flag = commentService.saveOrUpdate(build, wrapper);
        if (!flag) {
            return Result.fail("更新失败");
        }

        return Result.ok("评语保存成功");
    }

    static class Message {
        String role;
        String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    static class RequestBody {
        String model;
        Message[] messages;

        public RequestBody(String model, Message[] messages) {
            this.model = model;
            this.messages = messages;
        }
    }

    private String convertToTeacherComment(String jsonResponse) {
        // 使用Gson解析JSON响应
        Gson gson = new Gson();
        Map<String, Object> responseMap = gson.fromJson(jsonResponse, Map.class);

        // 提取相关信息
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) message.get("content");

        // 生成教师评语
        String teacherComment = content;

        return teacherComment;
    }
}
