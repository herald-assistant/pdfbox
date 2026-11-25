package pl.demo.pdfbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {
    @Autowired MockMvc mvc;
    @Autowired DemoPdfService demo;

    @Test void uploadEndpointReturnsPagesAndChunks() throws Exception {
        mvc.perform(multipart("/api/pdf/analyze").file(new MockMultipartFile("file", "atlas.pdf", "application/pdf", demo.generate())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pages.length()").value(4))
                .andExpect(jsonPath("$.chunks[0].page").value(1));
    }
    @Test void returnsUsefulUploadErrors() throws Exception {
        mvc.perform(multipart("/api/pdf/analyze").file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "bad".getBytes())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(multipart("/api/pdf/analyze").file(new MockMultipartFile("file", "broken.pdf", "application/pdf", "%PDF-1.7\nbroken".getBytes())))
                .andExpect(status().isUnprocessableEntity());
    }
    @Test void validatesContextBeforeRetrieval() throws Exception {
        mvc.perform(post("/api/context").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"\",\"chunks\":[],\"topK\":99}"))
                .andExpect(status().isBadRequest());
    }
}
