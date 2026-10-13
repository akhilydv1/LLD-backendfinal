package com.designloop.service;

import com.designloop.domain.AttemptDocument;
import com.designloop.exception.ApiException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AttachmentService {
    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "md", "rtf", "odt");
    private static final Set<String> DIAGRAM_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "svg", "pdf", "drawio", "xml");
    private static final Pattern SAFE_BASE_NAME = Pattern.compile("[^a-zA-Z0-9._-]");
    private final Path rootDirectory;
    private final long maxFileBytes;
    private final int maxTextCharacters;
    private final int maxAttachments;
    private final Tika tika = new Tika();

    public AttachmentService(@Value("${designloop.uploads-dir:./data/uploads}") String uploadsDir,
                             @Value("${designloop.max-file-size-bytes:20971520}") long maxFileBytes,
                             @Value("${designloop.max-document-text-chars:40000}") int maxTextCharacters,
                             @Value("${designloop.max-attachments-per-attempt:8}") int maxAttachments) {
        this.rootDirectory = Paths.get(uploadsDir).toAbsolutePath().normalize();
        this.maxFileBytes = maxFileBytes;
        this.maxTextCharacters = maxTextCharacters;
        this.maxAttachments = maxAttachments;
    }

    public AttemptDocument.AttachmentDocument store(AttemptDocument attempt, MultipartFile multipartFile, String kind, String notes) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Choose a non-empty file to upload.", "file");
        }
        if (attempt.getAttachments().size() >= maxAttachments) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_LIMIT", "This attempt has reached the attachment limit of " + maxAttachments + ".", "file");
        }
        if (notes != null && notes.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_NOTES_TOO_LONG", "Attachment notes must be at most 2,000 characters.", "notes");
        }
        String normalizedKind = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DOCUMENT", "DIAGRAM").contains(normalizedKind)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_KIND", "kind must be DOCUMENT or DIAGRAM.", "kind");
        }
        if (multipartFile.getSize() > maxFileBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The file is larger than the configured 20 MB limit.", "file");
        }

        String originalName = StringUtils.cleanPath(multipartFile.getOriginalFilename() == null ? "upload" : multipartFile.getOriginalFilename());
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_NAME", "The uploaded filename is invalid.", "file");
        }
        String extension = extensionOf(originalName);
        Set<String> allowList = normalizedKind.equals("DOCUMENT") ? DOCUMENT_EXTENSIONS : DIAGRAM_EXTENSIONS;
        if (!allowList.contains(extension)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                    "Unsupported " + normalizedKind.toLowerCase(Locale.ROOT) + " file type: ." + extension + ".", "file");
        }

        try {
            byte[] bytes = multipartFile.getBytes();
            String detectedType = tika.detect(bytes, originalName).toLowerCase(Locale.ROOT);
            if (isBinaryImageExtension(extension) && !isCompatibleImage(extension, detectedType, bytes)) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_CONTENT_MISMATCH",
                        "The file content does not look like the selected image format.", "file");
            }
            if (extension.equals("pdf") && bytes.length > 4 && !(bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F')) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_CONTENT_MISMATCH", "The uploaded file does not contain a valid PDF signature.", "file");
            }

            String attachmentId = UUID.randomUUID().toString();
            String safeName = SAFE_BASE_NAME.matcher(Paths.get(originalName).getFileName().toString()).replaceAll("_");
            if (safeName.isBlank()) safeName = "upload." + extension;
            Path attemptDirectory = rootDirectory.resolve(attempt.getId()).normalize();
            if (!attemptDirectory.startsWith(rootDirectory)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD_PATH", "Invalid upload path.");
            }
            Files.createDirectories(attemptDirectory);
            Path target = attemptDirectory.resolve(attachmentId + "_" + safeName).normalize();
            if (!target.startsWith(attemptDirectory)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD_PATH", "Invalid upload path.");
            }
            Files.write(target, bytes);

            String extracted = "";
            boolean extractedFlag = false;
            if (isTextExtractable(extension)) {
                extracted = extractText(bytes, extension);
                extractedFlag = !extracted.isBlank();
                if (extracted.length() > maxTextCharacters) {
                    extracted = extracted.substring(0, maxTextCharacters) + "\n[Document text clipped at configured limit.]";
                }
            }

            AttemptDocument.AttachmentDocument metadata = new AttemptDocument.AttachmentDocument();
            metadata.setId(attachmentId);
            metadata.setName(originalName);
            metadata.setKind(normalizedKind);
            metadata.setSize(bytes.length);
            metadata.setContentType(canonicalMime(extension, detectedType));
            metadata.setStoragePath(target.toString());
            metadata.setChecksum(sha256(bytes));
            metadata.setNotes(notes == null ? "" : notes.trim());
            metadata.setExtractedText(extracted);
            metadata.setUploadedAt(Instant.now());
            metadata.setTextExtracted(extractedFlag);
            return metadata;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | TikaException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_PROCESSING_FAILED", "The file could not be read. Try exporting it to PDF, DOCX, TXT or PNG/JPG and upload again.", "file");
        }
    }

    public void deleteStoredFileBestEffort(AttemptDocument.AttachmentDocument attachment) {
        if (attachment == null || attachment.getStoragePath() == null || attachment.getStoragePath().isBlank()) return;
        try {
            Path path = Paths.get(attachment.getStoragePath()).toAbsolutePath().normalize();
            if (!path.startsWith(rootDirectory)) {
                log.warn("Refusing to delete attachment outside configured upload directory; attachmentId={}", attachment.getId());
                return;
            }
            Files.deleteIfExists(path);
        } catch (Exception ex) {
            // The MongoDB reference has already been removed. A failed disk cleanup leaves an orphan,
            // not a visible attachment; log it so an operator can clean it up later.
            log.warn("Could not remove uploaded file for attachmentId={}", attachment.getId(), ex);
        }
    }

    public List<AttachmentEvidence> buildEvidence(List<AttemptDocument.AttachmentDocument> attachments) {
        List<AttachmentEvidence> result = new ArrayList<>();
        for (AttemptDocument.AttachmentDocument attachment : attachments) {
            String text = attachment.getExtractedText() == null ? "" : attachment.getExtractedText();
            if (attachment.getNotes() != null && !attachment.getNotes().isBlank()) {
                text = "Learner-provided note for this attachment: " + attachment.getNotes().trim() + "\n" + text;
            }
            List<ImagePart> images = new ArrayList<>();
            try {
                Path path = safeStoredPath(attachment.getStoragePath());
                if (attachment.getKind().equals("DIAGRAM")) {
                    String ext = extensionOf(attachment.getName());
                    if (Set.of("png", "jpg", "jpeg", "webp").contains(ext)) {
                        if (Files.size(path) <= 8L * 1024 * 1024) {
                            images.add(new ImagePart(attachment.getContentType(), Base64.getEncoder().encodeToString(Files.readAllBytes(path)), attachment.getName()));
                        } else {
                            text += "\nDiagram image " + attachment.getName() + " was not attached visually because it exceeds the 8 MB inline-image review limit.";
                        }
                    } else if (ext.equals("pdf")) {
                        images.addAll(renderPdfPages(path, attachment.getName()));
                    } else if (ext.equals("svg")) {
                        text += "\nSVG diagram source (review labels and relationships from this markup):\n" + safeReadText(path, 60000);
                    } else if (ext.equals("xml") || ext.equals("drawio")) {
                        text += "\nDiagram XML source (review node labels and connectors):\n" + safeReadText(path, 60000);
                    }
                }
            } catch (Exception ex) {
                // Keep extracted text even if visual rendering of a diagram fails.
                text += "\n[Visual rendering unavailable for " + attachment.getName() + "; extracted text is still provided when available.]";
            }
            result.add(new AttachmentEvidence(attachment.getId(), attachment.getName(), attachment.getKind(),
                    attachment.getContentType(), text, images));
        }
        return result;
    }

    private List<ImagePart> renderPdfPages(Path path, String fileName) throws IOException {
        List<ImagePart> parts = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), 3);
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 100);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                byte[] png = output.toByteArray();
                if (png.length <= 5 * 1024 * 1024) {
                    parts.add(new ImagePart("image/png", Base64.getEncoder().encodeToString(png), fileName + " page " + (i + 1)));
                }
            }
        }
        return parts;
    }

    private Path safeStoredPath(String rawPath) throws IOException {
        Path path = Paths.get(rawPath).toAbsolutePath().normalize();
        if (!path.startsWith(rootDirectory)) throw new IOException("Stored attachment is outside the upload directory.");
        if (!Files.isRegularFile(path)) throw new IOException("Stored attachment file is missing.");
        return path;
    }

    private String safeReadText(Path path, int maxChars) throws IOException {
        String value = Files.readString(path);
        return value.length() > maxChars ? value.substring(0, maxChars) + "\n[Markup clipped.]" : value;
    }

    private String extractText(byte[] bytes, String extension) throws IOException, TikaException {
        // Tika handles PDF, Word, OpenDocument, RTF, Markdown and plain text.
        String text = tika.parseToString(new ByteArrayInputStream(bytes));
        return text == null ? "" : text.trim();
    }

    private boolean isTextExtractable(String extension) {
        return Set.of("pdf", "doc", "docx", "txt", "md", "rtf", "odt", "svg", "drawio", "xml").contains(extension);
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isBinaryImageExtension(String extension) {
        return Set.of("png", "jpg", "jpeg", "webp").contains(extension);
    }

    private boolean isCompatibleImage(String extension, String detected, byte[] bytes) {
        if (extension.equals("png")) return starts(bytes, new byte[]{(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        if (extension.equals("jpg") || extension.equals("jpeg")) return starts(bytes, new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF});
        if (extension.equals("webp")) return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        return detected.startsWith("image/");
    }

    private boolean starts(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (source[i] != prefix[i]) return false;
        return true;
    }

    private String canonicalMime(String extension, String detected) {
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "svg" -> "image/svg+xml";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "odt" -> "application/vnd.oasis.opendocument.text";
            case "rtf" -> "application/rtf";
            case "txt", "md" -> "text/plain";
            case "drawio", "xml" -> "application/xml";
            default -> detected == null ? "application/octet-stream" : detected;
        };
    }

    private String sha256(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is not available", ex); }
    }

    @Getter
    @AllArgsConstructor
    public static class AttachmentEvidence {
        private String id;
        private String name;
        private String kind;
        private String contentType;
        private String extractedText;
        private List<ImagePart> images;
    }

    @Getter
    @AllArgsConstructor
    public static class ImagePart {
        private String mimeType;
        private String base64Data;
        private String sourceName;
    }
}
