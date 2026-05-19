package com.fx.eshop_chatmind.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentDTO {
    private String id;

    private String kbId;

    private String filename;

    private String filetype;

    private Long size;

    private MetaData metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Data
    public static class MetaData {
        private String filePath;    // 文件存储路径
        private String contentHash; // 文件内容 SHA-256 哈希，用于检测变更
    }
}
