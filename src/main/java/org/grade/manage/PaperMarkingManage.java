package org.grade.manage;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.benjaminwan.ocrlibrary.OcrResult;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import org.grade.bean.dto.IntelGradingDTO;
import org.grade.bean.request.Region.*;
import org.grade.bean.request.Task.TaskPageRequest;
import org.grade.bean.response.RegionResponse;
import org.grade.common.Result;
import org.grade.common.ServiceException;
import org.grade.model.AnswerSheet;
import org.grade.model.Region;
import org.grade.model.Task;
import org.grade.service.IAnswerSheetService;
import org.grade.service.IRegionService;
import org.grade.service.ITaskService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;

/**
 * @author lixin
 * @date 2024/5/28
 */
@Component
public class PaperMarkingManage {
    @Resource
    private IAnswerSheetService answerSheetService;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private IRegionService regionService;
    @Resource
    private ITaskService taskService;
    @Value("${api.grade.url}")
    private String SCORING_URL;
    @Value("${api.grade.request.secret}")
    private String SCORING_REQUEST_SECRET;

    public Result queryTaskPage(TaskPageRequest request) {
        String userId = request.getUserId();
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getUserId, userId)
                .orderByDesc(Task::getEndTime);

        Page<Task> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<Task> iPage = taskService.page(page, queryWrapper);

        if (iPage.getTotal() == 0L) {
            return Result.fail("您尚未被分配阅卷任务");
        }

