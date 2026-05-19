package com.fx.eshop_chatmind.service;

import com.fx.eshop_chatmind.model.request.CreateDocumentRequest;
import com.fx.eshop_chatmind.model.request.UpdateDocumentRequest;
import com.fx.eshop_chatmind.model.response.CreateDocumentResponse;
import com.fx.eshop_chatmind.model.response.GetDocumentsResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentFacadeService {
    GetDocumentsResponse getDocuments();

    GetDocumentsResponse getDocumentsByKbId(String kbId);

    CreateDocumentResponse createDocument(CreateDocumentRequest request);

    CreateDocumentResponse uploadDocument(String kbId, MultipartFile file);

    void deleteDocument(String documentId);

    void updateDocument(String documentId, UpdateDocumentRequest request);
}
