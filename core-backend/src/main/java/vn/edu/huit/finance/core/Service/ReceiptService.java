package vn.edu.huit.finance.core.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import vn.edu.huit.finance.core.Dto.AiReceiptResponse;
import vn.edu.huit.finance.core.Dto.ReceiptDto;
import vn.edu.huit.finance.core.Entity.Receipt;
import vn.edu.huit.finance.core.Repository.ReceiptRepository;
import vn.edu.huit.finance.core.Service.Storage.FileStorageService;

import java.io.IOException;
import java.time.LocalDate;

@Service
public class ReceiptService {
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private WebClient webClient;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private ReceiptRepository receiptRepository;
    private final String PYTHON_AI_URL = "http://data-mining-api:8000/api/extract";

    @Transactional(rollbackFor = Exception.class)
    public ReceiptDto processReceipt(MultipartFile file) throws IOException {

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });

        AiReceiptResponse aiResponse = webClient.post()
                .uri(PYTHON_AI_URL)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(AiReceiptResponse.class)
                .block();

        if (aiResponse != null) {

            String uploadedImageUrl = fileStorageService.uploadFile(file);

            Receipt newReceipt = new Receipt();
            newReceipt.setVendorName(aiResponse.getVendorName());
            newReceipt.setExtractedTotal(aiResponse.getTotalAmount());
            newReceipt.setExtractedDate(LocalDate.parse(aiResponse.getReceiptDate()));
            newReceipt.setImageUrl(uploadedImageUrl);


            Receipt savedReceipt = receiptRepository.save(newReceipt);
            try {
                String currentUser = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
                auditLogService.logAction(currentUser, "CREATE_RECEIPT", "Quét hóa đơn AI thành công với số tiền: " + savedReceipt.getExtractedTotal() + " VNĐ");
            } catch (Exception e) {
            }
            ReceiptDto responseDto = new ReceiptDto();
            responseDto.setId(savedReceipt.getId());
            responseDto.setVendorName(savedReceipt.getVendorName());
            responseDto.setTotal(savedReceipt.getExtractedTotal());
            responseDto.setDate(savedReceipt.getExtractedDate());
            responseDto.setImageUrl(savedReceipt.getImageUrl());

            return responseDto;
        }

        throw new RuntimeException("AI không thể đọc được hóa đơn này!");
    }
}
