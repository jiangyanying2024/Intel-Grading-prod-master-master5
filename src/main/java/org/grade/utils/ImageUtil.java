package org.grade.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.imageio.ImageIO;

public class ImageUtil {

    /**
     * 裁剪图片的方法
     * @param jsonStr 包含裁剪坐标信息的 JSON 字符串
     * @param imageUrl 图片的 URL
     * @return 裁剪后的图片字节数组
     * @throws IOException 如果在处理过程中发生 I/O 错误
     */
    public static byte[] cropImage(String jsonStr, String imageUrl) throws IOException {
        // 使用 Gson 解析 JSON 字符串
        Gson gson = new Gson();
        JsonObject rect = gson.fromJson(jsonStr, JsonObject.class);

        // 获取图片
        BufferedImage image = getImageFromUrl(imageUrl);

        // 提取矩形的坐标信息
        int left = rect.get("left").getAsInt();
        int top = rect.get("top").getAsInt();
        int width = rect.get("width").getAsInt();
        int height = rect.get("height").getAsInt();
        int right = left + width;
        int bottom = top + height;

        // 裁剪图片
        BufferedImage croppedImage = image.getSubimage(left, top, width, height);

        // 将裁剪后的图片保存到字节数组中
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(croppedImage, "PNG", baos);
        byte[] croppedImageBytes = baos.toByteArray();

        return croppedImageBytes;
    }

    /**
     * 从 URL 获取图片
     * @param imageUrl 图片的 URL
     * @return 图片的 BufferedImage 对象
     * @throws IOException 如果在处理过程中发生 I/O 错误
     */
    private static BufferedImage getImageFromUrl(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        InputStream inputStream = connection.getInputStream();
        return ImageIO.read(inputStream);
    }

    public static void main(String[] args) {
        try {
            // 示例 JSON 字符串
            String jsonStr = "{\"left\": 100, \"top\": 100, \"width\": 200, \"height\": 200}";
            String imageUrl = "https://modox.oss-cn-hangzhou.aliyuncs.com/grade/answersheet/西部第一高级中学/高三1班/27bbf73d-c066-419b-b3c6-6293e0f78b7e.png";

            // 调用裁剪图片的方法
            byte[] croppedImageBytes = cropImage(jsonStr, imageUrl);

            // 输出图片的字节流，前缀加上内容长度
            System.out.print(croppedImageBytes.length);
            System.out.write(croppedImageBytes);
            System.out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}