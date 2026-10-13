package com.designloop.service;

import com.designloop.domain.AttemptDocument;
import com.designloop.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsDiagramImagePersistsBytesAndRetainsLearnerNotes() throws Exception {
        AttachmentService service = new AttachmentService(tempDir.toString(), 20_000_000L, 40_000, 8);
        AttemptDocument attempt = new AttemptDocument();
        attempt.setId("attempt-test");
        attempt.setAttachments(new ArrayList<>());
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGNgYAAAAAMAASsJTYQAAAAASUVORK5CYII=");
        MockMultipartFile upload = new MockMultipartFile("file", "class-diagram.png", "image/png", png);

        AttemptDocument.AttachmentDocument saved = service.store(attempt, upload, "DIAGRAM", "Main classes and associations");
        List<AttachmentService.AttachmentEvidence> evidence = service.buildEvidence(List.of(saved));

        assertTrue(Files.exists(Path.of(saved.getStoragePath())));
        assertEquals("Main classes and associations", saved.getNotes());
        assertEquals(1, evidence.size());
        assertFalse(evidence.get(0).getImages().isEmpty());
        assertTrue(evidence.get(0).getExtractedText().contains("Main classes and associations"));
    }

    @Test
    void rejectsUnsupportedAttachmentExtension() {
        AttachmentService service = new AttachmentService(tempDir.toString(), 20_000_000L, 40_000, 8);
        AttemptDocument attempt = new AttemptDocument();
        attempt.setId("attempt-test");
        attempt.setAttachments(new ArrayList<>());
        MockMultipartFile upload = new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[]{1, 2, 3});

        ApiException exception = assertThrows(ApiException.class,
                () -> service.store(attempt, upload, "DIAGRAM", ""));

        assertEquals(415, exception.getStatus().value());
        assertEquals("UNSUPPORTED_FILE_TYPE", exception.getCode());
    }
}
