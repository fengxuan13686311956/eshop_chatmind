package com.fx.eshop_chatmind.model.response;

import com.fx.eshop_chatmind.model.vo.DocumentVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetDocumentsResponse {
    private DocumentVO[] documents;
}

