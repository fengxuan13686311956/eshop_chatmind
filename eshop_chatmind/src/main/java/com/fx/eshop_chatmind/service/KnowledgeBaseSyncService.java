package com.fx.eshop_chatmind.service;

import java.util.List;

public interface KnowledgeBaseSyncService {

    SyncResult syncKnowledgeBase(String kbId);

    String computeFileHash(String filePath);

    record SyncResult(
            String kbId,
            int totalDocuments,
            int newCount,
            int modifiedCount,
            int deletedCount,
            int unchangedCount,
            List<String> details
    ) {
        public String toSummary() {
            return String.format(
                    "knowledge base %s sync done: total=%d, new=%d, modified=%d, deleted=%d, unchanged=%d",
                    kbId, totalDocuments, newCount, modifiedCount, deletedCount, unchangedCount
            );
        }
    }
}
