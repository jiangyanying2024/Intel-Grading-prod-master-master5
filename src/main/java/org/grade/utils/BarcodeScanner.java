package org.grade.utils;

import cn.hutool.extra.qrcode.BufferedImageLuminanceSource;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class BarcodeScanner {

    public static String scanBarcode(String imagePath) throws IOException, NotFoundException {
        File file;

        // 如果是 URL，则先下载到本地
        if (imagePath.startsWith("http")) {
            try (InputStream in = new URL(imagePath).openStream()) {
                Path tempFile = Files.createTempFile("barcode", ".png");
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                file = tempFile.toFile();
            }
        } else {
            file = new File(imagePath);
        }

        // 检查文件是否存在
        if (!file.exists()) {
            throw new IOException("File does not exist: " + imagePath);
        }

        // 检查是否有读取权限
        if (!file.canRead()) {
            throw new IOException("No read permission for file: " + imagePath);
        }

        // 尝试读取图像
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                throw new IOException("Failed to read image from file: " + imagePath);
            }

            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            MultiFormatReader reader = new MultiFormatReader();
            Result result = reader.decode(bitmap);

            return result.getText();
        } catch (IIOException e) {
            throw new IOException("Error reading image file: " + imagePath, e);
        } finally {
            // 清理临时文件
            if (imagePath.startsWith("http") && file != null) {
                file.delete();
            }
        }
    }
}
