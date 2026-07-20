package com.smartdrive.file.service;

import com.smartdrive.common.constant.Constants;
import com.smartdrive.file.config.FileAppConfig;
import com.smartdrive.file.entity.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * AI 摘要服务：读取文件内容+文件名，调用 OpenAI 兼容 API 生成摘要。
 * 生成结果返回前端填入输入框，用户可编辑后再保存。
 */
@Service
public class AiSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(AiSummaryService.class);

    private final FileAppConfig appConfig;
    private final FileInfoService fileInfoService;
    private final RestTemplate restTemplate;

    @Value("${ai.summary.enabled:true}")
    private boolean enabled;

    @Value("${ai.summary.api-key:}")
    private String apiKey;

    @Value("${ai.summary.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${ai.summary.model:gpt-3.5-turbo}")
    private String model;

    /** 可直接提取文本的文件后缀 */
    private static final List<String> TEXT_SUFFIXES = List.of(
            "txt", "md", "java", "py", "js", "ts", "vue", "html", "css", "scss",
            "xml", "json", "yaml", "yml", "sql", "sh", "bat", "c", "cpp", "h",
            "cs", "go", "rs", "rb", "php", "swift", "kt", "scala", "log", "csv"
    );

    /** 支持AI摘要的文件后缀（含Office/PDF） */
    private static final List<String> ALL_SUPPORTED = List.of(
            "txt", "md", "java", "py", "js", "ts", "vue", "html", "css", "scss",
            "xml", "json", "yaml", "yml", "sql", "sh", "bat", "c", "cpp", "h",
            "cs", "go", "rs", "rb", "php", "swift", "kt", "scala", "log", "csv",
            "pdf", "docx", "xlsx", "pptx"
    );

    private static final int MAX_CONTENT_LENGTH = 8000;

    public AiSummaryService(FileAppConfig appConfig, FileInfoService fileInfoService) {
        this.appConfig = appConfig;
        this.fileInfoService = fileInfoService;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 根据文件ID生成AI摘要（读取文件内容+文件名）
     */
    public String generateSummary(String fileId) {
        if (!enabled) return "AI摘要功能未启用";
        if (isApiKeyNotSet()) return "请先配置AI摘要的api-key";

        FileInfo fileInfo = fileInfoService.getFileInfoByFileId(fileId);
        if (fileInfo == null) throw new RuntimeException("文件不存在");

        String fileName = fileInfo.getFileName();
        String fileContent = readFileContent(fileInfo);
        if (fileContent == null) return "该文件类型暂不支持AI摘要";

        String prompt = String.format(
                "请为以下文件生成一段摘要，概括文件的核心内容和用途。摘要总字符数不得超过180（含标点、英文、数字）。\n\n" +
                "文件名：%s\n\n文件内容：\n%s\n\n请直接输出摘要文本，不要加任何前缀或说明。",
                fileName, fileContent
        );
        return callAiApi(prompt);
    }

    private boolean isApiKeyNotSet() {
        return apiKey == null || apiKey.isEmpty() || "YOUR_API_KEY_HERE".equals(apiKey);
    }

    private String readFileContent(FileInfo fileInfo) {
        String filePath = fileInfo.getFilePath();
        if (filePath == null || filePath.isEmpty()) return null;

        String suffix = getFileSuffix(fileInfo.getFileName());
        if (suffix == null || !ALL_SUPPORTED.contains(suffix.toLowerCase())) return null;

        String fullPath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + "/" + filePath;
        File file = new File(fullPath);
        if (!file.exists()) return null;

        try {
            String content;
            String s = suffix.toLowerCase();
            if (TEXT_SUFFIXES.contains(s)) {
                content = readTextFile(file);
            } else if ("pdf".equals(s)) {
                content = readPdfFile(file);
            } else if ("docx".equals(s)) {
                content = readDocxFile(file);
            } else if ("xlsx".equals(s)) {
                content = readXlsxFile(file);
            } else if ("pptx".equals(s)) {
                content = readPptxFile(file);
            } else {
                return null;
            }
            if (content == null || content.isBlank()) return null;
            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH) + "\n...(内容已截断)";
            }
            return content;
        } catch (Exception e) {
            logger.warn("读取文件内容失败: {} ({})", filePath, e.getMessage());
            return null;
        }
    }

    private String readTextFile(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String readPdfFile(File file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file)) {
            if (doc.isEncrypted()) return "该PDF已加密，无法提取文本";
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            return text.isBlank() ? "该PDF可能为扫描件，无法提取文本" : text;
        }
    }

    private String readDocxFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            return sb.toString();
        }
    }

    private String readXlsxFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {
            StringBuilder sb = new StringBuilder();
            wb.forEach(sheet -> {
                sb.append("[").append(sheet.getSheetName()).append("]\n");
                int rowCount = 0;
                for (var row : sheet) {
                    if (rowCount++ > 200) { sb.append("...(行数过多已截断)\n"); break; }
                    row.forEach(cell -> sb.append(cell.toString()).append("\t"));
                    sb.append("\n");
                }
            });
            return sb.toString();
        }
    }

    private String readPptxFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            StringBuilder sb = new StringBuilder();
            ppt.getSlides().forEach(slide -> {
                sb.append("[幻灯片]\n");
                slide.getShapes().forEach(shape -> {
                    if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape ts) {
                        sb.append(ts.getText()).append("\n");
                    }
                });
            });
            return sb.toString();
        }
    }

    private String getFileSuffix(String fileName) {
        if (fileName == null || !fileName.contains(".")) return null;
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    @SuppressWarnings("unchecked")
    private String callAiApi(String prompt) {
        String url = baseUrl + "/chat/completions";

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一个专业的技术文档摘要助手，能够简洁准确地概括文件内容。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 300,
                "temperature", 0.3
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return "AI接口返回为空";

            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (choices == null || choices.isEmpty()) return "AI接口未返回有效结果";

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) return "AI接口返回格式异常";

            String content = (String) message.get("content");
            return content != null ? content.trim() : "AI摘要生成失败";
        } catch (Exception e) {
            logger.error("AI摘要API调用失败", e);
            return "AI摘要生成失败: " + e.getMessage();
        }
    }
}
