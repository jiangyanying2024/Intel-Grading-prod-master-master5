// 导入 OCR 结果类，用于存储和处理 OCR 识别的结果
import com.benjaminwan.ocrlibrary.OcrResult;

// 导入模型枚举类，定义了可用的 OCR 模型类型
import io.github.mymonstercat.Model;

// 导入推理引擎类，用于加载模型并执行 OCR 推理
import io.github.mymonstercat.ocr.InferenceEngine;

/**
 * 主类，用于演示如何使用 RapidOCR 库进行图像中的文字识别。
 */
public class Main {


    public static void main(String[] args) {
        // 获取 InferenceEngine 的单例实例，并指定使用 ONNX_PPOCR_V3 模型
        InferenceEngine engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);

        // 使用推理引擎对指定路径的图片进行 OCR 识别
        OcrResult ocrResult = engine.runOcr("F:\\QQ20250212-004654.jpg");

        // 打印 OCR 识别结果，去除多余的空白字符
        System.out.println(ocrResult.getStrRes().trim());
    }
}
