package com.smhrd.web.controller;

// Spring
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;

// MongoDB
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;

// PDFBox
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

// Lombok
import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.AllArgsConstructor;

// Java
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/vector-test")
@RequiredArgsConstructor
public class AdminVectorTestController {
    
    private final GridFsTemplate gridFsTemplate;
    private final MongoTemplate mongoTemplate;
    
    @Qualifier("vllmChat")
    private final WebClient vllmChat;
    
    @Qualifier("embeddingClient")
    private final WebClient embeddingClient;
    
    @Value("${vllm.chatbot.model}")
    private String chatbotModel;
    
    @Value("${vllm.chatbot.max-tokens}")
    private Integer maxTokens;
    
    @Value("${vllm.chatbot.temperature}")
    private Double temperature;
    
    @GetMapping
    public String vectorTestPage(Model model) {
        List<GridFSFile> pdfFiles = new ArrayList<>();
        gridFsTemplate.find(
            new Query(Criteria.where("metadata.contentType").is("application/pdf"))
        ).into(pdfFiles);
        
        List<FileInfo> fileInfos = pdfFiles.stream()
            .map(file -> {
                Document metadata = (Document) file.getMetadata();
                String originalName = metadata != null ? metadata.getString("originalName") : file.getFilename();
                String mimeType = metadata != null ? metadata.getString("contentType") : "application/pdf";
                String uploaderIdx = metadata != null ? metadata.getString("uploaderIdx") : "unknown";
                
                LocalDateTime uploadedAt = file.getUploadDate() != null
                    ? file.getUploadDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                    : LocalDateTime.now();
                
                return new FileInfo(
                    file.getObjectId().toString(),
                    file.getFilename(),
                    originalName,
                    file.getLength(),
                    mimeType,
                    uploadedAt,
                    uploaderIdx
                );
            })
            .collect(Collectors.toList());
        
        model.addAttribute("pdfFiles", fileInfos);
        return "admin/vector-test";
    }
    
    
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<?> uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            // 1. PDF 파일 검증
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "파일이 비어있습니다."));
            }
            
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body(Map.of("error", "PDF 파일만 업로드 가능합니다."));
            }
            
            // 2. GridFS에 저장
            Document metadata = new Document();
            metadata.put("contentType", "application/pdf");
            metadata.put("originalName", filename);
            metadata.put("uploadedAt", new Date());
            metadata.put("uploaderIdx", "test-user");
            
            ObjectId fileId = gridFsTemplate.store(
                file.getInputStream(),
                filename,
                "application/pdf",
                metadata
            );
            
            // 3. 업로드된 파일 정보 반환
            return ResponseEntity.ok(Map.of(
                "success", true,
                "fileId", fileId.toString(),
                "filename", filename,
                "size", file.getSize(),
                "message", "업로드 완료! 이제 벡터화를 진행하세요."
            ));
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    
    @PostMapping("/vectorize")
    @ResponseBody
    public ResponseEntity<?> vectorizePdf(@RequestBody VectorizeRequest request) {
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. GridFS에서 파일 가져오기
            GridFSFile gridFsFile = gridFsTemplate.findOne(
                new Query(Criteria.where("_id").is(new ObjectId(request.getFileId())))
            );
            
            if (gridFsFile == null) {
                return ResponseEntity.status(404).body(Map.of("error", "파일을 찾을 수 없습니다."));
            }
            
            // 2. 파일 바이트 배열로 읽기
            GridFsResource resource = gridFsTemplate.getResource(gridFsFile);
            byte[] pdfBytes = resource.getInputStream().readAllBytes();
            
            // 3. PDF 텍스트 추출 (제한 없이 강제 추출)
            String extractedText = parsePdfForce(pdfBytes);
            
            if (extractedText.isEmpty() || extractedText.length() < 50) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "텍스트를 추출할 수 없거나 내용이 너무 적습니다. (추출된 글자: " + extractedText.length() + "자)",
                    "status", "WARNING"
                ));
            }
            
            // 4. 텍스트 청킹
            List<String> chunks = chunkText(extractedText, request.getChunkSize(), request.getOverlap());
            
            // 5. 기존 청크 삭제
            mongoTemplate.remove(
                new Query(Criteria.where("file_id").is(request.getFileId())),
                "test_vector_chunks"
            );
            
            // 6. 임베딩 생성 & MongoDB 저장
            List<Document> vectorChunks = new ArrayList<>();
            
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                
                // 임베딩 생성
                List<Float> embedding = getEmbedding(chunkText);
                
                Document chunk = new Document();
                chunk.put("file_id", request.getFileId());
                chunk.put("file_name", gridFsFile.getFilename());
                chunk.put("chunk_index", i);
                chunk.put("content", chunkText);
                chunk.put("embedding", embedding);
                chunk.put("token_count", estimateTokenCount(chunkText));
                chunk.put("created_at", new Date());
                
                vectorChunks.add(chunk);
            }
            
            mongoTemplate.insert(vectorChunks, "test_vector_chunks");
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            int totalTokens = vectorChunks.stream()
                .mapToInt(doc -> (Integer) doc.get("token_count"))
                .sum();
            
            return ResponseEntity.ok(Map.of(
                "fileId", request.getFileId(),
                "filename", gridFsFile.getFilename(),
                "totalChunks", chunks.size(),
                "vectorizedChunks", vectorChunks.size(),
                "processingTimeMs", processingTime,
                "totalTokens", totalTokens,
                "extractedTextLength", extractedText.length(),
                "status", "SUCCESS"
            ));
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/chat")
    @ResponseBody
    public ResponseEntity<?> chatWithPdf(@RequestBody ChatRequest request) {
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. 질문 임베딩 생성
            List<Float> queryVector = getEmbedding(request.getQuestion());
            
            // 2. 1차 검색: Top-10 청크 가져오기
            int firstStageTopK = Math.min(request.getTopK() * 2, 10);
            List<Document> candidateChunks = searchRelevantChunks(
                request.getFileId(), 
                queryVector, 
                firstStageTopK
            );
            
            if (candidateChunks.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "answer", "관련된 내용을 찾을 수 없습니다.",
                    "chunks_used", 0,
                    "processing_time_ms", System.currentTimeMillis() - startTime
                ));
            }
            
            System.out.println("=== 1차 검색: " + candidateChunks.size() + "개 후보 청크 ===");
            
            // 3. 2차 Re-Ranking: 질문과 각 청크를 다시 비교
            List<Document> rerankedChunks = rerankChunks(request.getQuestion(), candidateChunks, request.getTopK());
            
            System.out.println("=== 2차 Re-Ranking: " + rerankedChunks.size() + "개 최종 선택 ===");
            
            // 4. 컨텍스트 생성 (이제 더 짧고 정확함)
            String context = buildCompactContext(rerankedChunks);
            String ragPrompt = buildTestRagPrompt(context, request.getQuestion());
            
            // 5. vLLM 호출
            String answer = callVllm(ragPrompt);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            return ResponseEntity.ok(Map.of(
                "answer", answer,
                "chunks_used", rerankedChunks.size(),
                "processing_time_ms", processingTime,
                "relevant_chunks", rerankedChunks.stream()
                    .map(chunk -> Map.of(
                        "chunk_index", chunk.get("chunk_index"),
                        "preview", chunk.getString("content").substring(0, Math.min(150, chunk.getString("content").length())) + "...",
                        "score", chunk.get("rerank_score")
                    ))
                    .collect(Collectors.toList())
            ));
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Re-Ranking: 질문과 청크를 다시 유사도 비교
    private List<Document> rerankChunks(String question, List<Document> chunks, int topK) {
        // 질문 임베딩 다시 생성 (또는 캐시 사용)
        List<Float> questionVector = getEmbedding(question);
        
        // 각 청크를 질문과 다시 비교
        for (Document chunk : chunks) {
            String chunkText = chunk.getString("content");
            
            // 청크의 각 문장을 질문과 비교
            List<Float> chunkVector = getEmbedding(chunkText.substring(0, Math.min(500, chunkText.length())));
            double relevanceScore = cosineSimilarity(questionVector, chunkVector);
            
            chunk.put("rerank_score", relevanceScore);
        }
        
        // Re-Ranking 점수 기준으로 정렬
        return chunks.stream()
            .sorted((a, b) -> Double.compare(
                (Double) b.get("rerank_score"),
                (Double) a.get("rerank_score")
            ))
            .limit(topK)
            .collect(Collectors.toList());
    }

    // 컴팩트한 컨텍스트 생성 (요약 버전)
    private String buildCompactContext(List<Document> chunks) {
        StringBuilder context = new StringBuilder();
        int totalLength = 0;
        int maxLength = 2000; // 2000자 제한
        
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String content = chunk.getString("content");
            
            // 각 청크를 300자로 제한
            if (content.length() > 300) {
                content = content.substring(0, 300) + "...";
            }
            
            if (totalLength + content.length() > maxLength) {
                break;
            }
            
            context.append(String.format("[관련 내용 %d]\n%s\n\n",
                i + 1,
                content
            ));
            
            totalLength += content.length();
        }
        
        System.out.println("=== 최종 컨텍스트: " + totalLength + "자 ===");
        
        return context.toString();
    }
    
    @GetMapping("/chunks/{fileId}")
    @ResponseBody
    public ResponseEntity<?> getChunks(@PathVariable String fileId) {
        List<Document> chunks = mongoTemplate.find(
            Query.query(Criteria.where("file_id").is(fileId)),
            Document.class,
            "test_vector_chunks"
        );
        
        return ResponseEntity.ok(Map.of(
            "total_chunks", chunks.size(),
            "chunks", chunks.stream()
                .map(doc -> Map.of(
                    "chunk_index", doc.get("chunk_index"),
                    "content", doc.get("content"),
                    "token_count", doc.get("token_count")
                ))
                .collect(Collectors.toList())
        ));
    }
    
    // ========== Private 메서드 ==========
    
    // PDF 강제 파싱 (크기/이미지 제한 없음, 테스트용)
    private String parsePdfForce(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            // 암호화만 체크
            if (doc.isEncrypted()) {
                throw new Exception("암호화된 PDF는 처리할 수 없습니다.");
            }
            
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            
            return (text == null) ? "" : text.strip();
        }
    }
    
    // 임베딩 생성
    private List<Float> getEmbedding(String text) {
        try {
            Map<String, Object> response = embeddingClient.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("texts", List.of(text)))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
            return embeddings.get(0).stream()
                .map(Double::floatValue)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    // 벡터 유사도 검색
    private List<Document> searchRelevantChunks(String fileId, List<Float> queryVector, int topK) {
        List<Document> chunks = mongoTemplate.find(
            Query.query(Criteria.where("file_id").is(fileId)),
            Document.class,
            "test_vector_chunks"
        );
        
        chunks.forEach(chunk -> {
            List<Double> chunkEmbedding = (List<Double>) chunk.get("embedding");
            if (chunkEmbedding != null) {
                List<Float> chunkVector = chunkEmbedding.stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());
                
                double similarity = cosineSimilarity(queryVector, chunkVector);
                chunk.put("similarity_score", similarity);
            } else {
                chunk.put("similarity_score", 0.0);
            }
        });
        
        return chunks.stream()
            .sorted((a, b) -> Double.compare(
                (Double) b.get("similarity_score"), 
                (Double) a.get("similarity_score")
            ))
            .limit(topK)
            .collect(Collectors.toList());
    }
    
    private double cosineSimilarity(List<Float> vec1, List<Float> vec2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < Math.min(vec1.size(), vec2.size()); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    private String buildContext(List<Document> chunks) {
        StringBuilder context = new StringBuilder();
        int totalLength = 0;
        int maxContextLength = 4000; // 4000자로 제한 (약 2000-2500 토큰)
        
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String content = chunk.getString("content");
            
            // 길이 제한 체크
            if (totalLength + content.length() > maxContextLength) {
                int remainingSpace = maxContextLength - totalLength;
                if (remainingSpace > 100) {
                    content = content.substring(0, remainingSpace) + "...";
                } else {
                    context.append("\n[나머지 청크는 길이 제한으로 생략됨]\n");
                    break;
                }
            }
            
            context.append(String.format("[청크 %d] (유사도: %.3f)\n%s\n\n---\n\n",
                chunk.get("chunk_index"),
                chunk.get("similarity_score"),
                content
            ));
            
            totalLength += content.length();
        }
        
        System.out.println("=== 컨텍스트 생성 완료 ===");
        System.out.println("총 길이: " + totalLength + "자");
        System.out.println("사용된 청크: " + Math.min(chunks.size(), (int)chunks.stream().filter(c -> context.toString().contains(c.getString("content").substring(0, 20))).count()) + "개");
        
        return context.toString();
    }
    
    private String buildTestRagPrompt(String context, String question) {
        // 컨텍스트가 너무 길면 자르기
        int maxContextLength = 6000; // 6000자로 제한
        if (context.length() > maxContextLength) {
            context = context.substring(0, maxContextLength) + "\n\n[... 길이 제한으로 일부 생략 ...]";
        }
        
        String prompt = String.format(
            "다음은 문서의 관련 내용입니다:\n\n%s\n\n" +
            "위 내용을 바탕으로 다음 질문에 답변해주세요:\n%s\n\n" +
            "답변은 반드시 제공된 문서 내용만을 근거로 작성하세요. " +
            "문서에 없는 내용은 추측하지 말고 \"문서에서 해당 내용을 찾을 수 없습니다\"라고 답변하세요.",
            context,
            question
        );
        
        System.out.println("=== RAG 프롬프트 생성 ===");
        System.out.println("컨텍스트 길이: " + context.length() + "자");
        System.out.println("질문 길이: " + question.length() + "자");
        System.out.println("전체 프롬프트 길이: " + prompt.length() + "자");
        
        return prompt;
    }
    
    private String callVllm(String prompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        
        messages.add(Map.of(
            "role", "system",
            "content", "너는 학습 도우미야. 사용자가 공부한 내용을 기반으로 정확하게 답변해줘. " +
                       "관련 문서가 제공되면 그 내용을 우선 참고하고, 없으면 일반 지식으로 답변해."
        ));
        
        messages.add(Map.of("role", "user", "content", prompt));
        
        // 입력 토큰 수 추정 (대략 1.5자 = 1토큰)
        int estimatedInputTokens = prompt.length() / 2; // 더 보수적으로 계산
        
        // 모델 최대 컨텍스트 (application.properties에서)
        int maxContextLength = 4096;
        
        // 동적으로 max_tokens 조정
        int dynamicMaxTokens = maxContextLength - estimatedInputTokens - 100; // 여유분 100
        dynamicMaxTokens = Math.max(100, Math.min(dynamicMaxTokens, maxTokens)); // 최소 100, 최대 설정값
        
        Map<String, Object> request = Map.of(
            "model", chatbotModel,
            "messages", messages,
            "max_tokens", dynamicMaxTokens,
            "temperature", temperature
        );
        
        try {
            System.out.println("=== vLLM 요청 디버깅 ===");
            System.out.println("Model: " + chatbotModel);
            System.out.println("Prompt length: " + prompt.length() + "자");
            System.out.println("Estimated input tokens: " + estimatedInputTokens);
            System.out.println("Dynamic max_tokens: " + dynamicMaxTokens);
            System.out.println("Total estimated: " + (estimatedInputTokens + dynamicMaxTokens));
            
            Map<String, Object> response = vllmChat.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            System.out.println("=== vLLM 응답 성공 ===");
            
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
            
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            System.err.println("=== vLLM 응답 에러 ===");
            System.err.println("Status Code: " + e.getStatusCode());
            System.err.println("Response Body: " + e.getResponseBodyAsString());
            
            return "❌ vLLM 호출 실패 (400 Bad Request)\n\n" +
                   "에러 응답: " + e.getResponseBodyAsString() + "\n\n" +
                   "프롬프트 길이: " + prompt.length() + "자\n" +
                   "추정 입력 토큰: " + estimatedInputTokens + "\n" +
                   "조정된 max_tokens: " + dynamicMaxTokens + "\n" +
                   "모델: " + chatbotModel;
            
        } catch (Exception e) {
            e.printStackTrace();
            return "죄송합니다. 일시적인 오류가 발생했습니다: " + e.getMessage();
        }
    }
    
    private List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?。])\\s+");
        
        StringBuilder currentChunk = new StringBuilder();
        int currentLength = 0;
        
        for (String sentence : sentences) {
            int sentenceLength = sentence.length();
            
            if (currentLength + sentenceLength > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                
                String chunkStr = currentChunk.toString();
                int overlapStart = Math.max(0, chunkStr.length() - overlap);
                currentChunk = new StringBuilder(chunkStr.substring(overlapStart));
                currentLength = currentChunk.length();
            }
            
            currentChunk.append(sentence).append(" ");
            currentLength += sentenceLength;
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }
    
    private int estimateTokenCount(String text) {
        return (int) (text.length() / 1.5);
    }
    
    // ========== DTO 클래스 ==========
    
    @Data
    public static class ChatRequest {
        private String fileId;
        private String question;
        private Integer topK = 5;
    }
    
    @Data
    public static class VectorizeRequest {
        private String fileId;
        private Integer chunkSize = 1000;
        private Integer overlap = 200;
    }
    
    @Getter
    @AllArgsConstructor
    public static class FileInfo {
        private final String id;
        private final String storedName;
        private final String originalName;
        private final long size;
        private final String mimeType;
        private final LocalDateTime uploadedAt;
        private final String uploaderIdx;
    }
}
