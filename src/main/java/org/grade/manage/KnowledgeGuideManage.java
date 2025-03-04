package org.grade.manage;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.benjaminwan.ocrlibrary.OcrResult;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import org.grade.bean.request.Comment.KnowledgeExtendRequest;
import org.grade.bean.request.Comment.QuestionCommentRequest;
import org.grade.common.Result;
import org.grade.common.ServiceException;
import org.grade.model.AnswerSheet;
import org.grade.model.Question;
import org.grade.model.TestPaper;
import org.grade.service.IQuestionService;
import org.grade.service.ITestPaperService;
import org.grade.service.IUserService;
import org.grade.service.impl.AnswerSheetServiceImpl;
import org.grade.utils.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;

/**
 * @author lixin
 * @date 2024/5/28
 */
@Service
public class KnowledgeGuideManage {
    @Resource
    private IUserService userService;
    @Resource
    private IQuestionService questionService;
    @Resource
    private AnswerSheetServiceImpl answerSheetService;
    @Resource
    private ITestPaperService testPaperService;
    private static final String SCORING_REQUEST_SECRET = "sk-4d0da57468b540b9a93b322fb2362597";

    @Value("${api.extend.url}")
    private String EXTEND_URL;
    @Value("${api.extend.request.secret}")
    private String EXTEND_REQUEST_SECRET;


    public Result intelGuide(KnowledgeExtendRequest request) {
        // 获取paperId和studentNumber
        String paperId = request.getPaperId();
        String userId = SecurityUtil.getUserFromHolder().getUserId();
        String studentNumber = userService.getStudentNumber(userId);
        // 获取答题卡图片数据流
        AnswerSheet sheet = answerSheetService.lambdaQuery()
                .eq(AnswerSheet::getPaperId, paperId)
                .eq(AnswerSheet::getStudentNumber, studentNumber)
                .one();
        if (Objects.isNull(sheet)) {
            throw new ServiceException("您的试卷尚未评阅，请等待！");
        }
        String imageUrl = sheet.getImageUrl();

        byte[] imageBytes = HttpUtil.downloadBytes(imageUrl);
        String suffix = imageUrl.substring(imageUrl.lastIndexOf('.'));
        String body = null;
        try {
            // 存入临时文件
            Path tempImagePath = Files.createTempFile("imageTemp", suffix);
            Files.write(tempImagePath, imageBytes, StandardOpenOption.CREATE);
            // 从临时文件中获取file
            File student = tempImagePath.toFile();

            // 2.3.使用OCR识别文本
            InferenceEngine engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);
            OcrResult studentOcrResult = engine.runOcr(student.getAbsolutePath());

            // 2.4.获取识别结果
            String studentText = studentOcrResult.getStrRes().trim();
            System.out.println("学生答案: " + studentText);

            // 2.5.构建请求体
            Message[] messages = new Message[] {
                    new Message("system", "你是负责通过学生答题卡给出答题建议和学生建议的助手,请直接帮我生成正文,任何其他内容不要加"),
                    new Message("user", "学生答案: " + studentText + ", 请提供具体的改进建议和提高方法。")
            };

            RequestBody requestBody = new RequestBody("qwen-plus", messages);
            Gson gson = new Gson();
            String jsonInputString = gson.toJson(requestBody);

            // 2.6.发送请求到大模型
            URL url = new URL(EXTEND_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // 设置请求方法为 POST
            connection.setRequestMethod("POST");

            // 设置请求头
            connection.setRequestProperty("Authorization", "Bearer " + EXTEND_REQUEST_SECRET);
            connection.setRequestProperty("Content-Type", "application/json");

            // 启用输出流
            connection.setDoOutput(true);



            // 发送请求
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 获取响应状态码
            int responseCode = connection.getResponseCode();
            System.out.println("响应状态码: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 读取响应内容
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    System.out.println("AI反馈答案: " + response.toString());

                    // 将响应内容转化为JSON对象
                    Map<String, Object> responseMap = gson.fromJson(response.toString(), new HashMap<String, Object>().getClass());
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> choice = choices.get(0);
                        Map<String, Object> message = (Map<String, Object>) choice.get("message");
                        String content = (String) message.get("content");

                        // 删除临时文件
                        Files.delete(tempImagePath);

                        return Result.ok(200,"AI反馈成功", content);
                    } else {
                        return Result.fail("无法解析AI反馈");
                    }
                }
            } else {
                return Result.fail("云端调用失败，错误码：" + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("处理请求时发生错误: " + e.getMessage());
        }
    }