        return Result.ok(iPage);
    }

    public Result queryGradingRegion(RegionListRequest request) {
        // 根据userId，paperId和regionNumber，获取需要阅的图片集
        // 1.前置准备
        // 1.1.获取paperId、regionNumber和userId，定义map存储区域
        List<RegionResponse> list = new ArrayList<>();
        String paperId = request.getPaperId();
        Integer regionNumber = request.getRegionNumber();
        String userId = request.getUserId();
        // 1.2.stream流获取答题卡Id集合
        List<AnswerSheet> sheetList = answerSheetService.lambdaQuery()
                .eq(AnswerSheet::getPaperId, paperId).list();
        if (CollectionUtils.isEmpty(sheetList)) {
            throw new ServiceException("未查询到题目");
        }
        List<String> sheetIds = sheetList.stream()
                .map(AnswerSheet::getSheetId)
                .collect(Collectors.toList());
        // 1.3.根据sheetIds集合和regionNumber、userId获取需要评阅的对应区域
        for (String sheetId : sheetIds) {
            Region region = regionService.lambdaQuery()
                    .eq(Region::getSheetId, sheetId)
                    .eq(Region::getRegionNumber, regionNumber)
                    .isNotNull(Region::getUserId)
                    .eq(Region::getUserId, userId)
                    .one();
            if (ObjectUtil.isNull(region)) {
                continue;
            }
            RegionResponse build = RegionResponse.builder()
                    .regionImage(region.getRegionImage())
                    .isGraded(region.getIsGraded())
                    .regionId(region.getRegionId()).build();
            list.add(build);
        }
        if (list.isEmpty()) {
            return Result.fail("该题组尚未划分区域");
        }

        return Result.ok(list);
    }

    public Result queryRegionScore(RegionGetScoreRequest request) {
        String regionId = request.getRegionId();
        // 获取评分
        Integer regionScore = regionService.lambdaQuery()
                .eq(Region::getRegionId, regionId)
                .one().getRegionScore();
        return Result.ok(regionScore);
    }

    public Result updateRegionScore(RegionPutScoreRequest request) {
        String regionId = request.getRegionId();
        Integer regionScore = request.getRegionScore();
        boolean update = regionService.lambdaUpdate()
                .eq(Region::getRegionId, regionId)
                .set(Region::getRegionScore, regionScore)
                .set(Region::getIsGraded, true).update();
        if (!update) {
            throw new ServiceException("评分更新失败");
        }
        return Result.ok("评分更新成功");
    }

    public Result queryEvaluatedCount(RegionGetNumberRequest request) {
        String paperId = request.getPaperId();
        Integer regionNumber = request.getRegionNumber();
        String userId = request.getUserId();
        int num = 0;
        // 根据paperId查询所有答题卡
        List<AnswerSheet> list = answerSheetService.lambdaQuery()
                .eq(AnswerSheet::getPaperId, paperId).list();
        if (CollectionUtils.isEmpty(list)) {
            throw new ServiceException("当前题组尚未划分阅卷区域");
        }
        List<String> sheetIds = list.stream()
                .map(AnswerSheet::getSheetId)
                .collect(Collectors.toList());

        for (String sheetId : sheetIds) {
            Region region = regionService.lambdaQuery()
                    .eq(Region::getSheetId, sheetId)
                    .eq(Region::getRegionNumber, regionNumber)
                    .isNotNull(Region::getUserId)
                    .eq(Region::getUserId, userId)
                    .one();
            if (ObjectUtil.isNotNull(region) && BooleanUtil.isTrue(region.getIsGraded())) {
                num++;
            }
        }

        return Result.ok(num);
    }

    public Result putIntelScore(RegionIntelScoreRequest request, MultipartFile images) {
        // 1.获取对应参数
        String regionImage = request.getRegionImage();
        String regionId = request.getRegionId();
        String filename = images.getOriginalFilename();

        // 2.与算法接口进行联调
        try {
            // 2.1.获取学生答案和标准答案的二进制
            byte[] studentBytes = HttpUtil.downloadBytes(regionImage);
            byte[] answerBytes = images.getBytes();

            // 2.2.将字节数组保存为临时文件
            File studentImageFile = File.createTempFile("student", ".jpg");
            File answerImageFile = File.createTempFile("answer", ".jpg");
            Files.write(studentImageFile.toPath(), studentBytes);
            Files.write(answerImageFile.toPath(), answerBytes);

            // 2.3.使用OCR识别文本
            InferenceEngine engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);
            OcrResult studentOcrResult = engine.runOcr(studentImageFile.getAbsolutePath());
            OcrResult answerOcrResult = engine.runOcr(answerImageFile.getAbsolutePath());

            // 2.4.获取识别结果
            String studentText = studentOcrResult.getStrRes().trim();
            String answerText = answerOcrResult.getStrRes().trim();
            System.out.println("学生答案: " + studentText);
            System.out.println("标准答案: " + answerText);

            // 2.5.构建请求体
            Message[] messages = new Message[] {
                    new Message("system", "你是负责给学生打分的助手,请直接帮我生成正文,任何其他内容不要加"),
                    new Message("user", "学生答案: " + studentText + ", 标准答案: " + answerText + ", 请给我一个分数，以score:xx的形式返回答案，其中xx是一个整数。请确保评分基于题目要求的内容。")
            };

            RequestBody requestBody = new RequestBody("qwen-plus", messages);
            Gson gson = new Gson();
            String jsonInputString = gson.toJson(requestBody);

            // 2.6.发送请求到大模型
            URL url = new URL(SCORING_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // 设置请求方法为 POST
            connection.setRequestMethod("POST");

            // 设置请求头
            connection.setRequestProperty("Authorization", "Bearer " + SCORING_REQUEST_SECRET);
            connection.setRequestProperty("Content-Type", "application/json");

            // 启用输出流
            connection.setDoOutput(true);

            // 发送请求
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(jsonInputString.getBytes(StandardCharsets.UTF_8));
                wr.flush();
            }

            // 获取响应状态码
            int responseCode = connection.getResponseCode();
            System.out.println("响应状态码: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 读取响应内容
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    System.out.println("AI评分答案: " + response.toString());

                    // 将响应内容转化为分数
                    Map<String, Object> responseMap = gson.fromJson(response.toString(), new HashMap<String, Object>().getClass());
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> choice = choices.get(0);
                        Map<String, Object> message = (Map<String, Object>) choice.get("message");
                        String content = (String) message.get("content");

                        // 提取分数
                        String[] parts = content.split("score:");
                        if (parts.length == 2) {
                            String scoreStr = parts[1].trim();
                            try {
                                int score = Integer.parseInt(scoreStr.split("\n")[0].trim());

                                // 2.7.将分数更新到数据库
                                boolean update = regionService.lambdaUpdate()
                                        .eq(Region::getRegionId, regionId)
                                        .set(Region::getRegionScore, score)
                                        .set(Region::getIsGraded, true).update();
                                if (!update) {
                                    throw new ServiceException("评分更新失败");
                                }

                                // 2.8.删除临时文件
                                studentImageFile.delete();
                                answerImageFile.delete();

                                // 创建一个包含分数的响应对象
                                Map<String, Object> responseObj = new HashMap<>();
                                responseObj.put("content", content);
                                responseObj.put("score", scoreStr);

                                // 创建 IntelGradingDTO 对象
                                HashMap<String, Object> paramMap = new HashMap<>();
                                paramMap.put("content", content);
                                paramMap.put("score", score);
                                IntelGradingDTO gradingDTO = new IntelGradingDTO(regionId, paramMap);

                                // 发送消息到 RabbitMQ
                                rabbitTemplate.convertAndSend("intel.topic", "intel.grade", gradingDTO);

                                return Result.ok(200,"AI评阅成功", responseObj);
                            } catch (NumberFormatException e) {
                                // 捕获并记录详细的错误信息
                                System.err.println("无法解析评分结果: " + scoreStr + " - " + e.getMessage());
                                return Result.fail("无法解析评分结果: " + scoreStr);
                            }
                        } else {
                            return Result.fail("无法解析评分结果");
                        }
                    } else {
                        return Result.fail("无法解析评分结果");
                    }
                }
            } else {
                // 读取错误流中的错误信息
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    String errorLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    return Result.fail("云端调用失败，错误信息: " + errorResponse.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("评分失败: " + e.getMessage());
        }
    }


    private int calculateScore(String studentText, String answerText) {
        // 这里可以根据具体需求实现评分逻辑
        // 例如，简单的字符串匹配
        if (studentText.equals(answerText)) {
            return 100; // 假设满分是100分
        } else {
            return 0; // 如果不匹配则得0分
        }
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
}
