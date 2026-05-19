package com.fx.eshop_chatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fx.eshop_chatmind.converter.DocumentConverter;
import com.fx.eshop_chatmind.mapper.ChunkBgeM3Mapper;
import com.fx.eshop_chatmind.mapper.DocumentMapper;
import com.fx.eshop_chatmind.mapper.KnowledgeBaseMapper;
import com.fx.eshop_chatmind.model.dto.DocumentDTO;
import com.fx.eshop_chatmind.model.entity.ChunkBgeM3;
import com.fx.eshop_chatmind.model.entity.Document;
import com.fx.eshop_chatmind.model.entity.KnowledgeBase;
import com.fx.eshop_chatmind.service.DocumentStorageService;
import com.fx.eshop_chatmind.service.KnowledgeBaseSyncService;
import com.fx.eshop_chatmind.service.MarkdownParserService;
import com.fx.eshop_chatmind.service.RagService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class KnowledgeBaseSyncServiceImpl implements KnowledgeBaseSyncService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentConverter documentConverter;
    private final DocumentStorageService documentStorageService;
    private final MarkdownParserService markdownParserService;
    private final RagService ragService;
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;

    @Override
    public SyncResult syncKnowledgeBase(String kbId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            log.warn("knowledge base not found: {}", kbId);
            return new SyncResult(kbId, 0, 0, 0, 0, 0, List.of("knowledge base not found"));
        }

        List<Document> documents = documentMapper.selectByKbId(kbId);
        int newCount = 0, modifiedCount = 0, deletedCount = 0, unchangedCount = 0;
        List<String> details = new ArrayList<>();

        for (Document document : documents) {
            try {
                DocumentDTO dto = documentConverter.toDTO(document);
                DocumentDTO.MetaData metadata = dto.getMetadata();

                if (metadata == null || metadata.getFilePath() == null) {
                    unchangedCount++;
                    continue;
                }

                Path filePath = documentStorageService.getFilePath(metadata.getFilePath());

                // file deleted from disk
                if (!Files.exists(filePath)) {
                    log.info("document file deleted, cleaning up: docId={}, path={}", document.getId(), metadata.getFilePath());
                    chunkBgeM3Mapper.deleteByDocId(document.getId());
                    documentMapper.deleteById(document.getId());
                    deletedCount++;
                    details.add("deleted: " + document.getFilename() + " (file missing)");
                    continue;
                }

                // compute current file hash
                String currentHash = computeFileHash(filePath.toString());
                String storedHash = metadata.getContentHash();

                if (storedHash == null) {
                    // new document: no hash recorded before
                    log.info("new document detected (no hash): docId={}, filename={}", document.getId(), document.getFilename());
                    processDocument(kbId, document, filePath);
                    updateDocumentHash(document, dto, currentHash);
                    newCount++;
                    details.add("new: " + document.getFilename());
                } else if (!currentHash.equals(storedHash)) {
                    // modified: hash mismatch
                    log.info("document modified: docId={}, filename={}, oldHash={}, newHash={}",
                            document.getId(), document.getFilename(), storedHash, currentHash);
                    chunkBgeM3Mapper.deleteByDocId(document.getId());
                    processDocument(kbId, document, filePath);
                    updateDocumentHash(document, dto, currentHash);
                    modifiedCount++;
                    details.add("modified: " + document.getFilename());
                } else {
                    unchangedCount++;
                }
            } catch (Exception e) {
                log.error("sync document failed: docId={}, error={}", document.getId(), e.getMessage(), e);
                details.add("failed: " + document.getFilename() + " - " + e.getMessage());
            }
        }

        SyncResult result = new SyncResult(kbId, documents.size(), newCount, modifiedCount, deletedCount, unchangedCount, details);
        log.info(result.toSummary());
        return result;
    }

    private void processDocument(String kbId, Document document, Path filePath) {
        String filetype = document.getFiletype();
        if (!"md".equalsIgnoreCase(filetype) && !"markdown".equalsIgnoreCase(filetype)) {
            log.warn("unsupported file type, skipping: {}", filetype);
            return;
        }

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            List<MarkdownParserService.MarkdownSection> sections = markdownParserService.parseMarkdown(inputStream);

            if (sections.isEmpty()) {
                log.warn("markdown parsed with no sections: docId={}", document.getId());
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            int chunkCount = 0;

            for (MarkdownParserService.MarkdownSection section : sections) {
                String title = section.getTitle();
                String content = section.getContent();

                if (title == null || title.trim().isEmpty()) {
                    continue;
                }

                float[] embedding = ragService.embed(title);

                ChunkBgeM3 chunk = ChunkBgeM3.builder()
                        .kbId(kbId)
                        .docId(document.getId())
                        .content(content != null ? content : "")
                        .metadata(null)
                        .embedding(embedding)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

                int result = chunkBgeM3Mapper.insert(chunk);
                if (result > 0) {
                    chunkCount++;
                }
            }
            log.info("document processed: docId={}, chunks={}", document.getId(), chunkCount);
        } catch (Exception e) {
            log.error("process document failed: docId={}", document.getId(), e);
        }
    }

    private void updateDocumentHash(Document document, DocumentDTO dto, String newHash) {
        try {
            DocumentDTO.MetaData metadata = dto.getMetadata();
            if (metadata == null) {
                metadata = new DocumentDTO.MetaData();
                dto.setMetadata(metadata);
            }
            metadata.setContentHash(newHash);
            dto.setUpdatedAt(LocalDateTime.now());

            Document updated = documentConverter.toEntity(dto);
            updated.setId(document.getId());
            updated.setKbId(document.getKbId());
            updated.setCreatedAt(document.getCreatedAt());
            updated.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(updated);
        } catch (JsonProcessingException e) {
            log.error("update document hash failed: docId={}", document.getId(), e);
        }
    }

    @Override
    public String computeFileHash(String filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(Path.of(filePath));
            byte[] hashBytes = digest.digest(fileBytes);
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            log.error("compute file hash failed: {}", filePath, e);
            return "";
        }
    }
}