    public Result queryPaperList(QuestionCommentRequest request) {
        // 获取学生id
        String userId = SecurityUtil.getUserFromHolder().getUserId();

        // 1.构建查询条件，根据schoolName查询样卷（本校管理者只能查询本校试卷）
        // 1.1.获取schoolName
        String schoolName = request.getSchoolName();
        // 1.2.构建查询条件
        QueryWrapper<TestPaper> queryWrapper = new QueryWrapper<>();

        queryWrapper.lambda()
                .eq(TestPaper::getSchoolName, schoolName)
                .eq(TestPaper::getPaperSubject, request.getPaperSubject())
                .orderByDesc(TestPaper::getCreateTime);

        List<TestPaper> testPaperPage = testPaperService.list(queryWrapper);

        // 4.如果查询不到数据
        if (testPaperPage.isEmpty()) {
            return Result.fail("您所在的学校尚未上传过样卷");
        }

        // 6.新建返回对象
        QuestionCommentRequest qusetionComment = new QuestionCommentRequest();
        // 7.将试卷列表放入新建对象中
        qusetionComment.setTestPaperPage(testPaperPage);
        // 8.查询该试卷的考情分析记录
        LambdaQueryWrapper<Question> lqw = new LambdaQueryWrapper<>();
        lqw.eq(Question::getPaperId, request.getPaperId())
                .eq(Question::getUserId, userId);
        // 9.新建question对象
        Question question = questionService.getOne(lqw);
        System.out.println(("==================================="));
        System.out.println(userId);
        System.out.println(request.getPaperId());
        System.out.println(("==================================="));
        // 10.将question对象加入返回对象
        qusetionComment.setQuestion(question);

        return Result.ok(qusetionComment);
    }

    // 获取该试卷学情扩展的所有记录
    public Result queryQuestionRecord(QuestionCommentRequest request) {
        // 获取学生id
        String userId = SecurityUtil.getUserFromHolder().getUserId();

        // 查询该试卷的考情分析记录
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper
                .eq(Question::getPaperId, request.getPaperId())
                .eq(Question::getUserId, userId);
        // 新建question对象
        Question question = questionService.getOne(wrapper);
        // 将字符串转化成json对象
        if (question != null) {
            JSONArray jsonArray = new JSONArray(question.getQuestionContent());
            return Result.ok(jsonArray);
        }
        return Result.ok(question);
    }

    // 修改或者添加该试卷的知识拓展
    public Result change(QuestionCommentRequest request) {
        // 获取学生id
        String userId = SecurityUtil.getUserFromHolder().getUserId();
        System.out.println("++++++++++++++++++++++++++++++++++");
        System.out.println(request.getPaperContent());
        JSONArray jsonArray = new JSONArray(request.getPaperContent());
        String paperContent = jsonArray.toString();
        // 新建一个question，并且放入所有数据
        Question question = new Question();
        question.setPaperId(request.getPaperId());
        question.setUserId(userId);
        question.setQuestionContent(paperContent);
        // 构建更新条件
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(Question::getUserId, userId)
                .eq(Question::getPaperId, request.getPaperId());

        // 检查记录是否存在
        int existingRecords = questionService.count(queryWrapper);
        if (existingRecords > 0) {
            // 如果记录存在，则更新
            boolean rowsAffected = questionService.update(question, queryWrapper);
            if (rowsAffected) {
                return Result.ok("修改成功"); // 更新成功
            }
        }
        if (existingRecords <= 0) {
            // 如果记录不存在，则插入
            boolean rowsInserted = questionService.save(question);
            if (rowsInserted) {
                return Result.ok("新建成功"); // 插入新数据成功
            }
        }
        return Result.fail("请求失败，请重试");
    }

    // 定义Message类
    static class Message {
        String role;
        String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    // 定义RequestBody类
    static class RequestBody {
        String model;
        Message[] messages;

        public RequestBody(String model, Message[] messages) {
            this.model = model;
            this.messages = messages;
        }
    }
}